
-- Create Friend Requests Table
CREATE TABLE public.friend_requests (
    id uuid NOT NULL DEFAULT gen_random_uuid(),
    sender_id uuid NOT NULL REFERENCES public.users(id),
    receiver_id uuid NOT NULL REFERENCES public.users(id),
    status text DEFAULT 'pending' CHECK (status IN ('pending', 'rejected')), -- Accepted move to friends table
    created_at timestamp with time zone DEFAULT now(),
    CONSTRAINT friend_requests_pkey PRIMARY KEY (id),
    CONSTRAINT friend_requests_unique UNIQUE (sender_id, receiver_id)
);

-- RLS for Friend Requests
ALTER TABLE public.friend_requests ENABLE ROW LEVEL SECURITY;

-- Sender can see their own sent requests
CREATE POLICY "Sender see requests" ON public.friend_requests 
    FOR SELECT USING (auth.uid() = sender_id);

-- Receiver can see their own received requests
CREATE POLICY "Receiver see requests" ON public.friend_requests 
    FOR SELECT USING (auth.uid() = receiver_id);

-- Sender can insert (create request)
CREATE POLICY "Sender create request" ON public.friend_requests 
    FOR INSERT WITH CHECK (auth.uid() = sender_id);

-- Receiver or Sender can delete (cancel/reject/accept)
CREATE POLICY "Participants delete request" ON public.friend_requests 
    FOR DELETE USING (auth.uid() = sender_id OR auth.uid() = receiver_id);

-- Migration (Optional): If connections had pending requests
-- We previously filtered for 'active' only when migrating friends.
-- Let's migrate 'pending' connections to friend_requests.
INSERT INTO public.friend_requests (sender_id, receiver_id, created_at)
SELECT follower_id, following_id, created_at
FROM public.connections_backup_deprecated
WHERE (status = 'pending' OR friendship_status = 'pendiente' OR friendship_status = 'pending')
ON CONFLICT DO NOTHING;
