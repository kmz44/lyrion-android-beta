-- SCRIPT: Añadir columna 'occupation' (ocupación) a la tabla de usuarios

ALTER TABLE public.users 
ADD COLUMN IF NOT EXISTS occupation text;

-- Comentario para documentación
COMMENT ON COLUMN public.users.occupation IS 'Almacena la profesión u ocupación del usuario (ej. Digital Creator & Designer)';
