-- Voice Notes v1.1 - Projects Feature Migration
-- This migration adds Projects support to the voice notes system

-- Create the projects table
CREATE TABLE IF NOT EXISTS projects (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    name TEXT NOT NULL,
    purpose TEXT,
    goal TEXT,
    is_archived BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- Add project_id to voice notes
ALTER TABLE notes
ADD COLUMN IF NOT EXISTS project_id UUID REFERENCES projects(id) ON DELETE SET NULL;

-- Create indexes for performance
CREATE INDEX IF NOT EXISTS idx_projects_user_active ON projects(user_id, is_archived, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_voice_notes_project ON notes(project_id, created_at DESC);

-- Enable Row Level Security for projects
ALTER TABLE projects ENABLE ROW LEVEL SECURITY;

-- Create RLS policies for projects
DROP POLICY IF EXISTS "Users can view own projects" ON projects;
CREATE POLICY "Users can view own projects" ON projects
    FOR SELECT USING (user_id = auth.uid());

DROP POLICY IF EXISTS "Users can insert own projects" ON projects;
CREATE POLICY "Users can insert own projects" ON projects
    FOR INSERT WITH CHECK (user_id = auth.uid());

DROP POLICY IF EXISTS "Users can update own projects" ON projects;
CREATE POLICY "Users can update own projects" ON projects
    FOR UPDATE USING (user_id = auth.uid());

DROP POLICY IF EXISTS "Users can delete own projects" ON projects;
CREATE POLICY "Users can delete own projects" ON projects
    FOR DELETE USING (user_id = auth.uid());

-- Create function to update project updated_at timestamp
CREATE OR REPLACE FUNCTION update_project_timestamp()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Create trigger for updating project updated_at
DROP TRIGGER IF EXISTS update_projects_timestamp ON projects;
CREATE TRIGGER update_projects_timestamp
    BEFORE UPDATE ON projects
    FOR EACH ROW
    EXECUTE FUNCTION update_project_timestamp();

-- Create trigger to update project timestamp when notes are added/modified
CREATE OR REPLACE FUNCTION update_project_on_note_change()
RETURNS TRIGGER AS $$
BEGIN
    -- Update the project's updated_at when a note is assigned to it
    IF NEW.project_id IS NOT NULL THEN
        UPDATE projects
        SET updated_at = NOW()
        WHERE id = NEW.project_id;
    END IF;

    -- If project was changed, update both old and new project
    IF TG_OP = 'UPDATE' AND OLD.project_id IS DISTINCT FROM NEW.project_id THEN
        IF OLD.project_id IS NOT NULL THEN
            UPDATE projects
            SET updated_at = NOW()
            WHERE id = OLD.project_id;
        END IF;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS update_project_on_note_change_trigger ON notes;
CREATE TRIGGER update_project_on_note_change_trigger
    AFTER INSERT OR UPDATE OF project_id ON notes
    FOR EACH ROW
    EXECUTE FUNCTION update_project_on_note_change();

-- Updated inbox query function (notes without projects)
CREATE OR REPLACE FUNCTION get_inbox_notes(
    p_user_id UUID,
    p_limit INT DEFAULT 50,
    p_offset INT DEFAULT 0
)
RETURNS TABLE (
    id UUID,
    transcript TEXT,
    created_at TIMESTAMPTZ,
    word_count INT,
    audio_duration_seconds INT
) AS $$
BEGIN
    RETURN QUERY
    SELECT n.id, n.transcript, n.created_at, n.word_count, n.audio_duration_seconds
    FROM notes n
    WHERE n.user_id = p_user_id
      AND n.project_id IS NULL
      AND n.is_processed = FALSE
      AND n.transcription_status = 'completed'
    ORDER BY n.created_at DESC
    LIMIT p_limit
    OFFSET p_offset;
END;
$$ LANGUAGE plpgsql;

-- Get project with note count
CREATE OR REPLACE FUNCTION get_projects_with_counts(
    p_user_id UUID,
    p_include_archived BOOLEAN DEFAULT FALSE
)
RETURNS TABLE (
    id UUID,
    name TEXT,
    purpose TEXT,
    goal TEXT,
    is_archived BOOLEAN,
    note_count BIGINT,
    created_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ
) AS $$
BEGIN
    RETURN QUERY
    SELECT
        p.id,
        p.name,
        p.purpose,
        p.goal,
        p.is_archived,
        COUNT(n.id) as note_count,
        p.created_at,
        p.updated_at
    FROM projects p
    LEFT JOIN notes n ON n.project_id = p.id
    WHERE p.user_id = p_user_id
      AND (p_include_archived = TRUE OR p.is_archived = FALSE)
    GROUP BY p.id
    ORDER BY p.updated_at DESC;
END;
$$ LANGUAGE plpgsql;

-- Get notes for a specific project
CREATE OR REPLACE FUNCTION get_project_notes(
    p_user_id UUID,
    p_project_id UUID,
    p_limit INT DEFAULT 50,
    p_offset INT DEFAULT 0
)
RETURNS TABLE (
    id UUID,
    transcript TEXT,
    created_at TIMESTAMPTZ,
    modified_at TIMESTAMPTZ,
    word_count INT,
    audio_duration_seconds INT,
    is_processed BOOLEAN
) AS $$
BEGIN
    RETURN QUERY
    SELECT
        n.id,
        n.transcript,
        n.created_at,
        n.modified_at,
        n.word_count,
        n.audio_duration_seconds,
        n.is_processed
    FROM notes n
    WHERE n.user_id = p_user_id
      AND n.project_id = p_project_id
      AND n.transcription_status = 'completed'
    ORDER BY n.created_at DESC
    LIMIT p_limit
    OFFSET p_offset;
END;
$$ LANGUAGE plpgsql;

-- Comments for documentation
COMMENT ON TABLE projects IS 'Projects for organizing voice notes by context/purpose';
COMMENT ON COLUMN projects.user_id IS 'References the authenticated user';
COMMENT ON COLUMN projects.name IS 'Short project identifier';
COMMENT ON COLUMN projects.purpose IS 'Why this project matters';
COMMENT ON COLUMN projects.goal IS 'What success looks like';
COMMENT ON COLUMN projects.is_archived IS 'Whether project is archived (soft delete)';
COMMENT ON COLUMN notes.project_id IS 'Optional reference to parent project';
