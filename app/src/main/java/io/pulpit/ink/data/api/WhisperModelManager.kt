package io.pulpit.ink.data.api

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class WhisperModelManager(private val context: Context) {
    private val TAG = "WhisperModelManager"
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    /** Where a downloaded model is stored in app-private storage. */
    fun getModelFile(config: WhisperModelConfig): File {
        return File(context.filesDir, config.filename)
    }

    /** Resolves the model file to load, or null if no usable copy is downloaded. */
    fun resolveModelFile(config: WhisperModelConfig): File? {
        val file = getModelFile(config)
        return if (file.exists() && file.length() > 0) file else null
    }

    fun isModelDownloaded(config: WhisperModelConfig): Boolean {
        return resolveModelFile(config) != null
    }

    fun deleteModel(config: WhisperModelConfig): Boolean {
        val file = getModelFile(config)
        if (file.exists()) {
            return file.delete()
        }
        return false
    }

    fun getStorageUsageBytes(): Long {
        var total = 0L
        WhisperModelConfig.values().forEach { config ->
            val file = getModelFile(config)
            if (file.exists()) {
                total += file.length()
            }
        }
        return total
    }

    suspend fun downloadModel(
        config: WhisperModelConfig,
        onProgress: (Int) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val file = getModelFile(config)
        val tempFile = File(context.filesDir, "${config.filename}.tmp")

        try {
            val request = Request.Builder().url(config.downloadUrl).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Failed to download model: ${response.message}")
                    return@withContext false
                }

                val body = response.body ?: return@withContext false
                val contentLength = body.contentLength()
                body.byteStream().use { inputStream ->
                    FileOutputStream(tempFile).use { outputStream ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var totalBytesRead = 0L

                        var lastProgress = -1
                        var lastUpdateTime = 0L

                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            if (!kotlinx.coroutines.currentCoroutineContext().isActive) {
                                throw kotlinx.coroutines.CancellationException("Download cancelled")
                            }
                            outputStream.write(buffer, 0, bytesRead)
                            totalBytesRead += bytesRead
                            if (contentLength > 0) {
                                val progress = ((totalBytesRead * 100) / contentLength).toInt()
                                val now = System.currentTimeMillis()
                                if (progress != lastProgress || now - lastUpdateTime >= 250L) {
                                    lastProgress = progress
                                    lastUpdateTime = now
                                    onProgress(progress)
                                }
                            }
                        }
                        outputStream.flush()
                    }
                }
            }

            // Rename temp file to final file safely
            if (tempFile.exists()) {
                if (file.exists()) {
                    file.delete()
                }
                val success = tempFile.renameTo(file)
                if (success) {
                    Log.d(TAG, "Successfully downloaded and renamed ${config.filename}")
                    return@withContext true
                }
            }
            return@withContext false

        } catch (e: Exception) {
            Log.e(TAG, "Error downloading model ${config.modelKey}", e)
            if (tempFile.exists()) {
                tempFile.delete()
            }
            return@withContext false
        }
    }
}
