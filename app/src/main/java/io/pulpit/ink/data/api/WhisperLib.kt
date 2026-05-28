package io.pulpit.ink.data.api

import android.util.Log

/**
 * JNI bindings for Whisper.cpp native library.
 */
object WhisperLib {
    private const val TAG = "WhisperLib"
    
    /**
     * Flag indicating whether the native C++ library was loaded successfully.
     */
    var isLibraryLoaded = false
        private set

    init {
        try {
            System.loadLibrary("whisper")
            isLibraryLoaded = true
            Log.i(TAG, "Successfully loaded native whisper library")
        } catch (e: UnsatisfiedLinkError) {
            Log.w(TAG, "Native whisper library (libwhisper.so) not found. On-device transcription will fall back to online mode: ${e.message}")
        }
    }

    /**
     * Initialize the Whisper context using the local GGML model file.
     *
     * @param modelPath Absolute path to the model file (e.g. ggml-base.bin).
     * @return Memory address pointer of the native whisper_context, or 0 if failed.
     */
    external fun initContext(modelPath: String): Long

    /**
     * Release the allocated native Whisper context.
     *
     * @param contextPtr Pointer to the native whisper_context.
     */
    external fun freeContext(contextPtr: Long)

    /**
     * Transcribe raw 16kHz mono float PCM audio data.
     *
     * @param contextPtr Pointer to the native whisper_context.
     * @param pcmData Raw PCM audio samples (16000Hz mono).
     * @param language ISO language code (e.g. "ko", "en", "auto").
     * @return Transcribed raw text segment.
     */
    external fun transcribeAudio(contextPtr: Long, pcmData: FloatArray, language: String): String
}
