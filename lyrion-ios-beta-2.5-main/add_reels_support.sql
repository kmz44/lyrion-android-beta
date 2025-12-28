-- Script para agregar soporte de Reels a la base de datos
-- Ejecutar este script en Supabase SQL Editor

-- 1. Agregar columna 'content_type' a la tabla posts
-- Esta columna indica si el contenido aparece en Posts, Reels, o Ambos
ALTER TABLE public.posts 
ADD COLUMN IF NOT EXISTS content_type text DEFAULT 'post' 
CHECK (content_type IN ('post', 'reel', 'both'));

-- 2. Crear índice para búsquedas más rápidas por content_type
CREATE INDEX IF NOT EXISTS idx_posts_content_type ON public.posts(content_type);

-- 3. Crear índice combinado para filtrar por content_type y created_at
CREATE INDEX IF NOT EXISTS idx_posts_content_type_created_at ON public.posts(content_type, created_at DESC);

-- 4. Agregar columna 'duration_seconds' para videos (opcional pero útil)
ALTER TABLE public.posts 
ADD COLUMN IF NOT EXISTS duration_seconds integer;

-- 5. Agregar columna 'thumbnail_url' para mostrar preview de videos
ALTER TABLE public.posts 
ADD COLUMN IF NOT EXISTS thumbnail_url text;

-- 6. Crear bucket de storage para videos si no existe (ejecutar en Storage)
-- Nota: Este comando debe ejecutarse desde la interfaz de Supabase Storage
-- o mediante la API de storage, no desde SQL Editor

-- 7. Comentarios sobre la estructura:
COMMENT ON COLUMN public.posts.content_type IS 'Indica dónde aparece el contenido: post (solo feed), reel (solo reels), both (ambos)';
COMMENT ON COLUMN public.posts.duration_seconds IS 'Duración del video en segundos (null para imágenes)';
COMMENT ON COLUMN public.posts.thumbnail_url IS 'URL del thumbnail/preview del video';

-- 8. Actualizar posts existentes para que sean 'post' por defecto
UPDATE public.posts 
SET content_type = 'post' 
WHERE content_type IS NULL;

-- 9. Crear vista para facilitar consultas de Reels
CREATE OR REPLACE VIEW public.reels_view AS
SELECT 
    p.*,
    u.username,
    u.avatar_url as creator_avatar_url,
    COUNT(DISTINCT pr.id) as reactions_count,
    COUNT(DISTINCT c.id) as comments_count
FROM public.posts p
LEFT JOIN public.users u ON p.creator_id = u.id
LEFT JOIN public.post_reactions pr ON p.id = pr.post_id
LEFT JOIN public.comments c ON p.id = c.post_id
WHERE p.content_type IN ('reel', 'both')
    AND p.media_type = 'video'
GROUP BY p.id, u.username, u.avatar_url
ORDER BY p.created_at DESC;

-- 10. Crear vista para Posts regulares
CREATE OR REPLACE VIEW public.posts_view AS
SELECT 
    p.*,
    u.username,
    u.avatar_url as creator_avatar_url,
    COUNT(DISTINCT pr.id) as reactions_count,
    COUNT(DISTINCT c.id) as comments_count
FROM public.posts p
LEFT JOIN public.users u ON p.creator_id = u.id
LEFT JOIN public.post_reactions pr ON p.id = pr.post_id
LEFT JOIN public.comments c ON p.id = c.post_id
WHERE p.content_type IN ('post', 'both')
GROUP BY p.id, u.username, u.avatar_url
ORDER BY p.created_at DESC;

-- Verificación: Mostrar estructura actualizada de la tabla posts
SELECT 
    column_name, 
    data_type, 
    column_default,
    is_nullable
FROM information_schema.columns 
WHERE table_name = 'posts' 
    AND table_schema = 'public'
ORDER BY ordinal_position;
