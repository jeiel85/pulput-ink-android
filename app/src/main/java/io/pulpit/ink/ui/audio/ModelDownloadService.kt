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
import io.pulpit.ink.data.api.WhisperModelConfig
import io.pulpit.ink.data.api.WhisperModelManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Foreground Service that handles Whisper JNI offline models background downloads.
 * This guarantees the download continues even if the user exits or swipes away the application.
 */
class ModelDownloadService : Service() {
    private val TAG = "ModelDownloadService"
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var modelManager: WhisperModelManager
    private var downloadJob: Job? = null
    private var lastNotificationTime = 0L
    private var lastNotificationProgress = -1
    
    companion object {
        const val ACTION_START_DOWNLOAD = "io.pulpit.ink.action.START_DOWNLOAD"
        const val ACTION_STOP_DOWNLOAD = "io.pulpit.ink.action.STOP_DOWNLOAD"
        const val EXTRA_MODEL_KEY = "io.pulpit.ink.extra.MODEL_KEY"
        
        private const val NOTIFICATION_ID = 9001
        private const val CHANNEL_ID = "model_download_channel"
        
        // Expose states reactively to the ViewModel
        private val _downloadProgress = MutableStateFlow<Map<String, Int>>(emptyMap())
        val downloadProgress = _downloadProgress.asStateFlow()
        
        private val _downloadState = MutableStateFlow<Map<String, String>>(emptyMap())
        val downloadState = _downloadState.asStateFlow()
        
        fun startDownload(context: Context, modelKey: String) {
            val intent = Intent(context, ModelDownloadService::class.java).apply {
                action = ACTION_START_DOWNLOAD
                putExtra(EXTRA_MODEL_KEY, modelKey)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        modelManager = WhisperModelManager(applicationContext)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_DOWNLOAD -> {
                val modelKey = intent.getStringExtra(EXTRA_MODEL_KEY)
                if (modelKey != null) {
                    val config = WhisperModelConfig.fromKey(modelKey)
                    startForegroundDownload(config)
                }
            }
            ACTION_STOP_DOWNLOAD -> {
                stopForegroundDownload()
            }
        }
        return START_NOT_STICKY
    }

    private fun stopForegroundDownload() {
        Log.d(TAG, "Stopping download job...")
        downloadJob?.cancel()
        downloadJob = null
        
        val currentStates = _downloadState.value
        val updatedStates = currentStates.toMutableMap()
        currentStates.forEach { (modelKey, state) ->
            if (state == "downloading") {
                updatedStates[modelKey] = "not_downloaded"
                val config = WhisperModelConfig.fromKey(modelKey)
                val tempFile = File(applicationContext.filesDir, "${config.filename}.tmp")
                if (tempFile.exists()) {
                     tempFile.delete()
                }
            }
        }
        _downloadState.value = updatedStates
        
        stopForeground(true)
        stopSelf()
    }

    private fun startForegroundDownload(config: WhisperModelConfig) {
        val modelKey = config.modelKey
        
        lastNotificationTime = 0L
        lastNotificationProgress = -1
        
        // Update states
        updateDownloadState(modelKey, "downloading")
        updateDownloadProgress(modelKey, 0)
        
        val notification = buildNotification(config, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        
        downloadJob = serviceScope.launch {
            Log.d(TAG, "Starting background model download: ${config.modelKey}")
            val success = withContext(Dispatchers.IO) {
                try {
                    modelManager.downloadModel(config) { progress ->
                        updateDownloadProgress(modelKey, progress)
                        updateNotification(config, progress)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to download model ${config.modelKey}", e)
                    false
                }
            }
            
            if (success) {
                Log.d(TAG, "Background model download success: ${config.modelKey}")
                updateDownloadState(modelKey, "downloaded")
                showToast(getString(R.string.toast_download_success, config.modelKey))
            } else {
                Log.e(TAG, "Background model download failed: ${config.modelKey}")
                updateDownloadState(modelKey, "not_downloaded")
                showToast(getString(R.string.toast_download_failed))
                // Clean up temp file
                val tempFile = File(applicationContext.filesDir, "${config.filename}.tmp")
                if (tempFile.exists()) {
                    tempFile.delete()
                }
            }
            
            // Finished, stop foreground service
            stopForeground(true)
            stopSelf()
        }
    }

    private fun updateDownloadState(modelKey: String, state: String) {
        val current = _downloadState.value.toMutableMap()
        current[modelKey] = state
        _downloadState.value = current
    }

    private fun updateDownloadProgress(modelKey: String, progress: Int) {
        val current = _downloadProgress.value.toMutableMap()
        current[modelKey] = progress
        _downloadProgress.value = current
    }

    private fun buildNotification(config: WhisperModelConfig, progress: Int): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val stopIntent = Intent(this, ModelDownloadService::class.java).apply {
            action = ACTION_STOP_DOWNLOAD
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val isKo = java.util.Locale.getDefault().language == "ko"
        val cancelLabel = if (isKo) "다운로드 취소" else "Cancel Download"
        val contentText = if (isKo) "Whisper ${config.modelKey.uppercase()} 필기 모델 다운로드 중 ($progress%)"
                          else "Downloading ${config.modelKey.uppercase()} offline transcription model ($progress%)"
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(if (isKo) "필기 엔진 모델 다운로더" else "Whisper JNI Model Downloader")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setProgress(100, progress, progress == 0)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, cancelLabel, stopPendingIntent)
            .build()
    }

    private fun updateNotification(config: WhisperModelConfig, progress: Int) {
        val now = System.currentTimeMillis()
        if (progress != lastNotificationProgress && (now - lastNotificationTime >= 1000L || progress == 0 || progress == 100)) {
            lastNotificationTime = now
            lastNotificationProgress = progress
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(NOTIFICATION_ID, buildNotification(config, progress))
        }
    }

    private fun showToast(message: String) {
        serviceScope.launch(Dispatchers.Main) {
            android.widget.Toast.makeText(applicationContext, message, android.widget.Toast.LENGTH_LONG).show()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Model Download"
            val descriptionText = "Notifications for Whisper offline JNI model background downloads"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        Log.d(TAG, "ModelDownloadService destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
