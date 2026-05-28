package io.pulpit.ink.ui.audio

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import io.pulpit.ink.MainActivity
import io.pulpit.ink.R
import io.pulpit.ink.data.db.AppDatabase
import io.pulpit.ink.data.repository.SermonRepository
import io.pulpit.ink.data.repository.TranscriptionRunner
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.Locale
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.media.MediaMetadata
import android.app.Notification

/**
 * Foreground Service for background sermon audio recording.
 * Supports background continuation, status bar ongoing chip (Now Brief),
 * and dynamic notification actions (Complete / Discard).
 */
class AudioRecordingService : Service() {
    private val TAG = "AudioRecordingService"
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    private lateinit var audioEngine: AudioEngine
    private lateinit var repository: SermonRepository
    
    private var timerJob: Job? = null
    private var mediaSession: MediaSession? = null
    
    private var recordTitle: String = ""
    private var recordTopic: String = ""

    override fun onCreate() {
        super.onCreate()
        audioEngine = AudioEngine(applicationContext)
        val database = AppDatabase.getDatabase(applicationContext)
        repository = SermonRepository(database.sermonDao())
        Log.d(TAG, "AudioRecordingService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        Log.d(TAG, "onStartCommand action: $action")
        
        when (action) {
            ACTION_START -> {
                val title = intent.getStringExtra(EXTRA_TITLE) ?: ""
                val topic = intent.getStringExtra(EXTRA_TOPIC) ?: ""
                startRecordingService(title, topic)
            }
            ACTION_STOP -> {
                stopRecordingAndSaveService()
            }
            ACTION_CANCEL -> {
                cancelRecordingService()
            }
        }
        
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startRecordingService(title: String, topic: String) {
        if (_isRecording.value) return

        recordTitle = title.trim()
        recordTopic = topic.trim()

        val tempTitle = "Sermon_Rec_${System.currentTimeMillis()}"
        val recordFile = audioEngine.startRecording(tempTitle)
        
        if (recordFile != null) {
            _isRecording.value = true
            _durationSec.value = 0.0
            _capturedFile.value = recordFile

            // Initialize MediaSession for Samsung/Android Quick Panel Integration
            setupMediaSession(title, topic)

            // Create notification channel and start foreground service
            createNotificationChannel()
            val notification = buildRecordingNotification(0.0)
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID, 
                    notification, 
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }

            // Start timer job to update duration
            timerJob?.cancel()
            timerJob = serviceScope.launch {
                while (isActive) {
                    delay(1000)
                    _durationSec.value += 1.0
                    updateNotification(_durationSec.value)
                }
            }
            Log.d(TAG, "Foreground recording service started successfully")
        } else {
            Log.e(TAG, "Failed to start recording inside service")
            stopSelf()
        }
    }

    private fun setupMediaSession(title: String, topic: String) {
        val isKo = Locale.getDefault().language == "ko"
        try {
            mediaSession = MediaSession(applicationContext, "SermonRecordingSession").apply {
                val metadataBuilder = MediaMetadata.Builder()
                    .putString(MediaMetadata.METADATA_KEY_TITLE, title.ifBlank { if (isKo) "설교 녹음 중" else "Recording Sermon" })
                    .putString(MediaMetadata.METADATA_KEY_ARTIST, topic.ifBlank { if (isKo) "실시간 오디오 캡처" else "Live Audio Capture" })
                setMetadata(metadataBuilder.build())

                val transcribeLabel = getString(R.string.transcribe)
                val discardLabel = getString(R.string.discard)

                // No ACTION_PLAY_PAUSE / ACTION_STOP — we don't support pause, and mapping
                // the system Pause icon to "save & stop" was misleading users. We rely on
                // our own custom actions, both rendered with matching Material icons.
                val state = PlaybackState.Builder()
                    .setState(PlaybackState.STATE_PLAYING, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1.0f)
                    .addCustomAction(
                        PlaybackState.CustomAction.Builder(
                            CUSTOM_ACTION_COMPLETE,
                            transcribeLabel,
                            R.drawable.ic_action_transcribe
                        ).build()
                    )
                    .addCustomAction(
                        PlaybackState.CustomAction.Builder(
                            CUSTOM_ACTION_DISCARD,
                            discardLabel,
                            R.drawable.ic_action_discard
                        ).build()
                    )
                    .build()
                setPlaybackState(state)

                setCallback(object : MediaSession.Callback() {
                    override fun onCustomAction(action: String, extras: android.os.Bundle?) {
                        Log.d(TAG, "MediaSession.Callback: onCustomAction=$action")
                        when (action) {
                            CUSTOM_ACTION_COMPLETE -> stopRecordingAndSaveService()
                            CUSTOM_ACTION_DISCARD -> cancelRecordingService()
                        }
                    }
                })
                isActive = true
            }
            Log.d(TAG, "MediaSession set up successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup MediaSession", e)
        }
    }

    private fun releaseMediaSession() {
        try {
            mediaSession?.apply {
                isActive = false
                release()
            }
            mediaSession = null
            Log.d(TAG, "MediaSession released successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing MediaSession", e)
        }
    }

    private fun stopRecordingAndSaveService() {
        if (!_isRecording.value) {
            stopSelf()
            return
        }

        timerJob?.cancel()
        timerJob = null

        val finalDuration = audioEngine.stopRecording()
        val file = _capturedFile.value
        
        _isRecording.value = false
        releaseMediaSession()
        
        val finalTitle = if (recordTitle.isBlank()) {
            if (Locale.getDefault().language == "ko") "무제 설교" else "Untitled Sermon"
        } else {
            recordTitle
        }
        
        val finalTopic = recordTopic.ifBlank { null }
        val capturedFile = file ?: File(cacheDir, "Sermon_Rec_${_durationSec.value}.m4a")

        serviceScope.launch {
            try {
                // Save new job to SQLite DB via repository
                val jobId = repository.createNewJob(
                    title = finalTitle,
                    audioPath = capturedFile.absolutePath,
                    durationSec = if (_durationSec.value > 0) _durationSec.value else finalDuration
                )

                // Emit new job event to trigger navigation
                _newJobIdFlow.emit(jobId)

                // Hand transcription off to the process-wide runner. Doing this on
                // serviceScope used to cancel the work as soon as stopSelf() ran.
                val modelKey = applicationContext
                    .getSharedPreferences("whisper_prefs", Context.MODE_PRIVATE)
                    .getString("selected_model", "base") ?: "base"
                TranscriptionRunner.submit(
                    context = applicationContext,
                    jobId = jobId,
                    topicHint = finalTopic,
                    modelKey = modelKey
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error saving recording job from background service", e)
            } finally {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun cancelRecordingService() {
        timerJob?.cancel()
        timerJob = null
        audioEngine.stopRecording()
        
        _isRecording.value = false
        _durationSec.value = 0.0
        _capturedFile.value = null
        
        releaseMediaSession()
        
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Log.d(TAG, "Foreground recording service cancelled and stopped")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = if (Locale.getDefault().language == "ko") "설교 녹음 서비스" else "Sermon Recording Service"
            val descriptionText = if (Locale.getDefault().language == "ko") "백그라운드 설교 녹음 상태 및 제어 기능을 제공합니다." else "Ongoing sermon capture session."
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                setShowBadge(false)
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildRecordingNotification(duration: Double): Notification {
        val isKo = Locale.getDefault().language == "ko"
        val formattedTime = formatDuration(duration)
        
        val notificationTitle = if (recordTitle.isNotBlank()) {
            if (isKo) "녹음 중: $recordTitle" else "Recording: $recordTitle"
        } else {
            if (isKo) "설교 녹음 중" else "Sermon Recording Live"
        }
        
        val notificationText = if (isKo) {
            "백그라운드에서 설교를 녹음하고 있습니다. ($formattedTime)"
        } else {
            "Sermon is being captured in the background. ($formattedTime)"
        }

        // Action 1: Open App Recording Screen
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingOpenIntent = PendingIntent.getActivity(
            this, 0, openIntent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action 2: Complete and save recording
        val stopIntent = Intent(this, AudioRecordingService::class.java).apply {
            action = ACTION_STOP
        }
        val pendingStopIntent = PendingIntent.getService(
            this, 1, stopIntent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action 3: Cancel and discard recording
        val cancelIntent = Intent(this, AudioRecordingService::class.java).apply {
            action = ACTION_CANCEL
        }
        val pendingCancelIntent = PendingIntent.getService(
            this, 2, cancelIntent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            Notification.Builder(this)
        }

        builder.setContentTitle(notificationTitle)
            .setContentText(notificationText)
            .setSmallIcon(android.R.drawable.presence_video_busy)
            .setContentIntent(pendingOpenIntent)
            .setOngoing(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            builder.setCategory(Notification.CATEGORY_SERVICE)

            // Match the MediaSession action labels: "필기 시작" / "취소". The same icons are
            // used so the notification expanded view and the quick-panel media tile stay
            // visually consistent.
            val stopAction = Notification.Action.Builder(
                R.drawable.ic_action_transcribe,
                getString(R.string.transcribe),
                pendingStopIntent
            ).build()

            val cancelAction = Notification.Action.Builder(
                R.drawable.ic_action_discard,
                getString(R.string.discard),
                pendingCancelIntent
            ).build()

            builder.addAction(stopAction)
            builder.addAction(cancelAction)

            // Integrate MediaSession to display in the Quick Panel/Samsung Now Brief / Media Controller
            mediaSession?.let { session ->
                val mediaStyle = Notification.MediaStyle()
                    .setMediaSession(session.sessionToken)
                    .setShowActionsInCompactView(0, 1)
                builder.setStyle(mediaStyle)
            }
        }

        return builder.build()
    }

    private fun updateNotification(duration: Double) {
        val notification = buildRecordingNotification(duration)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun formatDuration(seconds: Double): String {
        val s = seconds.toInt()
        val m = s / 60
        val h = m / 60
        return if (h > 0) {
            String.format(Locale.getDefault(), "%02d:%02d:%02d", h, m % 60, s % 60)
        } else {
            String.format(Locale.getDefault(), "%02d:%02d", m, s % 60)
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        releaseMediaSession()
        super.onDestroy()
        Log.d(TAG, "AudioRecordingService destroyed")
    }

    companion object {
        const val CHANNEL_ID = "recording_channel"
        const val NOTIFICATION_ID = 5005
        
        const val ACTION_START = "io.pulpit.ink.ACTION_START"
        const val ACTION_STOP = "io.pulpit.ink.ACTION_STOP"
        const val ACTION_CANCEL = "io.pulpit.ink.ACTION_CANCEL"

        const val CUSTOM_ACTION_COMPLETE = "io.pulpit.ink.CUSTOM_COMPLETE"
        const val CUSTOM_ACTION_DISCARD = "io.pulpit.ink.CUSTOM_DISCARD"
        
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_TOPIC = "extra_topic"

        // Static flows for the UI layer to bind to immediately
        private val _isRecording = MutableStateFlow(false)
        val isRecording = _isRecording.asStateFlow()

        private val _durationSec = MutableStateFlow(0.0)
        val durationSec = _durationSec.asStateFlow()

        private val _capturedFile = MutableStateFlow<File?>(null)
        val capturedFile = _capturedFile.asStateFlow()

        private val _newJobIdFlow = MutableSharedFlow<String>(extraBufferCapacity = 1)
        val newJobIdFlow = _newJobIdFlow.asSharedFlow()
    }
}
