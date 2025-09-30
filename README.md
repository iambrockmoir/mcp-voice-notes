# Voice Notes MCP - AI-Powered Voice Recording System

A complete voice notes ecosystem that lets you record voice memos on Android, automatically transcribe them with AI, and access them through Claude Desktop for intelligent analysis and processing.

## 🌟 Features

### Android App
- 🎙️ High-quality voice recording
- 🤖 Automatic AI transcription (OpenAI Whisper)
- ✏️ Edit and manage transcriptions
- 🗑️ Delete unwanted notes
- ☁️ Cloud sync with Supabase
- 🎨 Clean black & white UI

### MCP Server (Claude Desktop Integration)
- 📋 List unprocessed voice notes
- 📖 Read specific notes
- ✅ Mark notes as processed
- 🔍 Search notes by keywords
- 📊 Get inbox statistics
- 🤖 AI analysis through Claude

## 🏗️ Architecture

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Android App   │───▶│   Supabase DB   │◀───│  MCP Server     │
│                 │    │                 │    │                 │
│ • Record Audio  │    │ • Store Notes   │    │ • Claude Tools  │
│ • Transcribe    │    │ • User Auth     │    │ • Note Access   │
│ • Edit Notes    │    │ • REST API      │    │ • AI Analysis   │
└─────────────────┘    └─────────────────┘    └─────────────────┘
         │                                               │
         │              ┌─────────────────┐              │
         └─────────────▶│   OpenAI API    │              │
                        │                 │              │
                        │ • Whisper STT   │              │
                        │ • Transcription │              │
                        └─────────────────┘              │
                                                         │
                        ┌─────────────────┐              │
                        │ Claude Desktop  │◀─────────────┘
                        │                 │
                        │ • AI Analysis   │
                        │ • Note Processing│
                        │ • Smart Queries │
                        └─────────────────┘
```

## 🚀 Quick Start

### Prerequisites
- Android device for voice recording
- Supabase account
- OpenAI API account
- Claude Desktop (for MCP integration)
- Python 3.8+ (for MCP server)

### 1. Clone Repository
```bash
git clone <your-repo-url>
cd mcp_thought_recorder
```

### 2. Set Up Environment
```bash
# Copy environment template
cp .env.example .env

