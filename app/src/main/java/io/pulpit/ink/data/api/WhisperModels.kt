package io.pulpit.ink.data.api

import io.pulpit.ink.R

enum class WhisperModelConfig(
    val modelKey: String,
    val filename: String,
    val sizeDisplay: String,
    val downloadUrl: String,
    val titleResId: Int,
    val descResId: Int
) {
    TINY(
        modelKey = "tiny",
        filename = "ggml-tiny.bin",
        sizeDisplay = "75 MB",
        downloadUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.bin",
        titleResId = R.string.model_tiny_title,
        descResId = R.string.model_tiny_desc
    ),
    BASE(
        modelKey = "base",
        filename = "ggml-base.bin",
        sizeDisplay = "142 MB",
        downloadUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.bin",
        titleResId = R.string.model_base_title,
        descResId = R.string.model_base_desc
    ),
    SMALL(
        modelKey = "small",
        filename = "ggml-small.bin",
        sizeDisplay = "466 MB",
        downloadUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small.bin",
        titleResId = R.string.model_small_title,
        descResId = R.string.model_small_desc
    );

    companion object {
        fun fromKey(key: String): WhisperModelConfig {
            return values().firstOrNull { it.modelKey == key } ?: BASE
        }
    }
}
