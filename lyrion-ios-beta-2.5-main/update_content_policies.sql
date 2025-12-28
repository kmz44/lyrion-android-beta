-- Enable RLS on tables if not already enabled
ALTER TABLE public.posts ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.comments ENABLE ROW LEVEL SECURITY;

-- POSTS POLICIES

-- Drop existing policies if they exist
DROP POLICY IF EXISTS "Users can update their own posts" ON public.posts;
DROP POLICY IF EXISTS "Users can delete their own posts" ON public.posts;

-- Allow users to update their own posts
CREATE POLICY "Users can update their own posts"
ON public.posts
FOR UPDATE
USING (auth.uid() = creator_id);

-- Allow users to delete their own posts
CREATE POLICY "Users can delete their own posts"
ON public.posts
FOR DELETE
USING (auth.uid() = creator_id);

-- COMMENTS POLICIES

-- Drop existing policies if they exist
DROP POLICY IF EXISTS "Users can update their own comments" ON public.comments;
DROP POLICY IF EXISTS "Users can delete their own comments" ON public.comments;
DROP POLICY IF EXISTS "Post creators can delete comments on their posts" ON public.comments;

-- Allow users to update their own comments
CREATE POLICY "Users can update their own comments"
ON public.comments
FOR UPDATE
USING (auth.uid() = user_id);

-- Allow users to delete their own comments
CREATE POLICY "Users can delete their own comments"
ON public.comments
FOR DELETE
USING (auth.uid() = user_id);

-- Allow post owners to delete comments on their posts (Optional but good for moderation)
CREATE POLICY "Post creators can delete comments on their posts"
ON public.comments
FOR DELETE
USING (
  EXISTS (
    SELECT 1 FROM public.posts
    WHERE posts.id = comments.post_id
    AND posts.creator_id = auth.uid()
  )
);

-- COMMENT REACTIONS POLICIES (Required for like/dislike to work!)

ALTER TABLE public.comment_reactions ENABLE ROW LEVEL SECURITY;

-- Drop existing policies if they exist
DROP POLICY IF EXISTS "Users can view all comment reactions" ON public.comment_reactions;
DROP POLICY IF EXISTS "Users can insert their own comment reactions" ON public.comment_reactions;
DROP POLICY IF EXISTS "Users can update their own comment reactions" ON public.comment_reactions;
DROP POLICY IF EXISTS "Users can delete their own comment reactions" ON public.comment_reactions;

-- Allow users to view all reactions
CREATE POLICY "Users can view all comment reactions"
ON public.comment_reactions
FOR SELECT
USING (true);

-- Allow users to insert their own reactions
CREATE POLICY "Users can insert their own comment reactions"
ON public.comment_reactions
FOR INSERT
WITH CHECK (auth.uid() = user_id);

-- Allow users to update their own reactions
CREATE POLICY "Users can update their own comment reactions"
ON public.comment_reactions
FOR UPDATE
USING (auth.uid() = user_id);

-- Allow users to delete their own reactions
CREATE POLICY "Users can delete their own comment reactions"
ON public.comment_reactions
FOR DELETE
USING (auth.uid() = user_id);
