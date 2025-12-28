-- Modificar tabla de comentarios para soportar hilos y contadores
ALTER TABLE public.comments 
ADD COLUMN IF NOT EXISTS parent_id uuid REFERENCES public.comments(id),
ADD COLUMN IF NOT EXISTS likes_count integer DEFAULT 0,
ADD COLUMN IF NOT EXISTS dislikes_count integer DEFAULT 0;

-- Tabla para reacciones a comentarios (Like/Dislike)
CREATE TABLE IF NOT EXISTS public.comment_reactions (
  id uuid NOT NULL DEFAULT gen_random_uuid(),
  comment_id uuid NOT NULL REFERENCES public.comments(id) ON DELETE CASCADE,
  user_id uuid NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
  reaction_type text NOT NULL CHECK (reaction_type IN ('like', 'dislike')),
  created_at timestamp with time zone DEFAULT now(),
  CONSTRAINT comment_reactions_pkey PRIMARY KEY (id),
  CONSTRAINT unique_user_comment_reaction UNIQUE (user_id, comment_id)
);

-- Tabla unificada de interacciones del usuario (Historial de actividad para recomendaciones)
CREATE TABLE IF NOT EXISTS public.user_interactions (
  id uuid NOT NULL DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
  target_id uuid NOT NULL, -- ID del post, reel, comentario o usuario
  target_type text NOT NULL CHECK (target_type IN ('post', 'reel', 'comment', 'user')),
  interaction_type text NOT NULL CHECK (interaction_type IN ('like', 'dislike', 'view', 'comment', 'share', 'reaction_emoji', 'follow')),
  metadata jsonb DEFAULT '{}'::jsonb, -- Para guardar qué emoji fue, duración de vista, etc.
  created_at timestamp with time zone DEFAULT now(),
  CONSTRAINT user_interactions_pkey PRIMARY KEY (id)
);

-- Tabla para recomendaciones calculadas (Post/Reels recomendados)
CREATE TABLE IF NOT EXISTS public.content_recommendations (
  id uuid NOT NULL DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
  content_id uuid NOT NULL, -- ID del post/reel
  content_type text NOT NULL CHECK (content_type IN ('post', 'reel')),
  score float DEFAULT 0.0,
  reason text, -- Explicación opcional: "Porque te gustó X"
  created_at timestamp with time zone DEFAULT now(),
  CONSTRAINT content_recommendations_pkey PRIMARY KEY (id)
);

-- Índices para mejorar rendimiento
CREATE INDEX IF NOT EXISTS idx_comments_parent_id ON public.comments(parent_id);
CREATE INDEX IF NOT EXISTS idx_user_interactions_user_id ON public.user_interactions(user_id);
CREATE INDEX IF NOT EXISTS idx_content_recommendations_user_id ON public.content_recommendations(user_id);

-- Agregar título y categoría a los posts para mejorar recomendaciones
ALTER TABLE public.posts
ADD COLUMN IF NOT EXISTS title text,
ADD COLUMN IF NOT EXISTS category text;

-- Opcional: Validar categorías si se desea, o dejar texto libre
-- ALTER TABLE public.posts ADD CONSTRAINT check_category CHECK (length(category) > 0);
