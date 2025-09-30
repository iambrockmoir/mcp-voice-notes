#!/bin/bash

# Voice Notes MCP Setup Script
# This script helps set up the environment for the Voice Notes MCP system

set -e

echo "🎙️ Voice Notes MCP Setup Script"
echo "================================"

# Check if we're in the right directory
if [ ! -f "README.md" ] || [ ! -d "voice_notes_mcp" ] || [ ! -d "android_app" ]; then
    echo "❌ Error: Please run this script from the mcp_thought_recorder root directory"
    exit 1
fi

echo ""
echo "📋 Step 1: Creating environment configuration files..."

# Create .env from example if it doesn't exist
if [ ! -f ".env" ]; then
    cp .env.example .env
    echo "✅ Created .env file from template"
    echo "⚠️  Please edit .env with your actual API keys"
else
    echo "ℹ️  .env file already exists"
fi

# Create Android local.properties from example if it doesn't exist
if [ ! -f "android_app/local.properties" ]; then
    cp android_app/local.properties.example android_app/local.properties
    echo "✅ Created android_app/local.properties from template"
    echo "⚠️  Please edit android_app/local.properties with your actual API keys"
else
    echo "ℹ️  android_app/local.properties already exists"
fi

echo ""
echo "📦 Step 2: Installing MCP server dependencies..."

cd voice_notes_mcp

# Check if python3 is available
if ! command -v python3 &> /dev/null; then
    echo "❌ Error: python3 is not installed or not in PATH"
    echo "Please install Python 3.8 or later"
    exit 1
fi

# Install requirements
if [ -f "requirements.txt" ]; then
    python3 -m pip install -r requirements.txt
    echo "✅ Installed Python dependencies"
else
    echo "❌ Error: requirements.txt not found"
    exit 1
fi

cd ..

echo ""
echo "🔧 Step 3: Setting up configuration files..."

# Get current directory path
CURRENT_PATH=$(pwd)

# Create Claude Desktop config if it doesn't exist
CLAUDE_DESKTOP_CONFIG="$HOME/Library/Application Support/Claude/claude_desktop_config.json"
if [ ! -f "$CLAUDE_DESKTOP_CONFIG" ]; then
    # Create directory if it doesn't exist
    mkdir -p "$HOME/Library/Application Support/Claude"
    
    # Copy and update template
    sed "s|/path/to/your/mcp_thought_recorder|$CURRENT_PATH|g" claude_desktop_config.example.json > "$CLAUDE_DESKTOP_CONFIG"
    echo "✅ Created Claude Desktop configuration"
    echo "⚠️  Please edit $CLAUDE_DESKTOP_CONFIG with your Supabase credentials"
else
    echo "ℹ️  Claude Desktop configuration already exists"
    echo "💡 You may need to manually add the voice-notes server to your existing configuration"
fi

# Create Claude Code config if it doesn't exist
CLAUDE_CODE_CONFIG="$HOME/.claude/settings.json"
if [ ! -f "$CLAUDE_CODE_CONFIG" ]; then
    # Create directory if it doesn't exist
    mkdir -p "$HOME/.claude"
    
    # Copy and update template
    sed "s|/path/to/your/mcp_thought_recorder|$CURRENT_PATH|g" claude_settings.example.json > "$CLAUDE_CODE_CONFIG"
    echo "✅ Created Claude Code configuration"
    echo "⚠️  Please edit $CLAUDE_CODE_CONFIG with your Supabase credentials"
else
    echo "ℹ️  Claude Code configuration already exists"
    echo "💡 You may need to manually add the voice-notes server to your existing configuration"
fi

echo ""
echo "🎯 Next Steps:"
echo "============="
echo ""
echo "1. 🔑 Edit your API keys:"
echo "   • .env (for MCP server)"
echo "   • android_app/local.properties (for Android app)"
echo "   • $CLAUDE_DESKTOP_CONFIG"
echo ""
echo "2. 🗄️  Set up your Supabase database:"
echo "   • Create a new project at https://supabase.com"
echo "   • Run the SQL commands from the README.md"
echo "   • Copy your project URL and API keys"
echo ""
echo "3. 🤖 Get your OpenAI API key:"
echo "   • Visit https://platform.openai.com/api-keys"
echo "   • Create a new API key"
echo ""
echo "4. 📱 Build the Android app:"
echo "   • cd android_app"
echo "   • ./gradlew installDebug"
echo ""
echo "5. 🧪 Test the MCP server:"
echo "   • cd voice_notes_mcp"
echo "   • python3 mcp_server.py"
echo ""
echo "6. 🔄 Restart Claude Desktop to load the MCP server"
echo ""
echo "✅ Setup script completed!"
echo "📚 See README.md for detailed setup instructions"