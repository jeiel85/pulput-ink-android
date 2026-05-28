package io.pulpit.ink.data.text

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import io.pulpit.ink.MainActivity

/**
 * Handles creation and dynamic updates of system status bar notifications
 * with real-time ProgressBars for local Whisper speech transcription.
 */
object TranscriptionNotificationHelper {
    private const val CHANNEL_ID = "transcription_channel"
    private const val CHANNEL_NAME = "Sermon Transcription (설교 전사)"
    
    /**
     * Set up low-priority notification channel (Android 8.0+).
     * Using low priority prevents annoying sound/vibration alerts during active progression updates.
     */
    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Shows real-time progress of sermon audio offline transcription."
                    enableLights(false)
                    enableVibration(false)
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    /**
     * Create or update the real-time ProgressBar notification in the Status Bar.
     *
     * @param context Android context
     * @param jobId Unique ID of the sermon job
     * @param title Title of the sermon
     * @param progress Progress percentage (0 to 100)
     * @param statusMsg Localized description of the current stage
     */
    fun showProgressNotification(
        context: Context,
        jobId: String,
        title: String,
        progress: Int,
        statusMsg: String
    ) {
        createNotificationChannel(context)

        val notificationId = jobId.hashCode()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Setup PendingIntent to open MainActivity when notification is tapped
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("jobId", jobId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val isKo = java.util.Locale.getDefault().language == "ko"
        val headerTitle = if (isKo) "설교 전사 가공 중..." else "Transcribing Sermon..."

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download-1) // Use system active downloading icon
            .setContentTitle("$headerTitle ($progress%)")
            .setContentText("$title — $statusMsg")
            .setSubText(statusMsg)
            .setOngoing(progress < 100) // Keep ongoing locked until fully complete
            .setAutoCancel(progress == 100)
            .setContentIntent(pendingIntent)
            .setProgress(100, progress, false)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        // Standard microphone indicator as icon fallback if stat_sys_download is restricted
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            builder.setSmallIcon(android.R.drawable.ic_btn_speak_now)
        }

        if (progress >= 100) {
            val completeTitle = if (isKo) "★ 전사 완료 ★" else "★ Transcription Complete ★"
            val completeText = if (isKo) "『$title』전사 및 로컬 NLP 가공 완료" else "\"$title\" fully transcribed offline"
            
            builder.setContentTitle(completeTitle)
                .setContentText(completeText)
                .setSubText(if (isKo) "완료" else "Complete")
                .setProgress(0, 0, false) // Remove progress bar on completion
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
        }

        manager.notify(notificationId, builder.build())
    }

    /**
     * Manually dismiss the progression notification from status bar.
     */
    fun dismissNotification(context: Context, jobId: String) {
        val notificationId = jobId.hashCode()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(notificationId)
    }
}
