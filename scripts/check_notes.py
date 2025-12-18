#!/usr/bin/env python3
"""
Quick script to check what notes are in the database
"""
import os
import sys
from pathlib import Path

# Load credentials from android_app/local.properties
def load_properties(filepath):
    properties = {}
    if not filepath.exists():
        return properties

    with open(filepath, 'r') as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith('#'):
                continue
            if '=' in line:
                key, value = line.split('=', 1)
                properties[key.strip()] = value.strip()
    return properties

local_props = Path(__file__).parent / "android_app" / "local.properties"
props = load_properties(local_props)

SUPABASE_URL = props.get('SUPABASE_URL')
SUPABASE_KEY = props.get('SUPABASE_ANON_KEY')

if not SUPABASE_URL or not SUPABASE_KEY:
    print("❌ Could not load Supabase credentials")
    sys.exit(1)

try:
    from supabase import create_client
    supabase = create_client(SUPABASE_URL, SUPABASE_KEY)

    print("=" * 80)
    print("CHECKING NOTES DATABASE")
    print("=" * 80)
    print()

    # Check all notes
    print("📊 ALL NOTES:")
    all_notes = supabase.table("notes").select("id, transcript, project_id, is_processed, created_at").limit(50).execute()
    if all_notes.data:
        for note in all_notes.data:
            print(f"\nID: {note['id'][:8]}...")
            print(f"  Text: {note['transcript'][:50]}...")
            print(f"  Project ID: {note.get('project_id', 'NULL')}")
            print(f"  Processed: {note.get('is_processed', False)}")
            print(f"  Created: {note.get('created_at', 'N/A')[:10]}")
    else:
        print("  ❌ No notes found!")

    print("\n" + "=" * 80)
    print("📥 INBOX NOTES (project_id IS NULL):")
    inbox_query = supabase.table("notes").select("*").is_("project_id", "null").limit(20).execute()
    if inbox_query.data:
        print(f"  ✅ Found {len(inbox_query.data)} inbox notes:")
        for note in inbox_query.data:
            print(f"    - {note['transcript'][:50]}...")
    else:
        print("  ❌ No inbox notes found (all have project_id)")

    print("\n" + "=" * 80)
    print("📁 PROJECTS:")
    projects = supabase.table("projects").select("*").limit(10).execute()
    if projects.data:
        print(f"  ✅ Found {len(projects.data)} projects:")
        for proj in projects.data:
            print(f"    - {proj['name']} (ID: {proj['id'][:8]}...)")
    else:
        print("  ℹ️  No projects yet")

    print("\n" + "=" * 80)
    print()

except Exception as e:
    print(f"❌ Error: {e}")
    import traceback
    traceback.print_exc()
