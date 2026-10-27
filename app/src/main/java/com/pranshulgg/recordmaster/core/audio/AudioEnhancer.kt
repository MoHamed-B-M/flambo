package com.pranshulgg.recordmaster.core.audio

import com.pranshulgg.recordmaster.core.stt.PcmDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.log10

enum class EnhanceStrength(
    val label: String,
    val description: String,
    internal val gateMarginDb: Double,
    internal val floorGain: Double,
    internal val normPeak: Double
) {
    LIGHT("Light", "Gentle cleanup that keeps the room tone", 9.0, 0.25, 0.84),
    BALANCED("Balanced", "Hushes background hiss, keeps voice natural", 12.0, 0.12, 0.87),
    STRONG("Strong", "Maximum hush for noisy takes", 16.0, 0.05, 0.89);

    companion object {
        fun fromPref(value: String): EnhanceStrength =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: BALANCED
    }
}

data class EnhancedAudio(val file: File, val peaks: List<Float>)

/**
 * Offline cleanup/leveling. Output stays next to the source and is excluded
 * from the library index by [com.pranshulgg.recordmaster.core.data.RecordingRepository].
 */
object AudioEnhancer {

    const val MAX_MINUTES = 10

    suspend fun enhance(
        input: File,
        strength: EnhanceStrength,
        onProgress: (Float) -> Unit = {}
    ): Result<EnhancedAudio> = withContext(Dispatchers.IO) {
        try {
            onProgress(0.05f)
            val pcm = PcmDecoder.decodeNative(input.absolutePath, MAX_MINUTES)
            onProgress(0.35f)
            require(pcm.frames > 0) { "No audio could be read from this recording." }

            val out = FloatArray(pcm.samples.size)
            process(pcm, strength, out) { onProgress(0.35f + 0.55f * it) }

            val peaks = peaksOf(out)
            val destination = File(
                input.parentFile,
                input.nameWithoutExtension + "_enhanced.wav"
            )
            val outputBytes = out.size * 2L + 44
            val free = destination.parentFile?.usableSpace ?: Long.MAX_VALUE
            require(free > outputBytes + 10L * 1024 * 1024) {
                "Not enough free space for the cleaned file."
            }
            writeWav(destination, out, pcm.sampleRate, pcm.channels)
            onProgress(1f)
            Result.success(EnhancedAudio(destination, peaks))
        } catch (e: Exception) {
            val message = e.message ?: "Enhancement failed."
            Result.failure(IllegalStateException(message))
        }
    }

