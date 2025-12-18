# Voice Notes MCP - System Ontology Card

## System Identity
**Purpose**: Capture fleeting thoughts and transform them into actionable items through AI-assisted review
**Philosophy**: Frictionless capture, intelligent processing, seamless integration

## Core Entities & Relationships

### Primary Entities
```
┌─────────────────────────────────────────────────────────┐
│ VOICE_NOTE                                              │
│ ├─ Properties:                                          │
│ │  • id (UUID)                                          │
│ │  • transcript (text)                                  │
│ │  • duration_seconds (int)                             │
│ │  • created_at (timestamp)                             │
│ │  • is_processed (boolean)                             │
│ │  • word_count (int)                                   │
│ └─ States: pending → transcribing → ready → processed   │
└─────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────┐
│ AUDIO_RECORDING                                         │
│ ├─ Properties:                                          │
│ │  • temp_file_path                                     │
│ │  • duration_ms                                        │
│ │  • format (m4a)                                       │
│ └─ Lifecycle: created → queued → transcribed → deleted  │
└─────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────┐
│ USER_SESSION                                            │
│ ├─ Recording Session (Android)                          │
│ └─ Review Session (Claude Desktop)                      │
└─────────────────────────────────────────────────────────┘
```

### Entity Relationships
- `AUDIO_RECORDING` --transcribes-to--> `VOICE_NOTE`
- `VOICE_NOTE` --belongs-to--> `USER`
- `VOICE_NOTE` --reviewed-during--> `USER_SESSION`
- `USER_SESSION` --processes--> multiple `VOICE_NOTE`

## System Capabilities

### Input Capabilities
- **Audio Capture**: Push-to-record voice input (max 10 min)
- **Manual Edit**: Direct transcript modification
- **Queue Management**: Offline-capable recording queue

### Processing Capabilities  
- **Transcription**: OpenAI Whisper API conversion
- **Synchronization**: Real-time Supabase cloud sync
- **Search**: Full-text search across all notes
- **Filtering**: By processed status, date, keywords

### Output Capabilities
- **List Operations**: Paginated note retrieval
- **Read Operations**: Individual note access
- **Update Operations**: Mark as processed, edit transcript
- **Statistics**: Inbox metrics and summaries

## Data Flow Architecture

```
CAPTURE FLOW:
User Voice → Android MediaRecorder → Audio File → 
OpenAI Whisper → Transcript → Supabase → Available to MCP

REVIEW FLOW:
Claude Query → MCP Server → Supabase Query → 
Note Data → Formatted Response → Claude Analysis

SYNC FLOW:
Local Queue → Network Check → Batch Upload → 
Supabase → Real-time Subscription → MCP Cache Invalidation
```

## Integration Points

### External Services
```yaml
OpenAI:
  - Service: Whisper API
  - Purpose: Speech-to-text transcription
  - Protocol: REST API
  - Constraint: Internet required

Supabase:
  - Service: PostgreSQL Database
  - Purpose: Cloud storage & sync
  - Protocol: REST + WebSocket
  - Features: RLS, Real-time, Auth

Claude Desktop:
  - Service: MCP Client
  - Purpose: AI-powered review interface
  - Protocol: JSON-RPC 2.0
  - Tools: 6 exposed functions
```

### System Boundaries
- **Android App**: Audio capture, basic CRUD, display
- **MCP Server**: Read-only queries, processing flags
- **Claude**: Analysis, categorization, decision support
- **Supabase**: Source of truth, persistence, sync

## User Workflows

### Primary Workflow: Capture → Process
```
1. CAPTURE (Android)
   → Open app
   → Press record
   → Speak thought
   → Stop recording
   → [Auto-transcribe]
   → Edit if needed

2. REVIEW (Claude Desktop)
   → "Show unprocessed notes"
   → Read/analyze content
   → Extract todos/insights
   → "Mark as processed"

3. OUTCOMES
   → Todos added to task system
   → Ideas documented
   → Thoughts organized
```

### Error Recovery Flows
- **Offline Recording**: Queue → Retry → Sync when online
- **Failed Transcription**: Retry 3x → Mark failed → Manual option
- **Sync Conflicts**: Last-write-wins → No merge logic

## Technical Constraints & Decisions

### Design Constraints
- **No Audio Storage**: Deleted after transcription (privacy)
- **10-minute Maximum**: Recording length limit
- **English Only**: Transcription language
- **Single User**: No multi-user in v1
- **Read-Heavy**: Optimized for review over creation

### Architecture Decisions
- **Cloud-First**: Supabase over local (seamless sync)
- **Queue-Based**: Async processing for reliability
- **Stateless MCP**: No local persistence in server
- **Simple UI**: Function over form

## Extension Surface (Upgrade Points)

### Data Model Extensions
```python
Potential_Fields = {
    "category": "enum(todo, idea, reference, reminder)",
    "priority": "enum(high, medium, low)",
    "tags": "text[]",
    "related_notes": "uuid[]",
    "location": "point",
    "context": "jsonb"
}
```

### Feature Extension Points
1. **Input Methods**
   - Voice activation trigger
   - Widget recording
   - Share intent receiver
   - Wear OS companion

2. **Processing Pipeline**
   - Auto-categorization
   - Entity extraction
   - Sentiment analysis
   - Summary generation

3. **Integration Expansion**
   - Task manager export
   - Calendar event creation
   - Note-taking app sync
   - Team sharing

4. **MCP Tool Additions**
   - Bulk operations
   - Complex queries
   - Analytics/trends
   - Export functions

### State Machine Extensions
```
Current: pending → transcribing → ready → processed
Future:  pending → transcribing → categorizing → 
         enriching → ready → processed → archived
```

## System Metrics

### Performance Boundaries
- **Capacity**: ~10,000 notes without degradation
- **Latency**: <500ms MCP response time
- **Throughput**: 50 notes/week expected usage
- **Storage**: ~1KB per note average

### Quality Metrics
- **Transcription Accuracy**: ~95% (OpenAI Whisper)
- **Sync Reliability**: Eventual consistency
- **Availability**: Offline-capable capture

## Conceptual Model

```
The system is a PIPELINE that transforms THOUGHTS into 
ACTIONABLE INFORMATION through three stages:

1. CAPTURE: Frictionless thought recording
2. PERSIST: Reliable cloud synchronization  
3. PROCESS: AI-assisted review and organization

Key Principle: The system should feel like an extension
of memory - always available for capture, intelligent
in processing, and seamless in integration.
```

## Version Evolution Path

```
v1.0 (Current): Basic capture → transcribe → review
v1.x (Planned): + categorization, widgets, voice commands
v2.x (Future):  + multi-language, team sharing, analytics
v3.x (Vision):  + predictive capture, auto-scheduling, integrations
```

## Development Leverage Points

**High-Impact Additions** (Easy wins):
- Quick action widgets
- Batch processing tools
- Export capabilities
- Basic categorization

**Transformation Features** (Major upgrades):
- Real-time collaboration
- Multi-modal input (image + voice)
- Intelligent routing to tools/people
- Predictive assistance

**System Re-architecture** (If needed):
- Edge transcription (on-device)
- P2P sync (no cloud)
- Multi-tenant isolation
- Event-sourced architecture