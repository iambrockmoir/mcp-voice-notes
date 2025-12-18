# Voice Notes MCP Server

MCP (Model Context Protocol) server implementation for Claude Desktop integration with Voice Notes.

## Which Server File Should I Use?

| File | When to Use | Dependencies | Features |
|------|-------------|--------------|----------|
| **server.py** | **Production** | supabase-py, mcp, cachetools | ✅ Full Projects v1.1<br>✅ All 13 tools<br>✅ TTL caching<br>✅ Best performance |
| mcp_server.py | Lightweight setup | urllib only (Python stdlib) | ✅ Basic MCP protocol<br>✅ No pip dependencies<br>⚠️ No caching |
| simple_server.py | Testing/debugging | urllib only | ✅ Minimal implementation<br>⚠️ Basic tools only |
| debug_mcp.py | Connectivity testing | urllib only | ✅ Quick connection test<br>⚠️ Not an MCP server |

### Recommendation

**Use `server.py`** for normal operation - it's the most feature-complete and maintained version.

Only use `mcp_server.py` if you cannot install Python dependencies for some reason.

## Installation

### 1. Install Dependencies

```bash
cd voice_notes_mcp
pip install -r requirements.txt
```

### 2. Configure Environment

The server reads environment variables from `../.env` (project root) or from Claude Desktop config.

**Option A: Use project .env file**
```bash
# In project root
cp .env.example .env
nano .env
```

Add:
```env
SUPABASE_URL=https://your-project.supabase.co
SUPABASE_SERVICE_ROLE_KEY=eyJ...your-secret-key
```

**Option B: Configure in Claude Desktop directly**
```json
{
  "mcpServers": {
    "voice-notes": {
      "command": "python3",
      "args": ["/full/path/to/voice_notes_mcp/server.py"],
      "env": {
        "SUPABASE_URL": "https://your-project.supabase.co",
        "SUPABASE_SERVICE_ROLE_KEY": "eyJ...your-secret-key"
      }
    }
  }
}
```

### 3. Test the Server

```bash
# Test Supabase connection
python debug_mcp.py

# Test MCP server (will wait for stdin)
python server.py
# Press Ctrl+C to exit
```

### 4. Configure Claude Desktop

**For Claude Desktop:**
Edit `~/Library/Application Support/Claude/claude_desktop_config.json`

**For Claude Code:**
Edit `~/.claude/settings.json`

Add the configuration from Option B above, with full absolute paths.

**Important**: Use absolute paths, not relative paths!

### 5. Restart Claude

After configuration, restart Claude Desktop completely for changes to take effect.

## Available Tools

When `server.py` is running, Claude can use these tools:

### Notes Management
- `list_unprocessed_notes` - Get inbox (notes without projects)
- `read_note` - Read full note with project info
- `mark_as_processed` - Mark note as reviewed
- `bulk_mark_processed` - Mark multiple notes at once
- `search_notes` - Full-text search in transcripts
- `get_inbox_stats` - Get counts and statistics

### Projects Management (v1.1)
- `list_projects` - List all projects with note counts
- `get_project` - Get single project details
- `create_project` - Create new project
- `update_project` - Edit or archive project
- `get_notes_by_project` - Get all notes in a project
- `assign_note_to_project` - Move note from inbox to project

## Usage Examples

Once configured, ask Claude:

```
"Show me my inbox notes"
"List all my projects"
"Search my notes for 'meeting'"
"Create a project called 'Research' with purpose 'Gather information'"
"Assign note abc-123 to project xyz-789"
```

## Troubleshooting

### Server won't start
- Check Python version: `python3 --version` (need 3.8+)
- Verify dependencies: `pip list | grep -E 'supabase|mcp|cachetools'`
- Check environment variables are set

### Claude can't find tools
- Verify absolute paths in Claude config
- Check Claude Desktop logs in Console.app (macOS)
- Restart Claude Desktop completely
- Try running `python server.py` manually to see errors

### Database errors
- Verify SUPABASE_SERVICE_ROLE_KEY (not anon key!)
- Check Supabase project URL is correct
- Test connection: `python debug_mcp.py`
- Check RLS policies in Supabase dashboard

### Permission errors
- Use service_role key, not anon key for MCP server
- Android app uses anon key (client-side)
- MCP server needs service_role (server-side, bypasses RLS)

## Development

### Adding a New Tool

1. Add method to `VoiceNotesMCPServer` class:
```python
async def my_new_tool(self, param: str) -> Dict[str, Any]:
    """Your tool logic"""
    return {"result": "success"}
```

2. Register in `handle_list_tools()`:
```python
Tool(
    name="my_new_tool",
    description="What this tool does",
    inputSchema={...}
)
```

3. Add handler in `handle_call_tool()`:
```python
elif name == "my_new_tool":
    result = await voice_notes_server.my_new_tool(arguments["param"])
```

### Running Tests

```bash
cd ../tests
python test_mcp_server.py
python test_projects.py
```

## Files Explained

### server.py (821 lines) - MAIN SERVER ✅
Full-featured MCP server with:
- Complete MCP protocol implementation
- Supabase Python library for database
- TTL caching for performance
- All 13 tools (notes + projects)
- Comprehensive error handling

Use this for production.

### mcp_server.py (505 lines) - Lightweight Alternative
Minimal dependency version using only Python stdlib:
- urllib for HTTP requests (no supabase-py)
- Basic MCP protocol
- Fewer features than server.py
- Good for environments where pip install is problematic

### simple_server.py (234 lines) - Debug Version
Bare minimum implementation:
- Only core note operations
- No projects support
- For testing and learning

### debug_mcp.py (49 lines) - Connection Tester
Not an MCP server, just tests if Supabase is reachable:
```bash
python debug_mcp.py
```
Shows if your credentials work.

## Security Notes

- ⚠️ **Never commit .env file to git**
- 🔑 Use `service_role` key for MCP server (server-side)
- 🔓 Use `anon` key for Android app (client-side)
- 📝 The service_role key bypasses RLS - only use server-side!

## Architecture

```
Claude Desktop
      │
      ├─ MCP Protocol (stdio)
      │
   server.py
      │
      ├─ HTTPS REST API
      │
  Supabase PostgreSQL
      │
      ├─ notes table
      ├─ projects table
      └─ RLS policies
```

## Support

Having issues?

1. Check this README's troubleshooting section
2. Verify configuration with `debug_mcp.py`
3. Check Claude Desktop logs in Console.app
4. See main project README for more help

## License

See LICENSE file in project root.
