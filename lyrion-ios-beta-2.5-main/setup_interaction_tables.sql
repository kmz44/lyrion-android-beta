-- setup_interaction_tables_v2.sql

-- 1. Enable UUID extension if not already
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 2. Create Users Table (if not exists)
CREATE TABLE IF NOT EXISTS public.users (
  id uuid NOT NULL,
  username text UNIQUE,
  email text,
  avatar_url text,
  status text DEFAULT 'offline'::text CHECK (status = ANY (ARRAY['available'::text, 'chatting'::text, 'away'::text, 'busy'::text, 'offline'::text])),
  last_seen timestamp with time zone DEFAULT now(),
  is_searching boolean DEFAULT false,
  created_at timestamp with time zone DEFAULT now(),
  nombre text,
  apellido text,
  video_intro_url text,
  bio text,
  edad integer,
  altura_cm integer,
  peso_kg integer,
  estado_civil text,
  pais text,
  estado_region text,
  lat double precision,
  lng double precision,
  banner_url text,
  occupation text,
  CONSTRAINT users_pkey PRIMARY KEY (id),
  CONSTRAINT users_id_fkey FOREIGN KEY (id) REFERENCES auth.users(id)
);

ALTER TABLE public.users ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Users are viewable by everyone" ON public.users;
CREATE POLICY "Users are viewable by everyone" ON public.users FOR SELECT USING (true);

DROP POLICY IF EXISTS "Users can insert their own profile" ON public.users;
CREATE POLICY "Users can insert their own profile" ON public.users FOR INSERT WITH CHECK (auth.uid() = id);

DROP POLICY IF EXISTS "Users can update own profile" ON public.users;
CREATE POLICY "Users can update own profile" ON public.users FOR UPDATE USING (auth.uid() = id);

-- 3. Create Posts Table
CREATE TABLE IF NOT EXISTS public.posts (
  id uuid NOT NULL DEFAULT gen_random_uuid(),
  creator_id uuid NOT NULL,
  media_url text NOT NULL,
  media_type text NOT NULL CHECK (media_type = ANY (ARRAY['video'::text, 'image'::text])),
  caption text,
  attribute_boost text,
  likes_count integer DEFAULT 0,
  created_at timestamp with time zone NOT NULL DEFAULT timezone('utc'::text, now()),
  is_anonymous boolean DEFAULT false,
  content_type text DEFAULT 'post'::text CHECK (content_type = ANY (ARRAY['post'::text, 'reel'::text, 'both'::text])),
  duration_seconds integer,
  thumbnail_url text,
  CONSTRAINT posts_pkey PRIMARY KEY (id),
  CONSTRAINT posts_creator_id_fkey FOREIGN KEY (creator_id) REFERENCES public.users(id)
);

ALTER TABLE public.posts ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Posts are viewable by everyone" ON public.posts;
CREATE POLICY "Posts are viewable by everyone" ON public.posts FOR SELECT USING (true);

DROP POLICY IF EXISTS "Users can create posts" ON public.posts;
CREATE POLICY "Users can create posts" ON public.posts FOR INSERT WITH CHECK (auth.uid() = creator_id);

DROP POLICY IF EXISTS "Users can delete own posts" ON public.posts;
CREATE POLICY "Users can delete own posts" ON public.posts FOR DELETE USING (auth.uid() = creator_id);

-- 4. Create Post Reactions Table (Likes)
CREATE TABLE IF NOT EXISTS public.post_reactions (
  id uuid NOT NULL DEFAULT gen_random_uuid(),
  post_id uuid NOT NULL,
  user_id uuid NOT NULL,
  emoji text NOT NULL,
  created_at timestamp with time zone NOT NULL DEFAULT timezone('utc'::text, now()),
  CONSTRAINT post_reactions_pkey PRIMARY KEY (id),
  CONSTRAINT post_reactions_post_id_fkey FOREIGN KEY (post_id) REFERENCES public.posts(id) ON DELETE CASCADE,
  CONSTRAINT post_reactions_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id),
  CONSTRAINT post_reactions_unique_user_post UNIQUE (post_id, user_id)
);

ALTER TABLE public.post_reactions ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Reactions viewable by everyone" ON public.post_reactions;
CREATE POLICY "Reactions viewable by everyone" ON public.post_reactions FOR SELECT USING (true);

