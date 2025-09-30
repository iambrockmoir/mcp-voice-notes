#!/usr/bin/env python3
"""
Voice Notes MCP Server

An MCP server that provides tools to interact with voice notes stored in Supabase.
Allows Claude Desktop to list, read, search, and mark notes as processed.
"""

import asyncio
import json
import logging
import os
from typing import Any, Dict, List, Optional
from datetime import datetime

from mcp.server.models import InitializationOptions
from mcp.server import NotificationOptions, Server
from mcp.server.stdio import stdio_server
from mcp.types import (
    CallToolRequestParams,
    CallToolResult,
    ListToolsRequestParams,
    ListToolsResult,
    Tool,
    TextContent,
)
from supabase import create_client, Client
from cachetools import TTLCache

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

class VoiceNotesMCPServer:
    """MCP Server for Voice Notes interaction"""
    
    def __init__(self):
        self.supabase_url = os.getenv("SUPABASE_URL")
        self.supabase_key = os.getenv("SUPABASE_KEY")
        
        if not self.supabase_url or not self.supabase_key:
            raise ValueError("SUPABASE_URL and SUPABASE_KEY environment variables must be set")
        
        self.supabase: Client = create_client(self.supabase_url, self.supabase_key)
        self.cache = TTLCache(maxsize=100, ttl=60)  # 60 second TTL
        
        logger.info("Voice Notes MCP Server initialized")
    
    async def list_unprocessed_notes(self, limit: int = 50, offset: int = 0) -> Dict[str, Any]:
        """List all unprocessed notes"""
        try:
            cache_key = f"unprocessed_{limit}_{offset}"
            if cache_key in self.cache:
                logger.info(f"Cache hit for {cache_key}")
                return self.cache[cache_key]
            
            response = (
                self.supabase
                .table("notes")
                .select("id, transcript, created_at, word_count, audio_duration_seconds")
                .eq("is_processed", False)
                .eq("transcription_status", "completed")
                .order("created_at", desc=True)
                .range(offset, offset + limit - 1)
                .execute()
            )
            
            notes = response.data
            result = {
                "notes": notes,
                "count": len(notes),
                "has_more": len(notes) == limit
            }
            
            self.cache[cache_key] = result
            logger.info(f"Retrieved {len(notes)} unprocessed notes")
            return result
            
        except Exception as e:
            logger.error(f"Error listing unprocessed notes: {e}")
            return {"error": str(e), "notes": [], "count": 0, "has_more": False}
    
    async def read_note(self, note_id: str) -> Dict[str, Any]:
        """Read a specific note by ID"""
        try:
            cache_key = f"note_{note_id}"
            if cache_key in self.cache:
                return self.cache[cache_key]
            
            response = (
                self.supabase
                .table("notes")
                .select("*")
                .eq("id", note_id)
                .single()
                .execute()
            )
            
            if response.data:
                self.cache[cache_key] = response.data
                logger.info(f"Retrieved note {note_id}")
                return response.data
            else:
                return {"error": f"Note {note_id} not found"}
                
        except Exception as e:
            logger.error(f"Error reading note {note_id}: {e}")
            return {"error": str(e)}
    
    async def mark_as_processed(self, note_id: str) -> Dict[str, Any]:
        """Mark a note as processed"""
        try:
            response = (
                self.supabase
                .table("notes")
                .update({"is_processed": True, "modified_at": datetime.utcnow().isoformat()})
                .eq("id", note_id)
                .execute()
            )
            
            if response.data:
                # Invalidate cache
                self._invalidate_note_cache(note_id)
                logger.info(f"Marked note {note_id} as processed")
                return {"success": True, "note_id": note_id}
            else:
                return {"error": f"Failed to mark note {note_id} as processed"}
                
        except Exception as e:
            logger.error(f"Error marking note {note_id} as processed: {e}")
            return {"error": str(e)}
    
    async def bulk_mark_processed(self, note_ids: List[str]) -> Dict[str, Any]:
        """Mark multiple notes as processed"""
        try:
            response = (
                self.supabase
                .table("notes")
                .update({"is_processed": True, "modified_at": datetime.utcnow().isoformat()})
                .in_("id", note_ids)
                .execute()
            )
            
            # Invalidate cache for all updated notes
            for note_id in note_ids:
                self._invalidate_note_cache(note_id)
            
            processed_count = len(response.data) if response.data else 0
            logger.info(f"Marked {processed_count} notes as processed")
            return {
                "success": True,
                "processed_count": processed_count,
                "note_ids": note_ids
            }
            
        except Exception as e:
            logger.error(f"Error bulk marking notes as processed: {e}")
            return {"error": str(e), "processed_count": 0}
    
    async def search_notes(self, query: str, include_processed: bool = False, limit: int = 20) -> Dict[str, Any]:
        """Search notes by keyword"""
        try:
            cache_key = f"search_{query}_{include_processed}_{limit}"
            if cache_key in self.cache:
                return self.cache[cache_key]
            
            # Build the query
            search_query = self.supabase.table("notes").select(
                "id, transcript, created_at, word_count, is_processed"
            )
            
            # Add text search
            search_query = search_query.text_search("transcript", f"'{query}'")
            
            # Filter by processed status if needed
            if not include_processed:
                search_query = search_query.eq("is_processed", False)
            
            # Add ordering and limit
            search_query = search_query.order("created_at", desc=True).limit(limit)
            
            response = search_query.execute()
            notes = response.data or []
            
            result = {
                "notes": notes,
                "count": len(notes),
                "query": query
            }
            
            self.cache[cache_key] = result
            logger.info(f"Search for '{query}' returned {len(notes)} notes")
            return result
            
        except Exception as e:
            logger.error(f"Error searching notes with query '{query}': {e}")
            return {"error": str(e), "notes": [], "count": 0, "query": query}
    
    async def get_inbox_stats(self) -> Dict[str, Any]:
        """Get statistics about the inbox"""
        try:
            cache_key = "inbox_stats"
            if cache_key in self.cache:
                return self.cache[cache_key]
            
            # Get unprocessed count
            unprocessed_response = (
                self.supabase
                .table("notes")
                .select("id", count="exact")
                .eq("is_processed", False)
                .eq("transcription_status", "completed")
                .execute()
            )
            
            # Get total count
            total_response = (
                self.supabase
                .table("notes")
                .select("id", count="exact")
                .execute()
            )
            
            # Get recent activity (last 7 days)
            seven_days_ago = (datetime.utcnow().replace(hour=0, minute=0, second=0, microsecond=0) 
                             - timedelta(days=7)).isoformat()
            
            recent_response = (
                self.supabase
                .table("notes")
                .select("id", count="exact")
                .gte("created_at", seven_days_ago)
                .execute()
            )
            
            stats = {
                "unprocessed_count": unprocessed_response.count or 0,
                "total_count": total_response.count or 0,
                "recent_count": recent_response.count or 0,
                "processed_count": (total_response.count or 0) - (unprocessed_response.count or 0),
                "last_updated": datetime.utcnow().isoformat()
            }
            
            self.cache[cache_key] = stats
            logger.info(f"Retrieved inbox stats: {stats}")
            return stats
            
        except Exception as e:
            logger.error(f"Error getting inbox stats: {e}")
            return {"error": str(e)}
    
    def _invalidate_note_cache(self, note_id: str):
        """Invalidate cache entries related to a specific note"""
        keys_to_remove = []
        for key in self.cache.keys():
            if key.startswith("unprocessed_") or key == f"note_{note_id}" or key == "inbox_stats":
                keys_to_remove.append(key)
        
        for key in keys_to_remove:
            self.cache.pop(key, None)

