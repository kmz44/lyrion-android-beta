-- Add status tracking for WhatsApp-style messaging
-- Status: 'sent' (single check), 'delivered' (double check), 'read' (double blue check)

ALTER TABLE public.direct_messages 
ADD COLUMN IF NOT EXISTS status text DEFAULT 'sent' CHECK (status = ANY (ARRAY['sent'::text, 'delivered'::text, 'read'::text]));

ALTER TABLE public.direct_messages 
ADD COLUMN IF NOT EXISTS delivered_at timestamp with time zone;

-- Enable RLS
ALTER TABLE public.direct_messages ENABLE ROW LEVEL SECURITY;

-- Policy: Users can view messages they sent or received
DROP POLICY IF EXISTS "Users can view their own messages" ON public.direct_messages;
CREATE POLICY "Users can view their own messages" ON public.direct_messages
FOR SELECT USING (auth.uid() = sender_id OR auth.uid() = receiver_id);

-- Policy: Users can insert their own messages
DROP POLICY IF EXISTS "Users can send messages" ON public.direct_messages;
CREATE POLICY "Users can send messages" ON public.direct_messages
FOR INSERT WITH CHECK (auth.uid() = sender_id);

-- Policy: Receiver can update message status (delivered/read)
DROP POLICY IF EXISTS "Receiver can update message status" ON public.direct_messages;
CREATE POLICY "Receiver can update message status" ON public.direct_messages
FOR UPDATE USING (auth.uid() = receiver_id);

-- Policy: Sender can delete their own sent messages, receiver can delete read messages
DROP POLICY IF EXISTS "Users can delete their messages" ON public.direct_messages;
CREATE POLICY "Users can delete their messages" ON public.direct_messages
FOR DELETE USING (auth.uid() = sender_id OR (auth.uid() = receiver_id AND status = 'read'));
