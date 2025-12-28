-- ===================================================================
-- CONFIGURACIÓN DE MENSAJES EFÍMEROS Y ESTADOS EN TIEMPO REAL
-- ===================================================================
-- 1. Mensajes directos efímeros (máximo 2 días)
-- 2. Estados de usuario en tiempo real (is_active + status)
-- 3. Auto-timeout si no hay heartbeat
-- 4. Realtime habilitado para actualizaciones instantáneas
-- ===================================================================

-- ===================================================================
-- PARTE 1: AGREGAR COLUMNA last_heartbeat
-- ===================================================================

-- Agregar columna para tracking de heartbeat
ALTER TABLE users 
ADD COLUMN IF NOT EXISTS last_heartbeat timestamp with time zone DEFAULT now();

-- Crear índice para last_heartbeat
CREATE INDEX IF NOT EXISTS idx_users_last_heartbeat ON users(last_heartbeat) WHERE is_active = true;

-- ===================================================================
-- PARTE 2: HABILITAR REALTIME
-- ===================================================================

-- Habilitar Realtime en las tablas necesarias (no causa error si ya existen)
DO $$
BEGIN
  -- Agregar direct_messages si no está
  IF NOT EXISTS (
    SELECT 1 FROM pg_publication_tables 
    WHERE pubname = 'supabase_realtime' AND tablename = 'direct_messages'
  ) THEN
    ALTER PUBLICATION supabase_realtime ADD TABLE direct_messages;
  END IF;
  
  -- Agregar users si no está
  IF NOT EXISTS (
    SELECT 1 FROM pg_publication_tables 
    WHERE pubname = 'supabase_realtime' AND tablename = 'users'
  ) THEN
    ALTER PUBLICATION supabase_realtime ADD TABLE users;
  END IF;
  
  -- Agregar typing_indicators si no está
  IF NOT EXISTS (
    SELECT 1 FROM pg_publication_tables 
    WHERE pubname = 'supabase_realtime' AND tablename = 'typing_indicators'
  ) THEN
    ALTER PUBLICATION supabase_realtime ADD TABLE typing_indicators;
  END IF;
END $$;

-- ===================================================================
-- PARTE 3: AUTO-ELIMINACIÓN DE MENSAJES ANTIGUOS (> 2 DÍAS)
-- ===================================================================

-- Función para eliminar mensajes temporales mayores a 2 días
CREATE OR REPLACE FUNCTION delete_old_direct_messages()
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
BEGIN
  DELETE FROM direct_messages
  WHERE is_temporary = true
    AND created_at < (NOW() - INTERVAL '2 days');
    
  RAISE NOTICE 'Deleted old temporary messages';
END;
$$;

-- Trigger que limpia mensajes al insertar nuevos (1% probabilidad para no sobrecargar)
CREATE OR REPLACE FUNCTION cleanup_messages_on_insert()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
  IF random() < 0.01 THEN
    PERFORM delete_old_direct_messages();
  END IF;
  
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trigger_cleanup_messages ON direct_messages;
CREATE TRIGGER trigger_cleanup_messages
  AFTER INSERT ON direct_messages
  FOR EACH ROW
  EXECUTE FUNCTION cleanup_messages_on_insert();

-- ===================================================================
-- PARTE 4: AUTO-TIMEOUT POR INACTIVIDAD
-- ===================================================================

-- Función que detecta usuarios inactivos y los marca como offline
CREATE OR REPLACE FUNCTION check_inactive_users()
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
BEGIN
  -- Marcar como offline a usuarios sin heartbeat en los últimos 45 segundos
  UPDATE users
  SET 
    is_active = false,
    status = 'offline',
    last_seen = NOW()
  WHERE is_active = true
    AND last_heartbeat < (NOW() - INTERVAL '45 seconds');
    
  RAISE NOTICE 'Updated inactive users to offline';
END;
$$;

-- Trigger SIEMPRE ejecutado cuando is_active o status cambia
-- Esto asegura que los cambios se propagan inmediatamente via Realtime
CREATE OR REPLACE FUNCTION notify_user_status_change()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
  -- Este trigger simplemente permite que Realtime capture el cambio
  -- No hace nada especial, pero asegura que el evento se propague
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trigger_notify_status_change ON users;
CREATE TRIGGER trigger_notify_status_change
  AFTER UPDATE OF is_active, status ON users
  FOR EACH ROW
  WHEN (NEW.is_active IS DISTINCT FROM OLD.is_active OR NEW.status IS DISTINCT FROM OLD.status)
  EXECUTE FUNCTION notify_user_status_change();

