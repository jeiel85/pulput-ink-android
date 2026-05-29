package io.pulpit.ink.data.api

import android.content.Context
import android.util.Log
import io.pulpit.ink.ui.audio.AudioDecoder
import java.io.File
import java.util.Locale

/**
 * SpeechToTextEngine implementation that transcribes audio fully offline
 * using Whisper.cpp native JNI bindings and the downloaded local model binary.
 */
class OfflineWhisperEngine(private val context: Context) : SpeechToTextEngine {
    private val TAG = "OfflineWhisperEngine"
    private val modelManager = WhisperModelManager(context)

    override suspend fun transcribe(
        audioFile: File,
        modelKey: String,
        onInferenceProgress: ((Int) -> Unit)?
    ): String {
        Log.i(TAG, "Starting offline transcription for ${audioFile.name} using model: $modelKey")

        // 1. Verify JNI library is loaded
        if (!WhisperLib.isLibraryLoaded) {
            throw IllegalStateException(
                "Offline Whisper engine native library (libwhisper.so) is not loaded. " +
                "Please place the pre-built native libraries in your project's jniLibs directory."
            )
        }

        // 2. Locate downloaded model configuration
        val config = WhisperModelConfig.fromKey(modelKey)
        val modelFile = modelManager.getModelFile(config)
        if (!modelFile.exists() || modelFile.length() == 0L) {
            throw IllegalStateException(
                "Local model file for '$modelKey' is not found or corrupted at: ${modelFile.absolutePath}. " +
                "Please download the model from settings first."
            )
        }

        // 3. Decode compressed audio (.m4a) to raw PCM Float array
        Log.d(TAG, "Decoding audio file to PCM...")
        onInferenceProgress?.invoke(-1)
        val pcmData = AudioDecoder.decodeToPcm(audioFile, context)
        if (pcmData.isEmpty()) {
            // Treat as a valid-but-silent recording instead of erroring out — a brand
            // new sermon job stuck at "Failed" purely because the user tapped stop
            // too quickly (or the room was silent) is worse UX than an empty result.
            Log.w(TAG, "Audio decode produced zero samples — returning empty transcript.")
            return ""
        }
        Log.d(TAG, "Audio successfully decoded. Sample count: ${pcmData.size}")

        // 4. Initialize native Whisper context
        Log.d(TAG, "Initializing Whisper native context with model: ${modelFile.name}")
        onInferenceProgress?.invoke(-2)
        val contextPtr = WhisperLib.initContext(modelFile.absolutePath)
        if (contextPtr == 0L) {
            throw IllegalStateException("Failed to initialize native Whisper context with model: ${modelFile.absolutePath}")
        }

        // 5. Transcribe raw float samples safely inside a try-finally block to prevent native memory leaks
        try {
            Log.d(TAG, "Executing native Whisper inference...")
            val systemLanguage = Locale.getDefault().language
            val effectiveLanguage = if (systemLanguage.isNotBlank()) systemLanguage else "auto"

            val callback = onInferenceProgress?.let { sink ->
                object : WhisperLib.ProgressCallback {
                    override fun onProgress(percent: Int) = sink(percent)
                }
            }

            val resultText = WhisperLib.transcribeAudio(contextPtr, pcmData, effectiveLanguage, callback)
            Log.i(TAG, "Offline transcription completed successfully.")
            return resultText
        } finally {
            Log.d(TAG, "Safely releasing native Whisper context pointer: $contextPtr")
            WhisperLib.freeContext(contextPtr)
        }
    }
}
