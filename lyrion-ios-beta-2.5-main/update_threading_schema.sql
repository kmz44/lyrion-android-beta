-- Agregar columna reply_to_id a la tabla direct_messages
ALTER TABLE public.direct_messages
ADD COLUMN IF NOT EXISTS reply_to_id bigint REFERENCES public.direct_messages(id) ON DELETE SET NULL;

-- Crear un índice para mejorar el rendimiento de las consultas de hilos
CREATE INDEX IF NOT EXISTS idx_direct_messages_reply_to_id ON public.direct_messages(reply_to_id);

-- Comentario: Esto permite que un mensaje referencie a otro. 
-- Si el mensaje original se borra, el campo reply_to_id se pondrá en NULL (se conserva la respuesta pero se pierde el enlace).