-- Trigger que se ejecuta periódicamente en cada UPDATE de heartbeat (100% de las veces)
CREATE OR REPLACE FUNCTION trigger_check_inactive_on_heartbeat()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
  -- Ejecutar SIEMPRE, no solo 5% del tiempo
  PERFORM check_inactive_users();
  
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trigger_heartbeat_check ON users;
CREATE TRIGGER trigger_heartbeat_check
  AFTER UPDATE OF last_heartbeat ON users
  FOR EACH ROW
  WHEN (NEW.last_heartbeat IS DISTINCT FROM OLD.last_heartbeat)
  EXECUTE FUNCTION trigger_check_inactive_on_heartbeat();

-- ===================================================================
-- PARTE 5: ACTUALIZACIÓN AUTOMÁTICA DE TIMESTAMPS
-- ===================================================================

-- Trigger para actualizar last_active_at y last_seen
CREATE OR REPLACE FUNCTION update_user_activity_timestamps()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
  -- Actualizar last_active_at cuando is_active cambia a true
  IF NEW.is_active = true AND (OLD.is_active IS DISTINCT FROM NEW.is_active) THEN
    NEW.last_active_at = NOW();
  END IF;
  
  -- Actualizar last_seen cuando is_active cambia a false
  IF NEW.is_active = false AND (OLD.is_active IS DISTINCT FROM NEW.is_active) THEN
    NEW.last_seen = NOW();
  END IF;
  
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trigger_update_user_activity ON users;
CREATE TRIGGER trigger_update_user_activity
  BEFORE UPDATE OF is_active ON users
  FOR EACH ROW
  EXECUTE FUNCTION update_user_activity_timestamps();

-- ===================================================================
-- PARTE 6: POLÍTICAS RLS PARA REALTIME
-- ===================================================================

-- Políticas para direct_messages
DROP POLICY IF EXISTS "Users can view their own messages" ON direct_messages;
DROP POLICY IF EXISTS "Users can insert their own messages" ON direct_messages;
DROP POLICY IF EXISTS "Users can update their own messages" ON direct_messages;
DROP POLICY IF EXISTS "Users can delete their own messages" ON direct_messages;
DROP POLICY IF EXISTS "Users can view their conversations" ON direct_messages;
DROP POLICY IF EXISTS "Users can send messages" ON direct_messages;
DROP POLICY IF EXISTS "Users can mark messages as read" ON direct_messages;
DROP POLICY IF EXISTS "Users can delete their messages" ON direct_messages;

CREATE POLICY "Users can view their conversations"
ON direct_messages FOR SELECT TO authenticated
USING (sender_id = auth.uid() OR receiver_id = auth.uid());

CREATE POLICY "Users can send messages"
ON direct_messages FOR INSERT TO authenticated
WITH CHECK (sender_id = auth.uid());

CREATE POLICY "Users can mark messages as read"
ON direct_messages FOR UPDATE TO authenticated
USING (receiver_id = auth.uid())
WITH CHECK (receiver_id = auth.uid());

CREATE POLICY "Users can delete their messages"
ON direct_messages FOR DELETE TO authenticated
USING (sender_id = auth.uid() OR receiver_id = auth.uid());

-- Políticas para typing_indicators
DROP POLICY IF EXISTS "Users can view typing indicators" ON typing_indicators;
DROP POLICY IF EXISTS "Users can update typing indicators" ON typing_indicators;
DROP POLICY IF EXISTS "Users can view typing of their partners" ON typing_indicators;
DROP POLICY IF EXISTS "Users can update their own typing status" ON typing_indicators;

CREATE POLICY "Users can view typing of their partners"
ON typing_indicators FOR SELECT TO authenticated
USING (partner_id = auth.uid());

CREATE POLICY "Users can update their own typing status"
ON typing_indicators FOR ALL TO authenticated
USING (user_id = auth.uid())
WITH CHECK (user_id = auth.uid());

-- Políticas para users (solo para is_active y status - lectura pública)
DROP POLICY IF EXISTS "Anyone can view user status" ON users;
DROP POLICY IF EXISTS "Users can update their own status" ON users;
DROP POLICY IF EXISTS "Anyone can view user basic info" ON users;
DROP POLICY IF EXISTS "Users can update their own status and activity" ON users;

CREATE POLICY "Anyone can view user basic info"
ON users FOR SELECT TO authenticated
USING (true);

