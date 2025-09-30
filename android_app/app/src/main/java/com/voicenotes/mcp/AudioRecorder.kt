package com.voicenotes.mcp

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.io.IOException

class AudioRecorder(private val context: Context) {
    private var mediaRecorder: MediaRecorder? = null
    private var audioFile: File? = null
    
    private val _recordingState = MutableStateFlow(RecordingState.IDLE)
    val recordingState: StateFlow<RecordingState> = _recordingState
    
    private val _recordingDuration = MutableStateFlow(0L)
    val recordingDuration: StateFlow<Long> = _recordingDuration
    
    enum class RecordingState {
        IDLE, RECORDING, STOPPED
    }
    
    fun startRecording(): Boolean {
        return try {
            // Create audio file
            audioFile = File(context.cacheDir, "voice_note_${System.currentTimeMillis()}.m4a")
            
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(audioFile?.absolutePath)
                
                prepare()
                start()
            }
            
            _recordingState.value = RecordingState.RECORDING
            true
        } catch (e: IOException) {
            e.printStackTrace()
            _recordingState.value = RecordingState.IDLE
            false
        }
    }
    
    fun stopRecording(): File? {
        return try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            _recordingState.value = RecordingState.STOPPED
            audioFile
        } catch (e: Exception) {
            e.printStackTrace()
            _recordingState.value = RecordingState.IDLE
            null
        }
    }
    
    fun reset() {
        mediaRecorder?.release()
        mediaRecorder = null
        _recordingState.value = RecordingState.IDLE
        _recordingDuration.value = 0L
    }
    
    fun getCurrentAudioFile(): File? = audioFile
}