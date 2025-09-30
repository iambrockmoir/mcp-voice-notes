-- Voice Notes MCP - Supabase Database Setup
-- Run this script in your Supabase SQL editor

-- Enable necessary extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Create the main notes table
CREATE TABLE IF NOT EXISTS notes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    transcript TEXT NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    modified_at TIMESTAMPTZ DEFAULT NOW(),
    is_processed BOOLEAN DEFAULT FALSE,
    audio_duration_seconds INTEGER,
    transcription_status TEXT DEFAULT 'pending',
    word_count INTEGER,
    CONSTRAINT valid_status CHECK (
        transcription_status IN ('pending', 'processing', 'completed', 'failed')
    )
);

-- Create indexes for performance
CREATE INDEX IF NOT EXISTS idx_user_unprocessed ON notes(user_id, is_processed, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_user_search ON notes USING gin(to_tsvector('english', transcript));
CREATE INDEX IF NOT EXISTS idx_user_created ON notes(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_transcription_status ON notes(user_id, transcription_status);

-- Enable Row Level Security
ALTER TABLE notes ENABLE ROW LEVEL SECURITY;

-- Create RLS policies
DROP POLICY IF EXISTS "Users can only see their own notes" ON notes;
CREATE POLICY "Users can only see their own notes"
    ON notes FOR ALL
    USING (user_id = auth.uid());

-- Create function to update modified_at timestamp
CREATE OR REPLACE FUNCTION update_modified_time()
RETURNS TRIGGER AS $$
BEGIN
    NEW.modified_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Create trigger for updating modified_at
DROP TRIGGER IF EXISTS update_notes_modified ON notes;
CREATE TRIGGER update_notes_modified
    BEFORE UPDATE ON notes
    FOR EACH ROW
    EXECUTE FUNCTION update_modified_time();

-- Create function to automatically calculate word count
CREATE OR REPLACE FUNCTION calculate_word_count()
RETURNS TRIGGER AS $$
BEGIN
    NEW.word_count = array_length(string_to_array(trim(NEW.transcript), ' '), 1);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Create trigger for calculating word count
DROP TRIGGER IF EXISTS calculate_word_count_trigger ON notes;
CREATE TRIGGER calculate_word_count_trigger
    BEFORE INSERT OR UPDATE OF transcript ON notes
    FOR EACH ROW
    EXECUTE FUNCTION calculate_word_count();

-- Create optimized function for getting unprocessed notes
CREATE OR REPLACE FUNCTION get_unprocessed_notes(
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
      AND n.is_processed = false
      AND n.transcription_status = 'completed'
    ORDER BY n.created_at DESC
    LIMIT p_limit
    OFFSET p_offset;
END;
$$ LANGUAGE plpgsql;

-- Create function for full-text search
CREATE OR REPLACE FUNCTION search_notes(
    p_user_id UUID,
    p_query TEXT,
    p_include_processed BOOLEAN DEFAULT FALSE,
    p_limit INT DEFAULT 20
)
RETURNS TABLE (
    id UUID,
    transcript TEXT,
    created_at TIMESTAMPTZ,
    word_count INT,
    is_processed BOOLEAN,
    rank REAL
) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        n.id, 
        n.transcript, 
        n.created_at, 
        n.word_count, 
        n.is_processed,
        ts_rank(to_tsvector('english', n.transcript), plainto_tsquery('english', p_query)) as rank
    FROM notes n
    WHERE n.user_id = p_user_id
      AND to_tsvector('english', n.transcript) @@ plainto_tsquery('english', p_query)
      AND (p_include_processed = TRUE OR n.is_processed = FALSE)
      AND n.transcription_status = 'completed'
    ORDER BY rank DESC, n.created_at DESC
    LIMIT p_limit;
END;
$$ LANGUAGE plpgsql;

-- Enable real-time subscriptions for the notes table
ALTER PUBLICATION supabase_realtime ADD TABLE notes;

-- Create some helpful views
CREATE OR REPLACE VIEW unprocessed_notes_summary AS
SELECT 
    user_id,
    COUNT(*) as unprocessed_count,
    SUM(word_count) as total_words,
    MAX(created_at) as latest_note,
    MIN(created_at) as oldest_note
FROM notes 
WHERE is_processed = FALSE AND transcription_status = 'completed'
GROUP BY user_id;

-- Insert sample data (optional - remove in production)
-- This creates a test user and some sample notes for development
DO $$
DECLARE
    test_user_id UUID := '00000000-0000-0000-0000-000000000001';
BEGIN
    -- Only insert sample data if no notes exist
    IF NOT EXISTS (SELECT 1 FROM notes LIMIT 1) THEN
        INSERT INTO notes (user_id, transcript, transcription_status, audio_duration_seconds) VALUES
        (test_user_id, 'Remember to pick up groceries on the way home. Need milk, bread, and eggs.', 'completed', 15),
        (test_user_id, 'Meeting with client tomorrow at 2 PM. Discuss project timeline and budget requirements.', 'completed', 22),
        (test_user_id, 'Idea for new feature: voice-activated note taking with automatic categorization.', 'completed', 18),
        (test_user_id, 'Doctor appointment scheduled for Friday morning. Bring insurance card and list of medications.', 'completed', 20),
        (test_user_id, 'Weekend plan: Visit the museum with family, then lunch at the new restaurant downtown.', 'completed', 25);
    END IF;
END $$;

-- Create a function to clean up old processed notes (optional)
CREATE OR REPLACE FUNCTION cleanup_old_notes(days_old INT DEFAULT 365)
RETURNS INT AS $$
DECLARE
    deleted_count INT;
BEGIN
    DELETE FROM notes 
    WHERE is_processed = TRUE 
      AND created_at < NOW() - (days_old || ' days')::INTERVAL;
    
    GET DIAGNOSTICS deleted_count = ROW_COUNT;
    RETURN deleted_count;
END;
$$ LANGUAGE plpgsql;

-- Grant necessary permissions (adjust as needed for your setup)
-- GRANT USAGE ON SCHEMA public TO anon;
-- GRANT SELECT, INSERT, UPDATE ON notes TO anon;
-- GRANT USAGE ON ALL SEQUENCES IN SCHEMA public TO anon;

COMMENT ON TABLE notes IS 'Voice notes with transcription and processing status';
COMMENT ON COLUMN notes.user_id IS 'References the authenticated user';
COMMENT ON COLUMN notes.transcript IS 'Transcribed text from voice recording';
COMMENT ON COLUMN notes.is_processed IS 'Whether this note has been reviewed/processed';
COMMENT ON COLUMN notes.transcription_status IS 'Status of the transcription process';
COMMENT ON COLUMN notes.word_count IS 'Automatically calculated word count';