package com.voicenotes.mcp

import kotlinx.serialization.Serializable

/**
 * Data Models for Voice Notes MCP
 *
 * This file defines the core data structures used throughout the application:
 * - Note: Represents a voice note with transcript and metadata
 * - Project: Represents a project that organizes related notes
 *
 * These models are serializable for JSON parsing with Kotlinx Serialization,
 * matching the Supabase database schema.
 *
 * Version: 1.1 (Projects feature)
 */

/**
 * Represents a single voice note
 *
 * @property id Unique UUID identifier from Supabase
 * @property transcript The text transcription of the audio recording
 * @property created_at ISO 8601 timestamp of when the note was created
 * @property modified_at ISO 8601 timestamp of last modification (nullable)
 * @property is_processed Whether the note has been reviewed/processed
 * @property audio_duration_seconds Duration of the original audio recording
 * @property transcription_status Status of transcription ("completed", "pending", etc.)
 * @property word_count Number of words in the transcript
 * @property project_id UUID of the associated project (nullable if in inbox)
 */
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
    val project_id: String? = null
)

/**
 * Represents a project that organizes voice notes
 *
 * @property id Unique UUID identifier from Supabase
 * @property name Project name (required)
 * @property purpose Why this project matters (optional)
 * @property goal What success looks like for this project (optional)
 * @property is_archived Whether the project is archived (hidden by default)
 * @property created_at ISO 8601 timestamp of creation
 * @property updated_at ISO 8601 timestamp of last update
 * @property note_count Number of notes assigned to this project (calculated client-side or from DB)
 */
@Serializable
data class Project(
    val id: String,
    val name: String,
    val purpose: String? = null,
    val goal: String? = null,
    val is_archived: Boolean = false,
    val created_at: String,
    val updated_at: String,
    var note_count: Int = 0
)