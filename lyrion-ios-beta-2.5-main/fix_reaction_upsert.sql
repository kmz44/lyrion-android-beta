-- 1. Cleaning duplicates: Keep only the most recent reaction for each user/post pair
DELETE FROM public.post_reactions a USING public.post_reactions b
WHERE a.id < b.id
AND a.post_id = b.post_id
AND a.user_id = b.user_id;

-- 2. Add Unique Constraint to allow UPSERT (merge-duplicates)
-- This is required for ?on_conflict=post_id,user_id to work
ALTER TABLE public.post_reactions 
ADD CONSTRAINT post_reactions_user_post_unique UNIQUE (user_id, post_id);

-- 3. Ensure RLS is enabled and policies are correct (just in case)
ALTER TABLE public.post_reactions ENABLE ROW LEVEL SECURITY;

-- Re-apply policies (safe to run multiple times if dropped first)
DROP POLICY IF EXISTS "Users can view all post reactions" ON public.post_reactions;
DROP POLICY IF EXISTS "Users can insert their own post reactions" ON public.post_reactions;
DROP POLICY IF EXISTS "Users can update their own post reactions" ON public.post_reactions;
DROP POLICY IF EXISTS "Users can delete their own post reactions" ON public.post_reactions;

CREATE POLICY "Users can view all post reactions" ON public.post_reactions FOR SELECT USING (true);
CREATE POLICY "Users can insert their own post reactions" ON public.post_reactions FOR INSERT WITH CHECK (auth.uid() = user_id);
CREATE POLICY "Users can update their own post reactions" ON public.post_reactions FOR UPDATE USING (auth.uid() = user_id);
CREATE POLICY "Users can delete their own post reactions" ON public.post_reactions FOR DELETE USING (auth.uid() = user_id);