DROP POLICY IF EXISTS "Users can react" ON public.post_reactions;
CREATE POLICY "Users can react" ON public.post_reactions FOR INSERT WITH CHECK (auth.uid() = user_id);

DROP POLICY IF EXISTS "Users can remove reaction" ON public.post_reactions;
CREATE POLICY "Users can remove reaction" ON public.post_reactions FOR DELETE USING (auth.uid() = user_id);

-- Trigger to update likes_count
CREATE OR REPLACE FUNCTION update_likes_count()
RETURNS TRIGGER AS $$
BEGIN
  IF (TG_OP = 'INSERT') THEN
    UPDATE public.posts SET likes_count = likes_count + 1 WHERE id = NEW.post_id;
  ELSIF (TG_OP = 'DELETE') THEN
    UPDATE public.posts SET likes_count = likes_count - 1 WHERE id = OLD.post_id;
  END IF;
  RETURN NULL;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trigger_update_likes_count ON public.post_reactions;
CREATE TRIGGER trigger_update_likes_count
AFTER INSERT OR DELETE ON public.post_reactions
FOR EACH ROW EXECUTE FUNCTION update_likes_count();


-- 5. Create Comments Table
CREATE TABLE IF NOT EXISTS public.comments (
  id uuid NOT NULL DEFAULT gen_random_uuid(),
  post_id uuid NOT NULL,
  user_id uuid NOT NULL,
  content text NOT NULL,
  created_at timestamp with time zone NOT NULL DEFAULT timezone('utc'::text, now()),
  CONSTRAINT comments_pkey PRIMARY KEY (id),
  CONSTRAINT comments_post_id_fkey FOREIGN KEY (post_id) REFERENCES public.posts(id) ON DELETE CASCADE,
  CONSTRAINT comments_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id)
);

ALTER TABLE public.comments ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Comments viewable by everyone" ON public.comments;
CREATE POLICY "Comments viewable by everyone" ON public.comments FOR SELECT USING (true);

DROP POLICY IF EXISTS "Users can comment" ON public.comments;
CREATE POLICY "Users can comment" ON public.comments FOR INSERT WITH CHECK (auth.uid() = user_id);

DROP POLICY IF EXISTS "Users can delete own comments" ON public.comments;
CREATE POLICY "Users can delete own comments" ON public.comments FOR DELETE USING (auth.uid() = user_id);


-- 6. Create Connections Table (Friendships/Follows)
CREATE TABLE IF NOT EXISTS public.connections (
  id uuid NOT NULL DEFAULT gen_random_uuid(),
  follower_id uuid NOT NULL,
  following_id uuid NOT NULL,
  status text DEFAULT 'pendiente'::text CHECK (status = ANY (ARRAY['pendiente'::text, 'amigos'::text, 'bloqueado'::text, 'siguiendo'::text])),
  created_at timestamp with time zone NOT NULL DEFAULT timezone('utc'::text, now()),
  is_following boolean DEFAULT false,
  friendship_status text DEFAULT 'none'::text CHECK (friendship_status = ANY (ARRAY['none'::text, 'pendiente'::text, 'amigos'::text, 'bloqueado'::text])),
  CONSTRAINT connections_pkey PRIMARY KEY (id),
  CONSTRAINT connections_follower_id_fkey FOREIGN KEY (follower_id) REFERENCES public.users(id),
  CONSTRAINT connections_following_id_fkey FOREIGN KEY (following_id) REFERENCES public.users(id),
  CONSTRAINT connections_unique_pair UNIQUE (follower_id, following_id)
);

ALTER TABLE public.connections ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Connections viewable by involved users" ON public.connections;
CREATE POLICY "Connections viewable by involved users" ON public.connections FOR SELECT USING (auth.uid() = follower_id OR auth.uid() = following_id);

DROP POLICY IF EXISTS "Users can create connections" ON public.connections;
CREATE POLICY "Users can create connections" ON public.connections FOR INSERT WITH CHECK (auth.uid() = follower_id);

DROP POLICY IF EXISTS "Users can update interactions" ON public.connections;
CREATE POLICY "Users can update interactions" ON public.connections FOR UPDATE USING (auth.uid() = follower_id OR auth.uid() = following_id);

