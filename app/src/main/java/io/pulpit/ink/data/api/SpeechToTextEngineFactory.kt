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
        val isModelDownloaded = modelManager.isModelDownloaded(config)
        val isJniAvailable = WhisperLib.isLibraryLoaded

        return if (isModelDownloaded && isJniAvailable) {
            Log.i(TAG, "Selected Model '$modelKey' is downloaded and Whisper JNI library is loaded. Using OfflineWhisperEngine.")
            OfflineWhisperEngine(context)
        } else {
            if (!isModelDownloaded) {
                Log.i(TAG, "Model '$modelKey' is not downloaded locally. Falling back to OnlineOpenAIEngine.")
            } else {
                Log.w(TAG, "Local model '$modelKey' is downloaded, but Whisper JNI library (libwhisper.so) is missing. Falling back to OnlineOpenAIEngine.")
            }
            OnlineOpenAIEngine()
        }
    }
}
