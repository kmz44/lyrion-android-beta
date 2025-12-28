-- Network Schema Optimizations
-- Agregar índices para mejorar el rendimiento de consultas

-- Índice para buscar solicitudes recibidas (following_id + status)
CREATE INDEX IF NOT EXISTS idx_connections_following_status 
ON public.connections(following_id, status);

-- Índice para buscar solicitudes enviadas (follower_id + status)
CREATE INDEX IF NOT EXISTS idx_connections_follower_status 
ON public.connections(follower_id, status);

-- Índice compuesto para verificar relaciones específicas
CREATE INDEX IF NOT EXISTS idx_connections_follower_following 
ON public.connections(follower_id, following_id);

-- Índice para búsquedas de usuarios por username
CREATE INDEX IF NOT EXISTS idx_users_username 
ON public.users(username);

-- Índice para mensajes directos (optimizar inbox)
CREATE INDEX IF NOT EXISTS idx_direct_messages_receiver_created 
ON public.direct_messages(receiver_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_direct_messages_sender_receiver 
ON public.direct_messages(sender_id, receiver_id);

-- Opcional: Vista para estadísticas de usuario
CREATE OR REPLACE VIEW user_stats AS
SELECT 
    u.id,
    u.username,
    COUNT(DISTINCT CASE WHEN c1.status = 'amigos' THEN c1.following_id END) as friends_count,
    COUNT(DISTINCT CASE WHEN c2.status = 'siguiendo' AND c2.follower_id = u.id THEN c2.following_id END) as following_count,
    COUNT(DISTINCT CASE WHEN c3.status = 'siguiendo' AND c3.following_id = u.id THEN c3.follower_id END) as followers_count
FROM public.users u
LEFT JOIN public.connections c1 ON u.id = c1.follower_id
LEFT JOIN public.connections c2 ON u.id = c2.follower_id
LEFT JOIN public.connections c3 ON u.id = c3.following_id
GROUP BY u.id, u.username;

-- Comentarios para documentación
COMMENT ON INDEX idx_connections_following_status IS 'Optimiza consultas de solicitudes recibidas y seguidores';
COMMENT ON INDEX idx_connections_follower_status IS 'Optimiza consultas de solicitudes enviadas y usuarios seguidos';
COMMENT ON INDEX idx_connections_follower_following IS 'Optimiza verificación de relación entre dos usuarios';
