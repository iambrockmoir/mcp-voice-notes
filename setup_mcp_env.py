#!/usr/bin/env python3
"""
Setup MCP Environment
Loads credentials from local.properties and sets up environment for MCP server
"""

import os
import sys

def load_credentials_from_local_properties():
    """Load Supabase credentials from android_app/local.properties"""
    local_props_path = os.path.join(os.path.dirname(__file__), "android_app", "local.properties")
    
    if not os.path.exists(local_props_path):
        print(f"❌ local.properties not found at: {local_props_path}")
        print("📋 Make sure you have created local.properties with your Supabase credentials")
        return False
    
    credentials = {}
    try:
        with open(local_props_path, 'r') as f:
            for line in f:
                line = line.strip()
                if line and not line.startswith('#') and '=' in line:
                    key, value = line.split('=', 1)
                    credentials[key.strip()] = value.strip()
        
        supabase_url = credentials.get('SUPABASE_URL')
        supabase_key = credentials.get('SUPABASE_ANON_KEY')
        
        if not supabase_url or not supabase_key:
            print("❌ Missing SUPABASE_URL or SUPABASE_ANON_KEY in local.properties")
            return False
        
        # Set environment variables for MCP server
        os.environ['SUPABASE_URL'] = supabase_url
        os.environ['SUPABASE_KEY'] = supabase_key
        
        print("✅ Credentials loaded from local.properties")
        print(f"   SUPABASE_URL: {supabase_url}")
        print(f"   SUPABASE_KEY: {supabase_key[:20]}...")
        
        return True
        
    except Exception as e:
        print(f"❌ Error reading local.properties: {e}")
        return False

def create_mcp_env_file():
    """Create a .env file for the MCP server"""
    if not load_credentials_from_local_properties():
        return False
    
    env_file_path = os.path.join(os.path.dirname(__file__), "voice_notes_mcp", ".env")
    
    try:
        with open(env_file_path, 'w') as f:
            f.write(f"SUPABASE_URL={os.environ['SUPABASE_URL']}\n")
            f.write(f"SUPABASE_KEY={os.environ['SUPABASE_KEY']}\n")
        
        print(f"✅ Created .env file at: {env_file_path}")
        return True
        
    except Exception as e:
        print(f"❌ Error creating .env file: {e}")
        return False

def main():
    """Main setup function"""
    print("🔧 Setting up MCP Environment...\n")
    
    # Load credentials
    if not load_credentials_from_local_properties():
        print("\n❌ Setup failed - could not load credentials")
        return False
    
    # Create .env file for convenience
    create_mcp_env_file()
    
    print("\n✅ MCP Environment setup complete!")
    print("\n📋 Next steps:")
    print("   1. Install MCP dependencies (if not already installed)")
    print("   2. Run MCP server: python voice_notes_mcp/server.py")
    print("   3. Configure Claude Desktop to use this MCP server")
    
    return True

if __name__ == "__main__":
    success = main()
    sys.exit(0 if success else 1)