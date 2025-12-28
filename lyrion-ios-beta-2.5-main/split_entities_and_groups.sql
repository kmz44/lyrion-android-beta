-- 1. Create FOLLOWERS Table
CREATE TABLE public.followers (
    id uuid NOT NULL DEFAULT gen_random_uuid(),
    follower_id uuid NOT NULL REFERENCES public.users(id),
    followed_id uuid NOT NULL REFERENCES public.users(id),
    created_at timestamp with time zone DEFAULT now(),
    notifications_enabled boolean DEFAULT true,
    CONSTRAINT followers_pkey PRIMARY KEY (id),
    CONSTRAINT followers_unique_pair UNIQUE (follower_id, followed_id)
);

-- 2. Create FRIENDS Table
-- Stores confirmed friendships (bidirectional usually implied by existance, or single row? 
-- Let's go with single row (user1 < user2) or double row. 
-- For simplicity and querying efficiency in Supabase: Double Row is best for RLS, but Single Row is cleaner.
-- Decision: Double Row (Two rows per friendship: A->B and B->A) makes querying "My Friends" extremely simple (where user_id = me).
CREATE TABLE public.friends (
    id uuid NOT NULL DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES public.users(id),
    friend_id uuid NOT NULL REFERENCES public.users(id),
    status text DEFAULT 'active' CHECK (status IN ('active', 'blocked')),
    created_at timestamp with time zone DEFAULT now(),
    CONSTRAINT friends_pkey PRIMARY KEY (id),
    CONSTRAINT friends_unique_pair UNIQUE (user_id, friend_id)
);

-- 3. Create GROUPS Table
CREATE TABLE public.groups (
    id uuid NOT NULL DEFAULT gen_random_uuid(),
    name text NOT NULL,
    description text,
    image_url text,
    creator_id uuid NOT NULL REFERENCES public.users(id),
    type text NOT NULL CHECK (type IN ('public', 'friends_only', 'followers_only', 'private')),
    created_at timestamp with time zone DEFAULT now(),
    CONSTRAINT groups_pkey PRIMARY KEY (id)
);

-- 4. Create GROUP_MEMBERS Table
CREATE TABLE public.group_members (
    id uuid NOT NULL DEFAULT gen_random_uuid(),
    group_id uuid NOT NULL REFERENCES public.groups(id) ON DELETE CASCADE,
    user_id uuid NOT NULL REFERENCES public.users(id),
    role text DEFAULT 'member' CHECK (role IN ('admin', 'moderator', 'member')),
    joined_at timestamp with time zone DEFAULT now(),
    CONSTRAINT group_members_pkey PRIMARY KEY (id),
    CONSTRAINT group_members_unique UNIQUE (group_id, user_id)
);

-- 5. UPDATE Users Table (Presence)
ALTER TABLE public.users 
ADD COLUMN IF NOT EXISTS is_active boolean DEFAULT false,
ADD COLUMN IF NOT EXISTS last_active_at timestamp with time zone DEFAULT now();

-- 6. UPDATE Direct Messages (Ephemeral)
ALTER TABLE public.direct_messages
ADD COLUMN IF NOT EXISTS is_temporary boolean DEFAULT true,
ADD COLUMN IF NOT EXISTS seen_at timestamp with time zone;

-- 7. MIGRATE DATA FROM CONNECTIONS (Legacy) to NEW TABLES

-- Migrate Followers
-- Old: connections where is_following = true.
-- (follower_id follows following_id)
INSERT INTO public.followers (follower_id, followed_id, created_at)
SELECT follower_id, following_id, created_at
FROM public.connections
WHERE is_following = true
ON CONFLICT DO NOTHING;

-- Migrate Friends
-- Old: connections where status = 'amigos' OR friendship_status = 'amigos'
-- We want double rows.
-- Forward (A -> B)
INSERT INTO public.friends (user_id, friend_id, created_at)
SELECT follower_id, following_id, created_at
FROM public.connections
WHERE status = 'amigos' OR friendship_status = 'amigos'
ON CONFLICT DO NOTHING;

-- Reverse (B -> A) 
-- (Assuming connection meant friendship both ways, we insert the reverse explicitly)
INSERT INTO public.friends (user_id, friend_id, created_at)
SELECT following_id, follower_id, created_at
FROM public.connections
WHERE status = 'amigos' OR friendship_status = 'amigos'
ON CONFLICT DO NOTHING;

-- 8. Enable RLS (Row Level Security) - Basic Policies
ALTER TABLE public.followers ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.friends ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.groups ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.group_members ENABLE ROW LEVEL SECURITY;

-- Followers Policy: Public read, Authenticated insert
CREATE POLICY "Public followers read" ON public.followers FOR SELECT USING (true);
CREATE POLICY "Auth followers insert" ON public.followers FOR INSERT WITH CHECK (auth.uid() = follower_id);
CREATE POLICY "Auth followers delete" ON public.followers FOR DELETE USING (auth.uid() = follower_id);

-- Friends Policy: Users can see their own friends
CREATE POLICY "See own friends" ON public.friends FOR SELECT USING (auth.uid() = user_id);
-- Insert/Delete handled by app logic (requests system usually needed separately or handled here?)
-- For now, allow insert if user is auth
CREATE POLICY "Auth friends insert" ON public.friends FOR INSERT WITH CHECK (auth.uid() = user_id); 

-- Groups Policy: Public read for now (refine later based on Type)
CREATE POLICY "Groups read" ON public.groups FOR SELECT USING (true);
CREATE POLICY "Group members read" ON public.group_members FOR SELECT USING (true);

-- 9. NOT DROP connections YES?
-- User warning said "Splitting... will require migrating...". 
-- Safe to keep table for now renamed? Or drop? 
-- Let's RENAME it to backup just in case.
ALTER TABLE public.connections RENAME TO connections_backup_deprecated;