    private suspend fun process(
        pcm: PcmDecoder.PcmAudio,
        strength: EnhanceStrength,
        out: FloatArray,
        onProgress: (Float) -> Unit
    ) {
        val channels = pcm.channels.coerceAtLeast(1)
        val rate = pcm.sampleRate.coerceAtLeast(8000)
        val frameLength = (rate / 50).coerceAtLeast(64)
        val frames = pcm.frames
        val frameCount = (frames + frameLength - 1) / frameLength

        val levels = DoubleArray(frameCount)
        var frameIndex = 0
        while (frameIndex < frameCount) {
            var peak = 0
            val start = frameIndex * frameLength * channels
            val end = minOf(start + frameLength * channels, pcm.samples.size)
            var index = start
            while (index < end) {
                val value = abs(pcm.samples[index].toInt())
                if (value > peak) peak = value
                index++
            }
            levels[frameIndex] = 20 * log10((peak.coerceAtLeast(1)) / 32768.0)
            frameIndex++
            if (frameIndex % 64 == 0) {
                currentCoroutineContext().ensureActive()
                onProgress(frameIndex.toFloat() / frameCount * 0.5f)
            }
        }

        val sorted = levels.sorted()
        val floorDb = sorted[(sorted.size * 0.15).toInt().coerceIn(sorted.indices)]
            .coerceIn(-70.0, -30.0)
        val threshold = floorDb + strength.gateMarginDb

        val attack = Math.exp(-1.0 / (rate * 0.005))
        val release = Math.exp(-1.0 / (rate * 0.150))
        var gain = 0.0
        val previousInput = DoubleArray(channels)
        val previousOutput = DoubleArray(channels)
        var globalPeak = 0.0

        frameIndex = 0
        while (frameIndex < frameCount) {
            val start = frameIndex * frameLength * channels
            val end = minOf(start + frameLength * channels, pcm.samples.size)

            val over = levels[frameIndex] - threshold
            val target = when {
                over >= 3 -> 1.0
                over <= -3 -> strength.floorGain
                else -> {
                    val t = (over + 3) / 6.0
                    strength.floorGain +
                        (1.0 - strength.floorGain) * t * t * (3 - 2 * t)
                }
            }

            var index = start
            var channel = 0
            while (index < end) {
                gain = if (target > gain) {
                    target + (gain - target) * attack
                } else {
                    target + (gain - target) * release
                }
                val sample = pcm.samples[index].toDouble() / 32768.0

                val filtered = sample - previousInput[channel] +
                    0.992 * previousOutput[channel]
                previousInput[channel] = sample
                previousOutput[channel] = filtered
                val value = filtered * gain
                out[index] = value.toFloat()
                val absolute = abs(value)
                if (absolute > globalPeak) globalPeak = absolute
                index++
                channel = (channel + 1) % channels
            }

            frameIndex++
            if (frameIndex % 64 == 0) {
                currentCoroutineContext().ensureActive()
                onProgress(0.5f + frameIndex.toFloat() / frameCount * 0.5f)
            }
        }

        if (globalPeak > 1e-4) {
            val normalization = minOf(strength.normPeak / globalPeak, 4.0).toFloat()
            var index = 0
            while (index < out.size) {
                val value = out[index] * normalization
                val absolute = abs(value)
                out[index] = if (absolute <= 0.92f) {
                    value
                } else {
                    val sign = if (value < 0) -1f else 1f
                    sign * minOf(0.92f + (absolute - 0.92f) * 0.15f, 0.99f)
                }
                index++
            }
        }
        onProgress(1f)
    }

    private fun peaksOf(out: FloatArray, buckets: Int = 120): List<Float> {
        if (out.isEmpty()) return emptyList()
        val perBucket = (out.size / buckets).coerceAtLeast(1)
        return List(buckets) { bucket ->
            var peak = 0f
            val start = bucket * perBucket
            val end = minOf(start + perBucket, out.size)
            var index = start
            while (index < end) {
                val value = abs(out[index])
                if (value > peak) peak = value
                index++
            }
            peak.coerceIn(0f, 1f)
        }
    }

    private fun writeWav(
        destination: File,
        samples: FloatArray,
        sampleRate: Int,
        channels: Int
    ) {
        val channelCount = channels.coerceAtLeast(1)
        val dataBytes = samples.size * 2L
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray())
        header.putInt((36 + dataBytes).toInt())
        header.put("WAVE".toByteArray())
        header.put("fmt ".toByteArray())
        header.putInt(16)
        header.putShort(1)
        header.putShort(channelCount.toShort())
        header.putInt(sampleRate)
        header.putInt(sampleRate * channelCount * 2)
        header.putShort((channelCount * 2).toShort())
        header.putShort(16)
        header.put("data".toByteArray())
        header.putInt(dataBytes.toInt())

        destination.outputStream().buffered(128 * 1024).use { stream ->
            stream.write(header.array())
            val chunk = ByteBuffer.allocate(8192 * 2).order(ByteOrder.LITTLE_ENDIAN)
            var index = 0
            while (index < samples.size) {
                chunk.clear()
                val end = minOf(index + 8192, samples.size)
                while (index < end) {
                    chunk.putShort(
                        (samples[index] * 32767.0).toInt()
                            .coerceIn(-32768, 32767)
                            .toShort()
                    )
                    index++
                }
                stream.write(chunk.array(), 0, chunk.position())
            }
        }
    }
}

