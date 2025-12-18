# Voice Notes MCP - Project Structure

## Overview

This is a complete voice notes ecosystem with:
- **Android App**: Record and transcribe voice notes
- **MCP Server**: Claude Desktop integration for AI-powered note management
- **Projects Feature (v1.1)**: Organize notes into projects with goals and purposes

## Directory Structure

```
mcp_thought_recorder/
├── android_app/                    # Android voice recording application
│   ├── app/
│   │   ├── src/main/java/com/voicenotes/mcp/
│   │   │   ├── MainActivity.kt         # Main UI with 3 tabs (Inbox, Projects, Settings)
│   │   │   ├── SupabaseClient.kt       # Database operations for notes & projects
│   │   │   ├── OpenAIClient.kt         # Whisper API integration for transcription
│   │   │   ├── AudioRecorder.kt        # Audio recording functionality
│   │   │   └── Note.kt                 # Data models (Note, Project)
│   │   └── build.gradle.kts            # App dependencies and build config
│   ├── local.properties                # API keys (git-ignored)
│   └── INSTALL_INSTRUCTIONS.md         # Android setup guide
│
├── voice_notes_mcp/                # MCP Server implementations
│   ├── server.py                       # ✅ MAIN SERVER - Full-featured with Projects v1.1
│   ├── mcp_server.py                   # Lightweight server (urllib-based, no heavy deps)
│   ├── simple_server.py                # Minimal server for testing
│   ├── debug_mcp.py                    # Debug/testing script
│   ├── requirements.txt                # Python dependencies
│   └── .env                            # Server environment variables (git-ignored)
│
├── migrations/                     # Database schema updates
│   ├── 001_add_projects.sql            # Projects table creation (v1.1)
│   ├── 002_fix_projects_rls.sql        # Row-level security fixes
│   └── 003_add_note_count_column.sql   # Auto-updating note counts
│
├── scripts/                        # Utility scripts
│   ├── check_notes.py                  # Verify notes in database
│   ├── check_projects_schema.py        # Verify projects schema
│   ├── setup_mcp_env.py                # MCP environment setup
│   ├── test_mcp_connection.py          # Test Supabase connection
│   ├── test_mcp_live.py                # Test live MCP server
│   └── validate_mcp.py                 # Validate MCP installation
│
├── tests/                          # Test suites
│   ├── test_mcp_server.py              # MCP server tests
│   └── test_projects.py                # Projects feature tests
│
├── backups/                        # Backup files (not tracked)
│   ├── android_app_backup_*.tar.gz
│   └── app-debug-backup-*.apk
│
├── docs/                           # Documentation (see DOCUMENTATION.md)
│   └── [See below for doc organization]
│
├── .env.example                    # Template for environment setup
├── .gitignore                      # Git ignore rules
├── LICENSE                         # Project license
├── README.md                       # ✅ START HERE - Quick start guide
├── PROJECT_STRUCTURE.md            # ✅ THIS FILE - Project organization
└── setup.sh                        # Automated setup script
```

## Core Components

### 1. Android App (`android_app/`)

**Purpose**: Mobile app for recording and managing voice notes

**Key Files**:
- `MainActivity.kt` (1100 lines) - Jetpack Compose UI with:
  - Inbox tab: Unprocessed notes awaiting assignment
  - Projects tab: Organized project view with note counts
  - Settings tab: Supabase connection testing
  - Loading states and error handling

- `SupabaseClient.kt` (300 lines) - All database operations:
  - Notes CRUD (create, read, update, delete)
  - Projects CRUD with note counting
  - Inbox filtering (notes without projects)
  - Project assignment workflow

- `OpenAIClient.kt` - Whisper API integration for transcription
- `AudioRecorder.kt` - Android MediaRecorder wrapper
- `Note.kt` - Kotlin data classes for Note and Project

**Build**: `./gradlew installDebug` (requires API keys in `local.properties`)

### 2. MCP Server (`voice_notes_mcp/`)

**Purpose**: Provide Claude Desktop access to voice notes via MCP protocol

**Which Server to Use**:

| File | Use Case | Dependencies |
|------|----------|--------------|
| **server.py** | **Production** - Full Projects v1.1 support | supabase-py, mcp, cachetools |
| mcp_server.py | Lightweight - No heavy Python deps | urllib only |
| simple_server.py | Testing/debugging | urllib only |
| debug_mcp.py | Database connectivity testing | urllib only |

**Main Server** (`server.py` - 821 lines):
- Full MCP protocol implementation
- All 13 tools (notes + projects)
- TTL caching for performance
- Comprehensive error handling

**Tools Available**:
- `list_unprocessed_notes` - Inbox view
- `read_note` - Get note with project info
- `mark_as_processed` - Mark without assigning
- `bulk_mark_processed` - Batch processing
- `search_notes` - Full-text search
- `get_inbox_stats` - Analytics
- `list_projects` - All projects with counts
- `get_project` - Single project details
- `create_project` - New project
- `update_project` - Edit or archive
- `get_notes_by_project` - Project notes
- `assign_note_to_project` - Assign and mark processed

**Setup**: See `README.md` for Claude Desktop configuration

### 3. Database (`migrations/`)

**Schema**:

