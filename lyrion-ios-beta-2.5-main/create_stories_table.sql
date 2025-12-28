-- =====================================================
-- TABLA DE ESTADOS (STORIES)
-- =====================================================
-- Los estados se eliminan automáticamente después de 24 horas

CREATE TABLE IF NOT EXISTS public.stories (
  id uuid NOT NULL DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL,
  media_url text NOT NULL,
  media_type text NOT NULL CHECK (media_type = ANY (ARRAY['image'::text, 'video'::text])),
  thumbnail_url text,
  caption text,
  duration_seconds integer DEFAULT 5, -- Duración de visualización por defecto
  created_at timestamp with time zone NOT NULL DEFAULT timezone('utc'::text, now()),
  expires_at timestamp with time zone NOT NULL DEFAULT (timezone('utc'::text, now()) + interval '24 hours'),
  views_count integer DEFAULT 0,
  is_active boolean DEFAULT true,
  CONSTRAINT stories_pkey PRIMARY KEY (id),
  CONSTRAINT stories_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE
);

-- Índices para mejorar rendimiento
CREATE INDEX IF NOT EXISTS idx_stories_user_id ON public.stories(user_id);
CREATE INDEX IF NOT EXISTS idx_stories_expires_at ON public.stories(expires_at);
CREATE INDEX IF NOT EXISTS idx_stories_created_at ON public.stories(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_stories_active ON public.stories(is_active) WHERE is_active = true;

-- =====================================================
-- TABLA DE VISUALIZACIONES DE ESTADOS
-- =====================================================
-- Registra quién ha visto cada estado

CREATE TABLE IF NOT EXISTS public.story_views (
  id uuid NOT NULL DEFAULT gen_random_uuid(),
  story_id uuid NOT NULL,
  viewer_id uuid NOT NULL,
  viewed_at timestamp with time zone NOT NULL DEFAULT timezone('utc'::text, now()),
  CONSTRAINT story_views_pkey PRIMARY KEY (id),
  CONSTRAINT story_views_story_id_fkey FOREIGN KEY (story_id) REFERENCES public.stories(id) ON DELETE CASCADE,
  CONSTRAINT story_views_viewer_id_fkey FOREIGN KEY (viewer_id) REFERENCES public.users(id) ON DELETE CASCADE,
  CONSTRAINT unique_story_view UNIQUE (story_id, viewer_id)
);

-- Índices
CREATE INDEX IF NOT EXISTS idx_story_views_story_id ON public.story_views(story_id);
CREATE INDEX IF NOT EXISTS idx_story_views_viewer_id ON public.story_views(viewer_id);

-- =====================================================
-- POLÍTICAS RLS (Row Level Security)
-- =====================================================

-- Habilitar RLS en las tablas
ALTER TABLE public.stories ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.story_views ENABLE ROW LEVEL SECURITY;

-- Política: Los usuarios pueden ver estados activos y no expirados
CREATE POLICY "Users can view active non-expired stories"
  ON public.stories
  FOR SELECT
  USING (
    is_active = true 
    AND expires_at > timezone('utc'::text, now())
  );

-- Política: Los usuarios pueden insertar sus propios estados
CREATE POLICY "Users can insert their own stories"
  ON public.stories
  FOR INSERT
  WITH CHECK (auth.uid() = user_id);

-- Política: Los usuarios pueden actualizar sus propios estados
CREATE POLICY "Users can update their own stories"
  ON public.stories
  FOR UPDATE
  USING (auth.uid() = user_id)
  WITH CHECK (auth.uid() = user_id);

-- Política: Los usuarios pueden eliminar sus propios estados
CREATE POLICY "Users can delete their own stories"
  ON public.stories
  FOR DELETE
  USING (auth.uid() = user_id);

-- Política: Los usuarios pueden ver las visualizaciones de sus propios estados
CREATE POLICY "Users can view their story views"
  ON public.story_views
  FOR SELECT
  USING (
    EXISTS (
      SELECT 1 FROM public.stories
      WHERE stories.id = story_views.story_id
      AND stories.user_id = auth.uid()
    )
  );

-- Política: Los usuarios pueden registrar visualizaciones
CREATE POLICY "Users can insert story views"
  ON public.story_views
  FOR INSERT
  WITH CHECK (auth.uid() = viewer_id);

-- =====================================================
-- FUNCIÓN PARA ACTUALIZAR CONTADOR DE VISTAS
-- =====================================================

CREATE OR REPLACE FUNCTION public.increment_story_views()
RETURNS TRIGGER AS $$
BEGIN
  UPDATE public.stories
  SET views_count = views_count + 1
  WHERE id = NEW.story_id;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Trigger para actualizar contador automáticamente
CREATE TRIGGER update_story_views_count
  AFTER INSERT ON public.story_views
  FOR EACH ROW
  EXECUTE FUNCTION public.increment_story_views();

-- =====================================================
-- COMENTARIOS
-- =====================================================

COMMENT ON TABLE public.stories IS 'Almacena los estados (stories) de los usuarios que expiran en 24 horas';
COMMENT ON COLUMN public.stories.expires_at IS 'Fecha y hora de expiración del estado (24 horas después de creación)';
COMMENT ON COLUMN public.stories.is_active IS 'Indica si el estado está activo o fue eliminado manualmente';
COMMENT ON TABLE public.story_views IS 'Registra las visualizaciones de cada estado';
