package com.voicenotes.mcp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * SupabaseClient - Database operations for Voice Notes MCP
 *
 * Provides all database interactions with Supabase PostgreSQL backend.
 * Implements CRUD operations for both notes and projects (v1.1).
 *
 * Architecture:
 * - Uses Supabase REST API (not the Kotlin SDK)
 * - All operations are async using Kotlin coroutines (Dispatchers.IO)
 * - Returns domain models (Note, Project) deserialized from JSON
 * - Uses anon key for client-side operations (RLS protected)
 *
 * API Sections:
 * 1. Notes Operations (lines 23-127):
 *    - getNotes() - Get all notes for user
 *    - addNote() - Create new note with transcript
 *    - updateNote() - Edit existing note
 *    - deleteNote() - Remove note
 *    - testConnection() - Verify Supabase connectivity
 *
 * 2. Projects Operations (lines 138-224):
 *    - getProjects() - List projects with note counts
 *    - createProject() - Create new project
 *    - updateProject() - Edit project or archive
 *    - getInboxNotes() - Notes without projects (inbox view)
 *    - getProjectNotes() - Notes assigned to specific project
 *    - assignNoteToProject() - Move note from inbox to project
 *
 * Configuration:
 * - Requires SUPABASE_URL and SUPABASE_ANON_KEY in local.properties
 * - Uses hardcoded user_id for demo purposes (multi-user in future)
 *
 * Error Handling:
 * - Throws exceptions on HTTP errors
 * - Returns false on operation failure
 * - Errors include HTTP status and response body
 *
 * @see Note Data class for note structure
 * @see Project Data class for project structure
 */
object SupabaseClient {
    private val SUPABASE_URL = BuildConfig.SUPABASE_URL
    private val SUPABASE_ANON_KEY = BuildConfig.SUPABASE_ANON_KEY
    
    private val json = Json { 
        ignoreUnknownKeys = true 
        encodeDefaults = false
    }
    
    suspend fun getNotes(): List<Note> = withContext(Dispatchers.IO) {
        val url = URL("$SUPABASE_URL/rest/v1/notes?select=*&user_id=eq.00000000-0000-0000-0000-000000000001&order=created_at.desc")
        val connection = url.openConnection() as HttpURLConnection
        
        connection.apply {
            requestMethod = "GET"
            setRequestProperty("apikey", SUPABASE_ANON_KEY)
            setRequestProperty("Authorization", "Bearer $SUPABASE_ANON_KEY")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Prefer", "return=representation")
        }
        
        val responseCode = connection.responseCode
        if (responseCode == HttpURLConnection.HTTP_OK) {
            val response = connection.inputStream.bufferedReader().readText()
            json.decodeFromString<List<Note>>(response)
        } else {
            val errorStream = connection.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
            throw Exception("HTTP $responseCode: $errorStream")
        }
    }
    
    suspend fun addNote(transcript: String, durationSeconds: Int = 0, projectId: String? = null): Boolean = withContext(Dispatchers.IO) {
        val url = URL("$SUPABASE_URL/rest/v1/notes")
        val connection = url.openConnection() as HttpURLConnection

        val noteData = buildJsonObject {
            put("transcript", transcript)
            put("transcription_status", "completed")
            put("audio_duration_seconds", durationSeconds)
            put("user_id", "00000000-0000-0000-0000-000000000001")
            if (projectId != null) {
                put("project_id", projectId)
                put("is_processed", true)
            }
        }
        
        connection.apply {
            requestMethod = "POST"
            setRequestProperty("apikey", SUPABASE_ANON_KEY)
            setRequestProperty("Authorization", "Bearer $SUPABASE_ANON_KEY")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Prefer", "return=minimal")
            doOutput = true
        }
        
        connection.outputStream.use { os ->
            os.write(noteData.toString().toByteArray())
        }
        
        val responseCode = connection.responseCode
        if (responseCode in 200..299) {
            true
        } else {
            val errorStream = connection.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
            throw Exception("HTTP $responseCode: $errorStream")
        }
    }
    