# Global server instance
voice_notes_server = VoiceNotesMCPServer()

# Initialize the MCP server
server = Server("voice-notes-mcp")

@server.list_tools()
async def handle_list_tools() -> list[Tool]:
    """List available tools"""
    return [
        Tool(
            name="list_unprocessed_notes",
            description="Get all unprocessed notes for inbox review",
            inputSchema={
                "type": "object",
                "properties": {
                    "limit": {
                        "type": "integer",
                        "description": "Maximum number of notes to return (default: 50)",
                        "default": 50
                    },
                    "offset": {
                        "type": "integer", 
                        "description": "Pagination offset (default: 0)",
                        "default": 0
                    }
                }
            }
        ),
        Tool(
            name="read_note",
            description="Read the full content of a specific note",
            inputSchema={
                "type": "object",
                "properties": {
                    "note_id": {
                        "type": "string",
                        "description": "UUID of the note to read"
                    }
                },
                "required": ["note_id"]
            }
        ),
        Tool(
            name="mark_as_processed",
            description="Mark a note as processed/reviewed",
            inputSchema={
                "type": "object",
                "properties": {
                    "note_id": {
                        "type": "string",
                        "description": "UUID of the note to mark as processed"
                    }
                },
                "required": ["note_id"]
            }
        ),
        Tool(
            name="bulk_mark_processed",
            description="Mark multiple notes as processed at once",
            inputSchema={
                "type": "object",
                "properties": {
                    "note_ids": {
                        "type": "array",
                        "items": {"type": "string"},
                        "description": "Array of note UUIDs to mark as processed"
                    }
                },
                "required": ["note_ids"]
            }
        ),
        Tool(
            name="search_notes",
            description="Search notes by keyword using full-text search",
            inputSchema={
                "type": "object",
                "properties": {
                    "query": {
                        "type": "string",
                        "description": "Search query string"
                    },
                    "include_processed": {
                        "type": "boolean",
                        "description": "Include processed notes in search (default: false)",
                        "default": False
                    },
                    "limit": {
                        "type": "integer",
                        "description": "Maximum number of results (default: 20)",
                        "default": 20
                    }
                },
                "required": ["query"]
            }
        ),
        Tool(
            name="get_inbox_stats",
            description="Get statistics about inbox (counts of processed/unprocessed notes)",
            inputSchema={
                "type": "object",
                "properties": {}
            }
        )
    ]

