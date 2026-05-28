package io.pulpit.ink.data.api

import java.io.File

/**
 * SpeechToTextEngine implementation using the online OpenAI Whisper API.
 */
class OnlineOpenAIEngine : SpeechToTextEngine {
    override suspend fun transcribe(
        audioFile: File,
        modelKey: String,
        onInferenceProgress: ((Int) -> Unit)?
    ): String {
        return OpenAIService.transcribeAudio(audioFile)
    }
}