    suspend fun updateNote(noteId: String, newTranscript: String): Boolean = withContext(Dispatchers.IO) {
        val url = URL("$SUPABASE_URL/rest/v1/notes?id=eq.$noteId")
        val connection = url.openConnection() as HttpURLConnection
        
        val updateData = buildJsonObject {
            put("transcript", newTranscript)
        }
        
        connection.apply {
            requestMethod = "PATCH"
            setRequestProperty("apikey", SUPABASE_ANON_KEY)
            setRequestProperty("Authorization", "Bearer $SUPABASE_ANON_KEY")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Prefer", "return=minimal")
            doOutput = true
        }
        
        connection.outputStream.use { os ->
            os.write(updateData.toString().toByteArray())
        }
        
        val responseCode = connection.responseCode
        if (responseCode in 200..299) {
            true
        } else {
            val errorStream = connection.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
            throw Exception("HTTP $responseCode: $errorStream")
        }
    }
    
    suspend fun deleteNote(noteId: String): Boolean = withContext(Dispatchers.IO) {
        val url = URL("$SUPABASE_URL/rest/v1/notes?id=eq.$noteId")
        val connection = url.openConnection() as HttpURLConnection
        
        connection.apply {
            requestMethod = "DELETE"
            setRequestProperty("apikey", SUPABASE_ANON_KEY)
            setRequestProperty("Authorization", "Bearer $SUPABASE_ANON_KEY")
            setRequestProperty("Content-Type", "application/json")
        }
        
        val responseCode = connection.responseCode
        if (responseCode in 200..299) {
            true
        } else {
            val errorStream = connection.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
            throw Exception("HTTP $responseCode: $errorStream")
        }
    }
    
    suspend fun testConnection(): String = withContext(Dispatchers.IO) {
        try {
            val notes = getNotes()
            "✅ Connected! Found ${notes.size} notes"
        } catch (e: Exception) {
            "❌ Connection failed: ${e.message}"
        }
    }

    // ============ PROJECT METHODS (v1.1) ============

    suspend fun getProjects(includeArchived: Boolean = false): List<Project> = withContext(Dispatchers.IO) {
        val filterParam = if (includeArchived) "" else "&is_archived=eq.false"
        val url = URL("$SUPABASE_URL/rest/v1/projects?select=id,user_id,name,purpose,goal,is_archived,created_at,updated_at,note_count&user_id=eq.00000000-0000-0000-0000-000000000001$filterParam&order=updated_at.desc")
        val connection = url.openConnection() as HttpURLConnection

        connection.apply {
            requestMethod = "GET"
            setRequestProperty("apikey", SUPABASE_ANON_KEY)
            setRequestProperty("Authorization", "Bearer $SUPABASE_ANON_KEY")
            setRequestProperty("Content-Type", "application/json")
        }

        val responseCode = connection.responseCode
        if (responseCode == HttpURLConnection.HTTP_OK) {
            val response = connection.inputStream.bufferedReader().readText()
            val projects = json.decodeFromString<List<Project>>(response)
            // Note counts are now stored in the database and auto-updated by triggers
            projects
        } else {
            val errorStream = connection.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
            throw Exception("HTTP $responseCode: $errorStream")
        }
    }

    suspend fun createProject(name: String, purpose: String? = null, goal: String? = null): Boolean = withContext(Dispatchers.IO) {
        val url = URL("$SUPABASE_URL/rest/v1/projects")
        val connection = url.openConnection() as HttpURLConnection

        val projectData = buildJsonObject {
            put("name", name)
            if (purpose != null) put("purpose", purpose)
            if (goal != null) put("goal", goal)
            put("is_archived", false)
            put("user_id", "00000000-0000-0000-0000-000000000001")
        }


        connection.apply {
            requestMethod = "POST"
            setRequestProperty("apikey", SUPABASE_ANON_KEY)
            setRequestProperty("Authorization", "Bearer $SUPABASE_ANON_KEY")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Prefer", "return=minimal")
            doOutput = true
        }

        connection.outputStream.use { os ->
            os.write(projectData.toString().toByteArray())
        }

        val responseCode = connection.responseCode
        if (responseCode !in 200..299) {
            val errorStream = connection.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
        }
        responseCode in 200..299
    }

