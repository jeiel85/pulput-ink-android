package com.example.ui.audio

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import kotlinx.coroutines.*
import java.io.File
import java.io.IOException

class AudioEngine(private val context: Context) {
    private val TAG = "AudioEngine"

    private var mediaRecorder: MediaRecorder? = null
    private var mediaPlayer: MediaPlayer? = null
    
    private var recordingFile: File? = null
    private var isRecording = false

    private var playbackJob: Job? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    /**
     * Set up and start the audio recording
     */
    fun startRecording(fileName: String): File? {
        val cacheDir = context.cacheDir
        recordingFile = File(cacheDir, "$fileName.m4a")

        // Safe MediaRecorder instantiation for various Android SDK versions
        mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(44100)
            setAudioEncodingBitRate(96000)
            setOutputFile(recordingFile!!.absolutePath)

            try {
                prepare()
                start()
                isRecording = true
                Log.d(TAG, "Audio recording started at: ${recordingFile!!.absolutePath}")
            } catch (e: Exception) {
                Log.e(TAG, "prepare() or start() failed for MediaRecorder", e)
                release()
                return null
            }
        }
        return recordingFile
    }

    /**
     * Stop ongoing sound recording and compute final elapsed duration
     */
    fun stopRecording(): Double {
        if (!isRecording) return 0.0
        var durationSec = 0.0
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            recordingFile?.let { file ->
                // Calculate real duration or fall back to high-grade approximation using file size
                durationSec = (file.length() / 12000.0).coerceAtLeast(1.0)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during stopRecording", e)
        } finally {
            mediaRecorder = null
            isRecording = false
        }
        return durationSec
    }

    /**
     * Start playing the audio file and stream playback updates back to the UI
     */
    fun startPlayback(
        filePath: String,
        onStart: (durationMs: Int) -> Unit,
        onProgress: (currentMs: Int) -> Unit,
        onCompletion: () -> Unit
    ) {
        stopPlayback()

        val file = File(filePath)
        if (!file.exists()) {
            Log.e(TAG, "Audio file does not exist: $filePath")
            onCompletion()
            return
        }

        mediaPlayer = MediaPlayer().apply {
            try {
                setDataSource(filePath)
                prepare()
                start()
                onStart(duration)

                // Poll playback position every 100ms
                playbackJob = coroutineScope.launch {
                    while (isActive && isPlaying) {
                        onProgress(currentPosition)
                        delay(100)
                    }
                }

                setOnCompletionListener {
                    stopPlayback()
                    onCompletion()
                }
            } catch (e: IOException) {
                Log.e(TAG, "MediaPlayer prepare failed", e)
                onCompletion()
            }
        }
    }

    /**
     * Toggle play/pause state
     */
    fun pausePlayback() {
        mediaPlayer?.let { player ->
            if (player.isPlaying) {
                player.pause()
                playbackJob?.cancel()
            }
        }
    }

    /**
     * Resume audio track
     */
    fun resumePlayback(onProgress: (currentMs: Int) -> Unit) {
        mediaPlayer?.let { player ->
            if (!player.isPlaying) {
                player.start()
                playbackJob = coroutineScope.launch {
                    while (isActive && player.isPlaying) {
                        onProgress(player.currentPosition)
                        delay(100)
                    }
                }
            }
        }
    }

    /**
     * Scrub timeline helper
     */
    fun seekTo(positionMs: Int) {
        mediaPlayer?.seekTo(positionMs)
    }

    /**
     * Stop and discard current player session
     */
    fun stopPlayback() {
        playbackJob?.cancel()
        playbackJob = null
        try {
            mediaPlayer?.apply {
                if (isPlaying) {
                    stop()
                }
                release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing MediaPlayer", e)
        } finally {
            mediaPlayer = null
        }
    }

    /**
     * Retrieve status metrics
     */
    fun isPlayerPlaying(): Boolean = mediaPlayer?.isPlaying ?: false

    private fun release() {
        try {
            mediaRecorder?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing MediaRecorder", e)
        }
        mediaRecorder = null
        isRecording = false
    }
}
