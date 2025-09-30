#!/usr/bin/env python3
"""
Simplified Voice Notes MCP Server
A minimal MCP server implementation that works with basic Python libraries
"""

import asyncio
import json
import logging
import os
import sys
import urllib.request
import urllib.error
from datetime import datetime
from typing import Dict, Any, List

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

class SimpleVoiceNotesMCPServer:
    """Simplified MCP Server for Voice Notes"""
    
    def __init__(self):
        self.supabase_url = os.getenv("SUPABASE_URL")
        self.supabase_key = os.getenv("SUPABASE_KEY")
        
        if not self.supabase_url or not self.supabase_key:
            raise ValueError("SUPABASE_URL and SUPABASE_KEY environment variables must be set")
        
        logger.info("Simple Voice Notes MCP Server initialized")
    
    def make_supabase_request(self, endpoint: str, method: str = "GET", data: dict = None) -> dict:
        """Make HTTP request to Supabase"""
        url = f"{self.supabase_url}/rest/v1/{endpoint}"
        
        req = urllib.request.Request(url)
        req.add_header('apikey', self.supabase_key)
        req.add_header('Authorization', f'Bearer {self.supabase_key}')
        req.add_header('Content-Type', 'application/json')
        
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
    
    async def list_unprocessed_notes(self, limit: int = 50) -> dict:
        """List unprocessed notes"""
        endpoint = f"notes?select=id,transcript,created_at,word_count,audio_duration_seconds&is_processed=eq.false&transcription_status=eq.completed&order=created_at.desc&limit={limit}"
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
        endpoint = f"notes?id=eq.{note_id}"
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
        endpoint = f"notes?id=eq.{note_id}"
        result = self.make_supabase_request(endpoint, "PATCH", data)
        
        if not result.get("error"):
            return {"success": True, "note_id": note_id}
        return result
    
    async def search_notes(self, query: str, limit: int = 20) -> dict:
        """Search notes (simple text search)"""
        endpoint = f"notes?select=id,transcript,created_at,word_count,is_processed&transcript=ilike.%{query}%&order=created_at.desc&limit={limit}"
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
        # Get unprocessed count
        unprocessed = self.make_supabase_request("notes?select=id&is_processed=eq.false&transcription_status=eq.completed")
        total = self.make_supabase_request("notes?select=id")
        
        if isinstance(unprocessed, list) and isinstance(total, list):
            return {
                "unprocessed_count": len(unprocessed),
                "total_count": len(total),
                "processed_count": len(total) - len(unprocessed),
                "last_updated": datetime.utcnow().isoformat()
            }
        
        return {"error": "Failed to get stats"}

async def handle_mcp_message(server: SimpleVoiceNotesMCPServer, message: dict) -> dict:
    """Handle incoming MCP messages"""
    try:
        method = message.get("method")
        params = message.get("params", {})
        
        if method == "tools/list":
            return {
                "jsonrpc": "2.0",
                "id": message.get("id"),
                "result": {
                    "tools": [
                        {
                            "name": "list_unprocessed_notes",
                            "description": "Get all unprocessed notes for inbox review"
                        },
                        {
                            "name": "read_note", 
                            "description": "Read the full content of a specific note"
                        },
                        {
                            "name": "mark_as_processed",
                            "description": "Mark a note as processed/reviewed"
                        },
                        {
                            "name": "search_notes",
                            "description": "Search notes by keyword"
                        },
                        {
                            "name": "get_inbox_stats",
                            "description": "Get statistics about inbox"
                        }
                    ]
                }
            }
        
        elif method == "tools/call":
            tool_name = params.get("name")
            tool_args = params.get("arguments", {})
            
            if tool_name == "list_unprocessed_notes":
                result = await server.list_unprocessed_notes(tool_args.get("limit", 50))
            elif tool_name == "read_note":
                result = await server.read_note(tool_args["note_id"])
            elif tool_name == "mark_as_processed":
                result = await server.mark_as_processed(tool_args["note_id"])
            elif tool_name == "search_notes":
                result = await server.search_notes(tool_args["query"], tool_args.get("limit", 20))
            elif tool_name == "get_inbox_stats":
                result = await server.get_inbox_stats()
            else:
                result = {"error": f"Unknown tool: {tool_name}"}
            
            return {
                "jsonrpc": "2.0",
                "id": message.get("id"),
                "result": {
                    "content": [
                        {
                            "type": "text",
                            "text": json.dumps(result, indent=2, default=str)
                        }
                    ]
                }
            }
        
        else:
            return {
                "jsonrpc": "2.0",
                "id": message.get("id"),
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
    server = SimpleVoiceNotesMCPServer()
    logger.info("MCP Server started, waiting for messages...")
    
    while True:
        try:
            # Read from stdin
            line = await asyncio.get_event_loop().run_in_executor(None, sys.stdin.readline)
            if not line:
                break
            
            # Parse JSON message
            message = json.loads(line.strip())
            
            # Handle message
            response = await handle_mcp_message(server, message)
            
            # Send response to stdout
            print(json.dumps(response))
            sys.stdout.flush()
            
        except json.JSONDecodeError as e:
            logger.error(f"Invalid JSON: {e}")
        except Exception as e:
            logger.error(f"Error in main loop: {e}")
            break

if __name__ == "__main__":
    asyncio.run(main())