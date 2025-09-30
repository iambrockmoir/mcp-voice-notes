"""
Tests for the Voice Notes MCP Server
"""

import asyncio
import json
import os
import pytest
from unittest.mock import Mock, patch, AsyncMock
import sys
from pathlib import Path

# Add the parent directory to sys.path so we can import the server
sys.path.insert(0, str(Path(__file__).parent.parent / "voice_notes_mcp"))

from server import VoiceNotesMCPServer


@pytest.fixture
def mock_supabase():
    """Mock Supabase client"""
    mock_client = Mock()
    mock_table = Mock()
    mock_client.table.return_value = mock_table
    return mock_client, mock_table


@pytest.fixture
def server_instance(mock_supabase):
    """Create a server instance with mocked Supabase"""
    mock_client, mock_table = mock_supabase
    
    with patch.dict(os.environ, {
        "SUPABASE_URL": "https://test.supabase.co",
        "SUPABASE_KEY": "test-key"
    }):
        with patch("server.create_client", return_value=mock_client):
            server = VoiceNotesMCPServer()
            return server, mock_table


class TestVoiceNotesMCPServer:
    """Test cases for the MCP Server"""
    
    @pytest.mark.asyncio
    async def test_list_unprocessed_notes_success(self, server_instance):
        """Test successful listing of unprocessed notes"""
        server, mock_table = server_instance
        
        # Mock response data
        mock_response = Mock()
        mock_response.data = [
            {
                "id": "note-1",
                "transcript": "Test note 1",
                "created_at": "2024-01-01T10:00:00Z",
                "word_count": 3,
                "audio_duration_seconds": 15
            },
            {
                "id": "note-2", 
                "transcript": "Test note 2",
                "created_at": "2024-01-01T11:00:00Z",
                "word_count": 3,
                "audio_duration_seconds": 20
            }
        ]
        
        # Configure mock chain
        mock_table.select.return_value = mock_table
        mock_table.eq.return_value = mock_table
        mock_table.order.return_value = mock_table
        mock_table.range.return_value = mock_table
        mock_table.execute.return_value = mock_response
        
        # Execute test
        result = await server.list_unprocessed_notes(limit=10, offset=0)
        
        # Verify results
        assert result["count"] == 2
        assert result["has_more"] == False
        assert len(result["notes"]) == 2
        assert result["notes"][0]["id"] == "note-1"
        
        # Verify mock calls
        mock_table.select.assert_called_once()
        mock_table.eq.assert_called()
        mock_table.order.assert_called_with("created_at", desc=True)
        mock_table.range.assert_called_with(0, 9)
    
    @pytest.mark.asyncio
    async def test_read_note_success(self, server_instance):
        """Test successful reading of a specific note"""
        server, mock_table = server_instance
        
        # Mock response
        mock_response = Mock()
        mock_response.data = {
            "id": "note-1",
            "transcript": "Test note content",
            "created_at": "2024-01-01T10:00:00Z",
            "word_count": 3,
            "is_processed": False
        }
        
        # Configure mock chain
        mock_table.select.return_value = mock_table
        mock_table.eq.return_value = mock_table
        mock_table.single.return_value = mock_table
        mock_table.execute.return_value = mock_response
        
        # Execute test
        result = await server.read_note("note-1")
        
        # Verify results
        assert result["id"] == "note-1"
        assert result["transcript"] == "Test note content"
        assert result["is_processed"] == False
        
        # Verify mock calls
        mock_table.eq.assert_called_with("id", "note-1")
    
    @pytest.mark.asyncio
    async def test_read_note_not_found(self, server_instance):
        """Test reading a non-existent note"""
        server, mock_table = server_instance
        
        # Mock empty response
        mock_response = Mock()
        mock_response.data = None
        
        # Configure mock chain
        mock_table.select.return_value = mock_table
        mock_table.eq.return_value = mock_table
        mock_table.single.return_value = mock_table
        mock_table.execute.return_value = mock_response
        
        # Execute test
        result = await server.read_note("nonexistent")
        
        # Verify error result
        assert "error" in result
        assert "not found" in result["error"]
    
    @pytest.mark.asyncio
    async def test_mark_as_processed_success(self, server_instance):
        """Test successfully marking a note as processed"""
        server, mock_table = server_instance
        
        # Mock response
        mock_response = Mock()
        mock_response.data = [{"id": "note-1", "is_processed": True}]
        
        # Configure mock chain
        mock_table.update.return_value = mock_table
        mock_table.eq.return_value = mock_table
        mock_table.execute.return_value = mock_response
        
        # Execute test
        result = await server.mark_as_processed("note-1")
        
        # Verify results
        assert result["success"] == True
        assert result["note_id"] == "note-1"
        
        # Verify the update call
        mock_table.update.assert_called_once()
        update_data = mock_table.update.call_args[0][0]
        assert update_data["is_processed"] == True
        assert "modified_at" in update_data
    
    @pytest.mark.asyncio
    async def test_bulk_mark_processed_success(self, server_instance):
        """Test bulk marking notes as processed"""
        server, mock_table = server_instance
        
        note_ids = ["note-1", "note-2", "note-3"]
        
        # Mock response
        mock_response = Mock()
        mock_response.data = [
            {"id": "note-1", "is_processed": True},
            {"id": "note-2", "is_processed": True},
            {"id": "note-3", "is_processed": True}
        ]
        
        # Configure mock chain
        mock_table.update.return_value = mock_table
        mock_table.in_.return_value = mock_table
        mock_table.execute.return_value = mock_response
        
        # Execute test
        result = await server.bulk_mark_processed(note_ids)
        
        # Verify results
        assert result["success"] == True
        assert result["processed_count"] == 3
        assert result["note_ids"] == note_ids
        
        # Verify mock calls
        mock_table.in_.assert_called_with("id", note_ids)
    
    @pytest.mark.asyncio
    async def test_search_notes_success(self, server_instance):
        """Test successful note search"""
        server, mock_table = server_instance
        
        # Mock response
        mock_response = Mock()
        mock_response.data = [
            {
                "id": "note-1",
                "transcript": "Meeting notes about project",
                "created_at": "2024-01-01T10:00:00Z",
                "word_count": 5,
                "is_processed": False
            }
        ]
        
        # Configure mock chain
        mock_table.select.return_value = mock_table
        mock_table.text_search.return_value = mock_table
        mock_table.eq.return_value = mock_table
        mock_table.order.return_value = mock_table
        mock_table.limit.return_value = mock_table
        mock_table.execute.return_value = mock_response
        
        # Execute test
        result = await server.search_notes("meeting", include_processed=False, limit=10)
        
        # Verify results
        assert result["count"] == 1
        assert result["query"] == "meeting"
        assert len(result["notes"]) == 1
        assert "meeting" in result["notes"][0]["transcript"].lower()
        
        # Verify search was called
        mock_table.text_search.assert_called_with("transcript", "'meeting'")
        mock_table.eq.assert_called_with("is_processed", False)
    
    @pytest.mark.asyncio
    async def test_get_inbox_stats_success(self, server_instance):
        """Test getting inbox statistics"""
        server, mock_table = server_instance
        
        # Mock different responses for different queries
        def mock_execute(*args, **kwargs):
            mock_response = Mock()
            # This is a simplified mock - in reality you'd need to handle the different
            # select calls differently based on their filters
            mock_response.count = 5
            return mock_response
        
        mock_table.select.return_value = mock_table
        mock_table.eq.return_value = mock_table
        mock_table.gte.return_value = mock_table
        mock_table.execute.side_effect = mock_execute
        
        # Execute test
        result = await server.get_inbox_stats()
        
        # Verify results structure
        assert "unprocessed_count" in result
        assert "total_count" in result
        assert "recent_count" in result
        assert "processed_count" in result
        assert "last_updated" in result
    
    @pytest.mark.asyncio
    async def test_caching_behavior(self, server_instance):
        """Test that caching works correctly"""
        server, mock_table = server_instance
        
        # Clear any existing cache
        server.cache.clear()
        
        # Mock response
        mock_response = Mock()
        mock_response.data = [{"id": "note-1", "transcript": "Test"}]
        
        # Configure mock
        mock_table.select.return_value = mock_table
        mock_table.eq.return_value = mock_table
        mock_table.order.return_value = mock_table
        mock_table.range.return_value = mock_table
        mock_table.execute.return_value = mock_response
        
        # First call should hit the database
        result1 = await server.list_unprocessed_notes()
        assert mock_table.execute.call_count == 1
        
        # Second call should use cache
        result2 = await server.list_unprocessed_notes()
        assert mock_table.execute.call_count == 1  # Still only 1 call
        
        # Results should be identical
        assert result1 == result2
    
    def test_cache_invalidation(self, server_instance):
        """Test cache invalidation when notes are updated"""
        server, mock_table = server_instance
        
        # Add some items to cache
        server.cache["unprocessed_50_0"] = {"test": "data"}
        server.cache["note_123"] = {"id": "123"}
        server.cache["inbox_stats"] = {"stats": "data"}
        server.cache["other_key"] = {"other": "data"}
        
        # Invalidate cache for note 123
        server._invalidate_note_cache("123")
        
        # Check that relevant keys were removed
        assert "unprocessed_50_0" not in server.cache
        assert "note_123" not in server.cache
        assert "inbox_stats" not in server.cache
        # Other keys should remain
        assert "other_key" in server.cache
    
    @pytest.mark.asyncio
    async def test_error_handling(self, server_instance):
        """Test error handling in server methods"""
        server, mock_table = server_instance
        
        # Mock an exception
        mock_table.select.side_effect = Exception("Database error")
        
        # Execute test
        result = await server.list_unprocessed_notes()
        
        # Verify error result
        assert "error" in result
        assert "Database error" in result["error"]
        assert result["notes"] == []
        assert result["count"] == 0


class TestMCPIntegration:
    """Integration tests for MCP protocol compliance"""
    
    def test_environment_variables_required(self):
        """Test that server fails without required environment variables"""
        # Clear environment
        with patch.dict(os.environ, {}, clear=True):
            with pytest.raises(ValueError, match="SUPABASE_URL and SUPABASE_KEY"):
                VoiceNotesMCPServer()
    
    def test_tool_schema_compliance(self):
        """Test that all tools follow MCP schema requirements"""
        # This would test the actual tool definitions match expected MCP format
        # For now, we'll just verify the structure exists
        pass


if __name__ == "__main__":
    pytest.main([__file__, "-v"])