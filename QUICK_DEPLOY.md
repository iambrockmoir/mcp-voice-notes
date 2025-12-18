# 🚀 Quick Deploy - Projects v1.1

## Your Custom Deployment Instructions

I've detected your Supabase project. Follow these steps to deploy in **5 minutes**:

---

## Step 1: Run the Database Migration (3 minutes)

### Open Your Supabase Dashboard

1. **Click this link to open your project:**
   ```
   https://supabase.com/dashboard/project/gpgmkujrfykeeyzjcxmy
   ```

2. **Navigate to SQL Editor:**
   - Look for "SQL Editor" in the left sidebar
   - Click it, then click "New Query"

3. **Copy the Migration SQL:**
   - Open this file: `/Users/moirb/Projects/Agentic/Swarm Testing/mcp_thought_recorder/migrations/001_add_projects.sql`
   - Select all (Cmd+A) and copy (Cmd+C)

4. **Paste and Run:**
   - Paste into the SQL editor
   - Click "Run" button (or press Cmd+Enter)
   - Wait for completion (~2 seconds)

5. **Verify Success:**
   You should see: **"Success. No rows returned"**

   Run this verification query:
   ```sql
   SELECT tablename FROM pg_tables
   WHERE schemaname = 'public'
   AND tablename = 'projects';
   ```

   Should return: `projects`

✅ **Database is now updated!**

---

## Step 2: Activate the New Code (1 minute)

The code is ready on your feature branch. Choose one option:

### Option A: Use Feature Branch (Testing)

Already done! You're on `feature/projects-v1.1`

### Option B: Merge to Main (Production)

```bash
cd "/Users/moirb/Projects/Agentic/Swarm Testing/mcp_thought_recorder"
git checkout main
git merge feature/projects-v1.1
```

---

## Step 3: Restart MCP Server (1 minute)

### If using Claude Desktop:

1. **Quit Claude Desktop completely**
   - Cmd+Q (or Claude Desktop → Quit)

2. **Reopen Claude Desktop**
   - MCP server will auto-restart with new code

### If running server manually:

```bash
cd "/Users/moirb/Projects/Agentic/Swarm Testing/mcp_thought_recorder"
python3 voice_notes_mcp/server.py
```

---

## Step 4: Test It Works

Open Claude Desktop and try:

```
Hey! Can you list all my voice note projects?
```

You should get a response showing the new `list_projects` tool is available!

Then try:

```
Create a new project called "Test Project" with purpose "Testing the new projects feature"
```

---

## What You Can Do Now

After deployment, you have these new capabilities:

1. **Create projects** to organize notes by topic
2. **Assign inbox notes** to projects
3. **View all notes** within a project
4. **Archive old projects** when done
5. **Better inbox** - only shows unassigned notes

---

## Troubleshooting

### "Table already exists" error in Step 1
✅ Migration already ran - skip to Step 2!

### New tools don't appear in Claude Desktop
1. Make sure you completely quit (not just closed the window)
2. Check: Claude Desktop → Settings → Developer → MCP Servers
3. Restart your computer if needed

### Can't find SQL Editor
- Make sure you're logged into Supabase
- Try direct link: https://supabase.com/dashboard/project/gpgmkujrfykeeyzjcxmy/sql

---

## Summary

✅ **Database:** Run SQL in Supabase Dashboard
✅ **Code:** Already updated in feature branch
✅ **Server:** Just restart Claude Desktop
✅ **Test:** Try listing projects

**Total time:** ~5 minutes
**Risk:** None (only adds new features, doesn't touch existing data)

---

## Need Help?

- See full guide: `DEPLOYMENT_GUIDE.md`
- See implementation details: `PROJECTS_V1.1_IMPLEMENTATION.md`
- Test the migration first in SQL Editor before running

---

Ready to deploy? Start with Step 1 above! 🚀
