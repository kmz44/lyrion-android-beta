-- 1. Permitir a los usuarios dejar de seguir (DELETE en followers)
-- Esto permite que un usuario borre filas donde él es el 'follower_id'
ALTER TABLE public.followers ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Users can unfollow" ON public.followers;
CREATE POLICY "Users can unfollow" ON public.followers
FOR DELETE USING (auth.uid() = follower_id);

-- 2. Permitir a los usuarios borrar su propia conexión de amigos (DELETE en friends)
ALTER TABLE public.friends ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Users can remove their own friend connection" ON public.friends;
CREATE POLICY "Users can remove their own friend connection" ON public.friends
FOR DELETE USING (auth.uid() = user_id);

-- 3. Función RPC para eliminar la amistad bidireccionalmente (A->B y B->A)
-- Esta función se ejecutará con SECURITY DEFINER para tener permisos de borrar ambas filas.
CREATE OR REPLACE FUNCTION public.remove_friend(target_friend_id uuid)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
BEGIN
  -- Borrar mi conexión hacia ellos
  DELETE FROM public.friends
  WHERE user_id = auth.uid() AND friend_id = target_friend_id;

  -- Borrar la conexión de ellos hacia mí
  DELETE FROM public.friends
  WHERE user_id = target_friend_id AND friend_id = auth.uid();
END;
$$;
