-- SCRIPT DE CORRECCIÓN DE ESTADÍSTICAS Y DATOS
-- Ejecuta este script en el Editor SQL de Supabase para arreglar los contadores en 0.

-- 1. Corregir conexiones antiguas (Seguidores/Seguidos)
-- Actualiza las filas viejas para que 'is_following' sea true si el estado era 'siguiendo'
UPDATE public.connections
SET is_following = true
WHERE status = 'siguiendo' AND is_following IS DISTINCT FROM true;

-- Actualiza el estado de amistad basado en el status antiguo
UPDATE public.connections
SET friendship_status = status
WHERE status IN ('pendiente', 'amigos', 'bloqueado') AND friendship_status = 'none';

-- 2. OPCIONAL: Reasignar todos los posts a tu usuario actual
-- Si ves 0 posts pero sabes que hay posts en la base de datos, es probable que pertenezcan a un usuario anterior (tu ID cambia si borras el usuario).
-- Sustituye 'TU_UUID_AQUI' por tu ID real (lo puedes ver en la tabla users o auth.users).
-- UPDATE public.posts SET creator_id = 'TU_UUID_AQUI';

-- 3. Verificar resultados
SELECT count(*) as total_posts FROM public.posts;
SELECT count(*) as total_siguiendo FROM public.connections WHERE is_following = true;
