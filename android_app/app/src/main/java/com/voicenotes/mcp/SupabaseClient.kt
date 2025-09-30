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
}