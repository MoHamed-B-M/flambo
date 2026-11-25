package com.pranshulgg.recordmaster.core.audio

import net.sourceforge.javaflacencoder.EncodingConfiguration
import net.sourceforge.javaflacencoder.FLACEncoder
import net.sourceforge.javaflacencoder.FLACFileOutputStream
import net.sourceforge.javaflacencoder.StreamConfiguration
import java.io.File

/**
 * Final-only FLAC encode from the canonical WAV produced by PCM capture.
 * Uses the low-level [FLACEncoder] rather than `FLAC_FileEncoder`, which
 * depends on `javax.sound.sampled` and is unusable on Android.
 */
object FlacFinalEncoder {

    private const val BLOCK_FRAMES = 4096

    fun encode(source: File, target: File): Boolean {
        val reader = WavPcmReader(source)
        var stream: FLACFileOutputStream? = null
        try {
            val config = StreamConfiguration(
                reader.info.channels,
                BLOCK_FRAMES,
                BLOCK_FRAMES,
                reader.info.sampleRate,
                16
            )
            if (!config.isValid) return false

            val encoder = FLACEncoder()
            encoder.setThreadCount(0)
            if (!encoder.setStreamConfiguration(config)) return false
            encoder.setEncodingConfiguration(EncodingConfiguration())

            if (target.exists() && !target.delete()) return false
            stream = FLACFileOutputStream(target)
            encoder.setOutputStream(stream)
            encoder.openFLACStream()

            val shorts = ShortArray(BLOCK_FRAMES * reader.info.channels)
            val samples = IntArray(shorts.size)
            var sawSamples = false
            while (true) {
                val frames = reader.readFrames(shorts, BLOCK_FRAMES)
                if (frames <= 0) break
                sawSamples = true
                val count = frames * reader.info.channels
                for (index in 0 until count) samples[index] = shorts[index].toInt()
                encoder.addSamples(samples, frames)
                encoder.encodeSamples(encoder.samplesAvailableToEncode(), false)
            }
            // An empty take still needs one frame so the stream can be closed.
            if (!sawSamples) {
                samples.fill(0)
                encoder.addSamples(samples, 1)
            }
            encoder.encodeSamples(encoder.samplesAvailableToEncode(), true)
            return target.length() > 0L
        } catch (_: Exception) {
            runCatching { target.delete() }
            return false
        } finally {
            runCatching { stream?.close() }
            reader.close()
        }
    }
}
