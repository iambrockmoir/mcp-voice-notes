# 🚀 Projects v1.1 Deployment Guide

## Quick Overview

This guide will help you safely deploy the Projects feature to your Voice Notes system **without deleting any existing data**.

**Time Required:** ~10 minutes
**Risk Level:** ✅ Safe (only adds new tables/columns, doesn't modify existing data)

---

## Step 1: Run Database Migration (5 minutes)

### Using Supabase Web Interface (Recommended)

1. **Open your Supabase Dashboard:**
   - Go to: https://supabase.com/dashboard
   - Select your Voice Notes project

2. **Navigate to SQL Editor:**
   - Click "SQL Editor" in the left sidebar
   - Click "New Query" button

3. **Copy and Paste Migration SQL:**
   - Open this file: `migrations/001_add_projects.sql`
   - Copy the entire contents (5,865 bytes)
   - Paste into the SQL editor

4. **Run the Migration:**
   - Click the "Run" button (or press Cmd/Ctrl + Enter)
   - You should see: **"Success. No rows returned"**

5. **Verify the Migration:**
   - In the SQL editor, run this query:
   ```sql
   SELECT tablename FROM pg_tables
   WHERE schemaname = 'public'
   AND tablename = 'projects';
   ```
   - You should see one row with `projects` table

✅ **Database migration complete!**

### What Was Added (Safe Changes Only):

- ✅ New `projects` table (doesn't touch existing data)
- ✅ New `project_id` column in `notes` table (nullable, defaults to NULL)
- ✅ Indexes for performance
- ✅ RLS security policies
- ✅ Database functions and triggers

**Your existing notes are completely safe!** All existing notes will have `project_id = NULL` and continue working exactly as before.

---

## Step 2: Update MCP Server Code (2 minutes)

The code is already updated in your feature branch! You just need to make it active:

### Option A: Merge to Main (Recommended for production)

```bash
cd "/Users/moirb/Projects/Agentic/Swarm Testing/mcp_thought_recorder"

# Switch to main branch
git checkout main

# Merge the feature branch
git merge feature/projects-v1.1

# Confirm the merge
git log --oneline -1
```

### Option B: Stay on Feature Branch (For testing)

```bash
cd "/Users/moirb/Projects/Agentic/Swarm Testing/mcp_thought_recorder"

# Already on feature/projects-v1.1 branch
git branch --show-current
```

✅ **Code is ready!**

---

## Step 3: Restart MCP Server (1 minute)

### If using Claude Desktop:

1. **Quit Claude Desktop completely:**
   - On Mac: Cmd+Q or Claude Desktop → Quit Claude Desktop
   - On Windows: File → Exit

2. **Reopen Claude Desktop:**
   - The MCP server will automatically restart with new code

### If running MCP server manually:

```bash
# Stop the current server (Ctrl+C)
# Then restart it:
cd "/Users/moirb/Projects/Agentic/Swarm Testing/mcp_thought_recorder"
python3 voice_notes_mcp/server.py
```

✅ **Server is running with new features!**

---

## Step 4: Verify Deployment (2 minutes)

### Test that new tools are available:

Open Claude Desktop and try these commands:

1. **List projects:**
   ```
   Can you list all my voice note projects?
   ```

2. **Create a test project:**
   ```
   Create a new project called "Test Project" with purpose "Testing the new feature"
   ```

3. **Verify existing notes still work:**
   ```
   Show me my unprocessed notes
   ```

4. **Test assigning a note (if you have notes):**
   ```
   Assign note [note-id] to the Test Project
   ```

### Expected Results:

- ✅ All 6 new project tools should be available
- ✅ Existing notes should still appear in inbox
- ✅ You can create and manage projects
- ✅ You can assign notes to projects
- ✅ No data loss or errors

---

## Rollback Plan (Just in Case)

If you encounter any issues, you can easily roll back:

### 1. Revert Code Changes:

```bash
cd "/Users/moirb/Projects/Agentic/Swarm Testing/mcp_thought_recorder"
git checkout main  # If you merged
# or
git checkout 36a093a  # The commit before feature implementation
```

### 2. Keep Database Changes:

**Good news:** The database changes are completely safe to keep! They don't affect existing functionality. The `project_id` column is nullable, so all existing notes continue to work.

**But if you really want to remove them:**

```sql
-- Remove project_id column (your notes will be fine)
ALTER TABLE notes DROP COLUMN IF EXISTS project_id;

-- Drop projects table
DROP TABLE IF EXISTS projects CASCADE;
```

---

## Troubleshooting

### Issue: "Table already exists" error during migration

**Solution:** The migration has already been run. This is safe! Skip to Step 2.

### Issue: MCP server won't start after update

**Solution:**
1. Check for Python errors: `python3 voice_notes_mcp/server.py`
2. Verify environment variables are set in Claude Desktop config
3. Try: `pip install -r voice_notes_mcp/requirements.txt`

### Issue: New tools don't appear in Claude Desktop

**Solution:**
1. Completely quit and restart Claude Desktop (not just close window)
2. Check Claude Desktop settings → Developer → MCP Servers
3. Verify server is running: Look for green indicator

### Issue: Can't connect to Supabase

**Solution:**
1. Verify your Supabase credentials in Claude Desktop config
2. Test connection: `python3 test_mcp_connection.py`
3. Check Supabase project is active and not paused

---

## Post-Deployment Checklist

After successful deployment, verify:

- [ ] Migration ran successfully in Supabase
- [ ] No errors in SQL Editor
- [ ] `projects` table exists
- [ ] MCP server restarted
- [ ] Claude Desktop can see new tools
- [ ] Can create a test project
- [ ] Can list projects
- [ ] Can assign notes to projects
- [ ] Existing notes still work
- [ ] Inbox filtering works correctly

---

## What's New for Users

After deployment, you can:

1. **Create Projects** to organize your notes by context (Work, Personal, Ideas, etc.)
2. **Assign notes from inbox** to relevant projects
3. **View all notes** within a project
4. **Archive projects** when they're complete
5. **Better organization** - inbox only shows unprocessed notes without projects

All existing notes remain in the inbox until you assign them to a project!

---

## Support

If you encounter any issues:

1. Check the `PROJECTS_V1.1_IMPLEMENTATION.md` file for technical details
2. Review logs: Claude Desktop → Settings → Developer → View Logs
3. Test the server directly: `python3 voice_notes_mcp/server.py`
4. Run tests: `pytest tests/test_projects.py -v`

---

## Summary

✅ **Safe deployment:** No data deletion
✅ **Backward compatible:** Existing features work unchanged
✅ **Easy rollback:** Can revert if needed
✅ **Well tested:** 20+ test cases covering all scenarios

**Total time:** ~10 minutes
**Difficulty:** Easy (mostly copy-paste)
**Risk:** Very Low (only adds new features)

---

Happy organizing! 🎉
