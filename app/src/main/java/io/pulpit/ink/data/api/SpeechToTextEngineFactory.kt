package io.pulpit.ink.data.api

import android.content.Context
import android.util.Log

/**
 * Factory class to instantiate the appropriate SpeechToTextEngine
 * dynamically depending on the system's current state (model downloaded, JNI loaded).
 */
object SpeechToTextEngineFactory {
    private const val TAG = "STTEngineFactory"

    /**
     * Resolve the optimal engine: offline if the model is downloaded and JNI is available,
     * otherwise fallback to online OpenAI Whisper API.
     */
    fun getEngine(context: Context, modelKey: String): SpeechToTextEngine {
        val config = WhisperModelConfig.fromKey(modelKey)
        val modelManager = WhisperModelManager(context)
        if (!WhisperLib.isLibraryLoaded) {
            throw IllegalStateException(
                "Whisper native library (libwhisper.so) failed to load — offline transcription unavailable."
            )
        }
        if (!modelManager.isModelDownloaded(config)) {
            throw IllegalStateException(
                "Whisper model '$modelKey' is not installed. Download it from settings first."
            )
        }
        Log.i(TAG, "Resolved offline engine with model '$modelKey'.")
        return OfflineWhisperEngine(context)
    }
}
