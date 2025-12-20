package com.voicenotes.mcp

/**
 * Voice Notes MCP - Android Application
 *
 * A voice recording and transcription app with project organization.
 * Records audio, transcribes with OpenAI Whisper, stores in Supabase,
 * and syncs with Claude Desktop via MCP server.
 *
 * Main Features (v1.1):
 * - Record voice notes with one-tap interface
 * - Automatic AI transcription (OpenAI Whisper)
 * - Edit transcripts inline
 * - Organize notes into projects
 * - Inbox workflow for unprocessed notes
 * - Project management (create, edit, archive)
 * - Real-time cloud sync with Supabase
 *
 * UI Structure:
 * - 3 tabs: Inbox, Projects, Settings
 * - Black & white minimalist Material 3 design
 * - Loading states for all async operations
 * - Inline project creation from inbox
 *
 * Architecture:
 * - Jetpack Compose for UI (declarative, reactive)
 * - Kotlin Coroutines for async operations
 * - State hoisting pattern for data flow
 * - MVVM-like structure (state in MainActivity)
 *
 * Components:
 * - MainActivity: Main entry point and state management (~1100 lines)
 * - Screen composables: InboxScreen, ProjectsScreen, SettingsScreen
 * - Dialog composables: ProjectPickerDialog, CreateProjectDialog
 * - Card composables: NoteCard, ProjectCard for list items
 *
 * Data Flow:
 * 1. User records audio → AudioRecorder
 * 2. Audio sent to Whisper API → OpenAIClient
 * 3. Transcript saved to Supabase → SupabaseClient
 * 4. UI refreshes with new note → State update
 * 5. User assigns to project → Updates project_id
 *
 * Recent UX Improvements (2024):
 * - Projects load automatically in assign dialog
 * - Loading indicator when opening project
 * - Inline project creation (no separate dialog)
 * - Scrollable project list for many projects
 *
 * @see SupabaseClient for database operations
 * @see OpenAIClient for transcription
 * @see AudioRecorder for audio recording
 * @see Note and Project data models
 */

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color.Black,
                    onPrimary = Color.White,
                    secondary = Color.Gray,
                    onSecondary = Color.White,
                    surface = Color.White,
                    onSurface = Color.Black,
                    background = Color.White,
                    onBackground = Color.Black,
                    outline = Color.Gray
                )
            ) {
                VoiceNotesApp()
            }
        }
    }
}

enum class Screen {
    INBOX,
    PROJECTS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceNotesApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Navigation state
    var currentScreen by remember { mutableStateOf(Screen.INBOX) }
    var selectedProject by remember { mutableStateOf<Project?>(null) }

    // Data state
    var inboxNotes by remember { mutableStateOf(listOf<Note>()) }
    var projects by remember { mutableStateOf(listOf<Project>()) }
    var projectNotes by remember { mutableStateOf(listOf<Note>()) }

    // UI state
    var isRecording by remember { mutableStateOf(false) }
    var isTranscribing by remember { mutableStateOf(false) }
    var hasPermission by remember { mutableStateOf(false) }
    var showCreateProject by remember { mutableStateOf(false) }
    var showProjectPicker by remember { mutableStateOf<Note?>(null) }
    var isEditing by remember { mutableStateOf(false) }
    var editingNoteId by remember { mutableStateOf<String?>(null) }
    var currentTranscript by remember { mutableStateOf("") }
    var isLoadingProjectNotes by remember { mutableStateOf(false) }

    val audioRecorder = remember { AudioRecorder(context) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
    }

