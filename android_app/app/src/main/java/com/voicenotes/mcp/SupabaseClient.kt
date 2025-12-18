package com.voicenotes.mcp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

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
    
    suspend fun addNote(transcript: String, durationSeconds: Int = 0): Boolean = withContext(Dispatchers.IO) {
        // Try a simple approach - insert without user_id and let database handle it
        val url = URL("$SUPABASE_URL/rest/v1/notes")
        val connection = url.openConnection() as HttpURLConnection
        
        val noteData = buildJsonObject {
            put("transcript", transcript)
            put("transcription_status", "completed")
            put("audio_duration_seconds", durationSeconds)
            put("user_id", "00000000-0000-0000-0000-000000000001") // Required field
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
        println("SupabaseClient: Updating note $noteId")
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
        println("SupabaseClient: Update response code: $responseCode")
        if (responseCode in 200..299) {
            true
        } else {
            val errorStream = connection.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
            println("SupabaseClient: Update failed - HTTP $responseCode: $errorStream")
            throw Exception("HTTP $responseCode: $errorStream")
        }
    }
    
    suspend fun deleteNote(noteId: String): Boolean = withContext(Dispatchers.IO) {
        println("SupabaseClient: Deleting note $noteId")
        val url = URL("$SUPABASE_URL/rest/v1/notes?id=eq.$noteId")
        val connection = url.openConnection() as HttpURLConnection
        
        connection.apply {
            requestMethod = "DELETE"
            setRequestProperty("apikey", SUPABASE_ANON_KEY)
            setRequestProperty("Authorization", "Bearer $SUPABASE_ANON_KEY")
            setRequestProperty("Content-Type", "application/json")
        }
        
        val responseCode = connection.responseCode
        println("SupabaseClient: Delete response code: $responseCode")
        if (responseCode in 200..299) {
            true
        } else {
            val errorStream = connection.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
            println("SupabaseClient: Delete failed - HTTP $responseCode: $errorStream")
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
        val url = URL("$SUPABASE_URL/rest/v1/projects?select=*&user_id=eq.00000000-0000-0000-0000-000000000001$filterParam&order=updated_at.desc")
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

            // Get note counts for each project
            projects.forEach { project ->
                val notesUrl = URL("$SUPABASE_URL/rest/v1/notes?select=id&project_id=eq.${project.id}")
                val notesConn = notesUrl.openConnection() as HttpURLConnection
                notesConn.apply {
                    requestMethod = "GET"
                    setRequestProperty("apikey", SUPABASE_ANON_KEY)
                    setRequestProperty("Authorization", "Bearer $SUPABASE_ANON_KEY")
                }
                if (notesConn.responseCode == HttpURLConnection.HTTP_OK) {
                    val notesResponse = notesConn.inputStream.bufferedReader().readText()
                    val notes = json.decodeFromString<List<Note>>(notesResponse)
                    project.note_count = notes.size
                }
            }
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
        val url = URL("$SUPABASE_URL/rest/v1/notes?select=*&user_id=eq.00000000-0000-0000-0000-000000000001&is.project_id=null&order=created_at.desc")
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