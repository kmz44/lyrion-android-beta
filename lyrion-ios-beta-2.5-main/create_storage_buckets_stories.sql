-- =====================================================
-- CREAR BUCKETS DE STORAGE PARA STORIES Y MEDIA
-- =====================================================
-- Este script crea los buckets necesarios para el sistema de estados
-- y configura las políticas de acceso público

-- =====================================================
-- BUCKET: stories (para estados/stories)
-- =====================================================

INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES (
  'stories',
  'stories',
  true, -- público
  52428800, -- 50MB límite
  ARRAY['image/jpeg', 'image/png', 'image/gif', 'image/webp', 'video/mp4', 'video/quicktime', 'video/webm']
)
ON CONFLICT (id) DO NOTHING;

-- Política: Cualquier usuario autenticado puede subir a stories
CREATE POLICY "Authenticated users can upload stories"
ON storage.objects FOR INSERT
TO authenticated
WITH CHECK (bucket_id = 'stories');

-- Política: Cualquiera puede ver stories (bucket público)
CREATE POLICY "Public access to view stories"
ON storage.objects FOR SELECT
TO public
USING (bucket_id = 'stories');

-- Política: Los usuarios pueden actualizar sus propios archivos
CREATE POLICY "Users can update their own story files"
ON storage.objects FOR UPDATE
TO authenticated
USING (bucket_id = 'stories' AND owner = auth.uid())
WITH CHECK (bucket_id = 'stories' AND owner = auth.uid());

-- Política: Los usuarios pueden eliminar sus propios archivos
CREATE POLICY "Users can delete their own story files"
ON storage.objects FOR DELETE
TO authenticated
USING (bucket_id = 'stories' AND owner = auth.uid());

-- =====================================================
-- BUCKET: avatars (para fotos de perfil)
-- =====================================================

INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES (
  'avatars',
  'avatars',
  true, -- público
  5242880, -- 5MB límite
  ARRAY['image/jpeg', 'image/png', 'image/gif', 'image/webp']
)
ON CONFLICT (id) DO NOTHING;

-- Política: Usuarios autenticados pueden subir avatares
CREATE POLICY "Authenticated users can upload avatars"
ON storage.objects FOR INSERT
TO authenticated
WITH CHECK (bucket_id = 'avatars');

-- Política: Acceso público para ver avatares
CREATE POLICY "Public access to view avatars"
ON storage.objects FOR SELECT
TO public
USING (bucket_id = 'avatars');

-- Política: Los usuarios pueden actualizar sus avatares
CREATE POLICY "Users can update their own avatars"
ON storage.objects FOR UPDATE
TO authenticated
USING (bucket_id = 'avatars' AND owner = auth.uid())
WITH CHECK (bucket_id = 'avatars' AND owner = auth.uid());

-- Política: Los usuarios pueden eliminar sus avatares
CREATE POLICY "Users can delete their own avatars"
ON storage.objects FOR DELETE
TO authenticated
USING (bucket_id = 'avatars' AND owner = auth.uid());

-- =====================================================
-- BUCKET: banners (para banners de perfil)
-- =====================================================

INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES (
  'banners',
  'banners',
  true, -- público
  10485760, -- 10MB límite
  ARRAY['image/jpeg', 'image/png', 'image/gif', 'image/webp']
)
ON CONFLICT (id) DO NOTHING;

-- Política: Usuarios autenticados pueden subir banners
CREATE POLICY "Authenticated users can upload banners"
ON storage.objects FOR INSERT
TO authenticated
WITH CHECK (bucket_id = 'banners');

-- Política: Acceso público para ver banners
CREATE POLICY "Public access to view banners"
ON storage.objects FOR SELECT
TO public
USING (bucket_id = 'banners');

-- Política: Los usuarios pueden actualizar sus banners
CREATE POLICY "Users can update their own banners"
ON storage.objects FOR UPDATE
TO authenticated
USING (bucket_id = 'banners' AND owner = auth.uid())
WITH CHECK (bucket_id = 'banners' AND owner = auth.uid());

-- Política: Los usuarios pueden eliminar sus banners
CREATE POLICY "Users can delete their own banners"
ON storage.objects FOR DELETE
TO authenticated
USING (bucket_id = 'banners' AND owner = auth.uid());

-- =====================================================
-- BUCKET: videos (para videos intro y otros videos)
-- =====================================================

INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES (
  'videos',
  'videos',
  true, -- público
  104857600, -- 100MB límite
  ARRAY['video/mp4', 'video/quicktime', 'video/webm', 'video/x-m4v']
)
ON CONFLICT (id) DO NOTHING;

-- Política: Usuarios autenticados pueden subir videos
CREATE POLICY "Authenticated users can upload videos"
ON storage.objects FOR INSERT
TO authenticated
WITH CHECK (bucket_id = 'videos');

-- Política: Acceso público para ver videos
CREATE POLICY "Public access to view videos"
ON storage.objects FOR SELECT
TO public
USING (bucket_id = 'videos');

-- Política: Los usuarios pueden actualizar sus videos
CREATE POLICY "Users can update their own videos"
ON storage.objects FOR UPDATE
TO authenticated
USING (bucket_id = 'videos' AND owner = auth.uid())
WITH CHECK (bucket_id = 'videos' AND owner = auth.uid());

-- Política: Los usuarios pueden eliminar sus videos
CREATE POLICY "Users can delete their own videos"
ON storage.objects FOR DELETE
TO authenticated
USING (bucket_id = 'videos' AND owner = auth.uid());

-- =====================================================
-- VERIFICACIÓN
-- =====================================================

-- Ver todos los buckets creados
SELECT id, name, public, file_size_limit, allowed_mime_types
FROM storage.buckets
WHERE name IN ('stories', 'avatars', 'banners', 'videos');

-- Ver políticas de storage
SELECT schemaname, tablename, policyname 
FROM pg_policies 
WHERE schemaname = 'storage'
ORDER BY tablename, policyname;

-- =====================================================
-- COMENTARIOS
-- =====================================================

COMMENT ON TABLE storage.buckets IS 'Almacena la configuración de buckets de storage';

-- Mostrar mensaje de éxito
DO $$
BEGIN
  RAISE NOTICE '✅ Buckets de storage creados exitosamente:';
  RAISE NOTICE '   - stories (50MB, imágenes y videos)';
  RAISE NOTICE '   - avatars (5MB, solo imágenes)';
  RAISE NOTICE '   - banners (10MB, solo imágenes)';
  RAISE NOTICE '   - videos (100MB, solo videos)';
  RAISE NOTICE '';
  RAISE NOTICE '✅ Políticas de acceso configuradas correctamente';
END $$;
