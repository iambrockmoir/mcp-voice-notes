#!/usr/bin/env python3
"""
Safe Migration Deployment for Projects v1.1
Uses credentials from local.properties file
"""

import os
import sys
from pathlib import Path
import re

def load_properties_file(filepath):
    """Load properties from a .properties file"""
    properties = {}

    if not filepath.exists():
        return properties

    with open(filepath, 'r') as f:
        for line in f:
            line = line.strip()
            # Skip comments and empty lines
            if not line or line.startswith('#'):
                continue

            # Parse key=value
            if '=' in line:
                key, value = line.split('=', 1)
                properties[key.strip()] = value.strip()

    return properties

def main():
    print("=" * 80)
    print("🚀 VOICE NOTES PROJECTS v1.1 - MIGRATION DEPLOYMENT")
    print("=" * 80)
    print()

    # Load credentials from local.properties
    local_props_path = Path(__file__).parent / "android_app" / "local.properties"

    if not local_props_path.exists():
        print("❌ Error: local.properties file not found")
        print(f"   Expected at: {local_props_path}")
        print()
        print("Please create local.properties with your Supabase credentials")
        sys.exit(1)

    print(f"📖 Loading credentials from: {local_props_path}")
    props = load_properties_file(local_props_path)

    # Check for Supabase credentials
    supabase_url = props.get('SUPABASE_URL') or props.get('supabase.url') or props.get('supabaseUrl')
    supabase_key = (props.get('SUPABASE_KEY') or
                    props.get('SUPABASE_ANON_KEY') or
                    props.get('supabase.key') or
                    props.get('supabaseKey') or
                    props.get('SUPABASE_SERVICE_ROLE_KEY'))

    if not supabase_url or not supabase_key:
        print("❌ Error: Could not find Supabase credentials in local.properties")
        print()
        print("Found properties:")
        for key in props.keys():
            print(f"  - {key}")
        print()
        print("Please ensure local.properties contains:")
        print("  SUPABASE_URL=your_url")
        print("  SUPABASE_KEY=your_key")
        sys.exit(1)

    print("✅ Found Supabase credentials")
    print(f"   URL: {supabase_url[:30]}...")
    print(f"   Key: {supabase_key[:20]}...{supabase_key[-4:]}")
    print()

    # Check migration file exists
    migration_file = Path(__file__).parent / "migrations" / "001_add_projects.sql"

    if not migration_file.exists():
        print(f"❌ Error: Migration file not found: {migration_file}")
        sys.exit(1)

    print("📄 Migration file found")
    print(f"   Size: {migration_file.stat().st_size} bytes")
    print()

    # Try to import supabase
    try:
        from supabase import create_client
    except ImportError:
        print("❌ Error: supabase package not installed")
        print("   Run: pip install supabase")
        sys.exit(1)

    print("=" * 80)
    print("⚠️  IMPORTANT: Running SQL Migrations via Python")
    print("=" * 80)
    print()
    print("The Supabase Python client doesn't support direct SQL execution.")
    print()
    print("You have two options:")
    print()
    print("OPTION 1: Use Supabase Web Interface (RECOMMENDED)")
    print("-" * 80)
    print(f"1. Go to: {supabase_url.replace('/rest/v1', '').rstrip('/')}")
    print("2. Click 'SQL Editor' → 'New Query'")
    print(f"3. Copy contents of: {migration_file}")
    print("4. Paste and click 'Run'")
    print()
    print("OPTION 2: Use psql command line")
    print("-" * 80)
    print("If you have PostgreSQL installed:")
    print()

    # Extract database connection URL (if available)
    if 'supabase.co' in supabase_url or 'DATABASE_URL' in props:
        db_url = props.get('DATABASE_URL', '')
        if not db_url:
            # Construct from Supabase URL
            project_ref = supabase_url.split('.')[0].split('//')[1]
            print(f"psql postgres://postgres:[password]@db.{project_ref}.supabase.co:5432/postgres \\")
            print(f"     -f {migration_file}")
        else:
            print(f"psql {db_url} -f {migration_file}")

    print()
    print("=" * 80)
    print("TESTING CONNECTION")
    print("=" * 80)
    print()

    try:
        print(f"🔌 Connecting to {supabase_url[:30]}...")
        supabase = create_client(supabase_url, supabase_key)

        # Test connection by querying notes
        result = supabase.table("notes").select("id", count="exact").limit(1).execute()

        print(f"✅ Connection successful!")
        print(f"✅ Database is accessible")
        print(f"✅ Found {result.count} notes in database")
        print()

        # Check if projects table already exists
        try:
            project_result = supabase.table("projects").select("id", count="exact").limit(1).execute()
            print("⚠️  Projects table already exists!")
            print(f"   Found {project_result.count} projects")
            print()
            print("✅ Migration appears to be already completed!")
            print()
            print("Next steps:")
            print("1. Restart your MCP server")
            print("2. Test the new project features")

        except Exception:
            print("📝 Projects table does not exist yet")
            print()
            print("✅ Ready to run migration!")
            print()
            print("Please use OPTION 1 (Web Interface) above to run the migration SQL.")

    except Exception as e:
        print(f"❌ Connection failed: {e}")
        print()
        print("Please verify your credentials in local.properties")
        sys.exit(1)

    print()
    print("=" * 80)
    print()

if __name__ == "__main__":
    main()