```sql
notes (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL,
  transcript TEXT NOT NULL,
  project_id UUID NULLABLE,  -- v1.1: Link to projects
  is_processed BOOLEAN,
  transcription_status TEXT,
  audio_duration_seconds INTEGER,
  word_count INTEGER,
  created_at TIMESTAMP,
  modified_at TIMESTAMP
)

projects (  -- v1.1
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL,
  name TEXT NOT NULL,
  purpose TEXT,              -- Why this matters
  goal TEXT,                 -- What success looks like
  is_archived BOOLEAN,
  note_count INTEGER,        -- Auto-updated by triggers
  created_at TIMESTAMP,
  updated_at TIMESTAMP
)
```

**Migration Files**:
1. `001_add_projects.sql` - Creates projects table and relationships
2. `002_fix_projects_rls.sql` - Fixes row-level security policies
3. `003_add_note_count_column.sql` - Adds auto-updating note counts

**Deploy**: `python deploy_migration.py migrations/00X_*.sql`

## Documentation Organization

See separate documentation files for specific topics:

| File | Purpose | Audience |
|------|---------|----------|
| **README.md** | Quick start and installation | New users |
| **PROJECT_STRUCTURE.md** | This file - project organization | Developers |
| **voice-notes-v1.1-spec.md** | Complete v1.1 specification | Developers |
| **CLAUDE_DESKTOP_SETUP.md** | MCP server setup guide | End users |
| **SECURITY.md** | Security best practices | Ops/DevOps |
| voice-notes-architecture.md | Technical architecture | Architects |
| voice-notes-ontology.md | Data model and concepts | Data designers |
| DEPLOYMENT_GUIDE.md | Deployment procedures | DevOps |
| DEPLOYMENT_SUCCESS.md | Successful deployment log | Reference |
| PROJECTS_V1.1_IMPLEMENTATION.md | v1.1 implementation notes | Developers |

## Development Workflow

### Making Changes to Android App

1. **Edit Kotlin files** in `android_app/app/src/main/java/com/voicenotes/mcp/`
2. **Build and install**: `cd android_app && ./gradlew installDebug`
3. **Test on device**: Changes appear immediately on connected Android device

### Making Changes to MCP Server

1. **Edit** `voice_notes_mcp/server.py` (or chosen server file)
2. **Restart Claude Desktop** to reload the MCP server
3. **Test in Claude**: "List my projects" or "Show inbox notes"

### Database Migrations

1. **Create SQL file** in `migrations/` with sequential number
2. **Test locally** in Supabase SQL editor
3. **Deploy**: `python deploy_migration.py migrations/00X_migration_name.sql`
4. **Verify**: `python scripts/check_projects_schema.py`

## Configuration Files

### Environment Variables

**Root `.env`** (for MCP server):
```env
SUPABASE_URL=https://your-project.supabase.co
SUPABASE_SERVICE_ROLE_KEY=eyJ...secret-key
```

**Android `local.properties`** (for Android app):
```properties
SUPABASE_URL=https://your-project.supabase.co
SUPABASE_ANON_KEY=eyJ...public-key
OPENAI_API_KEY=sk-...
```

**Claude Desktop config** (`~/.claude/settings.json` or Claude Desktop config):
```json
{
  "mcpServers": {
    "voice-notes": {
      "command": "python3",
      "args": ["/full/path/to/voice_notes_mcp/server.py"],
      "env": {
        "SUPABASE_URL": "...",
        "SUPABASE_SERVICE_ROLE_KEY": "..."
      }
    }
  }
}
```

## Feature Versions

### v1.0 (Base)
- Android app with recording and transcription
- MCP server with basic note management
- Supabase database for notes storage

### v1.1 (Projects) ✅ Current
- Projects table and relationships
- Project CRUD operations in Android app
- Inbox workflow (unassigned notes)
- Project assignment with inline creation
- MCP tools for project management
- Auto-updating note counts

## Common Tasks

### Add a New MCP Tool

1. Add method to `VoiceNotesMCPServer` class in `server.py`
2. Register tool in `handle_list_tools()` function
3. Add handler in `handle_call_tool()` function
4. Test with Claude Desktop

### Add Android UI Feature

1. Add state variables in `MainActivity.kt`
2. Create `@Composable` function for UI component
3. Add to appropriate screen (Inbox, Projects, Settings)
4. Wire up with `SupabaseClient` methods

### Add Database Field

1. Create migration SQL file in `migrations/`
2. Update Kotlin data class in `Note.kt`
3. Update `SupabaseClient.kt` methods
4. Deploy migration to Supabase

## Testing

### Run MCP Server Tests
```bash
cd tests
python test_mcp_server.py
python test_projects.py
```

### Test Database Connection
```bash
python scripts/test_mcp_connection.py
```

### Validate MCP Setup
```bash
python scripts/validate_mcp.py
```

## Troubleshooting

See `README.md` section "Troubleshooting" for common issues.

Quick diagnostics:
- **MCP not working**: Check `~/.claude/settings.json` paths are absolute
- **Android can't save**: Check `local.properties` has correct Supabase URL
- **Transcription fails**: Verify OpenAI API key in `local.properties`
- **Database errors**: Check RLS policies in Supabase dashboard

## License

See `LICENSE` file for details.
