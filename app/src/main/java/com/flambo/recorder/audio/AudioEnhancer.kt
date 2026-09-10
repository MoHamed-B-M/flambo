package com.flambo.recorder.audio

import com.flambo.recorder.stt.PcmDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.min

// Offline "Clean audio": hushes background noise and evens out levels using
// plain Kotlin DSP — no native libraries, no cloud, works on every device.
// Pipeline: DC-block → adaptive noise gate → loudness normalize → WAV out.
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

object AudioEnhancer {

    // Enhancement holds full PCM in memory — 10 minutes of stereo 48 kHz is
    // ~115 MB, the most we'll ask of a phone.
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
            val dest = File(input.parentFile, input.nameWithoutExtension + "_enhanced.wav")
            val outBytes = out.size * 2L + 44
            val free = dest.parentFile?.usableSpace ?: Long.MAX_VALUE
            require(free > outBytes + 10L * 1024 * 1024) { "Not enough free space for the cleaned file." }
            writeWav(dest, out, pcm.sampleRate, pcm.channels)
            onProgress(1f)
            Result.success(EnhancedAudio(dest, peaks))
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
        val ch = pcm.channels.coerceAtLeast(1)
        val rate = pcm.sampleRate.coerceAtLeast(8000)
        val frameLen = (rate / 50).coerceAtLeast(64) // ~20 ms frames
        val frames = pcm.frames
        val frameCount = (frames + frameLen - 1) / frameLen

        // Pass 1: per-frame peak levels to learn the noise floor (15th percentile).
        val levels = DoubleArray(frameCount)
        var fi = 0
        while (fi < frameCount) {
            var peak = 0
            val start = fi * frameLen * ch
            val end = minOf(start + frameLen * ch, pcm.samples.size)
            var i = start
            while (i < end) {
                val v = abs(pcm.samples[i].toInt())
                if (v > peak) peak = v
                i++
            }
            levels[fi] = 20 * log10((peak.coerceAtLeast(1)) / 32768.0)
            fi++
            if (fi % 64 == 0) {
                kotlinx.coroutines.ensureActive()
                onProgress(fi.toFloat() / frameCount * 0.5f)
            }
        }
        val sorted = levels.sorted()
        val floorDb = sorted[(sorted.size * 0.15).toInt().coerceIn(sorted.indices)]
            .coerceIn(-70.0, -30.0)
        val threshold = floorDb + strength.gateMarginDb

        // Pass 2: DC-block + smoothed gate, shared decision across channels so
        // stereo music doesn't wobble side to side.
        val attack = Math.exp(-1.0 / (rate * 0.005)) // ~5 ms open
        val release = Math.exp(-1.0 / (rate * 0.150)) // ~150 ms close
        var gain = 0.0
        val xm1 = DoubleArray(ch)
        val ym1 = DoubleArray(ch)
        var globalPeak = 0.0

        fi = 0
        while (fi < frameCount) {
            val start = fi * frameLen * ch
            val end = minOf(start + frameLen * ch, pcm.samples.size)
            // Gate target from this frame's level with a ±3 dB soft knee.
            val over = levels[fi] - threshold
            val target = when {
                over >= 3 -> 1.0
                over <= -3 -> strength.floorGain
                else -> {
                    val t = (over + 3) / 6.0
                    strength.floorGain + (1.0 - strength.floorGain) * t * t * (3 - 2 * t)
                }
            }
            var i = start
            var c = 0
            while (i < end) {
                gain = if (target > gain) {
                    target + (gain - target) * attack
                } else {
                    target + (gain - target) * release
                }
                val s = pcm.samples[i].toDouble() / 32768.0
                // One-pole DC blocker (~75 Hz) kills rumble and mic thumps.
                val y = s - xm1[c] + 0.992 * ym1[c]
                xm1[c] = s
                ym1[c] = y
                val v = y * gain
                out[i] = v.toFloat()
                val a = abs(v)
                if (a > globalPeak) globalPeak = a
                i++
                c = (c + 1) % ch
            }
            fi++
            if (fi % 64 == 0) {
                kotlinx.coroutines.ensureActive()
                onProgress(0.5f + fi.toFloat() / frameCount * 0.5f)
            }
        }

        // Pass 3: normalize to the strength's target peak (capped so near-silent
        // takes don't get blasted), with a gentle ceiling above 0.92 instead
        // of hard clipping.
        if (globalPeak > 1e-4) {
            val g = minOf(strength.normPeak / globalPeak, 4.0)
            var i = 0
            while (i < out.size) {
                val v = out[i] * g
                val a = abs(v)
                out[i] = if (a <= 0.92f) v
                else (if (v < 0) -1f else 1f) * minOf(0.92f + (a - 0.92f) * 0.15f, 0.99f)
                i++
            }
        }
        onProgress(1f)
    }

    private fun peaksOf(out: FloatArray, buckets: Int = 120): List<Float> {
        if (out.isEmpty()) return emptyList()
        val perBucket = (out.size / buckets).coerceAtLeast(1)
        return List(buckets) { b ->
            var peak = 0f
            val start = b * perBucket
            val end = minOf(start + perBucket, out.size)
            var i = start
            while (i < end) {
                val v = abs(out[i])
                if (v > peak) peak = v
                i++
            }
            peak.coerceIn(0f, 1f)
        }
    }

    private fun writeWav(dest: File, samples: FloatArray, sampleRate: Int, channels: Int) {
        val ch = channels.coerceAtLeast(1)
        val dataBytes = samples.size * 2L
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray())
        header.putInt((36 + dataBytes).toInt())
        header.put("WAVE".toByteArray())
        header.put("fmt ".toByteArray())
        header.putInt(16)
        header.putShort(1)
        header.putShort(ch.toShort())
        header.putInt(sampleRate)
        header.putInt(sampleRate * ch * 2)
        header.putShort((ch * 2).toShort())
        header.putShort(16)
        header.put("data".toByteArray())
        header.putInt(dataBytes.toInt())
        dest.outputStream().buffered(128 * 1024).use { stream ->
            stream.write(header.array())
            val chunk = ByteBuffer.allocate(8192 * 2).order(ByteOrder.LITTLE_ENDIAN)
            var i = 0
            while (i < samples.size) {
                chunk.clear()
                val end = minOf(i + 8192, samples.size)
                while (i < end) {
                    chunk.putShort((samples[i] * 32767.0).toInt().coerceIn(-32768, 32767).toShort())
                    i++
                }
                stream.write(chunk.array(), 0, chunk.position())
            }
        }
    }
}
