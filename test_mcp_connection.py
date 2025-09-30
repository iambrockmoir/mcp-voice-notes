#!/usr/bin/env python3
"""
Test MCP Connection - Debug helper for Claude Desktop connection issues
"""

import json
import os
import sys

def test_claude_desktop_config():
    """Test Claude Desktop configuration"""
    print("🔍 Testing Claude Desktop MCP Configuration...\n")
    
    config_path = os.path.expanduser("~/.claude/settings.json")
    
    if not os.path.exists(config_path):
        print("❌ Claude Desktop settings.json not found")
        return False
    
    try:
        with open(config_path, 'r') as f:
            config = json.load(f)
        
        print("✅ Configuration file exists and is valid JSON")
        
        if "mcpServers" in config:
            print("✅ mcpServers section found")
            
            servers = config["mcpServers"]
            print(f"📊 Found {len(servers)} MCP server(s):")
            
            for name, server_config in servers.items():
                print(f"\n🔧 Server: {name}")
                print(f"   Command: {server_config.get('command', 'N/A')}")
                print(f"   Args: {server_config.get('args', [])}")
                print(f"   Env vars: {list(server_config.get('env', {}).keys())}")
                
                # Test if the server file exists
                if server_config.get('args'):
                    server_path = server_config['args'][0]
                    if os.path.exists(server_path):
                        print(f"   ✅ Server file exists: {server_path}")
                    else:
                        print(f"   ❌ Server file not found: {server_path}")
            
            return True
        else:
            print("❌ No mcpServers section found in configuration")
            return False
            
    except json.JSONDecodeError as e:
        print(f"❌ Invalid JSON in configuration file: {e}")
        return False
    except Exception as e:
        print(f"❌ Error reading configuration: {e}")
        return False

def check_claude_desktop_process():
    """Check if Claude Desktop is running"""
    print("\n🔍 Checking Claude Desktop Process...")
    
    try:
        import subprocess
        result = subprocess.run(['pgrep', '-f', 'Claude'], 
                              capture_output=True, text=True)
        
        if result.returncode == 0:
            processes = result.stdout.strip().split('\n')
            print(f"✅ Found {len([p for p in processes if p])} Claude Desktop process(es)")
            return True
        else:
            print("❌ Claude Desktop not running")
            print("📋 Make sure to restart Claude Desktop after configuration changes")
            return False
    except Exception as e:
        print(f"⚠️ Could not check processes: {e}")
        return False

def create_test_message():
    """Create a test MCP message"""
    print("\n🧪 Creating Test MCP Message...")
    
    test_message = {
        "jsonrpc": "2.0",
        "id": 1,
        "method": "tools/list",
        "params": {}
    }
    
    print(f"📤 Test message: {json.dumps(test_message, indent=2)}")
    return test_message

def main():
    """Run all diagnostic tests"""
    print("🚀 Claude Desktop MCP Connection Diagnostics\n")
    
    # Test configuration
    config_ok = test_claude_desktop_config()
    
    # Check if Claude Desktop is running  
    process_ok = check_claude_desktop_process()
    
    # Create test message
    create_test_message()
    
    print("\n" + "="*50)
    print("📋 DIAGNOSTIC SUMMARY")
    print("="*50)
    
    if config_ok:
        print("✅ Configuration is valid")
    else:
        print("❌ Configuration has issues")
    
    if process_ok:
        print("✅ Claude Desktop is running")
    else:
        print("❌ Claude Desktop needs to be restarted")
    
    print("\n🔧 TROUBLESHOOTING STEPS:")
    print("1. Make sure Claude Desktop is completely closed")
    print("2. Reopen Claude Desktop")
    print("3. Look for MCP server status in the interface")
    print("4. Check the bottom status bar or settings for 'voice-notes' server")
    print("5. Try asking Claude: 'What MCP servers are connected?'")
    
    print(f"\n📍 Your MCP server should appear as: 'voice-notes'")
    
    return config_ok

if __name__ == "__main__":
    success = main()
    sys.exit(0 if success else 1)