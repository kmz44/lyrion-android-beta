-- Enable RLS on users table
ALTER TABLE public.users ENABLE ROW LEVEL SECURITY;

-- Allow users to view their own profile and others' profiles (public)
CREATE POLICY "Public profiles are viewable by everyone" ON public.users
FOR SELECT USING (true);

-- Allow users to insert their own profile
CREATE POLICY "Users can insert their own profile" ON public.users
FOR INSERT WITH CHECK (auth.uid() = id);

-- Allow users to update their own profile
CREATE POLICY "Users can update their own profile" ON public.users
FOR UPDATE USING (auth.uid() = id);

-- STORAGE POLICIES
-- Ensure 'avatars' bucket exists (done via dashboard usually, but policies depend on it)

-- Allow public access to read avatars
CREATE POLICY "Avatar images are publicly accessible" ON storage.objects
FOR SELECT USING ( bucket_id = 'avatars' );

-- Allow authenticated users to upload their own avatar
-- We assume the path is {userId}/filename
CREATE POLICY "Users can upload their own avatar" ON storage.objects
FOR INSERT WITH CHECK (
    bucket_id = 'avatars' 
    AND auth.uid() = (storage.foldername(name))[1]::uuid
);

-- Allow users to update/delete their own avatar
CREATE POLICY "Users can update their own avatar" ON storage.objects
FOR UPDATE USING (
    bucket_id = 'avatars' 
    AND auth.uid() = (storage.foldername(name))[1]::uuid
);
