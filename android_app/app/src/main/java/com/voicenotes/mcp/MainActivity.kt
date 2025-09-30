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
import androidx.compose.material.icons.filled.MoreVert
import kotlinx.coroutines.delay
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceNotesApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // State management
    var notes by remember { mutableStateOf(listOf<Note>()) }
    var isRecording by remember { mutableStateOf(false) }
    var isTranscribing by remember { mutableStateOf(false) }
    var currentTranscript by remember { mutableStateOf("") }
    var isEditing by remember { mutableStateOf(false) }
    var editingNoteId by remember { mutableStateOf<String?>(null) }
    var hasPermission by remember { mutableStateOf(false) }
    
    // Audio recorder
    val audioRecorder = remember { AudioRecorder(context) }
    
    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
    }
    
    // Load notes on startup
    LaunchedEffect(Unit) {
        hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        
        scope.launch {
            try {
                notes = SupabaseClient.getNotes()
            } catch (e: Exception) {
                // Ignore errors for now
            }
        }
    }
    
    fun startRecording() {
        if (hasPermission && audioRecorder.startRecording()) {
            isRecording = true
            currentTranscript = ""
        }
    }
    
    fun stopRecording() {
        val audioFile = audioRecorder.stopRecording()
        isRecording = false
        
        if (audioFile != null) {
            scope.launch {
                isTranscribing = true
                try {
                    val transcript = OpenAIClient.transcribeAudio(audioFile)
                    // Auto-save new transcription immediately
                    val success = SupabaseClient.addNote(
                        transcript = transcript,
                        durationSeconds = 0
                    )
                    if (success) {
                        notes = SupabaseClient.getNotes()
                    }
                } catch (e: Exception) {
                    // If transcription failed, let user edit the error message
                    currentTranscript = "Transcription failed: ${e.message}"
                    isEditing = true
                } finally {
                    isTranscribing = false
                    audioFile.delete()
                }
            }
        }
    }
    
    fun saveEditedNote() {
        if (currentTranscript.isNotBlank() && editingNoteId != null) {
            scope.launch {
                try {
                    println("MainActivity: Saving edited note ${editingNoteId}")
                    val success = SupabaseClient.updateNote(editingNoteId!!, currentTranscript)
                    if (success) {
                        println("MainActivity: Edit successful, refreshing notes")
                        notes = SupabaseClient.getNotes()
                        currentTranscript = ""
                        isEditing = false
                        editingNoteId = null
                    }
                } catch (e: Exception) {
                    println("MainActivity: Edit failed - ${e.message}")
                }
            }
        }
    }
    
    fun editNote(note: Note) {
        currentTranscript = note.transcript
        editingNoteId = note.id
        isEditing = true
    }
    
    fun deleteNote(noteId: String) {
        scope.launch {
            try {
                println("MainActivity: Deleting note $noteId")
                val success = SupabaseClient.deleteNote(noteId)
                if (success) {
                    println("MainActivity: Delete successful, refreshing notes")
                    notes = SupabaseClient.getNotes()
                }
            } catch (e: Exception) {
                println("MainActivity: Delete failed - ${e.message}")
            }
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Simple header
        Text(
            text = "Voice Notes",
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
                // Large record button
                FloatingActionButton(
                    onClick = {
                        if (!hasPermission) {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        } else if (isRecording) {
                            stopRecording()
                        } else {
                            startRecording()
                        }
                    },
                    modifier = Modifier.size(100.dp),
                    shape = CircleShape,
                    containerColor = if (isRecording) Color.Black else Color.Black
                ) {
                    Text(
                        text = if (isRecording) "■" else "●",
                        fontSize = 40.sp,
                        color = if (isRecording) Color.Red else Color.White
                    )
                }
                
                Spacer(modifier = Modifier.height(20.dp))
                
                // Status text
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
                    onValueChange = { currentTranscript = it },
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
                        onClick = {
                            currentTranscript = ""
                            isEditing = false
                            editingNoteId = null
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Gray,
                            contentColor = Color.White
                        )
                    ) {
                        Text("Cancel")
                    }
                    
                    Button(
                        onClick = { saveEditedNote() },
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
        
        // Notes List (simplified)
        if (notes.isNotEmpty()) {
            Text(
                text = "Recent Notes",
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            )
            
            LazyColumn {
                items(notes) { note ->
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
                                Text(
                                    text = note.created_at.take(10),
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )
                                
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
                                        DropdownMenuItem(
                                            text = { Text("Edit") },
                                            onClick = {
                                                editNote(note)
                                                showMenu = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Delete") },
                                            onClick = {
                                                deleteNote(note.id)
                                                showMenu = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}