DROP POLICY IF EXISTS "Users can delete connections" ON public.connections;
CREATE POLICY "Users can delete connections" ON public.connections FOR DELETE USING (auth.uid() = follower_id);

-- 7. Direct Messages
CREATE TABLE IF NOT EXISTS public.direct_messages (
  id bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
  sender_id uuid NOT NULL,
  receiver_id uuid NOT NULL,
  content text NOT NULL,
  is_read boolean DEFAULT false,
  created_at timestamp with time zone NOT NULL DEFAULT timezone('utc'::text, now()),
  CONSTRAINT direct_messages_pkey PRIMARY KEY (id),
  CONSTRAINT direct_messages_sender_id_fkey FOREIGN KEY (sender_id) REFERENCES public.users(id),
  CONSTRAINT direct_messages_receiver_id_fkey FOREIGN KEY (receiver_id) REFERENCES public.users(id)
);

ALTER TABLE public.direct_messages ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Users can see their messages" ON public.direct_messages;
CREATE POLICY "Users can see their messages" ON public.direct_messages FOR SELECT USING (auth.uid() = sender_id OR auth.uid() = receiver_id);

DROP POLICY IF EXISTS "Users can send messages" ON public.direct_messages;
CREATE POLICY "Users can send messages" ON public.direct_messages FOR INSERT WITH CHECK (auth.uid() = sender_id);


-- 8. User Attributes
CREATE TABLE IF NOT EXISTS public.user_attributes (
  id uuid NOT NULL DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL,
  fuerza integer DEFAULT 0,
  resistencia integer DEFAULT 0,
  velocidad integer DEFAULT 0,
  agilidad integer DEFAULT 0,
  coordinacion integer DEFAULT 0,
  vitalidad integer DEFAULT 0,
  destreza integer DEFAULT 0,
  kinestesia integer DEFAULT 0,
  armadura_natural integer DEFAULT 0,
  reflejos integer DEFAULT 0,
  stamina integer DEFAULT 0,
  inteligencia integer DEFAULT 0,
  percepcion integer DEFAULT 0,
  voluntad integer DEFAULT 0,
  creatividad integer DEFAULT 0,
  atencion integer DEFAULT 0,
  conocimiento_tecnico integer DEFAULT 0,
  medicina integer DEFAULT 0,
  vision integer DEFAULT 0,
  audicion integer DEFAULT 0,
  olfato_gusto integer DEFAULT 0,
  consciencia integer DEFAULT 0,
  control_interno integer DEFAULT 0,
  carisma integer DEFAULT 0,
  estabilidad_emocional integer DEFAULT 0,
  empatia integer DEFAULT 0,
  confianza integer DEFAULT 0,
  reputacion integer DEFAULT 0,
  influencia integer DEFAULT 0,
  negociacion integer DEFAULT 0,
  liderazgo integer DEFAULT 0,
  supervivencia integer DEFAULT 0,
  defensa_magica integer DEFAULT 0,
  mana integer DEFAULT 0,
  aura integer DEFAULT 0,
  suerte integer DEFAULT 0,
  afinidad_elemental integer DEFAULT 0,
  adaptacion integer DEFAULT 0,
  habilidad_unica integer DEFAULT 0,
  moralidad integer DEFAULT 0,
  ambicion integer DEFAULT 0,
  lealtad integer DEFAULT 0,
  CONSTRAINT user_attributes_pkey PRIMARY KEY (id),
  CONSTRAINT user_attributes_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id)
);

ALTER TABLE public.user_attributes ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Attributes viewable by owner" ON public.user_attributes;
CREATE POLICY "Attributes viewable by owner" ON public.user_attributes FOR SELECT USING (auth.uid() = user_id);

DROP POLICY IF EXISTS "Attributes updatable by owner" ON public.user_attributes;
CREATE POLICY "Attributes updatable by owner" ON public.user_attributes FOR UPDATE USING (auth.uid() = user_id);

DROP POLICY IF EXISTS "Attributes insertable by owner" ON public.user_attributes;
CREATE POLICY "Attributes insertable by owner" ON public.user_attributes FOR INSERT WITH CHECK (auth.uid() = user_id);

