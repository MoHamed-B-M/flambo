package com.pranshulgg.recordmaster.core.audio

import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Streams 16-bit PCM frames out of a canonical WAV file.
 *
 * [targetSampleRate] lets the Opus path normalize any capture rate to the
 * 48 kHz rate its Ogg container requires without changing the frozen capture
 * config. This is pure JVM so the frame math can be unit-tested locally.
 */
class WavPcmReader(
    file: File,
    targetSampleRate: Int? = null
) : Closeable {

    val info: WavAudio.Info = WavAudio.inspect(file)
        ?: throw IllegalArgumentException("Not a complete 16-bit PCM WAV file")

    private val sourceRate = info.sampleRate
    private val outputRate = targetSampleRate ?: sourceRate
    private val resample = targetSampleRate != null && targetSampleRate != sourceRate
    private val channels = info.channels
    private val bytesPerFrame = channels * 2L
    private val sourceFrames = info.dataBytes / bytesPerFrame

    private val raf = RandomAccessFile(file, "r")
    private var remainingBytes = info.dataBytes
    private var outputFrame = 0L

    private val window = arrayOf(ShortArray(channels), ShortArray(channels))
    private var windowStart = -1L
    private var sourceEnded = false

    init {
        require(outputRate > 0) { "Sample rate must be positive" }
        raf.seek(info.dataOffset)
    }

    /** Reads up to [maxFrames] frames into interleaved [out], returning frames read. */
    fun readFrames(out: ShortArray, maxFrames: Int): Int {
        require(maxFrames >= 0 && out.size >= maxFrames * channels)
        if (maxFrames == 0) return 0
        return if (resample) readResampled(out, maxFrames) else readDirect(out, maxFrames)
    }

    private fun readDirect(out: ShortArray, maxFrames: Int): Int {
        val frames = minOf(maxFrames.toLong(), remainingBytes / bytesPerFrame).toInt()
        if (frames <= 0) return 0
        val bytes = frames * channels * 2
        val buffer = ByteArray(bytes)
        raf.readFully(buffer)
        remainingBytes -= bytes.toLong()
        for (index in 0 until frames * channels) {
            out[index] = shortAt(buffer, index * 2)
        }
        return frames
    }

    private fun readResampled(out: ShortArray, maxFrames: Int): Int {
        var written = 0
        while (written < maxFrames) {
            val position = (outputFrame + written).toDouble() * sourceRate / outputRate
            // Never invent samples past the last real frame; encoders rely on
            // a zero return to observe end of input.
            if (position >= sourceFrames) break
            val base = floor(position).toLong()
            ensureWindow(base)
            if (windowStart < 0) break
            val fraction = (position - base).toFloat()
            val offset = written * channels
            for (channel in 0 until channels) {
                val first = window[0][channel].toInt()
                val second = window[1][channel].toInt()
                out[offset + channel] = (first + (second - first) * fraction)
                    .roundToInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    .toShort()
            }
            written++
        }
        outputFrame += written.toLong()
        return written
    }

    /** Keeps a two-frame window positioned at [base]. */
    private fun ensureWindow(base: Long) {
        if (windowStart < 0) {
            val first = readFrame() ?: return
            val second = readFrame()
            window[0] = first
            window[1] = second ?: first.copyOf()
            sourceEnded = second == null
            windowStart = 0L
        }
        while (windowStart < base) {
            val next = if (sourceEnded) null else readFrame()
            if (next == null) {
                sourceEnded = true
                window[0] = window[1].copyOf()
                window[1] = window[0].copyOf()
            } else {
                window[0] = window[1]
                window[1] = next
            }
            windowStart++
        }
    }

    private fun readFrame(): ShortArray? {
        if (remainingBytes < bytesPerFrame) return null
        val buffer = ByteArray(channels * 2)
        raf.readFully(buffer)
        remainingBytes -= buffer.size.toLong()
        return ShortArray(channels) { channel -> shortAt(buffer, channel * 2) }
    }

    private fun shortAt(buffer: ByteArray, offset: Int): Short =
        ((buffer[offset].toInt() and 0xFF) or ((buffer[offset + 1].toInt() and 0xFF) shl 8)).toShort()

    override fun close() {
        runCatching { raf.close() }
    }
}
