-- =====================================================
-- TRIGGERS PARA LIMPIAR ARCHIVOS ANTIGUOS
-- =====================================================
-- Estos triggers se encargan de eliminar archivos antiguos del storage
-- cuando se actualizan avatares, banners o estados

-- =====================================================
-- FUNCIÓN PARA ELIMINAR ARCHIVO DEL STORAGE
-- =====================================================

CREATE OR REPLACE FUNCTION public.delete_storage_object(bucket_name text, object_path text)
RETURNS void AS $$
BEGIN
  -- Eliminar objeto del storage
  DELETE FROM storage.objects
  WHERE bucket_id = bucket_name
    AND name = object_path;
EXCEPTION
  WHEN OTHERS THEN
    -- Si hay error, solo registrarlo (no fallar la transacción principal)
    RAISE WARNING 'Error eliminando archivo: % en bucket: %', object_path, bucket_name;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- =====================================================
-- FUNCIÓN PARA EXTRAER PATH DEL URL
-- =====================================================

CREATE OR REPLACE FUNCTION public.extract_storage_path(file_url text)
RETURNS text AS $$
DECLARE
  path_parts text[];
  bucket_name text;
  file_path text;
BEGIN
  -- Si la URL es nula o vacía, retornar nulo
  IF file_url IS NULL OR file_url = '' THEN
    RETURN NULL;
  END IF;
  
  -- Extraer el path después de /storage/v1/object/public/
  -- Ejemplo: https://xxx.supabase.co/storage/v1/object/public/avatars/user123.jpg
  -- Resultado: avatars/user123.jpg
  
  IF file_url LIKE '%/storage/v1/object/public/%' THEN
    path_parts := regexp_split_to_array(file_url, '/storage/v1/object/public/');
    IF array_length(path_parts, 1) >= 2 THEN
      RETURN path_parts[2];
    END IF;
  END IF;
  
  RETURN NULL;
END;
$$ LANGUAGE plpgsql IMMUTABLE;

-- =====================================================
-- TRIGGER: LIMPIAR AVATAR ANTIGUO
-- =====================================================

CREATE OR REPLACE FUNCTION public.cleanup_old_avatar()
RETURNS TRIGGER AS $$
DECLARE
  old_path text;
BEGIN
  -- Solo proceder si el avatar cambió y existe un avatar antiguo
  IF OLD.avatar_url IS NOT NULL 
     AND OLD.avatar_url != '' 
     AND (NEW.avatar_url IS NULL OR OLD.avatar_url != NEW.avatar_url) THEN
    
    -- Extraer path del storage
    old_path := public.extract_storage_path(OLD.avatar_url);
    
    IF old_path IS NOT NULL THEN
      -- Eliminar archivo antiguo del storage
      PERFORM public.delete_storage_object('avatars', old_path);
      RAISE NOTICE 'Avatar antiguo eliminado: %', old_path;
    END IF;
  END IF;
  
  RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Crear trigger para avatares
DROP TRIGGER IF EXISTS trigger_cleanup_old_avatar ON public.users;
CREATE TRIGGER trigger_cleanup_old_avatar
  BEFORE UPDATE OF avatar_url ON public.users
  FOR EACH ROW
  EXECUTE FUNCTION public.cleanup_old_avatar();

-- =====================================================
-- TRIGGER: LIMPIAR BANNER ANTIGUO
-- =====================================================

CREATE OR REPLACE FUNCTION public.cleanup_old_banner()
RETURNS TRIGGER AS $$
DECLARE
  old_path text;
BEGIN
  -- Solo proceder si el banner cambió y existe un banner antiguo
  IF OLD.banner_url IS NOT NULL 
     AND OLD.banner_url != '' 
     AND (NEW.banner_url IS NULL OR OLD.banner_url != NEW.banner_url) THEN
    
    -- Extraer path del storage
    old_path := public.extract_storage_path(OLD.banner_url);
    
    IF old_path IS NOT NULL THEN
      -- Eliminar archivo antiguo del storage
      PERFORM public.delete_storage_object('banners', old_path);
      RAISE NOTICE 'Banner antiguo eliminado: %', old_path;
    END IF;
  END IF;
  
  RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Crear trigger para banners
