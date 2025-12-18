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
    val word_count: Int? = null,
    val project_id: String? = null  // NEW: Project assignment
)

@Serializable
data class Project(
    val id: String,
    val name: String,
    val purpose: String? = null,
    val goal: String? = null,
    val is_archived: Boolean = false,
    val created_at: String,
    val updated_at: String,
    var note_count: Int = 0  // Not from DB, calculated client-side
)