    suspend fun updateProject(projectId: String, name: String? = null, purpose: String? = null,
                             goal: String? = null, isArchived: Boolean? = null): Boolean = withContext(Dispatchers.IO) {
        val url = URL("$SUPABASE_URL/rest/v1/projects?id=eq.$projectId")
        val connection = url.openConnection() as HttpURLConnection

        val updateData = buildJsonObject {
            if (name != null) put("name", name)
            if (purpose != null) put("purpose", purpose)
            if (goal != null) put("goal", goal)
            if (isArchived != null) put("is_archived", isArchived)
        }

        connection.apply {
            requestMethod = "PATCH"
            setRequestProperty("apikey", SUPABASE_ANON_KEY)
            setRequestProperty("Authorization", "Bearer $SUPABASE_ANON_KEY")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Prefer", "return=minimal")
            doOutput = true
        }

        connection.outputStream.use { os ->
            os.write(updateData.toString().toByteArray())
        }

        val responseCode = connection.responseCode
        responseCode in 200..299
    }

    suspend fun getInboxNotes(): List<Note> = withContext(Dispatchers.IO) {
        // Show ALL notes without a project, regardless of is_processed status
        val url = URL("$SUPABASE_URL/rest/v1/notes?select=*&user_id=eq.00000000-0000-0000-0000-000000000001&project_id=is.null&order=created_at.desc")
        val connection = url.openConnection() as HttpURLConnection


        connection.apply {
            requestMethod = "GET"
            setRequestProperty("apikey", SUPABASE_ANON_KEY)
            setRequestProperty("Authorization", "Bearer $SUPABASE_ANON_KEY")
            setRequestProperty("Content-Type", "application/json")
        }

        val responseCode = connection.responseCode

        if (responseCode == HttpURLConnection.HTTP_OK) {
            val response = connection.inputStream.bufferedReader().readText()
            val notes = json.decodeFromString<List<Note>>(response)
            notes
        } else {
            val errorStream = connection.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
            throw Exception("HTTP $responseCode: $errorStream")
        }
    }

    suspend fun getProjectNotes(projectId: String): List<Note> = withContext(Dispatchers.IO) {
        val url = URL("$SUPABASE_URL/rest/v1/notes?select=*&project_id=eq.$projectId&order=created_at.desc")
        val connection = url.openConnection() as HttpURLConnection

        connection.apply {
            requestMethod = "GET"
            setRequestProperty("apikey", SUPABASE_ANON_KEY)
            setRequestProperty("Authorization", "Bearer $SUPABASE_ANON_KEY")
            setRequestProperty("Content-Type", "application/json")
        }

        val responseCode = connection.responseCode
        if (responseCode == HttpURLConnection.HTTP_OK) {
            val response = connection.inputStream.bufferedReader().readText()
            json.decodeFromString<List<Note>>(response)
        } else {
            val errorStream = connection.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
            throw Exception("HTTP $responseCode: $errorStream")
        }
    }

    suspend fun assignNoteToProject(noteId: String, projectId: String): Boolean = withContext(Dispatchers.IO) {
        val url = URL("$SUPABASE_URL/rest/v1/notes?id=eq.$noteId")
        val connection = url.openConnection() as HttpURLConnection

        val updateData = buildJsonObject {
            put("project_id", projectId)
            put("is_processed", true)
        }

        connection.apply {
            requestMethod = "PATCH"
            setRequestProperty("apikey", SUPABASE_ANON_KEY)
            setRequestProperty("Authorization", "Bearer $SUPABASE_ANON_KEY")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Prefer", "return=minimal")
            doOutput = true
        }

        connection.outputStream.use { os ->
            os.write(updateData.toString().toByteArray())
        }

        val responseCode = connection.responseCode
        responseCode in 200..299
    }
}