DROP TRIGGER IF EXISTS trigger_cleanup_old_banner ON public.users;
CREATE TRIGGER trigger_cleanup_old_banner
  BEFORE UPDATE OF banner_url ON public.users
  FOR EACH ROW
  EXECUTE FUNCTION public.cleanup_old_banner();

-- =====================================================
-- TRIGGER: LIMPIAR MEDIA DE ESTADOS ELIMINADOS
-- =====================================================

CREATE OR REPLACE FUNCTION public.cleanup_story_media()
RETURNS TRIGGER AS $$
DECLARE
  media_path text;
  thumb_path text;
BEGIN
  -- Solo proceder si el estado fue eliminado o marcado como inactivo
  IF (TG_OP = 'DELETE') OR (OLD.is_active = true AND NEW.is_active = false) THEN
    
    -- Extraer paths del storage
    media_path := public.extract_storage_path(OLD.media_url);
    thumb_path := public.extract_storage_path(OLD.thumbnail_url);
    
    -- Eliminar archivo de media
    IF media_path IS NOT NULL THEN
      PERFORM public.delete_storage_object('stories', media_path);
      RAISE NOTICE 'Media de estado eliminado: %', media_path;
    END IF;
    
    -- Eliminar thumbnail si existe
    IF thumb_path IS NOT NULL AND thumb_path != media_path THEN
      PERFORM public.delete_storage_object('stories', thumb_path);
      RAISE NOTICE 'Thumbnail de estado eliminado: %', thumb_path;
    END IF;
  END IF;
  
  IF TG_OP = 'DELETE' THEN
    RETURN OLD;
  ELSE
    RETURN NEW;
  END IF;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Crear triggers para estados
DROP TRIGGER IF EXISTS trigger_cleanup_story_media_delete ON public.stories;
CREATE TRIGGER trigger_cleanup_story_media_delete
  BEFORE DELETE ON public.stories
  FOR EACH ROW
  EXECUTE FUNCTION public.cleanup_story_media();

DROP TRIGGER IF EXISTS trigger_cleanup_story_media_update ON public.stories;
CREATE TRIGGER trigger_cleanup_story_media_update
  BEFORE UPDATE OF is_active ON public.stories
  FOR EACH ROW
  EXECUTE FUNCTION public.cleanup_story_media();

-- =====================================================
-- TRIGGER: LIMPIAR VIDEO INTRO ANTIGUO
-- =====================================================

CREATE OR REPLACE FUNCTION public.cleanup_old_video_intro()
RETURNS TRIGGER AS $$
DECLARE
  old_path text;
BEGIN
  -- Solo proceder si el video intro cambió y existe uno antiguo
  IF OLD.video_intro_url IS NOT NULL 
     AND OLD.video_intro_url != '' 
     AND (NEW.video_intro_url IS NULL OR OLD.video_intro_url != NEW.video_intro_url) THEN
    
    -- Extraer path del storage
    old_path := public.extract_storage_path(OLD.video_intro_url);
    
    IF old_path IS NOT NULL THEN
      -- Eliminar archivo antiguo del storage
      PERFORM public.delete_storage_object('videos', old_path);
      RAISE NOTICE 'Video intro antiguo eliminado: %', old_path;
    END IF;
  END IF;
  
  RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Crear trigger para video intro
DROP TRIGGER IF EXISTS trigger_cleanup_old_video_intro ON public.users;
CREATE TRIGGER trigger_cleanup_old_video_intro
  BEFORE UPDATE OF video_intro_url ON public.users
  FOR EACH ROW
  EXECUTE FUNCTION public.cleanup_old_video_intro();

-- =====================================================
-- COMENTARIOS
-- =====================================================

COMMENT ON FUNCTION public.delete_storage_object(text, text) IS 'Elimina un archivo del storage de Supabase';
COMMENT ON FUNCTION public.extract_storage_path(text) IS 'Extrae el path del storage desde una URL completa';
COMMENT ON FUNCTION public.cleanup_old_avatar() IS 'Elimina el avatar antiguo cuando se actualiza a uno nuevo';
COMMENT ON FUNCTION public.cleanup_old_banner() IS 'Elimina el banner antiguo cuando se actualiza a uno nuevo';
COMMENT ON FUNCTION public.cleanup_story_media() IS 'Elimina archivos de media cuando un estado es eliminado o desactivado';
COMMENT ON FUNCTION public.cleanup_old_video_intro() IS 'Elimina el video intro antiguo cuando se actualiza a uno nuevo';
