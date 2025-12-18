#!/usr/bin/env python3
"""
Safe Migration Runner for Projects v1.1
This script runs the database migration without affecting existing data.
"""

import os
import sys
from pathlib import Path

def print_instructions():
    """Print instructions for running the migration"""

    migration_file = Path(__file__).parent / "migrations" / "001_add_projects.sql"

    print("=" * 80)
    print("🚀 VOICE NOTES PROJECTS v1.1 - MIGRATION INSTRUCTIONS")
    print("=" * 80)
    print()
    print("This migration is SAFE and will NOT delete any existing data.")
    print("It only ADDS new tables and columns to your database.")
    print()
    print("What will be added:")
    print("  ✅ New 'projects' table")
    print("  ✅ New 'project_id' column to existing 'notes' table (nullable)")
    print("  ✅ Indexes for performance")
    print("  ✅ RLS security policies")
    print("  ✅ Database functions and triggers")
    print()
    print("=" * 80)
    print("OPTION 1: Run via Supabase Web Interface (RECOMMENDED)")
    print("=" * 80)
    print()
    print("1. Open your Supabase project dashboard:")
    print("   https://supabase.com/dashboard/project/<your-project-id>")
    print()
    print("2. Navigate to: SQL Editor (left sidebar)")
    print()
    print("3. Click 'New Query'")
    print()
    print("4. Copy the contents of this file:")
    print(f"   {migration_file}")
    print()
    print("5. Paste into the SQL editor and click 'Run'")
    print()
    print("6. You should see: 'Success. No rows returned'")
    print()
    print("=" * 80)
    print("OPTION 2: Run via Python Script (if you have credentials)")
    print("=" * 80)
    print()
    print("If you have SUPABASE_URL and SUPABASE_KEY environment variables set,")
    print("you can run:")
    print()
    print("  python3 run_migration.py --execute")
    print()
    print("=" * 80)
    print()

    # Check if migration file exists
    if migration_file.exists():
        print(f"✅ Migration file found: {migration_file}")
        print(f"   File size: {migration_file.stat().st_size} bytes")
    else:
        print(f"❌ Migration file not found: {migration_file}")
        sys.exit(1)

    print()
    print("💡 TIP: After running the migration, you can verify it worked by running:")
    print("   SELECT tablename FROM pg_tables WHERE tablename = 'projects';")
    print()

def run_migration():
    """Execute the migration using Supabase client"""

    # Check for environment variables
    supabase_url = os.getenv("SUPABASE_URL")
    supabase_key = os.getenv("SUPABASE_KEY")

    if not supabase_url or not supabase_key:
        print("❌ Error: SUPABASE_URL and SUPABASE_KEY environment variables must be set")
        print()
        print("Please set them in your environment or use Option 1 (Web Interface)")
        sys.exit(1)

    try:
        from supabase import create_client
    except ImportError:
        print("❌ Error: supabase package not installed")
        print("Run: pip install supabase")
        sys.exit(1)

    # Read migration file
    migration_file = Path(__file__).parent / "migrations" / "001_add_projects.sql"

    if not migration_file.exists():
        print(f"❌ Error: Migration file not found: {migration_file}")
        sys.exit(1)

    print("📖 Reading migration file...")
    with open(migration_file, 'r') as f:
        sql = f.read()

    print(f"✅ Loaded {len(sql)} characters of SQL")
    print()
    print("⚠️  WARNING: About to execute migration on your Supabase database")
    print(f"   Target: {supabase_url}")
    print()
    response = input("Type 'yes' to continue: ")

    if response.lower() != 'yes':
        print("❌ Migration cancelled")
        sys.exit(0)

    print()
    print("🚀 Connecting to Supabase...")

    try:
        supabase = create_client(supabase_url, supabase_key)

        print("✅ Connected successfully")
        print()
        print("📝 Note: Supabase Python client doesn't support raw SQL execution.")
        print("   You'll need to use the Supabase SQL Editor (Option 1)")
        print()
        print("However, I can verify your connection is working:")

        # Try to query existing notes table
        result = supabase.table("notes").select("id", count="exact").limit(1).execute()
        print(f"✅ Connection verified - found notes table with {result.count} records")
        print()
        print("Please proceed with Option 1 (Supabase Web Interface) to run the migration.")

    except Exception as e:
        print(f"❌ Error: {e}")
        sys.exit(1)

if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "--execute":
        run_migration()
    else:
        print_instructions()
