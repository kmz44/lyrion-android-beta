-- Enable RLS on post_reactions table
ALTER TABLE public.post_reactions ENABLE ROW LEVEL SECURITY;

-- Drop existing policies if they exist
DROP POLICY IF EXISTS "Users can view all post reactions" ON public.post_reactions;
DROP POLICY IF EXISTS "Users can insert their own post reactions" ON public.post_reactions;
DROP POLICY IF EXISTS "Users can update their own post reactions" ON public.post_reactions;
DROP POLICY IF EXISTS "Users can delete their own post reactions" ON public.post_reactions;

-- Allow users to view all reactions
CREATE POLICY "Users can view all post reactions"
ON public.post_reactions
FOR SELECT
USING (true);

-- Allow users to insert their own reactions
CREATE POLICY "Users can insert their own post reactions"
ON public.post_reactions
FOR INSERT
WITH CHECK (auth.uid() = user_id);

-- Allow users to update their own reactions
CREATE POLICY "Users can update their own post reactions"
ON public.post_reactions
FOR UPDATE
USING (auth.uid() = user_id);

-- Allow users to delete their own reactions
CREATE POLICY "Users can delete their own post reactions"
ON public.post_reactions
FOR DELETE
USING (auth.uid() = user_id);