CREATE POLICY "Users can update their own status and activity"
ON users FOR UPDATE TO authenticated
USING (id = auth.uid())
WITH CHECK (id = auth.uid());

-- ===================================================================
-- PARTE 7: ÍNDICES PARA RENDIMIENTO
-- ===================================================================

CREATE INDEX IF NOT EXISTS idx_direct_messages_created_at ON direct_messages(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_direct_messages_sender_receiver ON direct_messages(sender_id, receiver_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_direct_messages_is_temporary ON direct_messages(is_temporary, created_at) WHERE is_temporary = true;
CREATE INDEX IF NOT EXISTS idx_typing_indicators_partner ON typing_indicators(partner_id, is_typing);
CREATE INDEX IF NOT EXISTS idx_users_is_active ON users(is_active) WHERE is_active = true;
CREATE INDEX IF NOT EXISTS idx_users_status ON users(status);

-- ===================================================================
-- PARTE 8: FUNCIÓN RPC PARA HEARTBEAT (optimizada)
-- ===================================================================

CREATE OR REPLACE FUNCTION update_heartbeat()
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
BEGIN
  UPDATE users
  SET 
    last_heartbeat = NOW(),
    is_active = true
  WHERE id = auth.uid();
END;
$$;

GRANT EXECUTE ON FUNCTION update_heartbeat() TO authenticated;

-- ===================================================================
-- PARTE 9: FUNCIONES DE UTILIDAD
-- ===================================================================

-- Función para limpieza manual de mensajes
CREATE OR REPLACE FUNCTION cleanup_all_expired_messages()
RETURNS TABLE(deleted_count bigint)
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
  rows_deleted bigint;
BEGIN
  DELETE FROM direct_messages
  WHERE is_temporary = true
    AND created_at < (NOW() - INTERVAL '2 days');
    
  GET DIAGNOSTICS rows_deleted = ROW_COUNT;
  
  RETURN QUERY SELECT rows_deleted;
END;
$$;

-- Función para limpieza manual de usuarios inactivos
CREATE OR REPLACE FUNCTION force_check_inactive_users()
RETURNS TABLE(users_marked_offline bigint)
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
  rows_updated bigint;
BEGIN
  UPDATE users
  SET 
    is_active = false,
    status = 'offline',
    last_seen = NOW()
  WHERE is_active = true
    AND last_heartbeat < (NOW() - INTERVAL '45 seconds');
    
  GET DIAGNOSTICS rows_updated = ROW_COUNT;
  
  RETURN QUERY SELECT rows_updated;
END;
$$;

GRANT EXECUTE ON FUNCTION delete_old_direct_messages() TO authenticated;
GRANT EXECUTE ON FUNCTION cleanup_all_expired_messages() TO authenticated;
GRANT EXECUTE ON FUNCTION check_inactive_users() TO authenticated;
GRANT EXECUTE ON FUNCTION force_check_inactive_users() TO authenticated;

-- ===================================================================
-- PARTE 10: ASEGURAR VALORES POR DEFECTO
-- ===================================================================

-- Asegurar que is_temporary es true por defecto
ALTER TABLE direct_messages 
ALTER COLUMN is_temporary SET DEFAULT true;

-- Asegurar que usuarios nuevos empiezan con heartbeat actualizado
ALTER TABLE users
ALTER COLUMN last_heartbeat SET DEFAULT NOW();

-- ===================================================================
-- VERIFICACIÓN
-- ===================================================================
-- Ejecutar estas consultas para verificar:

-- 1. Ver políticas RLS de direct_messages
-- SELECT * FROM pg_policies WHERE tablename = 'direct_messages';

-- 2. Ver mensajes antiguos que serán eliminados
-- SELECT COUNT(*) FROM direct_messages 
-- WHERE is_temporary = true AND created_at < (NOW() - INTERVAL '2 days');

-- 3. Ejecutar limpieza manualmente
-- SELECT * FROM cleanup_all_expired_messages();

-- 4. Ver usuarios inactivos
-- SELECT id, username, is_active, status, last_heartbeat
-- FROM users
-- WHERE is_active = true AND last_heartbeat < (NOW() - INTERVAL '45 seconds');

-- 5. Forzar check de inactivos
-- SELECT * FROM force_check_inactive_users();

-- 6. Verificar que Realtime está habilitado
-- SELECT schemaname, tablename FROM pg_publication_tables 
-- WHERE pubname = 'supabase_realtime';
