-- Enable RLS on tables if not already enabled
ALTER TABLE public.direct_messages ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.connections ENABLE ROW LEVEL SECURITY;

-- Policy to allow users to send messages (INSERT)
CREATE POLICY "Users can send messages" ON public.direct_messages
FOR INSERT
WITH CHECK (auth.uid() = sender_id);

-- Policy to allow users to view their own messages (SELECT)
CREATE POLICY "Users can view their own messages" ON public.direct_messages
FOR SELECT
USING (auth.uid() = sender_id OR auth.uid() = receiver_id);

-- Policy to allow users to follow others (INSERT)
CREATE POLICY "Users can follow others" ON public.connections
FOR INSERT
WITH CHECK (auth.uid() = follower_id);

-- Policy to allow users to view their connections (SELECT)
CREATE POLICY "Users can view their connections" ON public.connections
FOR SELECT
USING (auth.uid() = follower_id OR auth.uid() = following_id);

-- Policy to allow users to update connection status (UPDATE) e.g. accept friend request
CREATE POLICY "Users can update connection status" ON public.connections
FOR UPDATE
USING (auth.uid() = following_id)
WITH CHECK (auth.uid() = following_id);
