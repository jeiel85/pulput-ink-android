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
     * @return The raw transcribed text.
     */
    suspend fun transcribe(audioFile: File, modelKey: String): String
}
