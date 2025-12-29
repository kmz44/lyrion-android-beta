-- ===================================================================
-- SOLUCIÓN DEFINITIVA PARA STATUS EN TIEMPO REAL
-- ===================================================================
-- Problema: Usuarios se quedan atascados en online/offline
-- Causa: Trigger dependía de heartbeats de otros usuarios
-- Solución: Sistema autónomo + Realtime directo
-- ===================================================================

-- ===================================================================
-- PASO 1: LIMPIAR TRIGGERS ANTERIORES
-- ===================================================================

DROP TRIGGER IF EXISTS trigger_heartbeat_check ON users;
DROP TRIGGER IF EXISTS trigger_notify_status_change ON users;
DROP TRIGGER IF EXISTS trigger_update_user_activity ON users;
DROP FUNCTION IF EXISTS trigger_check_inactive_on_heartbeat();
DROP FUNCTION IF EXISTS notify_user_status_change();

-- ===================================================================
-- PASO 2: TRIGGER QUE SE EJECUTA EN CADA CAMBIO DE is_active/status
-- ===================================================================

-- Este trigger simplemente notifica via Realtime cuando cambia el estado
-- No hace verificaciones, solo asegura que Realtime capte el cambio
CREATE OR REPLACE FUNCTION notify_status_change()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
  -- Actualizar last_active_at cuando se activa
  IF NEW.is_active = true AND (OLD.is_active IS DISTINCT FROM NEW.is_active) THEN
    NEW.last_active_at = NOW();
  END IF;
  
  -- Actualizar last_seen cuando se desactiva
  IF NEW.is_active = false AND (OLD.is_active IS DISTINCT FROM NEW.is_active) THEN
    NEW.last_seen = NOW();
  END IF;
  
  RETURN NEW;
END;
$$;

CREATE TRIGGER trigger_status_realtime
  BEFORE UPDATE OF is_active, status, chat_status ON users
  FOR EACH ROW
  EXECUTE FUNCTION notify_status_change();

-- ===================================================================
-- PASO 3: FUNCIÓN AUTOMÁTICA DE LIMPIEZA (llamada manualmente o por cron)
-- ===================================================================

-- Marca usuarios offline si no han enviado heartbeat en 60 segundos
CREATE OR REPLACE FUNCTION cleanup_inactive_users()
RETURNS TABLE(updated_count int)
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
  rows_updated int;
BEGIN
  -- Marcar como offline a usuarios sin heartbeat reciente
  UPDATE users
  SET 
    is_active = false,
    status = 'offline',
    last_seen = NOW()
  WHERE is_active = true
    AND last_heartbeat < (NOW() - INTERVAL '60 seconds');
    
  GET DIAGNOSTICS rows_updated = ROW_COUNT;
  
  RAISE NOTICE 'Marked % users as offline', rows_updated;
  RETURN QUERY SELECT rows_updated;
END;
$$;

GRANT EXECUTE ON FUNCTION cleanup_inactive_users() TO authenticated;

-- ===================================================================
-- PASO 4: OPTIMIZAR update_heartbeat RPC
-- ===================================================================

-- Eliminar función anterior con tipo de retorno diferente
DROP FUNCTION IF EXISTS update_heartbeat();

-- Función RPC que actualiza heartbeat Y verifica inactividad de forma eficiente
CREATE OR REPLACE FUNCTION update_heartbeat()
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
  current_user_id uuid;
  inactive_count int := 0;
BEGIN
  current_user_id := auth.uid();
  
  -- Actualizar heartbeat del usuario actual
  UPDATE users
  SET 
    last_heartbeat = NOW(),
    is_active = true
  WHERE id = current_user_id;
  
  -- Verificar usuarios inactivos (solo 10% del tiempo para no sobrecargar)
  IF random() < 0.1 THEN
    WITH inactive_updates AS (
      UPDATE users
      SET 
        is_active = false,
        status = 'offline',
        last_seen = NOW()
      WHERE is_active = true
        AND id != current_user_id  -- Excluir usuario actual
        AND last_heartbeat < (NOW() - INTERVAL '60 seconds')
      RETURNING id
    )
    SELECT COUNT(*)::int INTO inactive_count FROM inactive_updates;
  END IF;
  
  RETURN jsonb_build_object(
    'success', true,
    'heartbeat_updated', true,
    'inactive_users_updated', inactive_count
  );
END;
$$;

GRANT EXECUTE ON FUNCTION update_heartbeat() TO authenticated;

-- ===================================================================
-- PASO 5: ASEGURAR REALTIME ESTÁ CONFIGURADO
-- ===================================================================

-- Verificar que las tablas están en Realtime
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_publication_tables 
    WHERE pubname = 'supabase_realtime' AND tablename = 'users'
  ) THEN
    ALTER PUBLICATION supabase_realtime ADD TABLE users;
    RAISE NOTICE 'Added users table to Realtime';
  ELSE
    RAISE NOTICE 'users table already in Realtime';
  END IF;
END $$;

-- ===================================================================
-- PASO 6: POLÍTICAS RLS OPTIMIZADAS
-- ===================================================================

-- Asegurar que todos pueden leer estados de usuario (necesario para Realtime)
DROP POLICY IF EXISTS "Anyone can view user basic info" ON users;
CREATE POLICY "Anyone can view user basic info"
ON users FOR SELECT TO authenticated
USING (true);

-- Solo el usuario puede actualizar su propio estado
DROP POLICY IF EXISTS "Users can update their own status and activity" ON users;
CREATE POLICY "Users can update their own status and activity"
ON users FOR UPDATE TO authenticated
USING (id = auth.uid())
WITH CHECK (id = auth.uid());

-- ===================================================================
-- PASO 7: ÍNDICES PARA RENDIMIENTO
-- ===================================================================

CREATE INDEX IF NOT EXISTS idx_users_heartbeat_active 
ON users(last_heartbeat) 
WHERE is_active = true;

CREATE INDEX IF NOT EXISTS idx_users_status_active 
ON users(is_active, status);

CREATE INDEX IF NOT EXISTS idx_users_chat_status 
ON users(chat_status);

-- ===================================================================
-- VERIFICACIÓN Y PRUEBAS
-- ===================================================================

-- 1. Ver usuarios activos actualmente:
-- SELECT id, username, is_active, status, last_heartbeat, 
--        EXTRACT(EPOCH FROM (NOW() - last_heartbeat)) as seconds_since_heartbeat
-- FROM users 
-- WHERE is_active = true;

-- 2. Ejecutar limpieza manual:
-- SELECT * FROM cleanup_inactive_users();

-- 3. Verificar Realtime:
-- SELECT schemaname, tablename FROM pg_publication_tables 
-- WHERE pubname = 'supabase_realtime' AND tablename = 'users';

-- 4. Probar heartbeat:
-- SELECT update_heartbeat();

-- Mensaje de confirmación
DO $$
BEGIN
  RAISE NOTICE '✅ Realtime status system configured successfully';
END $$;
