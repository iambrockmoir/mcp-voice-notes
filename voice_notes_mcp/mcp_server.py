#!/usr/bin/env python3
"""
Voice Notes MCP Server - Proper MCP Protocol Implementation
Compatible with Claude Desktop MCP requirements
"""

import asyncio
import json
import logging
import os
import sys
import urllib.request
import urllib.error
from datetime import datetime
from typing import Dict, Any, List, Optional
from pathlib import Path

try:
    from dotenv import load_dotenv
    # Load .env file from the project root
    env_path = Path(__file__).parent.parent / '.env'
    load_dotenv(env_path)
except ImportError:
    # If python-dotenv is not installed, continue without it
    pass

# Configure logging
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(name)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)

class VoiceNotesMCPServer:
    """Voice Notes MCP Server with proper protocol implementation"""
    
    def __init__(self):
        self.supabase_url = os.getenv("SUPABASE_URL")
        # Try service role key first (preferred for MCP server), then fall back to anon key
        self.supabase_key = os.getenv("SUPABASE_SERVICE_ROLE_KEY") or os.getenv("SUPABASE_ANON_KEY")
        
        if not self.supabase_url or not self.supabase_key:
            raise ValueError("SUPABASE_URL and either SUPABASE_SERVICE_ROLE_KEY or SUPABASE_ANON_KEY environment variables must be set")
        
        self.initialized = False
        logger.info("Voice Notes MCP Server created")
    
    def make_supabase_request(self, endpoint: str, method: str = "GET", data: dict = None) -> dict:
        """Make HTTP request to Supabase"""
        url = f"{self.supabase_url}/rest/v1/{endpoint}"
        
        req = urllib.request.Request(url)
        req.add_header('apikey', self.supabase_key)
        req.add_header('Authorization', f'Bearer {self.supabase_key}')
        req.add_header('Content-Type', 'application/json')
        req.add_header('Prefer', 'return=representation')
        
        if method != "GET":
            req.get_method = lambda: method
        
        if data:
            req.data = json.dumps(data).encode('utf-8')
        
        try:
            with urllib.request.urlopen(req) as response:
                return json.loads(response.read().decode('utf-8'))
        except urllib.error.HTTPError as e:
            error_body = e.read().decode('utf-8')
            logger.error(f"HTTP Error {e.code}: {error_body}")
            return {"error": f"HTTP {e.code}: {error_body}"}
        except Exception as e:
            logger.error(f"Request error: {e}")
            return {"error": str(e)}
    
    async def handle_initialize(self, params: dict) -> dict:
        """Handle MCP initialize request"""
        logger.info("Handling initialize request")
        self.initialized = True
        
        return {
            "protocolVersion": "2024-11-05",
            "capabilities": {
                "tools": {},
                "logging": {}
            },
            "serverInfo": {
                "name": "voice-notes-mcp",
                "version": "1.0.0"
            }
        }
    
    async def handle_tools_list(self) -> dict:
        """Handle tools/list request"""
        if not self.initialized:
            raise Exception("Server not initialized")
        
        return {
            "tools": [
                {
                    "name": "list_unprocessed_notes",
                    "description": "List all unprocessed voice notes for review",
                    "inputSchema": {
                        "type": "object",
                        "properties": {
                            "limit": {
                                "type": "integer",
                                "description": "Maximum number of notes to return",
                                "default": 50
                            }
                        }
                    }
                },
                {
                    "name": "read_note",
                    "description": "Read the full content of a specific voice note",
                    "inputSchema": {
                        "type": "object",
                        "properties": {
                            "note_id": {
                                "type": "string",
                                "description": "UUID of the note to read"
                            }
                        },
                        "required": ["note_id"]
                    }
                },
                {
                    "name": "mark_as_processed",
                    "description": "Mark a voice note as processed/reviewed",
                    "inputSchema": {
                        "type": "object",
                        "properties": {
                            "note_id": {
                                "type": "string",
                                "description": "UUID of the note to mark as processed"
                            }
                        },
                        "required": ["note_id"]
                    }
                },
                {
                    "name": "search_notes",
                    "description": "Search voice notes by keyword",
                    "inputSchema": {
                        "type": "object",
                        "properties": {
                            "query": {
                                "type": "string",
                                "description": "Search query string"
                            },
                            "limit": {
                                "type": "integer",
                                "description": "Maximum number of results",
                                "default": 20
                            }
                        },
                        "required": ["query"]
                    }
                },
                {
                    "name": "get_inbox_stats",
                    "description": "Get statistics about voice notes inbox",
                    "inputSchema": {
                        "type": "object",
                        "properties": {}
                    }
                }
            ]
        }
    
    async def handle_tools_call(self, params: dict) -> dict:
        """Handle tools/call request"""
        if not self.initialized:
            raise Exception("Server not initialized")
        
        tool_name = params.get("name")
        arguments = params.get("arguments", {})
        
        try:
            if tool_name == "list_unprocessed_notes":
                result = await self.list_unprocessed_notes(arguments.get("limit", 50))
            elif tool_name == "read_note":
                result = await self.read_note(arguments["note_id"])
            elif tool_name == "mark_as_processed":
                result = await self.mark_as_processed(arguments["note_id"])
            elif tool_name == "search_notes":
                result = await self.search_notes(arguments["query"], arguments.get("limit", 20))
            elif tool_name == "get_inbox_stats":
                result = await self.get_inbox_stats()
            else:
                raise Exception(f"Unknown tool: {tool_name}")
            
            return {
                "content": [
                    {
                        "type": "text",
                        "text": json.dumps(result, indent=2, default=str)
                    }
                ]
            }
        except Exception as e:
            logger.error(f"Tool execution error: {e}")
            raise
    
    async def list_unprocessed_notes(self, limit: int = 50) -> dict:
        """List unprocessed notes"""
        endpoint = f"notes?select=id,transcript,created_at,word_count,audio_duration_seconds&user_id=eq.00000000-0000-0000-0000-000000000001&is_processed=eq.false&transcription_status=eq.completed&order=created_at.desc&limit={limit}"
        result = self.make_supabase_request(endpoint)
        
        if isinstance(result, list):
            return {
                "notes": result,
                "count": len(result),
                "has_more": len(result) == limit
            }
        return result
    
    async def read_note(self, note_id: str) -> dict:
        """Read a specific note"""
        endpoint = f"notes?id=eq.{note_id}&user_id=eq.00000000-0000-0000-0000-000000000001"
        result = self.make_supabase_request(endpoint)
        
        if isinstance(result, list) and len(result) > 0:
            return result[0]
        elif isinstance(result, list):
            return {"error": f"Note {note_id} not found"}
        return result
    
    async def mark_as_processed(self, note_id: str) -> dict:
        """Mark a note as processed"""
        data = {
            "is_processed": True,
            "modified_at": datetime.utcnow().isoformat()
        }
        endpoint = f"notes?id=eq.{note_id}&user_id=eq.00000000-0000-0000-0000-000000000001"
        result = self.make_supabase_request(endpoint, "PATCH", data)
        
        if not result.get("error"):
            return {"success": True, "note_id": note_id}
        return result
    
    async def search_notes(self, query: str, limit: int = 20) -> dict:
        """Search notes"""
        endpoint = f"notes?select=id,transcript,created_at,word_count,is_processed&user_id=eq.00000000-0000-0000-0000-000000000001&transcript=ilike.%{query}%&order=created_at.desc&limit={limit}"
        result = self.make_supabase_request(endpoint)
        
        if isinstance(result, list):
            return {
                "notes": result,
                "count": len(result),
                "query": query
            }
        return result
    
    async def get_inbox_stats(self) -> dict:
        """Get inbox statistics"""
        unprocessed = self.make_supabase_request("notes?select=id&user_id=eq.00000000-0000-0000-0000-000000000001&is_processed=eq.false&transcription_status=eq.completed")
        total = self.make_supabase_request("notes?select=id&user_id=eq.00000000-0000-0000-0000-000000000001")
        
        if isinstance(unprocessed, list) and isinstance(total, list):
            return {
                "unprocessed_count": len(unprocessed),
                "total_count": len(total),
                "processed_count": len(total) - len(unprocessed),
                "last_updated": datetime.utcnow().isoformat()
            }
        
        return {"error": "Failed to get stats"}

