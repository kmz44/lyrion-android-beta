-- =====================================================
-- CONFIGURACIÓN DE POLÍTICAS DE STORAGE
-- =====================================================
-- IMPORTANTE: Antes de ejecutar este script, debes:
-- 1. Ir a Storage en Supabase
-- 2. Crear manualmente los buckets: 'reel_videos' y 'video_thumbnails'
-- 3. Marcar ambos como "Public bucket"
-- =====================================================

-- ========================================
-- POLÍTICAS PARA BUCKET: reel_videos
-- ========================================

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
-- POLÍTICAS PARA BUCKET: video_thumbnails
-- ========================================

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
-- VERIFICACIÓN
-- ========================================

-- Ver todos los buckets creados
SELECT * FROM storage.buckets;

-- Ver todas las políticas de storage
SELECT 
    policyname,
    tablename,
    cmd,
    qual,
    with_check
FROM pg_policies
WHERE schemaname = 'storage' 
  AND tablename = 'objects'
ORDER BY policyname;
