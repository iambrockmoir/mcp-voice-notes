# Voice Notes App v1.1 - Projects & Processing

## Overview

This version adds **Projects** as a core concept and implements the **Processing Phase** workflow. The goal is to transform the inbox of unprocessed voice notes into organized, project-attached notes through a simple in-app flow.

### What We're Building
1. A **Projects** system to organize notes by context/purpose
2. A **Processing Flow** to move notes from inbox → project
3. A **Project Detail View** to see and add notes within a project

### What We're NOT Building (Yet)
- AI-powered project suggestions
- Task management or task creation from notes
- Multi-project note assignment
- Planning phase features (goal review, task scheduling)
- Motion integration

---

## Core Concepts

### Project
A container for related voice notes with a shared purpose.

**Properties:**
- `id` (UUID)
- `name` (string, required) — short identifier
- `purpose` (text, optional) — why this project matters
- `goal` (text, optional) — what success looks like
- `is_archived` (boolean, default false)
- `created_at` (timestamp)
- `updated_at` (timestamp)

**Lifecycle:** active → archived

### Voice Note (Updated)
Existing voice note entity gains a project relationship.

**New Property:**
- `project_id` (UUID, nullable, foreign key → Project)

**Updated State Machine:**
```
pending → transcribing → ready → processed
                           │
                           └─ A note is "processed" when project_id is set
```

**Inbox Definition:** Notes where `project_id IS NULL` and `is_processed = false`

---

## User Workflows

### Workflow 1: Process a Note from Inbox

**Entry Point:** User taps an unprocessed note in the inbox

**Screen: Note Processing View**

Displays:
- Full transcript (editable)
- Created timestamp
- Duration/word count

Actions available:
1. **Edit Transcript** — modify the text inline
2. **Assign to Project** — opens project picker
3. **Delete Note** — removes note entirely (with confirmation)

**Project Picker Flow:**
- Shows list of active (non-archived) projects
- User taps a project to select it
- Confirm button assigns note to project

**On Assignment:**
- `project_id` set to selected project
- `is_processed` set to true
- Note disappears from inbox
- Note appears in project's note list

### Workflow 2: Create a New Project

**Entry Points:**
- Projects tab → "New Project" button
- (Future: from processing flow, but not v1)

**Screen: Create Project**

Fields:
- Name (required)
- Purpose (optional, multiline)
- Goal (optional, multiline)

**On Save:**
- Project created with `is_archived = false`
- User returned to projects list
- New project appears in list

### Workflow 3: View and Manage Projects

**Entry Point:** Projects tab in main navigation

**Screen: Projects List**

Displays:
- List of active projects (sorted by most recently updated)
- Each row shows: name, note count, last updated
- "New Project" button

Actions:
- Tap project → opens Project Detail View
- Toggle to show archived projects (secondary view or filter)

### Workflow 4: Project Detail View

**Entry Point:** Tap a project from Projects List

**Screen: Project Detail**

Displays:
- Project name (editable)
- Purpose (editable)
- Goal (editable)
- List of attached notes (sorted by created_at desc)

Actions:
1. **Edit Project Info** — inline editing or edit mode
2. **Record New Note** — opens recorder, note auto-attached to this project
3. **Archive Project** — moves to archived state (with confirmation)
4. **Delete Project** — only if no notes attached? Or cascades? (see decision below)
5. **Tap a Note** — view/edit that note's transcript

**Recording from Project Detail:**
- Same recording flow as main capture
- On transcription complete: `project_id` automatically set to current project
- `is_processed` set to true
- Note does NOT appear in inbox
- Note appears in this project's list immediately

### Workflow 5: View/Edit a Note within a Project

**Entry Point:** Tap a note from Project Detail View

**Screen: Note View**

Displays:
- Full transcript (editable)
- Created timestamp
- Duration/word count
- Which project it belongs to

Actions:
1. **Edit Transcript** — save changes
2. **Move to Different Project** — reassign to another project
3. **Delete Note** — removes note (with confirmation)

---

## Navigation Structure

```
┌─────────────────────────────────────────┐
│           Bottom Navigation             │
├──────────────────┬──────────────────────┤
│    Inbox Tab     │    Projects Tab      │
├──────────────────┼──────────────────────┤
│ • Unprocessed    │ • Active projects    │
│   notes list     │   list               │
│ • Record button  │ • New project btn    │
│                  │ • Archive filter     │
└──────────────────┴──────────────────────┘

Inbox Tab                    Projects Tab
    │                             │
    ├─► Note Processing View      ├─► Create Project
    │       │                     │
    │       └─► Project Picker    ├─► Project Detail
    │                             │       │
    │                             │       ├─► Edit Project
    │                             │       ├─► Note View
    │                             │       └─► Record (attached)
    │                             │
    └───────────────────────────────────────┘
              (Shared Note View)
```

---

## Data Model Changes

### New Table: `projects`

```sql
CREATE TABLE projects (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES auth.users(id),
    name TEXT NOT NULL,
    purpose TEXT,
    goal TEXT,
    is_archived BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- Index for listing active projects
CREATE INDEX idx_projects_user_active ON projects(user_id, is_archived, updated_at DESC);
```

