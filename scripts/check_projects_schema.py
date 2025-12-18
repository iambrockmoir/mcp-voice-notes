#!/usr/bin/env python3
"""
Quick script to check the projects table schema and note_count column
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
    print("CHECKING PROJECTS TABLE")
    print("=" * 80)
    print()

    # Try to fetch projects with note_count explicitly
    print("📊 FETCHING PROJECTS (with explicit note_count selection):")
    try:
        projects = supabase.table("projects").select("id, name, note_count, is_archived").limit(10).execute()
        if projects.data:
            print(f"  ✅ Found {len(projects.data)} projects:")
            for proj in projects.data:
                print(f"    - {proj['name']}: note_count = {proj.get('note_count', 'MISSING')}")
        else:
            print("  ℹ️  No projects found")
    except Exception as e:
        print(f"  ❌ Error fetching with note_count: {e}")
        print("  This might mean the note_count column doesn't exist!")

    print()
    print("📊 FETCHING PROJECTS (with * selection):")
    try:
        projects_all = supabase.table("projects").select("*").limit(10).execute()
        if projects_all.data and len(projects_all.data) > 0:
            print(f"  ✅ Found {len(projects_all.data)} projects")
            print(f"  First project fields: {list(projects_all.data[0].keys())}")
            if 'note_count' in projects_all.data[0]:
                print(f"  ✅ note_count field IS present in response!")
            else:
                print(f"  ❌ note_count field is MISSING from response!")
        else:
            print("  ℹ️  No projects found")
    except Exception as e:
        print(f"  ❌ Error: {e}")

    print()
    print("=" * 80)
    print()

except ImportError:
    print("❌ supabase-py not installed. Install with: pip install supabase")
except Exception as e:
    print(f"❌ Error: {e}")
    import traceback
    traceback.print_exc()
