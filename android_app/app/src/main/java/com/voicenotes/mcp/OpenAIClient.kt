package com.voicenotes.mcp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * OpenAIClient - Whisper API integration for speech-to-text
 *
 * Handles transcription of audio recordings using OpenAI's Whisper API.
 * Sends audio files via multipart/form-data POST request and extracts
 * the transcribed text from the JSON response.
 *
 * Features:
 * - Async transcription using Kotlin coroutines
 * - Automatic multipart form encoding
 * - Simple JSON response parsing
 * - Fallback to mock transcription if API key not configured
 *
 * Configuration:
 * - Requires OPENAI_API_KEY in android_app/local.properties
 * - Uses whisper-1 model for transcription
 *
 * Usage:
 *   val transcript = OpenAIClient.transcribeAudio(audioFile)
 *   // transcript contains the text transcription
 *
 * @see BuildConfig.OPENAI_API_KEY The API key injected at build time
 */
object OpenAIClient {
    private const val WHISPER_API_URL = "https://api.openai.com/v1/audio/transcriptions"
    
    suspend fun transcribeAudio(audioFile: File): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.OPENAI_API_KEY
        
        if (apiKey.isBlank()) {
            // Return mock transcription for testing without API key
            return@withContext "Test transcription: This is a mock transcription. Add your OpenAI API key to local.properties for real speech-to-text."
        }
        
        try {
            val boundary = "boundary${System.currentTimeMillis()}"
            val url = URL(WHISPER_API_URL)
            val connection = url.openConnection() as HttpURLConnection
            
            connection.apply {
                requestMethod = "POST"
                setRequestProperty("Authorization", "Bearer $apiKey")
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                doOutput = true
            }
            
            connection.outputStream.use { outputStream ->
                // Write multipart form data
                outputStream.write("--$boundary\r\n".toByteArray())
                outputStream.write("Content-Disposition: form-data; name=\"file\"; filename=\"audio.m4a\"\r\n".toByteArray())
                outputStream.write("Content-Type: audio/m4a\r\n\r\n".toByteArray())
                
                // Write audio file content
                audioFile.inputStream().use { fileStream ->
                    fileStream.copyTo(outputStream)
                }
                
                outputStream.write("\r\n--$boundary\r\n".toByteArray())
                outputStream.write("Content-Disposition: form-data; name=\"model\"\r\n\r\n".toByteArray())
                outputStream.write("whisper-1\r\n".toByteArray())
                outputStream.write("--$boundary--\r\n".toByteArray())
            }
            
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().readText()
                // Parse JSON response to extract just the transcription text
                try {
                    // Simple JSON parsing to extract "text" field
                    val textStart = response.indexOf("\"text\":\"") + 8
                    val textEnd = response.indexOf("\"", textStart)
                    if (textStart > 7 && textEnd > textStart) {
                        response.substring(textStart, textEnd)
                    } else {
                        response // Return full response if parsing fails
                    }
                } catch (e: Exception) {
                    response // Fallback to full response
                }
            } else {
                val errorResponse = connection.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
                "Transcription failed: HTTP $responseCode - $errorResponse"
            }
        } catch (e: Exception) {
            "Transcription error: ${e.message}"
        }
    }
}