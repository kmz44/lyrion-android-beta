-- ============================================
-- SCRIPT COMPLETO PARA ACTUALIZAR LA RED SOCIAL
-- Ejecutar en Supabase SQL Editor
-- ============================================

-- 1. Actualizar el constraint de la tabla connections para permitir 'siguiendo'
ALTER TABLE public.connections DROP CONSTRAINT IF EXISTS connections_status_check;

ALTER TABLE public.connections 
ADD CONSTRAINT connections_status_check 
CHECK (status = ANY (ARRAY['pendiente'::text, 'amigos'::text, 'bloqueado'::text, 'siguiendo'::text]));

COMMENT ON COLUMN public.connections.status IS 'Estados: siguiendo (follow unilateral), pendiente (solicitud enviada), amigos (amistad mutua), bloqueado';

-- 2. Crear índices para mejorar el rendimiento
CREATE INDEX IF NOT EXISTS idx_connections_following_status 
ON public.connections(following_id, status);

CREATE INDEX IF NOT EXISTS idx_connections_follower_status 
ON public.connections(follower_id, status);

CREATE INDEX IF NOT EXISTS idx_connections_follower_following 
ON public.connections(follower_id, following_id);

CREATE INDEX IF NOT EXISTS idx_users_username 
ON public.users(username);

CREATE INDEX IF NOT EXISTS idx_direct_messages_receiver_created 
ON public.direct_messages(receiver_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_direct_messages_sender_receiver 
ON public.direct_messages(sender_id, receiver_id);

-- 3. Habilitar RLS en la tabla connections
ALTER TABLE public.connections ENABLE ROW LEVEL SECURITY;

-- 4. Eliminar políticas existentes si existen
DROP POLICY IF EXISTS "Users can view their own connections" ON public.connections;
DROP POLICY IF EXISTS "Users can create connections" ON public.connections;
DROP POLICY IF EXISTS "Users can update their connections" ON public.connections;
DROP POLICY IF EXISTS "Users can delete their connections" ON public.connections;
DROP POLICY IF EXISTS "Users can view connections where they are involved" ON public.connections;

-- 5. Crear políticas RLS para connections

-- Política de LECTURA: Los usuarios pueden ver conexiones donde están involucrados
CREATE POLICY "Users can view connections where they are involved"
ON public.connections
FOR SELECT
USING (
  auth.uid() = follower_id OR auth.uid() = following_id
);

-- Política de INSERCIÓN: Los usuarios pueden crear conexiones donde ellos son el follower
CREATE POLICY "Users can create connections"
ON public.connections
FOR INSERT
WITH CHECK (
  auth.uid() = follower_id
);

-- Política de ACTUALIZACIÓN: Los usuarios pueden actualizar conexiones donde están involucrados
CREATE POLICY "Users can update their connections"
ON public.connections
FOR UPDATE
USING (
  auth.uid() = follower_id OR auth.uid() = following_id
)
WITH CHECK (
  auth.uid() = follower_id OR auth.uid() = following_id
);

-- Política de ELIMINACIÓN: Los usuarios pueden eliminar conexiones donde son el follower
CREATE POLICY "Users can delete their connections"
ON public.connections
FOR DELETE
USING (
  auth.uid() = follower_id
);

-- 6. Comentarios para documentación
COMMENT ON INDEX idx_connections_following_status IS 'Optimiza consultas de solicitudes recibidas y seguidores';
COMMENT ON INDEX idx_connections_follower_status IS 'Optimiza consultas de solicitudes enviadas y usuarios seguidos';
COMMENT ON INDEX idx_connections_follower_following IS 'Optimiza verificación de relación entre dos usuarios';

-- 7. Verificar que todo está correcto
SELECT 'Script ejecutado correctamente! La tabla connections ahora tiene:' AS resultado
UNION ALL
SELECT '✓ Estados: siguiendo, pendiente, amigos, bloqueado'
UNION ALL  
SELECT '✓ Políticas RLS configuradas'
UNION ALL
SELECT '✓ Índices creados para mejor rendimiento';

