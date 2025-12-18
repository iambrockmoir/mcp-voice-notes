-- Fix RLS policies for projects to allow anon key access for test user
-- This allows the Android app to create/manage projects using the anon key

-- Drop the restrictive policies
DROP POLICY IF EXISTS "Users can view own projects" ON projects;
DROP POLICY IF EXISTS "Users can insert own projects" ON projects;
DROP POLICY IF EXISTS "Users can update own projects" ON projects;
DROP POLICY IF EXISTS "Users can delete own projects" ON projects;

-- Create permissive policies for test/development
-- Allow read access for test user
CREATE POLICY "Allow anon read for test user" ON projects
    FOR SELECT
    USING (user_id = '00000000-0000-0000-0000-000000000001'::UUID);

-- Allow insert for test user
CREATE POLICY "Allow anon insert for test user" ON projects
    FOR INSERT
    WITH CHECK (user_id = '00000000-0000-0000-0000-000000000001'::UUID);

-- Allow update for test user
CREATE POLICY "Allow anon update for test user" ON projects
    FOR UPDATE
    USING (user_id = '00000000-0000-0000-0000-000000000001'::UUID)
    WITH CHECK (user_id = '00000000-0000-0000-0000-000000000001'::UUID);

-- Allow delete for test user
CREATE POLICY "Allow anon delete for test user" ON projects
    FOR DELETE
    USING (user_id = '00000000-0000-0000-0000-000000000001'::UUID);
