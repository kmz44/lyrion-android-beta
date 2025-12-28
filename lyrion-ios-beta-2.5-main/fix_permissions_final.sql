-- ==========================================
-- 1. Habilitar RLS en la tabla 'users'
-- ==========================================
ALTER TABLE public.users ENABLE ROW LEVEL SECURITY;

-- Permitir que CUALQUIERA vea los perfiles (necesario para buscar amigos, chats, etc.)
CREATE POLICY "Public profiles are viewable by everyone" 
ON public.users
FOR SELECT 
USING (true);

-- Permitir que el usuario edite SU PROPIO perfil
-- "auth.uid()" es el ID del usuario logueado en Supabase
CREATE POLICY "Users can update their own profile" 
ON public.users
FOR UPDATE 
USING (auth.uid() = id)
WITH CHECK (auth.uid() = id);

-- Permitir que el usuario inserte SU PROPIO perfil (si no existe)
CREATE POLICY "Users can insert their own profile" 
ON public.users
FOR INSERT 
WITH CHECK (auth.uid() = id);

-- ==========================================
-- 2. Configurar Storage para Avatares
-- ==========================================
-- Insertar el bucket 'avatars' si no existe (esto suele hacerse manual, pero por si acaso)
INSERT INTO storage.buckets (id, name, public) 
VALUES ('avatars', 'avatars', true)
ON CONFLICT (id) DO NOTHING;

-- Permitir acceso público de LECTURA a los avatares
CREATE POLICY "Avatar images are publicly accessible" 
ON storage.objects
FOR SELECT 
USING ( bucket_id = 'avatars' );

-- Permitir al usuario SUBIR su propio avatar
-- Asume que la app sube a la ruta: "userId/nombrearchivo"
-- (storage.foldername(name))[1] extrae la primera parte de la ruta (el userId)
CREATE POLICY "Users can upload their own avatar" 
ON storage.objects
FOR INSERT 
WITH CHECK (
    bucket_id = 'avatars' 
    AND auth.uid()::text = (storage.foldername(name))[1]
);

-- Permitir al usuario ACTUALIZAR/BORRAR su propio avatar
CREATE POLICY "Users can update their own avatar" 
ON storage.objects
FOR UPDATE 
USING (
    bucket_id = 'avatars' 
    AND auth.uid()::text = (storage.foldername(name))[1]
);

CREATE POLICY "Users can delete their own avatar" 
ON storage.objects
FOR DELETE 
USING (
    bucket_id = 'avatars' 
    AND auth.uid()::text = (storage.foldername(name))[1]
);