async def handle_message(server: VoiceNotesMCPServer, message: dict) -> dict:
    """Handle incoming MCP messages"""
    try:
        method = message.get("method")
        params = message.get("params", {})
        msg_id = message.get("id")
        
        if method == "initialize":
            result = await server.handle_initialize(params)
            return {"jsonrpc": "2.0", "id": msg_id, "result": result}
        
        elif method == "notifications/initialized":
            # Acknowledge initialization
            return None
        
        elif method == "tools/list":
            result = await server.handle_tools_list()
            return {"jsonrpc": "2.0", "id": msg_id, "result": result}
        
        elif method == "tools/call":
            result = await server.handle_tools_call(params)
            return {"jsonrpc": "2.0", "id": msg_id, "result": result}
        
        else:
            return {
                "jsonrpc": "2.0",
                "id": msg_id,
                "error": {"code": -32601, "message": f"Method not found: {method}"}
            }
    
    except Exception as e:
        logger.error(f"Error handling message: {e}")
        return {
            "jsonrpc": "2.0",
            "id": message.get("id"),
            "error": {"code": -32603, "message": f"Internal error: {str(e)}"}
        }

async def main():
    """Main MCP server loop"""
    try:
        server = VoiceNotesMCPServer()
        logger.info("Voice Notes MCP Server started, waiting for messages...")
        
        while True:
            try:
                # Read line from stdin
                line = await asyncio.get_event_loop().run_in_executor(None, sys.stdin.readline)
                if not line:
                    logger.info("EOF received, shutting down")
                    break
                
                line = line.strip()
                if not line:
                    continue
                
                # Parse JSON message
                try:
                    message = json.loads(line)
                except json.JSONDecodeError as e:
                    logger.error(f"Invalid JSON: {e}")
                    continue
                
                # Handle message
                response = await handle_message(server, message)
                
                # Send response if not None
                if response is not None:
                    print(json.dumps(response))
                    sys.stdout.flush()
                
            except Exception as e:
                logger.error(f"Error in message loop: {e}")
                continue
    
    except Exception as e:
        logger.error(f"Fatal error: {e}")
        sys.exit(1)

if __name__ == "__main__":
    asyncio.run(main())