package io.pulpit.ink.ui.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.pulpit.ink.R
import io.pulpit.ink.data.db.AppDatabase
import io.pulpit.ink.data.model.SermonJob
import io.pulpit.ink.data.model.SermonSegment
import io.pulpit.ink.data.repository.SermonRepository
import io.pulpit.ink.data.repository.TranscriptionRunner
import io.pulpit.ink.data.api.WhisperModelManager
import io.pulpit.ink.data.api.WhisperModelConfig
import io.pulpit.ink.ui.audio.AudioEngine
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.util.Locale

class SermonViewModel(
    private val context: Context,
    private val repository: SermonRepository
) : ViewModel() {

    private val TAG = "SermonViewModel"
    private val audioEngine = AudioEngine(context)
    private val whisperModelManager = WhisperModelManager(context)

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
    val isRecording: StateFlow<Boolean> = io.pulpit.ink.ui.audio.AudioRecordingService.isRecording
    val recordingDurationSec: StateFlow<Double> = io.pulpit.ink.ui.audio.AudioRecordingService.durationSec

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

    // Dispatch newly recorded sermon IDs to auto-transition into Detail Screen
    private val _newJobFlow = MutableSharedFlow<String>()
    val newJobFlow: SharedFlow<String> = _newJobFlow.asSharedFlow()

    // Whisper Model State Flows
    val selectedWhisperModel = MutableStateFlow(context.getSharedPreferences("whisper_prefs", Context.MODE_PRIVATE).getString("selected_model", "base") ?: "base")
    
    private val _downloadProgress = MutableStateFlow<Map<String, Int>>(emptyMap())
    val downloadProgress = _downloadProgress.asStateFlow()

    private val _downloadState = MutableStateFlow<Map<String, String>>(emptyMap())
    val downloadState = _downloadState.asStateFlow()

    private val _storageUsage = MutableStateFlow(0L)
    val storageUsage = _storageUsage.asStateFlow()

    init {
        refreshWhisperStates()
        viewModelScope.launch {
            // Sweep any jobs left in `Transcribing` state from a previous app run —
            // the transcription runner can't survive process death.
            repository.resetStuckTranscribingJobs()
        }
        viewModelScope.launch {
            io.pulpit.ink.ui.audio.AudioRecordingService.newJobIdFlow.collect { jobId ->
                _newJobFlow.emit(jobId)
            }
        }
    }

    private fun isKoreanLanguage(): Boolean {
        return Locale.getDefault().language == "ko"
    }

    fun selectSermon(jobId: String?) {
        _activeJobId.value = jobId
        // Stop playing any existing audio when shifting context
        stopPlayback()
    }

    /* =========================================================================
       WHISPER MODEL DOWNLOAD & SETTINGS LOGIC
       ========================================================================= */

    fun refreshWhisperStates() {
        viewModelScope.launch {
            val stateMap = mutableMapOf<String, String>()
            WhisperModelConfig.values().forEach { config ->
                val isDownloaded = whisperModelManager.isModelDownloaded(config)
                stateMap[config.modelKey] = if (isDownloaded) "downloaded" else "not_downloaded"
            }
            _downloadState.value = stateMap
            _storageUsage.value = whisperModelManager.getStorageUsageBytes()
        }
    }

    fun selectWhisperModel(modelKey: String) {
        selectedWhisperModel.value = modelKey
        context.getSharedPreferences("whisper_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("selected_model", modelKey)
            .apply()
    }

    fun downloadWhisperModel(modelKey: String) {
        val config = WhisperModelConfig.fromKey(modelKey)
        
        // Update state map to show downloading status
        val currentStates = _downloadState.value.toMutableMap()
        currentStates[modelKey] = "downloading"
        _downloadState.value = currentStates

        postUiEvent(context.getString(R.string.toast_download_start, config.modelKey))

        viewModelScope.launch {
            val success = whisperModelManager.downloadModel(config) { progress ->
                val currentProgress = _downloadProgress.value.toMutableMap()
                currentProgress[modelKey] = progress
                _downloadProgress.value = currentProgress
            }

            if (success) {
                postUiEvent(context.getString(R.string.toast_download_success, config.modelKey))
            } else {
                postUiEvent(context.getString(R.string.toast_download_failed))
            }
            refreshWhisperStates()
        }
    }

    fun deleteWhisperModel(modelKey: String) {
        val config = WhisperModelConfig.fromKey(modelKey)
        val success = whisperModelManager.deleteModel(config)
        if (success) {
            postUiEvent(context.getString(R.string.toast_delete_success, config.modelKey))
        }
        refreshWhisperStates()
    }

    /* =========================================================================
       AUDIO RECORDING LOGIC
       ========================================================================= */

    fun startRecording(title: String = "", topic: String = "") {
        if (isRecording.value) return
        val intent = android.content.Intent(context, io.pulpit.ink.ui.audio.AudioRecordingService::class.java).apply {
            action = io.pulpit.ink.ui.audio.AudioRecordingService.ACTION_START
            putExtra(io.pulpit.ink.ui.audio.AudioRecordingService.EXTRA_TITLE, title)
            putExtra(io.pulpit.ink.ui.audio.AudioRecordingService.EXTRA_TOPIC, topic)
        }
        androidx.core.content.ContextCompat.startForegroundService(context, intent)
    }

    fun stopRecordingAndSave(title: String, sermonTheme: String? = null) {
        if (!isRecording.value) return
        val intent = android.content.Intent(context, io.pulpit.ink.ui.audio.AudioRecordingService::class.java).apply {
            action = io.pulpit.ink.ui.audio.AudioRecordingService.ACTION_STOP
            putExtra(io.pulpit.ink.ui.audio.AudioRecordingService.EXTRA_TITLE, title)
            putExtra(io.pulpit.ink.ui.audio.AudioRecordingService.EXTRA_TOPIC, sermonTheme ?: "")
        }
        context.startService(intent)
    }

    fun cancelRecording() {
        if (!isRecording.value) return
        val intent = android.content.Intent(context, io.pulpit.ink.ui.audio.AudioRecordingService::class.java).apply {
            action = io.pulpit.ink.ui.audio.AudioRecordingService.ACTION_CANCEL
        }
        context.startService(intent)
    }

    /* =========================================================================
       API TRANSCRIPTION & POST-PROCESSING ENGINE
       ========================================================================= */

    fun triggerTranscription(jobId: String, themeHint: String? = null) {
        // Guard: refuse to start if the chosen model isn't installed. Without this the
        // factory would silently fall through to the (deprecated) online engine and the
        // failure would only surface deep in the transcription pipeline.
        val modelKey = selectedWhisperModel.value
        val config = WhisperModelConfig.fromKey(modelKey)
        if (!whisperModelManager.isModelDownloaded(config)) {
            postUiEvent(context.getString(R.string.transcription_failed))
            viewModelScope.launch {
                repository.markJobFailed(
                    jobId,
                    if (isKoreanLanguage())
                        "Whisper '$modelKey' 모델이 설치되어 있지 않습니다. 설정에서 먼저 다운로드해 주세요."
                    else
                        "Whisper '$modelKey' model is not installed. Please download it from settings first."
                )
            }
            return
        }
        postUiEvent(context.getString(R.string.status_transcribing))
        // Submit to the process-wide runner so the work survives ViewModel destruction.
        TranscriptionRunner.submit(
            context = context,
            jobId = jobId,
            topicHint = themeHint,
            modelKey = modelKey
        )
    }

    fun deleteSermon(jobId: String) {
        viewModelScope.launch {
            repository.deleteJob(jobId)
            if (_activeJobId.value == jobId) {
                _activeJobId.value = null
            }
            postUiEvent(context.getString(R.string.rec_removed))
        }
    }

    fun editSegment(segmentId: Int, newText: String) {
        viewModelScope.launch {
            repository.updateSegmentText(segmentId, newText)
        }
    }

    fun runAutoRefCorrection(jobId: String) {
        viewModelScope.launch {
            postUiEvent(context.getString(R.string.toast_proofreading))
            val success = repository.runAutoCorrect(jobId)
            if (success) {
                postUiEvent(context.getString(R.string.toast_proofread_success))
            } else {
                postUiEvent(context.getString(R.string.toast_proofread_failed))
            }
        }
    }

    fun runRegenerateOutline(jobId: String) {
        viewModelScope.launch {
            postUiEvent(context.getString(R.string.toast_outline_compiling))
            val success = repository.regenerateOutline(jobId)
            if (success) {
                postUiEvent(context.getString(R.string.toast_outline_success))
            } else {
                postUiEvent(context.getString(R.string.toast_outline_failed))
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
