-- SAFE UPDATE SCRIPT
-- This script is designed to be safe to run on your existing database.

-- 1. Update POSTS table (Add is_anonymous column)
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'posts' AND column_name = 'is_anonymous') THEN
        ALTER TABLE public.posts ADD COLUMN is_anonymous BOOLEAN DEFAULT false;
    END IF;
END $$;

-- 1.1 Enable RLS on POSTS (Just in case)
ALTER TABLE public.posts ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Public posts are viewable by everyone" ON public.posts;
DROP POLICY IF EXISTS "Users can insert their own posts" ON public.posts;
DROP POLICY IF EXISTS "Users can delete their own posts" ON public.posts;
DROP POLICY IF EXISTS "Users can update their own posts" ON public.posts;

CREATE POLICY "Public posts are viewable by everyone" ON public.posts FOR SELECT USING (true);
CREATE POLICY "Users can insert their own posts" ON public.posts FOR INSERT WITH CHECK (auth.uid() = creator_id);
CREATE POLICY "Users can delete their own posts" ON public.posts FOR DELETE USING (auth.uid() = creator_id);
CREATE POLICY "Users can update their own posts" ON public.posts FOR UPDATE USING (auth.uid() = creator_id);

-- 2. Create REACTIONS table (if it doesn't exist)
CREATE TABLE IF NOT EXISTS public.post_reactions (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    post_id UUID NOT NULL REFERENCES public.posts(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
    emoji TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()) NOT NULL,
    UNIQUE(post_id, user_id)
);

-- 3. Configure RLS for COMMENTS (Safe reset)
ALTER TABLE public.comments ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Public comments are viewable by everyone" ON public.comments;
DROP POLICY IF EXISTS "Users can insert their own comments" ON public.comments;
DROP POLICY IF EXISTS "Users can delete their own comments" ON public.comments;

CREATE POLICY "Public comments are viewable by everyone" ON public.comments FOR SELECT USING (true);
CREATE POLICY "Users can insert their own comments" ON public.comments FOR INSERT WITH CHECK (auth.uid() = user_id);
CREATE POLICY "Users can delete their own comments" ON public.comments FOR DELETE USING (auth.uid() = user_id);

-- 4. Configure RLS for POST_REACTIONS (Safe reset)
ALTER TABLE public.post_reactions ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Public reactions are viewable by everyone" ON public.post_reactions;
DROP POLICY IF EXISTS "Users can insert their own reactions" ON public.post_reactions;
DROP POLICY IF EXISTS "Users can delete their own reactions" ON public.post_reactions;
DROP POLICY IF EXISTS "Users can update their own reactions" ON public.post_reactions;

CREATE POLICY "Public reactions are viewable by everyone" ON public.post_reactions FOR SELECT USING (true);
CREATE POLICY "Users can insert their own reactions" ON public.post_reactions FOR INSERT WITH CHECK (auth.uid() = user_id);
CREATE POLICY "Users can delete their own reactions" ON public.post_reactions FOR DELETE USING (auth.uid() = user_id);
CREATE POLICY "Users can update their own reactions" ON public.post_reactions FOR UPDATE USING (auth.uid() = user_id);

-- 5. Configure STORAGE (Bucket for images)
INSERT INTO storage.buckets (id, name, public)
VALUES ('post_media', 'post_media', true)
ON CONFLICT (id) DO NOTHING;

-- Storage Policies
DROP POLICY IF EXISTS "Public Access" ON storage.objects;
DROP POLICY IF EXISTS "Authenticated users can upload media" ON storage.objects;
DROP POLICY IF EXISTS "Users can delete own media" ON storage.objects;

CREATE POLICY "Public Access" ON storage.objects FOR SELECT USING ( bucket_id = 'post_media' );
CREATE POLICY "Authenticated users can upload media" ON storage.objects FOR INSERT WITH CHECK ( bucket_id = 'post_media' AND auth.role() = 'authenticated' );
CREATE POLICY "Users can delete own media" ON storage.objects FOR DELETE USING ( bucket_id = 'post_media' AND owner = auth.uid() );
