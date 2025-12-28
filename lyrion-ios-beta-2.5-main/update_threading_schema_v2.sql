-- 1. Eliminar la restricción de clave foránea estricta
-- Esto permite que reply_to_id guarde un ID aunque el mensaje original se haya borrado.
ALTER TABLE public.direct_messages 
DROP CONSTRAINT IF EXISTS direct_messages_reply_to_id_fkey;

-- 2. Agregar columnas de "Snapshot" (Respaldo)
-- Guardarán el texto y el autor del mensaje original en el momento de responder.
-- Así, si el mensaje original se borra, aún tenemos qué mostrar en la burbuja de respuesta.
ALTER TABLE public.direct_messages
ADD COLUMN IF NOT EXISTS reply_context_content text,
ADD COLUMN IF NOT EXISTS reply_context_sender_username text;