# Edit .env with your API keys (see setup sections below)
nano .env
```

### 3. Configure Services

#### Supabase Setup
1. Create a new project at [supabase.com](https://supabase.com)
2. Go to Settings → API and copy:
   - Project URL
   - `anon public` key
   - `service_role` key (⚠️ Keep secret!)
3. Run this SQL in the Supabase SQL editor:

```sql
-- Create notes table
CREATE TABLE notes (
  id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
  user_id UUID NOT NULL DEFAULT '00000000-0000-0000-0000-000000000001',
  transcript TEXT NOT NULL,
  transcription_status TEXT DEFAULT 'completed',
  audio_duration_seconds INTEGER DEFAULT 0,
  word_count INTEGER DEFAULT 0,
  is_processed BOOLEAN DEFAULT false,
  created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
  modified_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Create indexes for performance
CREATE INDEX idx_notes_user_id ON notes(user_id);
CREATE INDEX idx_notes_created_at ON notes(created_at DESC);
CREATE INDEX idx_notes_is_processed ON notes(is_processed);

-- Enable Row Level Security (optional, for production)
-- ALTER TABLE notes ENABLE ROW LEVEL SECURITY;
```

#### OpenAI Setup
1. Get API key from [platform.openai.com/api-keys](https://platform.openai.com/api-keys)
2. Add to `.env` file

### 4. Install Dependencies

#### MCP Server
```bash
cd voice_notes_mcp
pip install -r requirements.txt
```

#### Android App
```bash
cd android_app

# Copy environment template
cp local.properties.example local.properties

# Edit with your API keys
nano local.properties
```

### 5. Build Android App
```bash
# Connect your Android device
adb devices

# Build and install
./gradlew installDebug

# Or build APK
./gradlew assembleDebug
# APK will be in app/build/outputs/apk/debug/
```

### 6. Configure Claude Desktop

#### For Claude Desktop:
```bash
# Copy template and edit paths
cp claude_desktop_config.example.json ~/Library/Application\ Support/Claude/claude_desktop_config.json

# Edit the file and update:
# 1. Replace "/path/to/your/mcp_thought_recorder" with actual path
# 2. Add your Supabase URL and service_role key
```

#### For Claude Code:
```bash
# Copy template and edit paths  
cp claude_settings.example.json ~/.claude/settings.json

# Edit the file and update:
# 1. Replace "/path/to/your/mcp_thought_recorder" with actual path
# 2. Add your Supabase URL and service_role key
```

### 7. Test the System

#### Test MCP Server
```bash
cd voice_notes_mcp
python3 mcp_server.py
# Should start without errors
```

#### Test Android App
1. Open the Voice Notes app
2. Record a test note
3. Verify it appears in Supabase dashboard

#### Test Claude Integration
1. Restart Claude Desktop
2. Try: "List my unprocessed voice notes"
3. Try: "What did I record this week?"

## 📱 Using the Android App

1. **Record**: Tap the microphone button to start recording
2. **Stop**: Tap again to stop and auto-transcribe
3. **Edit**: Tap any note to edit the transcription
4. **Delete**: Swipe or use delete button
5. **Sync**: Notes automatically sync to cloud

## 🤖 Using with Claude

Once set up, you can ask Claude:
- "What voice notes do I have from this week?"
- "Search my notes for mentions of 'meeting'"
- "Mark note ABC-123 as processed"
- "How many unprocessed notes do I have?"
- "Read me the note about project planning"

## 🔧 Configuration

### Environment Variables (.env)
```env
# Required
SUPABASE_URL=https://your-project.supabase.co
SUPABASE_SERVICE_ROLE_KEY=your-secret-key
OPENAI_API_KEY=sk-your-openai-key

# Optional (for Android development)
SUPABASE_ANON_KEY=your-public-key
```

### Android Configuration (android_app/local.properties)
```properties
SUPABASE_URL=https://your-project.supabase.co
SUPABASE_ANON_KEY=your-public-key
OPENAI_API_KEY=sk-your-openai-key
```

## 🛠️ Development

### Project Structure
```
mcp_thought_recorder/
├── android_app/                 # Android voice recording app
│   ├── app/src/main/java/com/voicenotes/mcp/
│   │   ├── MainActivity.kt      # Main app interface
│   │   ├── SupabaseClient.kt   # Database operations  
│   │   └── OpenAIClient.kt     # Transcription service
│   └── local.properties        # Android API keys
├── voice_notes_mcp/            # MCP server for Claude
│   ├── mcp_server.py          # Main MCP implementation
│   └── requirements.txt       # Python dependencies
├── .env                       # Environment variables
├── .env.example              # Template for setup
└── README.md                 # This file
```

### Building from Source

#### Android App
```bash
cd android_app
./gradlew build
```

#### MCP Server
```bash
cd voice_notes_mcp  
python3 -m pip install -r requirements.txt
python3 mcp_server.py
```

## 🔒 Security Notes

- ⚠️ **Never commit API keys to git**
- 🔑 Use `service_role` key only for MCP server (server-side)
- 🔓 Use `anon` key for Android app (client-side)
- 🛡️ Enable RLS policies for production use
- 📱 Keep `local.properties` gitignored

## 🐛 Troubleshooting

### MCP Server Issues
```bash
# Test connection
cd voice_notes_mcp
python3 -c "
import os
from dotenv import load_dotenv
load_dotenv('../.env')
print('URL:', os.getenv('SUPABASE_URL'))
print('Key ends with:', os.getenv('SUPABASE_SERVICE_ROLE_KEY')[-10:] if os.getenv('SUPABASE_SERVICE_ROLE_KEY') else 'None')
"
```

### Android App Issues
- Check `local.properties` has correct API keys
- Verify Supabase URL is correct
- Test internet connection
- Check Android permissions for microphone

### Claude Integration Issues
- Restart Claude Desktop after config changes
- Check file paths in configuration
- Verify MCP server starts without errors
- Check Claude Desktop logs

## 🤝 Contributing

1. Fork the repository
2. Create feature branch: `git checkout -b feature-name`
3. Commit changes: `git commit -am 'Add feature'`
4. Push to branch: `git push origin feature-name`
5. Submit pull request

## 📄 License

[Add your preferred license]

## 🙋 Support

Having issues? Please check:
1. All API keys are correctly set
2. Supabase database is set up
3. MCP server starts without errors
4. Claude Desktop configuration is correct

For more help, create an issue in this repository.