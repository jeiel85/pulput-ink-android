package io.pulpit.ink.ui.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Decodes compressed audio files (like .m4a AAC) into 16kHz mono Float PCM arrays.
 * Applies software-based DSP filters (IIR highpass, lowpass, and windowed RMS speech compressor)
 * matching the user's desktop audio enhancement presets to maximize Whisper transcription accuracy.
 */
object AudioDecoder {
    private const val TAG = "AudioDecoder"
    private const val TARGET_SAMPLE_RATE = 16000

    /**
     * Decode an audio file and resample it to 16kHz mono float array in the range [-1.0f, 1.0f].
     * Applies the selected audio preprocessing preset from SharedPreferences.
     */
    fun decodeToPcm(audioFile: File, context: Context): FloatArray {
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
            val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val info = MediaCodec.BufferInfo()
            var allSamples = ArrayList<Short>()
            var isInputEOS = false
            var isDecoderEOS = false

            val sourceChannels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val sourceSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            Log.i(TAG, "Starting decoding. File: ${audioFile.name}, channels: $sourceChannels, sample rate: $sourceSampleRate")

            var decodedChannels = sourceChannels
            var decodedSampleRate = sourceSampleRate

            while (!isDecoderEOS) {
                if (!isInputEOS) {
                    val inputBufferIndex = codec.dequeueInputBuffer(10000)
                    if (inputBufferIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputBufferIndex)
                        if (inputBuffer != null) {
                            val sampleSize = extractor.readSampleData(inputBuffer, 0)
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                isInputEOS = true
                            } else {
                                codec.queueInputBuffer(inputBufferIndex, 0, sampleSize, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                }

                val outputBufferIndex = codec.dequeueOutputBuffer(info, 10000)
                if (outputBufferIndex >= 0) {
                    val outputBuffer = codec.getOutputBuffer(outputBufferIndex)
                    if (outputBuffer != null && info.size > 0) {
                        outputBuffer.position(info.offset)
                        outputBuffer.order(ByteOrder.nativeOrder())
                        
                        // Copy samples based on layout
                        val count = info.size / 2 // 16-bit short samples
                        val pcmShorts = ShortArray(count)
                        outputBuffer.asShortBuffer().get(pcmShorts)

                        // Downmix dynamically based on active decoded channels in the PCM stream
                        if (decodedChannels > 1) {
                            for (i in 0 until count step decodedChannels) {
                                var sum = 0
                                for (c in 0 until decodedChannels) {
                                    sum += pcmShorts[i + c]
                                }
                                allSamples.add((sum / decodedChannels).toShort())
                            }
                        } else {
                            for (s in pcmShorts) {
                                allSamples.add(s)
                            }
                        }
                    }
                    codec.releaseOutputBuffer(outputBufferIndex, false)

                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        Log.d(TAG, "Decoder output EOS reached.")
                        isDecoderEOS = true
                    }
                } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val newFormat = codec.outputFormat
                    if (newFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                        decodedChannels = newFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    }
                    if (newFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                        decodedSampleRate = newFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    }
                    Log.d(TAG, "Decoder output format changed: $newFormat, channels: $decodedChannels, rate: $decodedSampleRate")
                }
            }

            // 1. Resample & normalize using actual decoded sample rate
            val rawPcm = resampleAndNormalize(allSamples.toShortArray(), decodedSampleRate)
            
            // 2. Read selected audio enhancement preset from SharedPreferences
            val prefs = context.getSharedPreferences("whisper_prefs", Context.MODE_PRIVATE)
            val preset = prefs.getString("selected_audio_preset", "sermon") ?: "sermon"
            
            Log.i(TAG, "Applying audio enhancement DSP preset: '$preset' to PCM data (size: ${rawPcm.size})")
            val processedPcm = when (preset) {
                "stt_basic" -> {
                    val hp = applyHighPassFilter(rawPcm, 80f, TARGET_SAMPLE_RATE.toFloat())
                    applyLowPassFilter(hp, 7800f, TARGET_SAMPLE_RATE.toFloat())
                }
                "sermon" -> {
                    val hp = applyHighPassFilter(rawPcm, 80f, TARGET_SAMPLE_RATE.toFloat())
                    val lp = applyLowPassFilter(hp, 7500f, TARGET_SAMPLE_RATE.toFloat())
                    applyDynamicGain(lp, TARGET_SAMPLE_RATE.toFloat(), maxGain = 4.0f, minRms = 0.01f)
                }
                "noisy" -> {
                    val hp = applyHighPassFilter(rawPcm, 100f, TARGET_SAMPLE_RATE.toFloat())
                    val lp = applyLowPassFilter(hp, 6500f, TARGET_SAMPLE_RATE.toFloat())
                    applyDynamicGain(lp, TARGET_SAMPLE_RATE.toFloat(), maxGain = 5.0f, minRms = 0.02f)
                }
                else -> rawPcm // "none"
            }
            
            Log.d(TAG, "Audio enhancement completed. Final samples: ${processedPcm.size}")
            return processedPcm

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

    /**
     * 2nd-order IIR Butterworth Highpass Filter to remove sub-bass rumble (< 80Hz/100Hz).
     */
    private fun applyHighPassFilter(pcm: FloatArray, cutoff: Float, sampleRate: Float): FloatArray {
        val omega = (2.0 * Math.PI * cutoff / sampleRate).toFloat()
        val gamma = Math.tan(omega / 2.0).toFloat()
        val sqrt2 = Math.sqrt(2.0).toFloat()
        
        val d = gamma * gamma + sqrt2 * gamma + 1f
        val b0 = 1f / d
        val b1 = -2f / d
        val b2 = 1f / d
        val a1 = 2f * (gamma * gamma - 1f) / d
        val a2 = (gamma * gamma - sqrt2 * gamma + 1f) / d
        
        val output = FloatArray(pcm.size)
        var x1 = 0f
        var x2 = 0f
        var y1 = 0f
        var y2 = 0f
        
        for (i in pcm.indices) {
            val x = pcm[i]
            val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            
            x2 = x1
            x1 = x
            y2 = y1
            y1 = y
            
            output[i] = y
        }
        return output
    }

    /**
     * 2nd-order IIR Butterworth Lowpass Filter to remove high-frequency static hiss (> 6.5kHz/7.5kHz).
     */
    private fun applyLowPassFilter(pcm: FloatArray, cutoff: Float, sampleRate: Float): FloatArray {
        val omega = (2.0 * Math.PI * cutoff / sampleRate).toFloat()
        val gamma = Math.tan(omega / 2.0).toFloat()
        val sqrt2 = Math.sqrt(2.0).toFloat()
        
        val d = gamma * gamma + sqrt2 * gamma + 1f
        val b0 = (gamma * gamma) / d
        val b1 = (2f * gamma * gamma) / d
        val b2 = (gamma * gamma) / d
        val a1 = 2f * (gamma * gamma - 1f) / d
        val a2 = (gamma * gamma - sqrt2 * gamma + 1f) / d
        
        val output = FloatArray(pcm.size)
        var x1 = 0f
        var x2 = 0f
        var y1 = 0f
        var y2 = 0f
        
        for (i in pcm.indices) {
            val x = pcm[i]
            val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            
            x2 = x1
            x1 = x
            y2 = y1
            y1 = y
            
            output[i] = y
        }
        return output
    }

    /**
     * Sliding window Speech Gain dynamic compressor/limiter to balance volume dynamically.
     */
    private fun applyDynamicGain(
        pcm: FloatArray, 
        sampleRate: Float, 
        maxGain: Float, 
        minRms: Float
    ): FloatArray {
        val output = FloatArray(pcm.size)
        val windowSize = (sampleRate * 0.5f).toInt() // 500ms sliding window
        if (pcm.size < windowSize) return pcm.clone()
        
        var smoothGain = 1.0f
        val targetRms = 0.15f // target RMS level for speech
        val alpha = 0.05f     // gain smoothing factor
        
        for (i in pcm.indices step (windowSize / 2)) {
            val end = (i + windowSize).coerceAtMost(pcm.size)
            val len = end - i
            if (len <= 0) break
            
            var sumSquare = 0f
            for (j in i until end) {
                sumSquare += pcm[j] * pcm[j]
            }
            val rms = Math.sqrt((sumSquare / len).toDouble()).toFloat()
            
            val targetGain = when {
                rms < minRms -> 1.0f // don't amplify silence noise
                else -> (targetRms / rms).coerceIn(1.0f, maxGain)
            }
            
            for (j in i until end) {
                smoothGain += alpha * (targetGain - smoothGain)
                output[j] = (pcm[j] * smoothGain).coerceIn(-1.0f, 1.0f)
            }
        }
        
        return output
    }
}
