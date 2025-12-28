-- Add title and category to posts if they don't exist
ALTER TABLE public.posts ADD COLUMN IF NOT EXISTS title text;
ALTER TABLE public.posts ADD COLUMN IF NOT EXISTS category text;

-- Asegurar RLS en posts
ALTER TABLE public.posts ENABLE ROW LEVEL SECURITY;

-- Policy for Deleting own posts
DROP POLICY IF EXISTS "Users can delete their own posts" ON public.posts;
CREATE POLICY "Users can delete their own posts" ON public.posts
FOR DELETE USING (auth.uid() = creator_id);

-- Policy for Updating own posts
DROP POLICY IF EXISTS "Users can update their own posts" ON public.posts;
CREATE POLICY "Users can update their own posts" ON public.posts
FOR UPDATE USING (auth.uid() = creator_id);

-- Ensure Select is public (or matches existing visibility logic)
DROP POLICY IF EXISTS "Posts are viewable by everyone" ON public.posts;
CREATE POLICY "Posts are viewable by everyone" ON public.posts
FOR SELECT USING (true);
