package io.pulpit.ink.data.repository

import android.content.Context
import android.util.Log
import io.pulpit.ink.data.db.AppDatabase
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Process-wide scope that owns long-running transcription work so it survives the
 * AudioRecordingService stopping and the hosting Activity/ViewModel being destroyed.
 *
 * The previous implementation launched transcription on the recording service's own
 * CoroutineScope; `stopSelf()` immediately after triggered `serviceScope.cancel()` and
 * killed the transcription mid-flight, leaving jobs frozen at `Transcribing`.
 */
object TranscriptionRunner {
    private const val TAG = "TranscriptionRunner"

    private val handler = CoroutineExceptionHandler { _, t ->
        Log.e(TAG, "Uncaught exception in transcription runner", t)
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + handler)

    // Serialize transcriptions: Whisper inference is heavy and concurrent jobs would
    // thrash CPU/memory on mid-range Android devices.
    private val runMutex = Mutex()

    /**
     * Enqueue a transcription job. Returns immediately; the caller must not assume
     * completion timing. Uses the application context so callers (services, activities)
     * cannot accidentally pin themselves alive.
     */
    fun submit(
        context: Context,
        jobId: String,
        topicHint: String? = null,
        modelKey: String
    ) {
        val appContext = context.applicationContext
        scope.launch {
            runMutex.withLock {
                val repository = SermonRepository(AppDatabase.getDatabase(appContext).sermonDao())
                try {
                    repository.transcribeJob(appContext, jobId, topicHint, modelKey)
                } catch (t: Throwable) {
                    Log.e(TAG, "Transcription crashed for job $jobId", t)
                    // The repository handles its own status writes inside a NonCancellable
                    // cleanup block, but if something escapes (e.g. OOM in native land)
                    // make sure we don't silently leave the job stuck.
                    withContext(NonCancellable) {
                        repository.markJobFailed(jobId, t.localizedMessage ?: "Transcription crashed")
                    }
                }
            }
        }
    }
}
