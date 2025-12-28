-- =====================================================
-- CREAR BUCKETS FALTANTES (MÉTODO SEGURO Y NO INTRUSIVO)
-- =====================================================
-- Este script SOLO crea los buckets 'stories' y 'videos' si no existen.
-- RESPETA tus buckets existentes (banners, avatars, reel_videos, etc).

-- 1. Función temporal segura
CREATE OR REPLACE FUNCTION public.create_missing_buckets(
  b_name text,
  b_public boolean,
  b_size int,
  b_mime text[]
) RETURNS void AS $$
BEGIN
  -- Solo intenta insertar si no existe
  IF NOT EXISTS (SELECT 1 FROM storage.buckets WHERE id = b_name) THEN
    INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
    VALUES (b_name, b_name, b_public, b_size, b_mime);
    RAISE NOTICE '✅ Bucket creado: %', b_name;
  ELSE
    RAISE NOTICE 'ℹ️ Bucket ya existe (se respeta): %', b_name;
  END IF;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- 2. Crear SOLO los buckets que te faltan
-- STORIES (Nuevo)
SELECT public.create_missing_buckets(
  'stories', 
  true, 
  52428800, -- 50MB
  ARRAY['image/jpeg', 'image/png', 'image/gif', 'image/webp', 'video/mp4', 'video/quicktime', 'video/webm']
);

-- VIDEOS (Este es para videos de intro de perfil, distinto a reel_videos)
-- Si prefieres usar 'reel_videos' para todo, avísame. Si no, creamos este nuevo.
SELECT public.create_missing_buckets(
  'videos', 
  true, 
  104857600, -- 100MB
  ARRAY['video/mp4', 'video/quicktime', 'video/webm', 'video/x-m4v']
);

-- AVATARS (Ya existe, esto solo asegurará que no de error)
SELECT public.create_missing_buckets('avatars', true, 5242880, null);

-- BANNERS (Ya existe, esto solo asegurará que no de error)
SELECT public.create_missing_buckets('banners', true, 10485760, null);


-- 3. Eliminar función temporal
DROP FUNCTION public.create_missing_buckets;

-- =====================================================
-- POLÍTICAS DE ACCESO (SOLO PARA LOS NUEVOS)
-- =====================================================

-- Políticas para STORIES (Seguro de aplicar)
DROP POLICY IF EXISTS "Authenticated users can upload stories" ON storage.objects;
DROP POLICY IF EXISTS "Public access to view stories" ON storage.objects;
DROP POLICY IF EXISTS "Users can update their own story files" ON storage.objects;
DROP POLICY IF EXISTS "Users can delete their own story files" ON storage.objects;

CREATE POLICY "Authenticated users can upload stories" ON storage.objects FOR INSERT TO authenticated WITH CHECK (bucket_id = 'stories');
CREATE POLICY "Public access to view stories" ON storage.objects FOR SELECT TO public USING (bucket_id = 'stories');
CREATE POLICY "Users can update their own story files" ON storage.objects FOR UPDATE TO authenticated USING (bucket_id = 'stories' AND owner = auth.uid());
CREATE POLICY "Users can delete their own story files" ON storage.objects FOR DELETE TO authenticated USING (bucket_id = 'stories' AND owner = auth.uid());

-- Políticas para VIDEOS (Seguro de aplicar)
DROP POLICY IF EXISTS "Authenticated users can upload videos" ON storage.objects;
DROP POLICY IF EXISTS "Public access to view videos" ON storage.objects;
DROP POLICY IF EXISTS "Users can update their own videos" ON storage.objects;
DROP POLICY IF EXISTS "Users can delete their own videos" ON storage.objects;

CREATE POLICY "Authenticated users can upload videos" ON storage.objects FOR INSERT TO authenticated WITH CHECK (bucket_id = 'videos');
CREATE POLICY "Public access to view videos" ON storage.objects FOR SELECT TO public USING (bucket_id = 'videos');
CREATE POLICY "Users can update their own videos" ON storage.objects FOR UPDATE TO authenticated USING (bucket_id = 'videos' AND owner = auth.uid());
CREATE POLICY "Users can delete their own videos" ON storage.objects FOR DELETE TO authenticated USING (bucket_id = 'videos' AND owner = auth.uid());

DO $$
BEGIN
  RAISE NOTICE '✅ Proceso completado de forma segura.';
END $$;
