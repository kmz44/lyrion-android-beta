-- ============================================
-- FIX: Políticas RLS para tabla friends
-- ============================================
-- Problema: Después de aceptar solicitud de amistad,
-- el contador de amigos se va a 0 porque no se puede
-- leer la tabla friends de otros usuarios
-- ============================================

-- 1. Eliminar políticas existentes que puedan estar bloqueando SELECT
DROP POLICY IF EXISTS "Users can view friends where they are user_id" ON friends;
DROP POLICY IF EXISTS "Users can view friends where they are friend_id" ON friends;
DROP POLICY IF EXISTS "Users can view their own friendships" ON friends;
DROP POLICY IF EXISTS "Users can view all friendships" ON friends;

-- 2. Crear política permisiva para SELECT
-- Esta política permite a CUALQUIER usuario autenticado ver TODAS las amistades
-- Esto es necesario para mostrar contadores de amigos en perfiles públicos
CREATE POLICY "Anyone can view all active friendships"
ON friends
FOR SELECT
TO authenticated
USING (status = 'active');

-- 3. Mantener políticas restrictivas para INSERT
-- Solo la función accept_friend_request puede insertar (usando SECURITY DEFINER)
DROP POLICY IF EXISTS "Only RPC can insert friendships" ON friends;
CREATE POLICY "Only RPC can insert friendships"
ON friends
FOR INSERT
TO authenticated
WITH CHECK (false);  -- Nadie puede hacer INSERT directo, solo a través del RPC

-- 4. Política para DELETE (eliminar amistades)
DROP POLICY IF EXISTS "Users can delete their own friendships" ON friends;
CREATE POLICY "Users can delete their own friendships"
ON friends
FOR DELETE
TO authenticated
USING (
  user_id = auth.uid() OR friend_id = auth.uid()
);

-- 5. Verificar que la función RPC existe
-- Si no existe, crearla
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_proc WHERE proname = 'accept_friend_request'
  ) THEN
    CREATE OR REPLACE FUNCTION public.accept_friend_request(request_id uuid)
    RETURNS void
    LANGUAGE plpgsql
    SECURITY DEFINER
    AS $func$
    DECLARE
      sender uuid;
      receiver uuid;
    BEGIN
      -- Obtener sender y receiver del friend_request
      SELECT sender_id, receiver_id INTO sender, receiver
      FROM friend_requests
      WHERE id = request_id AND receiver_id = auth.uid() AND status = 'pending';

      -- Verificar que el request existe y que el usuario actual es el receiver
      IF NOT FOUND THEN
        RAISE EXCEPTION 'Friend request not found or you are not the receiver';
      END IF;

      -- Insertar amistad bidireccional
      INSERT INTO friends (user_id, friend_id, status)
      VALUES (sender, receiver, 'active')
      ON CONFLICT DO NOTHING;

      INSERT INTO friends (user_id, friend_id, status)
      VALUES (receiver, sender, 'active')
      ON CONFLICT DO NOTHING;

      -- Eliminar la solicitud
      DELETE FROM friend_requests WHERE id = request_id;
    END;
    $func$;

    -- Grant execute to authenticated users
    GRANT EXECUTE ON FUNCTION public.accept_friend_request(uuid) TO authenticated;
  END IF;
END$$;

-- 6. Asegurar que la tabla tiene índices para mejor rendimiento
CREATE INDEX IF NOT EXISTS idx_friends_user_id_status ON friends(user_id, status);
CREATE INDEX IF NOT EXISTS idx_friends_friend_id_status ON friends(friend_id, status);

-- ============================================
-- VERIFICACIÓN
-- ============================================
-- Ejecutar esta consulta para verificar las políticas:
-- SELECT * FROM pg_policies WHERE tablename = 'friends';
