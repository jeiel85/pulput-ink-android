package com.example.ui.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.db.AppDatabase
import com.example.data.model.SermonJob
import com.example.data.model.SermonSegment
import com.example.data.repository.SermonRepository
import com.example.ui.audio.AudioEngine
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File

class SermonViewModel(
    private val context: Context,
    private val repository: SermonRepository
) : ViewModel() {

    private val TAG = "SermonViewModel"
    private val audioEngine = AudioEngine(context)

    // Search query states
    val searchQuery = MutableStateFlow("")

    // Raw sermons from Database
    private val _allJobs = repository.allJobs

    // Filtered sermons combined with query
    val sermonJobs: StateFlow<List<SermonJob>> = _allJobs
        .combine(searchQuery) { jobs, query ->
            if (query.isBlank()) {
                jobs
            } else {
                jobs.filter {
                    it.title.contains(query, ignoreCase = true) ||
                    (it.summary?.contains(query, ignoreCase = true) ?: false) ||
                    (it.keywords?.contains(query, ignoreCase = true) ?: false)
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Active Sermon detailed navigation
    private val _activeJobId = MutableStateFlow<String?>(null)
    val activeJobId: StateFlow<String?> = _activeJobId.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val activeSermonJob: StateFlow<SermonJob?> = _activeJobId
        .flatMapLatest { id ->
            if (id == null) flowOf(null)
            else repository.getJobByIdFlow(id)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val activeSermonSegments: StateFlow<List<SermonSegment>> = _activeJobId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList())
            else repository.getSegmentsFlow(id)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Recording States
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _recordingDurationSec = MutableStateFlow(0.0)
    val recordingDurationSec: StateFlow<Double> = _recordingDurationSec.asStateFlow()

    private var recordingTimerJob: Job? = null

    // Audio Playback States
    private val _playbackProgress = MutableStateFlow(0)
    val playbackProgress: StateFlow<Int> = _playbackProgress.asStateFlow()

    private val _playbackDuration = MutableStateFlow(0)
    val playbackDuration: StateFlow<Int> = _playbackDuration.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    // Status Notification Alerts
    private val _uiEvents = MutableSharedFlow<String>()
    val uiEvents: SharedFlow<String> = _uiEvents.asSharedFlow()

    fun selectSermon(jobId: String?) {
        _activeJobId.value = jobId
        // Stop playing any existing audio when shifting context
        stopPlayback()
    }

    /* =========================================================================
       AUDIO RECORDING LOGIC
       ========================================================================= */

    fun startRecording() {
        if (_isRecording.value) return
        
        val recordTitle = "Sermon_Rec_${System.currentTimeMillis()}"
        val recordFile = audioEngine.startRecording(recordTitle)
        
        if (recordFile != null) {
            _isRecording.value = true
            _recordingDurationSec.value = 0.0

            // Ticker to update recorder clock
            recordingTimerJob = viewModelScope.launch {
                while (isActive) {
                    delay(1000)
                    _recordingDurationSec.value += 1.0
                }
            }
        } else {
            postUiEvent("Microphone calibration failed. Double-check permissions.")
        }
    }

    fun stopRecordingAndSave(title: String, sermonTheme: String? = null) {
        if (!_isRecording.value) return

        recordingTimerJob?.cancel()
        recordingTimerJob = null

        val duration = audioEngine.stopRecording()
        _isRecording.value = false

        // Construct target filename in internal storage directory
        val recordTitle = if (title.isBlank()) "Untitled Sermon" else title.trim()
        val tempFile = File(context.cacheDir, "Sermon_Rec_${_recordingDurationSec.value}.m4a") // approximated path matching output
        
        viewModelScope.launch {
            // Find captured file from cache and register job
            val cacheFiles = context.cacheDir.listFiles()
            val capturedFile = cacheFiles?.sortedByDescending { it.lastModified() }
                ?.firstOrNull { it.name.endsWith(".m4a") }
                ?: tempFile

            val jobId = repository.createNewJob(
                title = recordTitle,
                audioPath = capturedFile.absolutePath,
                durationSec = if (_recordingDurationSec.value > 0) _recordingDurationSec.value else duration
            )

            postUiEvent("Recording saved. Starting transcription...")
            
            // Queue automatic transcription process asynchronously!
            launch {
                repository.transcribeJob(jobId, sermonTheme)
                postUiEvent("Transcription Complete!")
            }
        }
    }

    fun cancelRecording() {
        if (!_isRecording.value) return
        recordingTimerJob?.cancel()
        recordingTimerJob = null
        audioEngine.stopRecording()
        _isRecording.value = false
    }

    /* =========================================================================
       API TRANSCRIPTION & POST-PROCESSING ENGINE
       ========================================================================= */

    fun triggerTranscription(jobId: String, themeHint: String? = null) {
        viewModelScope.launch {
            postUiEvent("Transcribing sermon...")
            repository.transcribeJob(jobId, themeHint)
            postUiEvent("Transcription database synchronized successfully!")
        }
    }

    fun deleteSermon(jobId: String) {
        viewModelScope.launch {
            repository.deleteJob(jobId)
            if (_activeJobId.value == jobId) {
                _activeJobId.value = null
            }
            postUiEvent("Sermon removed from records.")
        }
    }

    fun editSegment(segmentId: Int, newText: String) {
        viewModelScope.launch {
            repository.updateSegmentText(segmentId, newText)
        }
    }

    fun runAutoRefCorrection(jobId: String) {
        viewModelScope.launch {
            postUiEvent("Gemini is proofreading references and formatting nouns...")
            val success = repository.runAutoCorrect(jobId)
            if (success) {
                postUiEvent("References successfully formatted!")
            } else {
                postUiEvent("Proofreading failed. Verify API configuration and connection.")
            }
        }
    }

    fun runRegenerateOutline(jobId: String) {
        viewModelScope.launch {
            postUiEvent("Re-compiling markdown outlines...")
            val success = repository.regenerateOutline(jobId)
            if (success) {
                postUiEvent("Outline successfully compiled!")
            } else {
                postUiEvent("Compilation failed. Ensure your Gemini API is online.")
            }
        }
    }

    /* =========================================================================
       AUDIO PLAYBACK HANDLERS
       ========================================================================= */

    fun startPlayback(audioPath: String) {
        audioEngine.startPlayback(
            filePath = audioPath,
            onStart = { duration ->
                _playbackDuration.value = duration
                _isPlaying.value = true
            },
            onProgress = { position ->
                _playbackProgress.value = position
            },
            onCompletion = {
                _isPlaying.value = false
                _playbackProgress.value = 0
            }
        )
    }

    fun togglePlayback(audioPath: String) {
        if (_isPlaying.value) {
            audioEngine.pausePlayback()
            _isPlaying.value = false
        } else {
            if (_playbackProgress.value > 0 && _playbackProgress.value < _playbackDuration.value) {
                audioEngine.resumePlayback { position ->
                    _playbackProgress.value = position
                }
                _isPlaying.value = true
            } else {
                startPlayback(audioPath)
            }
        }
    }

    fun seekPlayback(positionMs: Int) {
        audioEngine.seekTo(positionMs)
        _playbackProgress.value = positionMs
    }

    fun stopPlayback() {
        audioEngine.stopPlayback()
        _isPlaying.value = false
        _playbackProgress.value = 0
    }

    private fun postUiEvent(msg: String) {
        viewModelScope.launch {
            _uiEvents.emit(msg)
        }
    }

    override fun onCleared() {
        super.onCleared()
        audioEngine.stopPlayback()
        audioEngine.stopRecording()
    }
}

// Simple Factory provider
class SermonViewModelFactory(
    private val context: Context,
    private val repository: SermonRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SermonViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SermonViewModel(context, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
