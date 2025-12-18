# Voice Notes MCP - Technical Specification

## Project Overview

Voice Notes MCP is an open-source system for capturing, transcribing, and processing voice notes through a seamless integration between an Android app and Claude Desktop via the Model Context Protocol (MCP). The system enables users to quickly record thoughts, todos, and notes throughout the day, then efficiently process them during structured inbox reviews with AI assistance.

## System Goals

1. **Frictionless Capture**: Simple push-button recording on Android device
2. **Accurate Transcription**: Leverage OpenAI Whisper API for high-quality transcription
3. **Seamless Sync**: Automatic synchronization via Supabase cloud database
4. **AI-Powered Review**: Integration with Claude Desktop for intelligent inbox processing
5. **Privacy-Conscious**: User data isolated with row-level security
6. **Open Source**: Community-driven development and deployment

## Technical Stack

### Android Application
- **Language**: Kotlin
- **UI Framework**: Jetpack Compose
- **Database**: Supabase (PostgreSQL)
- **Transcription**: OpenAI Whisper API
- **Audio Recording**: MediaRecorder API
- **Background Processing**: WorkManager
- **Network**: Retrofit + OkHttp
- **Dependency Injection**: Hilt

### MCP Server
- **Language**: Python 3.9+
- **Framework**: MCP SDK (Model Context Protocol)
- **Database**: Supabase Python Client
- **Async**: asyncio
- **Environment**: systemd (Linux/Mac) or Task Scheduler (Windows)

### Cloud Infrastructure
- **Database**: Supabase (PostgreSQL)
- **Authentication**: Supabase Auth (API Keys)
- **Real-time**: Supabase Realtime subscriptions
- **Storage**: PostgreSQL for text data only (no audio storage)

## Core Features

### Android App Features
1. **Voice Recording**
   - Push-to-start, push-to-stop recording
   - Visual feedback during recording (timer, waveform)
   - Maximum recording length: 10 minutes
   - Automatic stop at maximum length

2. **Transcription**
   - Automatic transcription upon recording completion
   - Queue-based processing for offline scenarios
   - Progress indicator during transcription
   - Immediate edit capability post-transcription

3. **Note Management**
   - List view of all notes (newest first)
   - Inline editing of transcripts
   - Visual indicator for processing status
   - Hide processed notes option
   - Character count display

4. **Offline Support**
   - Queue recordings when offline
   - Local caching of recent notes
   - Automatic sync when connection restored
   - Visual indicator of sync status

### MCP Server Features
1. **Tools Exposed to Claude**
   - `list_unprocessed_notes`: Retrieve all unprocessed notes
   - `read_note`: Get specific note content by ID
   - `mark_as_processed`: Flag note as reviewed
   - `search_notes`: Search notes by keyword
   - `get_inbox_stats`: Summary statistics of pending items
   - `bulk_mark_processed`: Mark multiple notes as processed

2. **Real-time Updates**
   - Subscribe to new note creation
   - Watch for processing status changes
   - Connection health monitoring

## Data Models

### Database Schema

```sql
-- Main notes table
CREATE TABLE notes (
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

-- Indexes for performance
CREATE INDEX idx_user_unprocessed ON notes(user_id, is_processed, created_at DESC);
CREATE INDEX idx_user_search ON notes USING gin(to_tsvector('english', transcript));
CREATE INDEX idx_user_created ON notes(user_id, created_at DESC);

-- Row Level Security
ALTER TABLE notes ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Users can only see their own notes"
    ON notes FOR ALL
    USING (user_id = auth.uid());

-- Updated timestamp trigger
CREATE OR REPLACE FUNCTION update_modified_time()
RETURNS TRIGGER AS $$
BEGIN
    NEW.modified_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER update_notes_modified
    BEFORE UPDATE ON notes
    FOR EACH ROW
    EXECUTE FUNCTION update_modified_time();
```

### API Interfaces

#### Android App ↔ Supabase

```kotlin
// Data model
data class Note(
    val id: String,
    val userId: String,
    val transcript: String,
    val createdAt: Instant,
    val modifiedAt: Instant,
    val isProcessed: Boolean,
    val audioDurationSeconds: Int?,
    val transcriptionStatus: TranscriptionStatus,
    val wordCount: Int
)

enum class TranscriptionStatus {
    PENDING, PROCESSING, COMPLETED, FAILED
}
```

#### MCP Server ↔ Claude Desktop

```python
# Tool definitions
tools = [
    {
        "name": "list_unprocessed_notes",
        "description": "Get all unprocessed notes for inbox review",
        "parameters": {
            "limit": "Maximum number of notes to return (default: 50)",
            "offset": "Pagination offset (default: 0)"
        }
    },
    {
        "name": "read_note",
        "description": "Read the full content of a specific note",
        "parameters": {
            "note_id": "UUID of the note to read"
        }
    },
    {
        "name": "mark_as_processed",
        "description": "Mark a note as processed/reviewed",
        "parameters": {
            "note_id": "UUID of the note to mark as processed"
        }
    },
    {
        "name": "search_notes",
        "description": "Search notes by keyword",
        "parameters": {
            "query": "Search query string",
            "include_processed": "Include processed notes in search (default: false)"
        }
    },
    {
        "name": "get_inbox_stats",
        "description": "Get statistics about inbox",
        "parameters": {}
    }
]
```