    // Load data on startup and screen change
    LaunchedEffect(currentScreen, selectedProject) {
        hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        scope.launch {
            try {
                if (currentScreen == Screen.INBOX) {
                    inboxNotes = SupabaseClient.getInboxNotes()
                } else if (currentScreen == Screen.PROJECTS) {
                    projects = SupabaseClient.getProjects()
                    if (selectedProject != null) {
                        isLoadingProjectNotes = true
                        projectNotes = SupabaseClient.getProjectNotes(selectedProject!!.id)
                        isLoadingProjectNotes = false
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                isLoadingProjectNotes = false
            }
        }
    }

    // Load projects when assign dialog opens
    LaunchedEffect(showProjectPicker) {
        if (showProjectPicker != null && projects.isEmpty()) {
            scope.launch {
                try {
                    projects = SupabaseClient.getProjects()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun refreshData() {
        scope.launch {
            try {
                if (currentScreen == Screen.INBOX) {
                    inboxNotes = SupabaseClient.getInboxNotes()
                } else if (currentScreen == Screen.PROJECTS) {
                    projects = SupabaseClient.getProjects()
                    if (selectedProject != null) {
                        projectNotes = SupabaseClient.getProjectNotes(selectedProject!!.id)
                    }
                }
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    Scaffold(
        bottomBar = {
            if (selectedProject == null) {
                NavigationBar(
                    containerColor = Color.White,
                    contentColor = Color.Black
                ) {
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Email, contentDescription = "Inbox") },
                        label = { Text("Inbox") },
                        selected = currentScreen == Screen.INBOX,
                        onClick = { currentScreen = Screen.INBOX }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Star, contentDescription = "Projects") },
                        label = { Text("Projects") },
                        selected = currentScreen == Screen.PROJECTS,
                        onClick = { currentScreen = Screen.PROJECTS }
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            when {
                selectedProject != null -> {
                    ProjectDetailScreen(
                        project = selectedProject!!,
                        notes = projectNotes,
                        isLoading = isLoadingProjectNotes,
                        isRecording = isRecording,
                        isTranscribing = isTranscribing,
                        hasPermission = hasPermission,
                        onStartRecording = {
                            if (audioRecorder.startRecording()) {
                                isRecording = true
                            }
                        },
                        onStopRecording = {
                            val audioFile = audioRecorder.stopRecording()
                            isRecording = false
                            if (audioFile != null) {
                                scope.launch {
                                    isTranscribing = true
                                    try {
                                        val transcript = OpenAIClient.transcribeAudio(audioFile)
                                        SupabaseClient.addNote(transcript, 0, selectedProject!!.id)
                                        refreshData()
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    } finally {
                                        isTranscribing = false
                                        audioFile.delete()
                                    }
                                }
                            }
                        },
                        onRequestPermission = {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        },
                        onBack = {
                            selectedProject = null
                            refreshData()
                        },
                        onArchive = {
                            scope.launch {
                                SupabaseClient.updateProject(selectedProject!!.id, isArchived = true)
                                selectedProject = null
                                refreshData()
                            }
                        },
                        onDeleteNote = { noteId ->
                            scope.launch {
                                SupabaseClient.deleteNote(noteId)
                                refreshData()
                            }
                        },
                        onRefresh = { refreshData() }
                    )
                }
                currentScreen == Screen.INBOX -> {
                    InboxScreen(
                        notes = inboxNotes,
                        isRecording = isRecording,
                        isTranscribing = isTranscribing,
                        hasPermission = hasPermission,
                        audioRecorder = audioRecorder,
                        isEditing = isEditing,
                        editingNoteId = editingNoteId,
                        currentTranscript = currentTranscript,
                        onTranscriptChange = { currentTranscript = it },
                        onRequestPermission = {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        },
                        onStartRecording = {
                            if (audioRecorder.startRecording()) {
                                isRecording = true
                                currentTranscript = ""
                            }
                        },
                        onStopRecording = {
                            val audioFile = audioRecorder.stopRecording()
                            isRecording = false

                            if (audioFile != null) {
                                scope.launch {
                                    isTranscribing = true
                                    try {
                                        val transcript = OpenAIClient.transcribeAudio(audioFile)
                                        SupabaseClient.addNote(transcript, 0)
                                        refreshData()
                                    } catch (e: Exception) {
                                        currentTranscript = "Transcription failed: ${e.message}"
                                        isEditing = true
                                    } finally {
                                        isTranscribing = false
                                        audioFile.delete()
                                    }
                                }
                            }
                        },
                        onSaveEdit = {
                            if (currentTranscript.isNotBlank() && editingNoteId != null) {
                                scope.launch {
                                    SupabaseClient.updateNote(editingNoteId!!, currentTranscript)
                                    currentTranscript = ""
                                    isEditing = false
                                    editingNoteId = null
                                    refreshData()
                                }
                            }
                        },
                        onCancelEdit = {
                            currentTranscript = ""
                            isEditing = false
                            editingNoteId = null
                        },
                        onEditNote = { note ->
                            currentTranscript = note.transcript
                            editingNoteId = note.id
                            isEditing = true
                        },
                        onDeleteNote = { noteId ->
                            scope.launch {
                                SupabaseClient.deleteNote(noteId)
                                refreshData()
                            }
                        },
                        onAssignToProject = { note ->
                            showProjectPicker = note
                        }
                    )
                }
                currentScreen == Screen.PROJECTS -> {
                    ProjectsListScreen(
                        projects = projects,
                        onProjectClick = { project ->
                            selectedProject = project
                        },
                        onCreateProject = {
                            showCreateProject = true
                        }
                    )
                }
            }
        }
    }

    // Project Picker Dialog
    if (showProjectPicker != null) {
        ProjectPickerDialog(
            projects = projects,
            onDismiss = { showProjectPicker = null },
            onSelectProject = { project ->
                scope.launch {
                    try {
                        SupabaseClient.assignNoteToProject(showProjectPicker!!.id, project.id)
                        showProjectPicker = null
                        refreshData()
                    } catch (e: Exception) {
                        e.printStackTrace()
                        showProjectPicker = null
                    }
                }
            },
            onCreateProject = { projectName ->
                // Create project inline with just the name
                val noteToAssign = showProjectPicker
                scope.launch {
                    try {
                        val success = SupabaseClient.createProject(projectName, null, null)
                        if (success) {
                            // Refresh projects list to get the newly created project
                            projects = SupabaseClient.getProjects()

                            // Assign the note to the new project
                            if (noteToAssign != null) {
                                val newProject = projects.firstOrNull { it.name == projectName }
                                if (newProject != null) {
                                    SupabaseClient.assignNoteToProject(noteToAssign.id, newProject.id)
                                    showProjectPicker = null
                                }
                            }
                            refreshData()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        )
    }

    // Create Project Dialog (can be shown from Projects tab or from note assignment)
    if (showCreateProject) {
        val noteToAssign = showProjectPicker // Save reference before creating project
        CreateProjectDialog(
            onDismiss = {
                showCreateProject = false
                // Don't clear showProjectPicker here - user might want to pick again
            },
            onCreate = { name, purpose, goal ->
                scope.launch {
                    try {
                        val success = SupabaseClient.createProject(name, purpose, goal)
                        if (success) {
                            // Refresh projects list to get the newly created project
                            projects = SupabaseClient.getProjects()

                            // If we were in the note assignment flow, assign the note to the new project
                            if (noteToAssign != null) {
                                // Find the newly created project (it should be first in the list)
                                val newProject = projects.firstOrNull { it.name == name }
                                if (newProject != null) {
                                    SupabaseClient.assignNoteToProject(noteToAssign.id, newProject.id)
                                    showProjectPicker = null // Clear the note assignment flow
                                }
                            }

                            showCreateProject = false
                            refreshData()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        showCreateProject = false
                    }
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(
    notes: List<Note>,
    isRecording: Boolean,
    isTranscribing: Boolean,
    hasPermission: Boolean,
    audioRecorder: AudioRecorder,
    isEditing: Boolean,
    editingNoteId: String?,
    currentTranscript: String,
    onTranscriptChange: (String) -> Unit,
    onRequestPermission: () -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onSaveEdit: () -> Unit,
    onCancelEdit: () -> Unit,
    onEditNote: (Note) -> Unit,
    onDeleteNote: (String) -> Unit,
    onAssignToProject: (Note) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Inbox",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        // Recording Interface
        if (!isEditing) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(bottom = 32.dp)
            ) {
                FloatingActionButton(
                    onClick = {
                        if (!hasPermission) {
                            onRequestPermission()
                        } else if (isRecording) {
                            onStopRecording()
                        } else {
                            onStartRecording()
                        }
                    },
                    modifier = Modifier.size(100.dp),
                    shape = CircleShape,
                    containerColor = Color.Black
                ) {
                    Text(
                        text = if (isRecording) "■" else "●",
                        fontSize = 40.sp,
                        color = if (isRecording) Color.Red else Color.White
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = when {
                        !hasPermission -> "Tap to allow microphone"
                        isRecording -> "Recording... tap to stop"
                        isTranscribing -> "Transcribing..."
                        else -> "Tap to record"
                    },
                    fontSize = 18.sp,
                    color = if (isRecording) Color.Red else Color.Black
                )

                if (isTranscribing) {
                    Spacer(modifier = Modifier.height(20.dp))
                    CircularProgressIndicator(color = Color.Black)
                }
            }
        }

        // Editing Interface
        if (isEditing) {
            Column(modifier = Modifier.padding(bottom = 32.dp)) {
                OutlinedTextField(
                    value = currentTranscript,
                    onValueChange = {
                        onTranscriptChange(it)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp),
                    placeholder = { Text("Edit your note...") },
                    maxLines = 6,
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(
                        onClick = onCancelEdit,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Gray,
                            contentColor = Color.White
                        )
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = onSaveEdit,
                        enabled = currentTranscript.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Black,
                            contentColor = Color.White
                        )
                    ) {
                        Text("Save")
                    }
                }
            }
        }

        // Notes List
        if (notes.isNotEmpty()) {
            Text(
                text = "Unprocessed Notes",
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            )

            LazyColumn {
                items(notes) { note ->
                    NoteCard(
                        note = note,
                        onEdit = { onEditNote(note) },
                        onDelete = { onDeleteNote(note.id) },
                        onAssignToProject = { onAssignToProject(note) },
                        showAssignOption = true
                    )
                }
            }
        } else if (!isEditing && !isTranscribing) {
            Text(
                text = "All caught up! No notes to process.",
                fontSize = 16.sp,
                color = Color.Gray,
                modifier = Modifier.padding(top = 32.dp)
            )
        }
    }
}

@Composable
fun NoteCard(
    note: Note,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onAssignToProject: (() -> Unit)? = null,
    showAssignOption: Boolean = false
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White,
            contentColor = Color.Black
        ),
        border = BorderStroke(1.dp, Color.Black)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = note.transcript,
                fontSize = 16.sp,
                color = Color.Black,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = note.created_at.take(10),
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                    if (note.word_count != null) {
                        Text(
                            text = "${note.word_count} words",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }
                }

                Box {
                    IconButton(
                        onClick = { showMenu = !showMenu }
                    ) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "More options",
                            tint = Color.Black
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        if (showAssignOption && onAssignToProject != null) {
                            DropdownMenuItem(
                                text = { Text("Assign to Project") },
                                onClick = {
                                    onAssignToProject()
                                    showMenu = false
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Star, contentDescription = null)
                                }
                            )
                            HorizontalDivider()
                        }
                        DropdownMenuItem(
                            text = { Text("Edit") },
                            onClick = {
                                onEdit()
                                showMenu = false
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Edit, contentDescription = null)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            onClick = {
                                onDelete()
                                showMenu = false
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Delete, contentDescription = null)
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectsListScreen(
    projects: List<Project>,
    onProjectClick: (Project) -> Unit,
    onCreateProject: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Projects",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold
            )

            FloatingActionButton(
                onClick = onCreateProject,
                containerColor = Color.Black,
                contentColor = Color.White,
                modifier = Modifier.size(56.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "New Project")
            }
        }

        if (projects.isEmpty()) {
            Text(
                text = "Create your first project to start organizing.",
                fontSize = 16.sp,
                color = Color.Gray,
                modifier = Modifier.padding(top = 32.dp)
            )
        } else {
            LazyColumn {
                items(projects) { project ->
                    ProjectCard(
                        project = project,
                        onClick = { onProjectClick(project) }
                    )
                }
            }
        }
    }
}

@Composable
fun ProjectCard(
    project: Project,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White,
            contentColor = Color.Black
        ),
        border = BorderStroke(2.dp, Color.Black)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = project.name,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "${project.note_count} notes",
                    fontSize = 14.sp,
                    color = Color.Gray
                )
            }

            if (!project.purpose.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = project.purpose,
                    fontSize = 14.sp,
                    color = Color.DarkGray
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Updated ${project.updated_at.take(10)}",
                fontSize = 12.sp,
                color = Color.Gray
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectDetailScreen(
    project: Project,
    notes: List<Note>,
    isLoading: Boolean = false,
    isRecording: Boolean = false,
    isTranscribing: Boolean = false,
    hasPermission: Boolean = true,
    onStartRecording: () -> Unit = {},
    onStopRecording: () -> Unit = {},
    onRequestPermission: () -> Unit = {},
    onBack: () -> Unit,
    onArchive: () -> Unit,
    onDeleteNote: (String) -> Unit,
    onRefresh: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
        ) {
        // Header with back button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.Black)
            }

            Box {
                IconButton(onClick = { showMenu = !showMenu }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = Color.Black)
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Archive Project") },
                        onClick = {
                            onArchive()
                            showMenu = false
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Delete, contentDescription = null)
                        }
                    )
                }
            }
        }

        // Project Info
        Text(
            text = project.name,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (!project.purpose.isNullOrBlank()) {
            Text(
                text = project.purpose,
                fontSize = 16.sp,
                color = Color.DarkGray,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        if (!project.goal.isNullOrBlank()) {
            Text(
                text = "Goal: ${project.goal}",
                fontSize = 14.sp,
                color = Color.Gray,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

        // Notes Section
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Notes (${notes.size})",
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium
            )
        }

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color.Black)
            }
        } else if (notes.isEmpty()) {
            Text(
                text = "No notes yet. Record one or assign from inbox.",
                fontSize = 16.sp,
                color = Color.Gray,
                modifier = Modifier.padding(top = 32.dp)
            )
        } else {
            LazyColumn {
                items(notes) { note ->
                    NoteCard(
                        note = note,
                        onEdit = { /* Could implement editing here */ },
                        onDelete = { onDeleteNote(note.id) },
                        showAssignOption = false
                    )
                }
            }
        }
        }

        // Recording FAB in bottom-right corner
        FloatingActionButton(
            onClick = {
                when {
                    !hasPermission -> onRequestPermission()
                    isRecording -> onStopRecording()
                    !isTranscribing -> onStartRecording()
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .size(56.dp),
            shape = CircleShape,
            containerColor = if (isRecording) Color.Red else Color.Black
        ) {
            if (isTranscribing) {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Text(
                    text = if (isRecording) "■" else "●",
                    fontSize = 20.sp,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
fun CreateProjectDialog(
    onDismiss: () -> Unit,
    onCreate: (String, String?, String?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var purpose by remember { mutableStateOf("") }
    var goal by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Project") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                    },
                    label = { Text("Name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = purpose,
                    onValueChange = {
                        purpose = it
                    },
                    label = { Text("Purpose (optional)") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = goal,
                    onValueChange = {
                        goal = it
                    },
                    label = { Text("Goal (optional)") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onCreate(
                            name,
                            purpose.ifBlank { null },
                            goal.ifBlank { null }
                        )
                    } else {
                    }
                },
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Black,
                    contentColor = Color.White
                )
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.Black)
            }
        }
    )
}

@Composable
fun ProjectPickerDialog(
    projects: List<Project>,
    onDismiss: () -> Unit,
    onSelectProject: (Project) -> Unit,
    onCreateProject: (String) -> Unit
) {
    var newProjectName by remember { mutableStateOf("") }
    var showCreateField by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Assign to Project") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
            ) {
                // Inline project creation
                if (showCreateField) {
                    OutlinedTextField(
                        value = newProjectName,
                        onValueChange = { newProjectName = it },
                        label = { Text("Project Name") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(
                            onClick = {
                                if (newProjectName.isNotBlank()) {
                                    onCreateProject(newProjectName)
                                }
                            },
                            enabled = newProjectName.isNotBlank()
                        ) {
                            Text("Create", color = if (newProjectName.isNotBlank()) Color.Black else Color.Gray)
                        }
                        TextButton(onClick = {
                            showCreateField = false
                            newProjectName = ""
                        }) {
                            Text("Cancel", color = Color.Black)
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                } else {
                    TextButton(
                        onClick = { showCreateField = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = "Create New",
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text("Create New Project")
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                }

                // Scrollable project list
                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    val activeProjects = projects.filter { !it.is_archived }
                    if (activeProjects.isEmpty()) {
                        item {
                            Text(
                                "No projects yet",
                                color = Color.Gray,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    } else {
                        items(activeProjects) { project ->
                            Card(
                                onClick = { onSelectProject(project) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = Color.White
                                ),
                                border = BorderStroke(1.dp, Color.Black)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = project.name,
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 14.sp
                                        )
                                        if (!project.purpose.isNullOrBlank()) {
                                            Text(
                                                text = project.purpose,
                                                fontSize = 11.sp,
                                                color = Color.Gray,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                    Text(
                                        text = "${project.note_count}",
                                        fontSize = 12.sp,
                                        color = Color.Gray,
                                        modifier = Modifier.padding(start = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = Color.Black)
            }
        }
    )
}
