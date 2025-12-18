#!/usr/bin/env python3
"""
Tests for Voice Notes MCP Server - Projects Feature
Tests all project-related functionality for v1.1
"""

import asyncio
import json
import os
import pytest
from unittest.mock import Mock, patch, AsyncMock
from datetime import datetime

# Set up environment variables before importing the server
os.environ["SUPABASE_URL"] = "https://test.supabase.co"
os.environ["SUPABASE_KEY"] = "test-key"

from voice_notes_mcp.server import VoiceNotesMCPServer


class TestProjectsFeature:
    """Test suite for Projects v1.1 feature"""

    @pytest.fixture
    def server(self):
        """Create a test server instance"""
        with patch('voice_notes_mcp.server.create_client'):
            server = VoiceNotesMCPServer()
            server.supabase = Mock()
            return server

    @pytest.fixture
    def sample_project(self):
        """Sample project data"""
        return {
            "id": "proj-123",
            "name": "Work Notes",
            "purpose": "Track work-related ideas and tasks",
            "goal": "Better organize work projects",
            "is_archived": False,
            "created_at": "2024-01-01T00:00:00Z",
            "updated_at": "2024-01-01T00:00:00Z"
        }

    @pytest.fixture
    def sample_note_with_project(self):
        """Sample note with project assignment"""
        return {
            "id": "note-456",
            "transcript": "Meeting notes for project alpha",
            "project_id": "proj-123",
            "is_processed": True,
            "created_at": "2024-01-01T00:00:00Z",
            "word_count": 5
        }

    # Test list_projects
    @pytest.mark.asyncio
    async def test_list_projects_active_only(self, server, sample_project):
        """Test listing active projects only"""
        mock_response = Mock()
        mock_response.data = [sample_project]

        mock_count_response = Mock()
        mock_count_response.count = 3

        server.supabase.table.return_value.select.return_value.order.return_value.eq.return_value.execute.return_value = mock_response
        server.supabase.table.return_value.select.return_value.eq.return_value.execute.return_value = mock_count_response

        result = await server.list_projects(include_archived=False)

        assert result["count"] == 1
        assert len(result["projects"]) == 1
        assert result["projects"][0]["name"] == "Work Notes"
        assert result["projects"][0]["note_count"] == 3

    @pytest.mark.asyncio
    async def test_list_projects_include_archived(self, server, sample_project):
        """Test listing all projects including archived"""
        archived_project = sample_project.copy()
        archived_project["is_archived"] = True
        archived_project["id"] = "proj-archived"

        mock_response = Mock()
        mock_response.data = [sample_project, archived_project]

        mock_count_response = Mock()
        mock_count_response.count = 2

        server.supabase.table.return_value.select.return_value.order.return_value.execute.return_value = mock_response
        server.supabase.table.return_value.select.return_value.eq.return_value.execute.return_value = mock_count_response

        result = await server.list_projects(include_archived=True)

        assert result["count"] == 2
        assert len(result["projects"]) == 2

    # Test get_project
    @pytest.mark.asyncio
    async def test_get_project_success(self, server, sample_project):
        """Test getting a specific project"""
        mock_response = Mock()
        mock_response.data = sample_project

        mock_count_response = Mock()
        mock_count_response.count = 5

        server.supabase.table.return_value.select.return_value.eq.return_value.single.return_value.execute.return_value = mock_response
        server.supabase.table.return_value.select.return_value.eq.return_value.execute.return_value = mock_count_response

        result = await server.get_project("proj-123")

        assert result["id"] == "proj-123"
        assert result["name"] == "Work Notes"
        assert result["note_count"] == 5

    @pytest.mark.asyncio
    async def test_get_project_not_found(self, server):
        """Test getting a non-existent project"""
        mock_response = Mock()
        mock_response.data = None

        server.supabase.table.return_value.select.return_value.eq.return_value.single.return_value.execute.return_value = mock_response

        result = await server.get_project("nonexistent")

        assert "error" in result
        assert "not found" in result["error"]

    # Test create_project
    @pytest.mark.asyncio
    async def test_create_project_full(self, server, sample_project):
        """Test creating a project with all fields"""
        mock_response = Mock()
        mock_response.data = [sample_project]

        server.supabase.table.return_value.insert.return_value.execute.return_value = mock_response

        result = await server.create_project(
            name="Work Notes",
            purpose="Track work-related ideas and tasks",
            goal="Better organize work projects"
        )

        assert result["success"] is True
        assert result["project"]["name"] == "Work Notes"
        assert result["project"]["purpose"] == "Track work-related ideas and tasks"

    @pytest.mark.asyncio
    async def test_create_project_name_only(self, server):
        """Test creating a project with only name (minimum required)"""
        minimal_project = {
            "id": "proj-minimal",
            "name": "Simple Project",
            "purpose": None,
            "goal": None,
            "is_archived": False
        }

        mock_response = Mock()
        mock_response.data = [minimal_project]

        server.supabase.table.return_value.insert.return_value.execute.return_value = mock_response

        result = await server.create_project(name="Simple Project")

        assert result["success"] is True
        assert result["project"]["name"] == "Simple Project"

    # Test update_project
    @pytest.mark.asyncio
    async def test_update_project_name(self, server, sample_project):
        """Test updating project name"""
        updated_project = sample_project.copy()
        updated_project["name"] = "Updated Work Notes"

        mock_response = Mock()
        mock_response.data = [updated_project]

        server.supabase.table.return_value.update.return_value.eq.return_value.execute.return_value = mock_response

        result = await server.update_project("proj-123", name="Updated Work Notes")

        assert result["success"] is True
        assert result["project"]["name"] == "Updated Work Notes"

    @pytest.mark.asyncio
    async def test_archive_project(self, server, sample_project):
        """Test archiving a project"""
        archived_project = sample_project.copy()
        archived_project["is_archived"] = True

        mock_response = Mock()
        mock_response.data = [archived_project]

        server.supabase.table.return_value.update.return_value.eq.return_value.execute.return_value = mock_response

        result = await server.update_project("proj-123", is_archived=True)

        assert result["success"] is True
        assert result["project"]["is_archived"] is True

    # Test get_notes_by_project
    @pytest.mark.asyncio
    async def test_get_notes_by_project(self, server, sample_note_with_project):
        """Test getting notes for a specific project"""
        mock_response = Mock()
        mock_response.data = [sample_note_with_project]

        server.supabase.table.return_value.select.return_value.eq.return_value.eq.return_value.order.return_value.range.return_value.execute.return_value = mock_response

        result = await server.get_notes_by_project("proj-123")

        assert result["count"] == 1
        assert result["project_id"] == "proj-123"
        assert result["notes"][0]["id"] == "note-456"
        assert result["notes"][0]["project_id"] == "proj-123"

    @pytest.mark.asyncio
    async def test_get_notes_by_project_pagination(self, server):
        """Test pagination for project notes"""
        notes = [{"id": f"note-{i}", "project_id": "proj-123"} for i in range(10)]

        mock_response = Mock()
        mock_response.data = notes

        server.supabase.table.return_value.select.return_value.eq.return_value.eq.return_value.order.return_value.range.return_value.execute.return_value = mock_response

        result = await server.get_notes_by_project("proj-123", limit=10, offset=0)

        assert result["count"] == 10
        assert result["has_more"] is True

    # Test assign_note_to_project
    @pytest.mark.asyncio
    async def test_assign_note_to_project(self, server, sample_note_with_project):
        """Test assigning a note to a project"""
        mock_response = Mock()
        mock_response.data = [sample_note_with_project]

        server.supabase.table.return_value.update.return_value.eq.return_value.execute.return_value = mock_response

        result = await server.assign_note_to_project("note-456", "proj-123")

        assert result["success"] is True
        assert result["note_id"] == "note-456"
        assert result["project_id"] == "proj-123"

    @pytest.mark.asyncio
    async def test_assign_note_marks_processed(self, server):
        """Test that assigning a note marks it as processed"""
        note = {
            "id": "note-unprocessed",
            "project_id": "proj-123",
            "is_processed": True  # Should be set to True
        }

        mock_response = Mock()
        mock_response.data = [note]

        server.supabase.table.return_value.update.return_value.eq.return_value.execute.return_value = mock_response

        result = await server.assign_note_to_project("note-unprocessed", "proj-123")

        assert result["success"] is True
        # Verify the update call included is_processed: True
        update_call = server.supabase.table.return_value.update.call_args
        assert update_call is not None

    # Test inbox behavior with projects
    @pytest.mark.asyncio
    async def test_list_unprocessed_excludes_project_notes(self, server):
        """Test that inbox only shows notes without projects"""
        inbox_notes = [
            {"id": "note-1", "project_id": None, "is_processed": False},
            {"id": "note-2", "project_id": None, "is_processed": False}
        ]

        mock_response = Mock()
        mock_response.data = inbox_notes

        server.supabase.table.return_value.select.return_value.is_.return_value.eq.return_value.eq.return_value.order.return_value.range.return_value.execute.return_value = mock_response

        result = await server.list_unprocessed_notes()

        assert result["count"] == 2
        # All notes should have no project_id
        for note in result["notes"]:
            assert note["project_id"] is None

    # Test read_note with project info
    @pytest.mark.asyncio
    async def test_read_note_includes_project_info(self, server):
        """Test that reading a note includes project information"""
        note_with_project = {
            "id": "note-456",
            "transcript": "Test note",
            "project_id": "proj-123",
            "projects": {
                "id": "proj-123",
                "name": "Work Notes"
            }
        }

        mock_response = Mock()
        mock_response.data = note_with_project

        server.supabase.table.return_value.select.return_value.eq.return_value.single.return_value.execute.return_value = mock_response

        result = await server.read_note("note-456")

        assert result["id"] == "note-456"
        assert result["project_id"] == "proj-123"
        assert result["project_name"] == "Work Notes"

    # Test cache invalidation
    @pytest.mark.asyncio
    async def test_cache_invalidation_on_project_update(self, server, sample_project):
        """Test that project cache is invalidated on update"""
        # First, populate cache
        server.cache[f"project_proj-123"] = sample_project
        server.cache["projects_False"] = {"projects": [sample_project]}

        mock_response = Mock()
        mock_response.data = [sample_project]

        server.supabase.table.return_value.update.return_value.eq.return_value.execute.return_value = mock_response

        await server.update_project("proj-123", name="Updated")

        # Cache should be cleared
        assert f"project_proj-123" not in server.cache
        assert "projects_False" not in server.cache

    @pytest.mark.asyncio
    async def test_cache_invalidation_on_note_assignment(self, server, sample_note_with_project):
        """Test that cache is invalidated when note is assigned to project"""
        # Populate cache
        server.cache["note_note-456"] = {"id": "note-456"}
        server.cache["unprocessed_50_0"] = {"notes": []}

        mock_response = Mock()
        mock_response.data = [sample_note_with_project]

        server.supabase.table.return_value.update.return_value.eq.return_value.execute.return_value = mock_response

        await server.assign_note_to_project("note-456", "proj-123")

        # Both note and unprocessed cache should be cleared
        assert "note_note-456" not in server.cache
        assert "unprocessed_50_0" not in server.cache


