-- =====================================================
-- SCRIPT COMPLETO PARA CREAR BUCKETS DE STORAGE Y POLÍTICAS
-- =====================================================
-- Este script crea los buckets para videos y sus políticas de seguridad
-- =====================================================

-- ========================================
-- 1. CREAR BUCKETS
-- ========================================

-- Crear bucket para videos (reels)
INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES (
    'reel_videos',
    'reel_videos',
    true,
    104857600, -- 100 MB en bytes
    ARRAY['video/mp4', 'video/quicktime', 'video/x-msvideo', 'video/x-matroska']::text[]
)
ON CONFLICT (id) DO UPDATE SET
    public = true,
    file_size_limit = 104857600,
    allowed_mime_types = ARRAY['video/mp4', 'video/quicktime', 'video/x-msvideo', 'video/x-matroska']::text[];

-- Crear bucket para thumbnails de videos
INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES (
    'video_thumbnails',
    'video_thumbnails',
    true,
    5242880, -- 5 MB en bytes
    ARRAY['image/jpeg', 'image/png', 'image/webp']::text[]
)
ON CONFLICT (id) DO UPDATE SET
    public = true,
    file_size_limit = 5242880,
    allowed_mime_types = ARRAY['image/jpeg', 'image/png', 'image/webp']::text[];

-- ========================================
-- 2. POLÍTICAS PARA BUCKET: reel_videos
-- ========================================

-- Limpiar políticas existentes (si las hay)
DROP POLICY IF EXISTS "Usuarios autenticados pueden subir videos" ON storage.objects;
DROP POLICY IF EXISTS "Cualquiera puede ver videos públicos" ON storage.objects;
DROP POLICY IF EXISTS "Usuarios pueden actualizar sus propios videos" ON storage.objects;
DROP POLICY IF EXISTS "Usuarios pueden eliminar sus propios videos" ON storage.objects;

-- Política de INSERT: Usuarios autenticados pueden subir videos
CREATE POLICY "Usuarios autenticados pueden subir videos"
ON storage.objects FOR INSERT
TO authenticated
WITH CHECK (
    bucket_id = 'reel_videos' 
    AND auth.uid()::text = (storage.foldername(name))[1]
);

-- Política de SELECT: Cualquiera puede ver videos públicos
CREATE POLICY "Cualquiera puede ver videos públicos"
ON storage.objects FOR SELECT
TO public
USING (bucket_id = 'reel_videos');

-- Política de UPDATE: Usuarios pueden actualizar sus propios videos
CREATE POLICY "Usuarios pueden actualizar sus propios videos"
ON storage.objects FOR UPDATE
TO authenticated
USING (
    bucket_id = 'reel_videos' 
    AND auth.uid()::text = (storage.foldername(name))[1]
)
WITH CHECK (
    bucket_id = 'reel_videos' 
    AND auth.uid()::text = (storage.foldername(name))[1]
);

-- Política de DELETE: Usuarios pueden eliminar sus propios videos
CREATE POLICY "Usuarios pueden eliminar sus propios videos"
ON storage.objects FOR DELETE
TO authenticated
USING (
    bucket_id = 'reel_videos' 
    AND auth.uid()::text = (storage.foldername(name))[1]
);

-- ========================================
-- 3. POLÍTICAS PARA BUCKET: video_thumbnails
-- ========================================

-- Limpiar políticas existentes (si las hay)
DROP POLICY IF EXISTS "Usuarios autenticados pueden subir thumbnails" ON storage.objects;
DROP POLICY IF EXISTS "Cualquiera puede ver thumbnails" ON storage.objects;
DROP POLICY IF EXISTS "Usuarios pueden actualizar sus propios thumbnails" ON storage.objects;
DROP POLICY IF EXISTS "Usuarios pueden eliminar sus propios thumbnails" ON storage.objects;

-- Política de INSERT: Usuarios autenticados pueden subir thumbnails
CREATE POLICY "Usuarios autenticados pueden subir thumbnails"
ON storage.objects FOR INSERT
TO authenticated
WITH CHECK (
    bucket_id = 'video_thumbnails' 
    AND auth.uid()::text = (storage.foldername(name))[1]
);

-- Política de SELECT: Cualquiera puede ver thumbnails
CREATE POLICY "Cualquiera puede ver thumbnails"
ON storage.objects FOR SELECT
TO public
USING (bucket_id = 'video_thumbnails');

-- Política de UPDATE: Usuarios pueden actualizar sus propios thumbnails
CREATE POLICY "Usuarios pueden actualizar sus propios thumbnails"
ON storage.objects FOR UPDATE
TO authenticated
USING (
    bucket_id = 'video_thumbnails' 
    AND auth.uid()::text = (storage.foldername(name))[1]
)
WITH CHECK (
    bucket_id = 'video_thumbnails' 
    AND auth.uid()::text = (storage.foldername(name))[1]
);

-- Política de DELETE: Usuarios pueden eliminar sus propios thumbnails
CREATE POLICY "Usuarios pueden eliminar sus propios thumbnails"
ON storage.objects FOR DELETE
TO authenticated
USING (
    bucket_id = 'video_thumbnails' 
    AND auth.uid()::text = (storage.foldername(name))[1]
);

-- ========================================
-- 4. VERIFICACIÓN
-- ========================================

-- Ver todos los buckets creados
SELECT 
    id,
    name,
    public,
    file_size_limit,
    allowed_mime_types,
    created_at
FROM storage.buckets
ORDER BY created_at DESC;

-- Ver todas las políticas de storage para reel_videos y video_thumbnails
SELECT 
    schemaname,
    tablename,
    policyname,
    permissive,
    roles,
    cmd,
    qual,
    with_check
FROM pg_policies
WHERE schemaname = 'storage' 
  AND tablename = 'objects'
  AND (
    policyname LIKE '%videos%' 
    OR policyname LIKE '%thumbnails%'
  )
ORDER BY policyname;

-- ========================================
-- RESULTADO ESPERADO
-- ========================================
-- Deberías ver 5 buckets en total:
-- 1. avatars
-- 2. post_media
-- 3. banners
-- 4. reel_videos (NUEVO)
-- 5. video_thumbnails (NUEVO)
--
-- Y 8 políticas nuevas (4 para cada bucket)
-- ========================================
