package com.pranshulgg.recordmaster.core.audio

import com.github.axet.vorbisjni.Vorbis
import java.io.File
import java.io.FileOutputStream

/**
 * Final-only Vorbis encode. The bundled JNI wrapper emits complete Ogg pages
 * from [encode], so writing them in order produces a playable stream.
 * `encode_float` is unusable in the published artifact; use interleaved shorts
 * and a fixed VBR quality instead of a bitrate.
 */
object VorbisFinalEncoder {

    private const val BLOCK_FRAMES = 2048
    private const val VBR_QUALITY = 0.5f

    fun encode(source: File, target: File): Boolean {
        val reader = WavPcmReader(source)
        val encoder = Vorbis()
        var opened = false
        try {
            encoder.open(reader.info.channels, reader.info.sampleRate, VBR_QUALITY)
            opened = true
            if (target.exists() && !target.delete()) return false

            FileOutputStream(target).use { out ->
                val shorts = ShortArray(BLOCK_FRAMES * reader.info.channels)
                while (true) {
                    val frames = reader.readFrames(shorts, BLOCK_FRAMES)
                    if (frames <= 0) break
                    val bytes = encoder.encode(shorts, 0, frames * reader.info.channels)
                    if (bytes.isNotEmpty()) out.write(bytes)
                }
                // A zero-length encode tells the JNI wrapper to flush EOF.
                val tail = encoder.encode(ShortArray(0), 0, 0)
                if (tail.isNotEmpty()) out.write(tail)
                out.flush()
            }
            return target.length() > 0L
        } catch (_: Exception) {
            runCatching { target.delete() }
            return false
        } finally {
            if (opened) runCatching { encoder.close() }
            reader.close()
        }
    }
}
