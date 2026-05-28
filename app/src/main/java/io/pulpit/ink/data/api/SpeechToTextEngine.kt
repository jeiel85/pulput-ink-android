package io.pulpit.ink.data.api

import java.io.File

/**
 * Common abstraction for Speech-To-Text transcribers.
 */
interface SpeechToTextEngine {
    /**
     * Transcribe an audio file into text.
     *
     * @param audioFile The local file containing the recording.
     * @param modelKey The key of the model config selected.
     * @param onInferenceProgress Optional callback invoked with 0..100 percent
     *   updates while the engine is running its inner inference loop. May be
     *   invoked from a non-UI thread; implementations must be cheap.
     * @return The raw transcribed text.
     */
    suspend fun transcribe(
        audioFile: File,
        modelKey: String,
        onInferenceProgress: ((Int) -> Unit)? = null
    ): String
}
