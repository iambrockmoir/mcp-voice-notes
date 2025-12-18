-- Add note_count column to projects table and auto-update it with triggers

-- Add the note_count column if it doesn't exist
ALTER TABLE projects
ADD COLUMN IF NOT EXISTS note_count INTEGER DEFAULT 0;

-- Function to update project note count
CREATE OR REPLACE FUNCTION update_project_note_count()
RETURNS TRIGGER AS $$
BEGIN
    -- Update count for the new project (INSERT)
    IF TG_OP = 'INSERT' AND NEW.project_id IS NOT NULL THEN
        UPDATE projects
        SET note_count = (
            SELECT COUNT(*) FROM notes WHERE project_id = NEW.project_id
        )
        WHERE id = NEW.project_id;
    END IF;

    -- Update count for both old and new project (UPDATE)
    IF TG_OP = 'UPDATE' THEN
        IF OLD.project_id IS NOT NULL THEN
            UPDATE projects
            SET note_count = (
                SELECT COUNT(*) FROM notes WHERE project_id = OLD.project_id
            )
            WHERE id = OLD.project_id;
        END IF;

        IF NEW.project_id IS NOT NULL THEN
            UPDATE projects
            SET note_count = (
                SELECT COUNT(*) FROM notes WHERE project_id = NEW.project_id
            )
            WHERE id = NEW.project_id;
        END IF;
    END IF;

    -- Update count for the old project (DELETE)
    IF TG_OP = 'DELETE' AND OLD.project_id IS NOT NULL THEN
        UPDATE projects
        SET note_count = (
            SELECT COUNT(*) FROM notes WHERE project_id = OLD.project_id
        )
        WHERE id = OLD.project_id;
    END IF;

    RETURN COALESCE(NEW, OLD);
END;
$$ LANGUAGE plpgsql;

-- Drop old trigger if exists
DROP TRIGGER IF EXISTS update_project_note_count_trigger ON notes;

-- Create trigger to auto-update note counts
CREATE TRIGGER update_project_note_count_trigger
    AFTER INSERT OR UPDATE OF project_id OR DELETE ON notes
    FOR EACH ROW
    EXECUTE FUNCTION update_project_note_count();

-- Initialize counts for existing projects
UPDATE projects
SET note_count = (
    SELECT COUNT(*)
    FROM notes
    WHERE notes.project_id = projects.id
);
