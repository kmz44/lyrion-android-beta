-- Enable RLS (safe to run multiple times)
ALTER TABLE public.direct_messages ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.connections ENABLE ROW LEVEL SECURITY;

-- Drop existing policies to avoid "already exists" errors and ensure correct definitions
DROP POLICY IF EXISTS "Users can send messages" ON public.direct_messages;
DROP POLICY IF EXISTS "Users can view their own messages" ON public.direct_messages;
DROP POLICY IF EXISTS "Users can follow others" ON public.connections;
DROP POLICY IF EXISTS "Users can view their connections" ON public.connections;
DROP POLICY IF EXISTS "Users can update connection status" ON public.connections;

-- Re-create policies

-- 1. Direct Messages
CREATE POLICY "Users can send messages" ON public.direct_messages
FOR INSERT
WITH CHECK (auth.uid() = sender_id);

CREATE POLICY "Users can view their own messages" ON public.direct_messages
FOR SELECT
USING (auth.uid() = sender_id OR auth.uid() = receiver_id);

-- 2. Connections (Follows)
CREATE POLICY "Users can follow others" ON public.connections
FOR INSERT
WITH CHECK (auth.uid() = follower_id);

CREATE POLICY "Users can view their connections" ON public.connections
FOR SELECT
USING (auth.uid() = follower_id OR auth.uid() = following_id);

CREATE POLICY "Users can update connection status" ON public.connections
FOR UPDATE
USING (auth.uid() = following_id)
WITH CHECK (auth.uid() = following_id);
