-- Update connections status check to include 'siguiendo'
-- We cannot easily "ALTER CONSTRAINT", so we typically drop and recreate it.

ALTER TABLE public.connections DROP CONSTRAINT IF EXISTS connections_status_check;

ALTER TABLE public.connections 
ADD CONSTRAINT connections_status_check 
CHECK (status = ANY (ARRAY['pendiente'::text, 'amigos'::text, 'bloqueado'::text, 'siguiendo'::text]));

-- 'siguiendo' = Follow (One way)
-- 'pendiente' = Friend Request (Sent)
-- 'amigos' = Friends (Mutual)
