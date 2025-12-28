-- PASO 1: Agregar columna banner_url a la tabla users
ALTER TABLE public.users 
ADD COLUMN IF NOT EXISTS banner_url text;

-- Comentario para documentar
COMMENT ON COLUMN public.users.banner_url IS 'URL del banner de perfil almacenado en Supabase Storage (banners bucket)';

-- PASO 2: Crear bucket de banners
-- NOTA: Ejecutar este bloque solo una vez. Si el bucket ya existe, comentarlo.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM storage.buckets WHERE id = 'banners') THEN
        INSERT INTO storage.buckets (id, name, public) 
        VALUES ('banners', 'banners', true);
    END IF;
END $$;

-- PASO 3: Eliminar políticas existentes si las hay
DROP POLICY IF EXISTS "Users can upload their own banner" ON storage.objects;
DROP POLICY IF EXISTS "Users can update their own banner" ON storage.objects;
DROP POLICY IF EXISTS "Users can delete their own banner" ON storage.objects;
DROP POLICY IF EXISTS "Banners are publicly accessible" ON storage.objects;

-- PASO 4: Crear políticas de Storage para el bucket banners
CREATE POLICY "Users can upload their own banner"
ON storage.objects FOR INSERT
WITH CHECK (
    bucket_id = 'banners' 
    AND auth.uid()::text = (storage.foldername(name))[1]
);

CREATE POLICY "Users can update their own banner"
ON storage.objects FOR UPDATE
USING (
    bucket_id = 'banners' 
    AND auth.uid()::text = (storage.foldername(name))[1]
);

CREATE POLICY "Users can delete their own banner"
ON storage.objects FOR DELETE
USING (
    bucket_id = 'banners' 
    AND auth.uid()::text = (storage.foldername(name))[1]
);

CREATE POLICY "Banners are publicly accessible"
ON storage.objects FOR SELECT
USING (bucket_id = 'banners');