## Security Requirements

1. **Authentication**
   - Supabase API keys stored securely
   - User authentication via Supabase Auth
   - Row-level security for data isolation

2. **Data Privacy**
   - No audio files stored permanently
   - Transcripts encrypted at rest (Supabase)
   - HTTPS for all API communications
   - Local caching encrypted on device

3. **API Security**
   - Rate limiting on transcription requests
   - API key rotation capability
   - Secure storage using Android Keystore

## Performance Requirements

1. **Recording**
   - Start recording within 100ms of button press
   - No dropped audio frames
   - Maximum 50MB temporary storage per recording

2. **Transcription**
   - Queue processing within 5 seconds of completion
   - Retry failed transcriptions up to 3 times
   - Timeout after 60 seconds

3. **Sync**
   - Changes reflected in Claude within 2 seconds
   - Batch sync for multiple offline notes
   - Automatic retry with exponential backoff

4. **MCP Server**
   - Response time < 500ms for all tools
   - Handle up to 10,000 notes efficiently
   - Concurrent request handling

## User Interface Requirements

### Android App Screens

1. **Main Screen**
   - Large, prominent record button (center bottom)
   - Notes list above (scrollable)
   - Sync status indicator (top right)
   - Settings gear (top left)

2. **Recording Screen**
   - Stop button (replaces record)
   - Recording timer
   - Audio level indicator
   - Cancel option

3. **Note Detail/Edit**
   - Editable text field
   - Word count
   - Save/Cancel buttons
   - Creation timestamp
   - Duration (if available)

4. **Settings Screen**
   - API key configuration
   - Show/hide processed notes
   - Clear cache option
   - About/version info

### Visual Design
- Material Design 3 guidelines
- High contrast for accessibility
- Dark mode support
- Large touch targets (48dp minimum)

## Error Handling

1. **Network Failures**
   - Graceful offline mode
   - Clear user messaging
   - Automatic retry with backoff

2. **Transcription Failures**
   - Keep audio until successful
   - Allow manual retry
   - Fallback to manual entry

3. **Storage Issues**
   - Alert when storage low
   - Automatic cleanup of old audio
   - Prevent recording if insufficient space

## Configuration

### Android App Configuration
```kotlin
object Config {
    const val OPENAI_API_ENDPOINT = "https://api.openai.com/v1/audio/transcriptions"
    const val SUPABASE_URL = "https://[PROJECT].supabase.co"
    const val MAX_RECORDING_DURATION_MS = 600000 // 10 minutes
    const val TRANSCRIPTION_MODEL = "whisper-1"
    const val SYNC_INTERVAL_MS = 30000 // 30 seconds
}
```

### MCP Server Configuration
```python
# config.py
CONFIG = {
    "supabase_url": os.getenv("SUPABASE_URL"),
    "supabase_key": os.getenv("SUPABASE_KEY"),
    "max_notes_per_query": 100,
    "cache_ttl_seconds": 60,
    "connection_timeout": 30
}
```

### Claude Desktop Configuration
```json
{
  "mcpServers": {
    "voice-notes-inbox": {
      "command": "python",
      "args": ["/path/to/voice_notes_mcp/server.py"],
      "env": {
        "SUPABASE_URL": "https://[PROJECT].supabase.co",
        "SUPABASE_KEY": "[ANON_KEY]"
      }
    }
  }
}
```

## Testing Requirements

1. **Unit Tests**
   - Audio recording/playback
   - Transcription queue logic
   - Database operations
   - MCP tool implementations

2. **Integration Tests**
   - End-to-end recording to transcription
   - Offline/online transitions
   - MCP server communication
   - Supabase sync

3. **User Acceptance Tests**
   - Record and review 10 notes
   - Process notes via Claude
   - Offline recording and sync
   - Error recovery scenarios

## Deployment

### Android App
1. Build signed APK
2. Initial deployment via direct install
3. Future: Google Play Store / F-Droid

### MCP Server
1. Install Python dependencies
2. Configure systemd service (Linux/Mac)
3. Set environment variables
4. Start service

### Supabase Setup
1. Create new project
2. Run schema migrations
3. Configure Row Level Security
4. Generate API keys
5. Set up backups

## Future Enhancements

1. **Voice Commands**: "Start note", "Stop recording"
2. **Auto-categorization**: AI-powered todo detection
3. **Reminders**: Notification for unprocessed notes
4. **Export**: Backup to Google Drive, Notion
5. **Multi-language**: Support beyond English
6. **Widgets**: Home screen quick record widget
7. **Watch App**: Wear OS companion app
8. **Templates**: Quick note templates for common patterns

## License

MIT License - Open source for community use and contribution

## Version History

- v1.0.0 - Initial release with core functionality
- v1.1.0 - (Planned) Widget support and voice activation
- v1.2.0 - (Planned) Multi-language transcription