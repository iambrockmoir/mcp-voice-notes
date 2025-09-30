package com.voicenotes.mcp

import kotlinx.serialization.Serializable

@Serializable
data class Note(
    val id: String,
    val transcript: String,
    val created_at: String,
    val modified_at: String? = null,
    val is_processed: Boolean = false,
    val audio_duration_seconds: Int? = null,
    val transcription_status: String = "completed",
    val word_count: Int? = null
)