@server.call_tool()
async def handle_call_tool(name: str, arguments: dict | None) -> list[TextContent]:
    """Handle tool calls"""
    if arguments is None:
        arguments = {}
    
    try:
        if name == "list_unprocessed_notes":
            result = await voice_notes_server.list_unprocessed_notes(
                limit=arguments.get("limit", 50),
                offset=arguments.get("offset", 0)
            )
        elif name == "read_note":
            result = await voice_notes_server.read_note(arguments["note_id"])
        elif name == "mark_as_processed":
            result = await voice_notes_server.mark_as_processed(arguments["note_id"])
        elif name == "bulk_mark_processed":
            result = await voice_notes_server.bulk_mark_processed(arguments["note_ids"])
        elif name == "search_notes":
            result = await voice_notes_server.search_notes(
                query=arguments["query"],
                include_processed=arguments.get("include_processed", False),
                limit=arguments.get("limit", 20)
            )
        elif name == "get_inbox_stats":
            result = await voice_notes_server.get_inbox_stats()
        else:
            raise ValueError(f"Unknown tool: {name}")
        
        return [TextContent(type="text", text=json.dumps(result, indent=2, default=str))]
        
    except Exception as e:
        logger.error(f"Error handling tool call {name}: {e}")
        error_result = {"error": f"Tool execution failed: {str(e)}"}
        return [TextContent(type="text", text=json.dumps(error_result, indent=2))]

async def main():
    """Main entry point"""
    async with stdio_server() as (read_stream, write_stream):
        await server.run(
            read_stream,
            write_stream,
            InitializationOptions(
                server_name="voice-notes-mcp",
                server_version="1.0.0",
                capabilities=server.get_capabilities(
                    notification_options=NotificationOptions(),
                    experimental_capabilities={}
                )
            )
        )

if __name__ == "__main__":
    # Import timedelta here to avoid circular import issues
    from datetime import timedelta
    asyncio.run(main())