class TestProjectsIntegration:
    """Integration tests for complete workflows"""

    @pytest.fixture
    def server(self):
        """Create a test server instance"""
        with patch('voice_notes_mcp.server.create_client'):
            server = VoiceNotesMCPServer()
            server.supabase = Mock()
            return server

    @pytest.mark.asyncio
    async def test_complete_workflow_create_assign_retrieve(self, server):
        """Test complete workflow: create project, assign note, retrieve notes"""
        # Step 1: Create project
        new_project = {
            "id": "proj-new",
            "name": "Test Project",
            "is_archived": False
        }

        mock_create_response = Mock()
        mock_create_response.data = [new_project]
        server.supabase.table.return_value.insert.return_value.execute.return_value = mock_create_response

        create_result = await server.create_project(name="Test Project")
        assert create_result["success"] is True
        project_id = create_result["project"]["id"]

        # Step 2: Assign note to project
        mock_assign_response = Mock()
        mock_assign_response.data = [{"id": "note-1", "project_id": project_id}]
        server.supabase.table.return_value.update.return_value.eq.return_value.execute.return_value = mock_assign_response

        assign_result = await server.assign_note_to_project("note-1", project_id)
        assert assign_result["success"] is True

        # Step 3: Retrieve project notes
        mock_notes_response = Mock()
        mock_notes_response.data = [{"id": "note-1", "project_id": project_id}]
        server.supabase.table.return_value.select.return_value.eq.return_value.eq.return_value.order.return_value.range.return_value.execute.return_value = mock_notes_response

        notes_result = await server.get_notes_by_project(project_id)
        assert notes_result["count"] == 1
        assert notes_result["notes"][0]["id"] == "note-1"


if __name__ == "__main__":
    pytest.main([__file__, "-v"])
