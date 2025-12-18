package com.voicenotes.mcp

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
                        projectNotes = SupabaseClient.getProjectNotes(selectedProject!!.id)
                    }
                }
            } catch (e: Exception) {
                // Handle error
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
                        icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
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

    // Create Project Dialog
    if (showCreateProject) {
        CreateProjectDialog(
            onDismiss = { showCreateProject = false },
            onCreate = { name, purpose, goal ->
                scope.launch {
                    SupabaseClient.createProject(name, purpose, goal)
                    showCreateProject = false
                    refreshData()
                }
            }
        )
    }

    // Project Picker Dialog
    if (showProjectPicker != null) {
        ProjectPickerDialog(
            projects = projects,
            onDismiss = { showProjectPicker = null },
            onSelectProject = { project ->
                scope.launch {
                    SupabaseClient.assignNoteToProject(showProjectPicker!!.id, project.id)
                    showProjectPicker = null
                    refreshData()
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
                    onValueChange = onTranscriptChange,
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
    onBack: () -> Unit,
    onArchive: () -> Unit,
    onDeleteNote: (String) -> Unit,
    onRefresh: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

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

        if (notes.isEmpty()) {
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
                    onValueChange = { name = it },
                    label = { Text("Name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = purpose,
                    onValueChange = { purpose = it },
                    label = { Text("Purpose (optional)") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = goal,
                    onValueChange = { goal = it },
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
    onSelectProject: (Project) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Assign to Project") },
        text = {
            if (projects.isEmpty()) {
                Text("No projects available. Create one first!")
            } else {
                LazyColumn {
                    items(projects.filter { !it.is_archived }) { project ->
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
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = project.name,
                                    fontWeight = FontWeight.Bold
                                )
                                if (!project.purpose.isNullOrBlank()) {
                                    Text(
                                        text = project.purpose,
                                        fontSize = 12.sp,
                                        color = Color.Gray
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
                Text("Cancel", color = Color.Black)
            }
        }
    )
}
