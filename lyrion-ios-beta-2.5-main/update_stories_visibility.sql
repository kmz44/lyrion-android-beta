-- =====================================================
-- MIGRACIÓN: VISIBILIDAD DE HISTORIAS
-- =====================================================

-- 1. Agregar columna de visibilidad a la tabla stories
ALTER TABLE public.stories 
ADD COLUMN IF NOT EXISTS visibility text DEFAULT 'followers'::text;

-- 2. Agregar restricción para valores válidos
ALTER TABLE public.stories 
DROP CONSTRAINT IF EXISTS stories_visibility_check;

ALTER TABLE public.stories 
ADD CONSTRAINT stories_visibility_check 
CHECK (visibility = ANY (ARRAY['followers'::text, 'friends'::text]));

-- 3. Actualizar función get_friends_stories para respetar la visibilidad
-- IMPORTANTE: Eliminamos la función anterior porque cambió el tipo de retorno (agregamos columna visibility)
DROP FUNCTION IF EXISTS public.get_friends_stories(uuid);

CREATE OR REPLACE FUNCTION public.get_friends_stories(p_user_id uuid)
RETURNS TABLE (
  id uuid,
  user_id uuid,
  username text,
  avatar_url text,
  media_url text,
  media_type text,
  thumbnail_url text,
  caption text,
  duration_seconds integer,
  created_at timestamp with time zone,
  expires_at timestamp with time zone,
  views_count integer,
  has_viewed boolean,
  visibility text
) AS $$
BEGIN
  RETURN QUERY
  SELECT 
    s.id,
    s.user_id,
    u.username,
    u.avatar_url,
    s.media_url,
    s.media_type,
    s.thumbnail_url,
    s.caption,
    s.duration_seconds,
    s.created_at,
    s.expires_at,
    s.views_count,
    EXISTS(
      SELECT 1 FROM public.story_views sv
      WHERE sv.story_id = s.id AND sv.viewer_id = p_user_id
    ) as has_viewed,
    s.visibility
  FROM public.stories s
  INNER JOIN public.users u ON s.user_id = u.id
  WHERE s.is_active = true
    AND s.expires_at > timezone('utc'::text, now())
    AND (
      -- CASO 1: TU PROPIA HISTORIA (Siempre visible para ti)
      s.user_id = p_user_id
      
      OR
      
      -- CASO 2: VISIBILIDAD 'FOLLOWERS' (Seguidores + Amigos)
      (
        s.visibility = 'followers' 
        AND (
           -- Eres seguidor
           EXISTS(
             SELECT 1 FROM public.connections c
             WHERE c.follower_id = p_user_id
               AND c.following_id = s.user_id
               AND c.is_following = true
           )
           OR
           -- O eres amigo
           EXISTS(
             SELECT 1 FROM public.connections c
             WHERE (
               (c.follower_id = p_user_id AND c.following_id = s.user_id) OR
               (c.following_id = p_user_id AND c.follower_id = s.user_id)
             )
             AND c.friendship_status = 'amigos'
           )
        )
      )
      
      OR
      
      -- CASO 3: VISIBILIDAD 'FRIENDS' (Solo Amigos)
      (
        s.visibility = 'friends' 
        AND 
        EXISTS(
           SELECT 1 FROM public.connections c
           WHERE (
             (c.follower_id = p_user_id AND c.following_id = s.user_id) OR
             (c.following_id = p_user_id AND c.follower_id = s.user_id)
           )
           AND c.friendship_status = 'amigos'
        )
      )
    )
  ORDER BY s.created_at DESC;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Comentarios
COMMENT ON COLUMN public.stories.visibility IS 'Determina quién puede ver la historia: followers (todos los seguidores) o friends (solo mejores amigos)';
