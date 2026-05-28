package io.pulpit.ink.ui.audio

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Decodes compressed audio files (like .m4a AAC) into 16kHz mono Float PCM arrays.
 * This format is strictly required by the local Whisper.cpp inference engine.
 */
object AudioDecoder {
    private const val TAG = "AudioDecoder"
    private const val TARGET_SAMPLE_RATE = 16000

    /**
     * Decode an audio file and resample it to 16kHz mono float array in the range [-1.0f, 1.0f].
     */
    fun decodeToPcm(audioFile: File): FloatArray {
        if (!audioFile.exists() || audioFile.length() == 0L) {
            Log.e(TAG, "Audio file is empty or missing: ${audioFile.absolutePath}")
            return FloatArray(0)
        }

        val extractor = MediaExtractor()
        var codec: MediaCodec? = null

        try {
            extractor.setDataSource(audioFile.absolutePath)
            var trackIndex = -1
            var format: MediaFormat? = null

            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    trackIndex = i
                    format = f
                    break
                }
            }

            if (trackIndex == -1 || format == null) {
                Log.e(TAG, "No audio track found in: ${audioFile.name}")
                return FloatArray(0)
            }

            extractor.selectTrack(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME)!!
            val sourceSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val sourceChannels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

            val localCodec = MediaCodec.createDecoderByType(mime)
            codec = localCodec
            localCodec.configure(format, null, null, 0)
            localCodec.start()

            val allSamples = ArrayList<Short>()
            val bufferInfo = MediaCodec.BufferInfo()
            var isExtractorEOS = false
            var isDecoderEOS = false
            val timeoutUs = 10000L

            while (!isDecoderEOS) {
                if (!isExtractorEOS) {
                    val inputBufferIndex = localCodec.dequeueInputBuffer(timeoutUs)
                    if (inputBufferIndex >= 0) {
                        val inputBuffer = localCodec.getInputBuffer(inputBufferIndex)!!
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)

                        if (sampleSize < 0) {
                            localCodec.queueInputBuffer(
                                inputBufferIndex, 0, 0, 0L,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            isExtractorEOS = true
                        } else {
                            localCodec.queueInputBuffer(
                                inputBufferIndex, 0, sampleSize, extractor.sampleTime, 0
                            )
                            extractor.advance()
                        }
                    }
                }

                val outputBufferIndex = localCodec.dequeueOutputBuffer(bufferInfo, timeoutUs)
                if (outputBufferIndex >= 0) {
                    val outputBuffer = localCodec.getOutputBuffer(outputBufferIndex)!!
                    outputBuffer.order(ByteOrder.nativeOrder())

                    val shortBuffer = outputBuffer.asShortBuffer()
                    val count = shortBuffer.remaining()
                    
                    // M4A streams might be stereo; we downmix to mono by averaging channels
                    if (sourceChannels == 2) {
                        for (i in 0 until count step 2) {
                            if (i + 1 < count) {
                                val left = shortBuffer.get(i).toInt()
                                val right = shortBuffer.get(i + 1).toInt()
                                allSamples.add(((left + right) / 2).toShort())
                            }
                        }
                    } else {
                        // Already mono or other, just read sequentially
                        for (i in 0 until count) {
                            allSamples.add(shortBuffer.get(i))
                        }
                    }

                    localCodec.releaseOutputBuffer(outputBufferIndex, false)

                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        isDecoderEOS = true
                    }
                } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    Log.d(TAG, "Decoder output format changed: ${localCodec.outputFormat}")
                }
            }

            // Resample & normalize the raw PCM short samples
            return resampleAndNormalize(allSamples.toShortArray(), sourceSampleRate)

        } catch (e: Exception) {
            Log.e(TAG, "Error decoding audio file ${audioFile.name}", e)
            return FloatArray(0)
        } finally {
            try {
                codec?.stop()
                codec?.release()
            } catch (_: Exception) {}
            try {
                extractor.release()
            } catch (_: Exception) {}
        }
    }

    /**
     * Resamples raw mono PCM from source rate to 16kHz, and normalizes it to [-1.0f, 1.0f].
     */
    private fun resampleAndNormalize(inputSamples: ShortArray, sourceRate: Int): FloatArray {
        if (inputSamples.isEmpty()) return FloatArray(0)

        val outputSamples: FloatArray
        if (sourceRate == TARGET_SAMPLE_RATE) {
            // Already 16000Hz, only normalize
            outputSamples = FloatArray(inputSamples.size)
            for (i in inputSamples.indices) {
                outputSamples[i] = inputSamples[i] / 32768.0f
            }
        } else {
            // Resample from sourceRate to 16000Hz using linear interpolation
            val ratio = sourceRate.toDouble() / TARGET_SAMPLE_RATE.toDouble()
            val outputSize = (inputSamples.size / ratio).toInt()
            outputSamples = FloatArray(outputSize)

            for (i in 0 until outputSize) {
                val sourceIndex = i * ratio
                val leftIndex = sourceIndex.toInt()
                val rightIndex = (leftIndex + 1).coerceAtMost(inputSamples.size - 1)
                val weight = sourceIndex - leftIndex

                val leftSample = inputSamples[leftIndex].toFloat()
                val rightSample = inputSamples[rightIndex].toFloat()
                
                // Interpolate
                val interpolated = leftSample + weight.toFloat() * (rightSample - leftSample)
                // Normalize
                outputSamples[i] = interpolated / 32768.0f
            }
        }
        return outputSamples
    }
}
