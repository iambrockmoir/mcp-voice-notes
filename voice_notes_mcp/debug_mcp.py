#!/usr/bin/env python3
"""
Debug script to test MCP server functionality
"""
import os
import json
import urllib.request
import urllib.error
from datetime import datetime

# Test environment variables
supabase_url = os.getenv("SUPABASE_URL")
supabase_key = os.getenv("SUPABASE_ANON_KEY")

print(f"Environment variables:")
print(f"SUPABASE_URL: {supabase_url}")
print(f"SUPABASE_ANON_KEY: {'***' + str(supabase_key)[-4:] if supabase_key else 'None'}")
print()

if not supabase_url or not supabase_key:
    print("❌ Environment variables not set")
    exit(1)

def test_request(endpoint_desc, endpoint):
    """Test a specific endpoint"""
    print(f"Testing {endpoint_desc}...")
    url = f"{supabase_url}/rest/v1/{endpoint}"
    
    req = urllib.request.Request(url)
    req.add_header('apikey', supabase_key)
    req.add_header('Authorization', f'Bearer {supabase_key}')
    req.add_header('Content-Type', 'application/json')
    
    try:
        with urllib.request.urlopen(req) as response:
            result = json.loads(response.read().decode('utf-8'))
            print(f"✅ {endpoint_desc}: Success - {len(result) if isinstance(result, list) else 'OK'}")
            return result
    except urllib.error.HTTPError as e:
        error_body = e.read().decode('utf-8')
        print(f"❌ {endpoint_desc}: HTTP {e.code} - {error_body}")
        return None
    except Exception as e:
        print(f"❌ {endpoint_desc}: {e}")
        return None

# Test endpoints that the MCP server uses
test_request("Basic notes query", "notes?select=id&user_id=eq.00000000-0000-0000-0000-000000000001&limit=1")
test_request("Unprocessed notes", "notes?select=id,transcript,created_at&user_id=eq.00000000-0000-0000-0000-000000000001&is_processed=eq.false&transcription_status=eq.completed&limit=5")
test_request("All notes count", "notes?select=id&user_id=eq.00000000-0000-0000-0000-000000000001")