### Modified Table: `voice_notes`

```sql
-- Add project reference
ALTER TABLE voice_notes 
ADD COLUMN project_id UUID REFERENCES projects(id) ON DELETE SET NULL;

-- Index for fetching notes by project
CREATE INDEX idx_voice_notes_project ON voice_notes(project_id, created_at DESC);
```

### Updated Inbox Query

```sql
-- Inbox: unprocessed notes not assigned to any project
SELECT * FROM voice_notes 
WHERE user_id = :user_id 
  AND project_id IS NULL 
  AND is_processed = FALSE
ORDER BY created_at DESC;
```

### Supabase RLS Policies

```sql
-- Projects: users can only see their own
CREATE POLICY "Users can view own projects" ON projects
    FOR SELECT USING (auth.uid() = user_id);

CREATE POLICY "Users can insert own projects" ON projects
    FOR INSERT WITH CHECK (auth.uid() = user_id);

CREATE POLICY "Users can update own projects" ON projects
    FOR UPDATE USING (auth.uid() = user_id);

CREATE POLICY "Users can delete own projects" ON projects
    FOR DELETE USING (auth.uid() = user_id);
```

---

## Edge Cases & Decisions

### What happens when a project is deleted?
**Decision:** Soft delete only (archive). If hard delete is needed, notes become orphaned (`project_id` set to NULL via `ON DELETE SET NULL`), returning them to a quasi-inbox state. Consider preventing hard delete if notes exist.

### What happens when a project is archived?
**Decision:** Notes remain attached. Archived projects don't appear in the project picker during processing. User can view archived projects via filter/toggle.

### Can a processed note be "unprocessed"?
**Decision:** Not directly. User can move it to a different project, or delete it. Moving a note updates `project_id` but keeps `is_processed = true`.

### What if user wants to keep a note but not assign to any project?
**Decision for v1:** They must create a project (even a generic "Reference" or "Ideas" project). No special "unassigned but processed" state.

### Recording fails mid-way in project context?
**Behavior:** Same as current—queued for retry. When transcription succeeds, note is attached to the project that was active when recording started.

---

## UI/UX Notes

### Processing Should Be Fast
The main value is quick triage. Minimize taps:
- One tap to open note
- One tap to open project picker  
- One tap to select project
- Done (auto-saves and closes)

### Project Picker Design
- Simple scrollable list
- Most recently used projects at top (optional enhancement)
- Search/filter if project count grows (future)

### Visual Distinction
- Inbox notes: no project badge
- Project notes: show project name badge
- Archived projects: greyed out or separate section

### Empty States
- Empty inbox: "All caught up! No notes to process."
- Empty project: "No notes yet. Record one or assign from inbox."
- No projects: "Create your first project to start organizing."

---

## MCP Server Updates

The MCP server should be updated to support project-aware queries.

### New Tools

**`list_projects`**
- Returns all active projects (optionally include archived)
- Fields: id, name, purpose, goal, note_count, is_archived

**`get_project`**
- Returns single project with its notes
- Input: project_id
- Returns: project details + list of attached notes

**`get_notes_by_project`**
- Returns notes for a specific project
- Input: project_id, limit, offset

### Updated Tools

**`list_notes`**
- Add optional filter: `project_id`
- Add optional filter: `unassigned_only` (inbox view)

**`get_note`**
- Include `project_id` and `project_name` in response

---

## Implementation Order

Suggested sequence for the coding agent:

1. **Database Migration**
   - Create `projects` table
   - Add `project_id` column to `voice_notes`
   - Add indexes and RLS policies

2. **Data Layer (Android)**
   - Project entity and DAO
   - Update VoiceNote entity with project_id
   - Repository methods for project CRUD

3. **Projects List Screen**
   - Basic list view
   - Create project flow
   - Navigation setup (bottom nav with tabs)

4. **Project Detail Screen**
   - View project info
   - List notes in project
   - Edit project info
   - Archive project

5. **Processing Flow**
   - Note processing view
   - Project picker component
   - Assignment logic

6. **Project-Attached Recording**
   - Record from project detail
   - Auto-attach on transcription complete

7. **MCP Server Updates**
   - New project-related tools
   - Updated note queries

---

## Success Criteria

This version is complete when:

1. ✓ User can create, view, edit, and archive projects
2. ✓ User can tap an inbox note and assign it to a project
3. ✓ Assigned notes disappear from inbox and appear in project
4. ✓ User can view all notes within a project
5. ✓ User can record a new note directly into a project
6. ✓ Projects and assignments sync to Supabase
7. ✓ MCP server can query notes by project

---

## Open Questions (Resolved)

| Question | Decision |
|----------|----------|
| AI-powered suggestions? | No, not in v1 |
| Multiple projects per note? | No, 1:1 relationship |
| Miscellaneous bucket? | No special handling, user creates projects as needed |
| Notes become tasks? | Out of scope, not modeled |
| Project-first recording skips inbox? | Yes |
