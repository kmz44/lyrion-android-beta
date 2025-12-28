-- =====================================================
-- FUNCIÓN PARA LIMPIAR ESTADOS EXPIRADOS
-- =====================================================
-- Marca como inactivos los estados que han expirado

CREATE OR REPLACE FUNCTION public.cleanup_expired_stories()
RETURNS void AS $$
BEGIN
  -- Marcar estados expirados como inactivos
  UPDATE public.stories
  SET is_active = false
  WHERE expires_at <= timezone('utc'::text, now())
    AND is_active = true;
  
  -- Opcional: Eliminar estados muy antiguos (más de 7 días)
  -- Esto ayuda a mantener la base de datos limpia
  DELETE FROM public.stories
  WHERE expires_at <= (timezone('utc'::text, now()) - interval '7 days');
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- =====================================================
-- CONFIGURAR LIMPIEZA AUTOMÁTICA CON pg_cron
-- =====================================================
-- NOTA: Para que esto funcione, debes habilitar la extensión pg_cron
-- en Supabase desde el Dashboard: Database > Extensions > pg_cron

-- Habilitar extensión pg_cron (solo ejecutar una vez)
CREATE EXTENSION IF NOT EXISTS pg_cron;

-- Programar limpieza automática cada hora
-- Esto ejecutará la función cleanup_expired_stories() cada hora
SELECT cron.schedule(
  'cleanup-expired-stories',           -- nombre del job
  '0 * * * *',                         -- cada hora en punto (minuto 0)
  $$SELECT public.cleanup_expired_stories()$$
);

-- =====================================================
-- ALTERNATIVA: FUNCIÓN AUTOMÁTICA CON TRIGGER
-- =====================================================
-- Si no quieres usar pg_cron, puedes usar esta aproximación
-- que limpia estados expirados cada vez que se consultan

CREATE OR REPLACE FUNCTION public.auto_cleanup_on_select()
RETURNS TRIGGER AS $$
BEGIN
  -- Ejecutar limpieza ocasionalmente (10% de las veces)
  IF random() < 0.1 THEN
    PERFORM public.cleanup_expired_stories();
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- =====================================================
-- FUNCIÓN PARA OBTENER ESTADOS ACTIVOS DE UN USUARIO
-- =====================================================
-- Retorna solo estados activos y no expirados

CREATE OR REPLACE FUNCTION public.get_active_user_stories(p_user_id uuid)
RETURNS TABLE (
  id uuid,
  user_id uuid,
  media_url text,
  media_type text,
  thumbnail_url text,
  caption text,
  duration_seconds integer,
  created_at timestamp with time zone,
  expires_at timestamp with time zone,
  views_count integer
) AS $$
BEGIN
  RETURN QUERY
  SELECT 
    s.id,
    s.user_id,
    s.media_url,
    s.media_type,
    s.thumbnail_url,
    s.caption,
    s.duration_seconds,
    s.created_at,
    s.expires_at,
    s.views_count
  FROM public.stories s
  WHERE s.user_id = p_user_id
    AND s.is_active = true
    AND s.expires_at > timezone('utc'::text, now())
  ORDER BY s.created_at DESC;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- =====================================================
-- FUNCIÓN PARA OBTENER ESTADOS DE AMIGOS/SEGUIDOS
-- =====================================================
-- Retorna estados de usuarios que sigues o son tus amigos

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
  has_viewed boolean
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
    ) as has_viewed
  FROM public.stories s
  INNER JOIN public.users u ON s.user_id = u.id
  WHERE s.is_active = true
    AND s.expires_at > timezone('utc'::text, now())
    AND (
      -- Usuarios que sigues
      EXISTS(
        SELECT 1 FROM public.connections c
        WHERE c.follower_id = p_user_id
          AND c.following_id = s.user_id
          AND c.is_following = true
      )
      OR
      -- Usuarios que son tus amigos
      EXISTS(
        SELECT 1 FROM public.connections c
        WHERE (
          (c.follower_id = p_user_id AND c.following_id = s.user_id) OR
          (c.following_id = p_user_id AND c.follower_id = s.user_id)
        )
        AND c.friendship_status = 'amigos'
      )
      OR
      -- Tus propios estados
      s.user_id = p_user_id
    )
  ORDER BY s.created_at DESC;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- =====================================================
-- COMENTARIOS
-- =====================================================

COMMENT ON FUNCTION public.cleanup_expired_stories() IS 'Marca estados expirados como inactivos y elimina estados muy antiguos';
COMMENT ON FUNCTION public.get_active_user_stories(uuid) IS 'Obtiene estados activos de un usuario específico';
COMMENT ON FUNCTION public.get_friends_stories(uuid) IS 'Obtiene estados de amigos y usuarios seguidos';
