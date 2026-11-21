package com.pranshulgg.recordmaster.core.audio

import java.io.File
import java.io.RandomAccessFile

/**
 * Pure-JVM helpers for the canonical 44-byte PCM WAV format used by recording
 * chunks. Keeping this free of Android imports makes the header and merge logic
 * unit-testable on the host JVM.
 */
object WavAudio {

    data class Info(
        val sampleRate: Int,
        val channels: Int,
        val dataOffset: Long,
        val dataBytes: Long
    )

    private const val HEADER_SIZE = 44
    private const val COPY_BUFFER_SIZE = 64 * 1024

    /**
     * Writes the RIFF/WAVE header at the start of an existing file. The file is
     * never truncated, so a payload that was appended after a reserved 44-byte
     * header stays intact.
     */
    fun writeHeader(file: File, dataBytes: Long, sampleRate: Int, channels: Int) {
        val header = ByteArray(HEADER_SIZE)
        writeAscii(header, 0, "RIFF")
        writeU32(header, 4, 36L + dataBytes)
        writeAscii(header, 8, "WAVE")
        writeAscii(header, 12, "fmt ")
        writeU32(header, 16, 16L) // PCM fmt chunk size
        writeU16(header, 20, 1) // WAVE_FORMAT_PCM
        writeU16(header, 22, channels)
        writeU32(header, 24, sampleRate.toLong())
        writeU32(header, 28, sampleRate.toLong() * channels * 2L) // byte rate
        writeU16(header, 32, channels * 2) // block align
        writeU16(header, 34, 16) // bits per sample
        writeAscii(header, 36, "data")
        writeU32(header, 40, dataBytes)

        RandomAccessFile(file, "rw").use { raf ->
            raf.seek(0L)
            raf.write(header)
        }
    }

    /**
     * Validates a PCM WAV file and reports where its sample data lives. Returns
     * null for anything that is not a complete, 16-bit PCM WAV file.
     */
    fun inspect(file: File): Info? {
        if (!file.exists() || file.length() < HEADER_SIZE) return null
        return try {
            RandomAccessFile(file, "r").use { raf ->
                val length = raf.length()
                if (length < HEADER_SIZE) return null

                val base = ByteArray(HEADER_SIZE)
                raf.seek(0L)
                raf.readFully(base)
                if (ascii(base, 0, 4) != "RIFF" || ascii(base, 8, 4) != "WAVE") return null

                var position = 12L
                var sampleRate = -1
                var channels = -1
                while (position + 8L <= length) {
                    raf.seek(position)
                    val chunkHeader = ByteArray(8)
                    raf.readFully(chunkHeader)
                    val id = ascii(chunkHeader, 0, 4)
                    val size = readU32(chunkHeader, 4)
                    val body = position + 8L

                    when (id) {
                        "fmt " -> {
                            if (size < 16L) return null
                            val fmt = ByteArray(16)
                            raf.readFully(fmt)
                            val format = readU16(fmt, 0)
                            val fmtChannels = readU16(fmt, 2)
                            val fmtRate = readU32(fmt, 4).toInt()
                            val bitsPerSample = readU16(fmt, 14)
                            if (format != 1 || fmtChannels <= 0 || fmtRate <= 0 || bitsPerSample != 16) {
                                return null
                            }
                            sampleRate = fmtRate
                            channels = fmtChannels
                        }

                        "data" -> {
                            if (sampleRate <= 0 || channels <= 0) return null
                            if (body + size > length) return null
                            return Info(sampleRate, channels, body, size)
                        }
                    }

                    val next = body + size + (size and 1L) // chunks are word-aligned
                    if (next <= position) return null
                    position = next
                }
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Concatenates the PCM payloads of equally formatted WAV files into one
     * WAV file. Any invalid input, format mismatch, or I/O failure removes the
     * output and returns false.
     */
    fun merge(files: List<File>, output: File): Boolean {
        fun fail(): Boolean {
            runCatching { output.delete() }
            return false
        }

        if (files.isEmpty()) return fail()

        val infos = ArrayList<Info>(files.size)
        var expectedRate = -1
        var expectedChannels = -1
        var totalBytes = 0L
        for (file in files) {
            val info = inspect(file) ?: return fail()
            if (expectedRate < 0) {
                expectedRate = info.sampleRate
                expectedChannels = info.channels
            } else if (info.sampleRate != expectedRate || info.channels != expectedChannels) {
                return fail()
            }
            // The legacy WAV header stores the data size in a signed 32-bit field.
            if (info.dataBytes > Int.MAX_VALUE - totalBytes) return fail()
            totalBytes += info.dataBytes
            infos += info
        }

        output.parentFile?.let { parent ->
            if (!parent.exists() && !parent.mkdirs()) return fail()
        }
        if (output.exists() && !output.delete()) return fail()

        var out: RandomAccessFile? = null
        var success = false
        try {
            out = RandomAccessFile(output, "rw")
            out.setLength(HEADER_SIZE.toLong()) // reserve space for the final header
            out.seek(HEADER_SIZE.toLong())

            val buffer = ByteArray(COPY_BUFFER_SIZE)
            for (index in files.indices) {
                val source = RandomAccessFile(files[index], "r")
                try {
                    source.seek(infos[index].dataOffset)
                    var remaining = infos[index].dataBytes
                    while (remaining > 0L) {
                        val requested = minOf(remaining, buffer.size.toLong()).toInt()
                        val read = source.read(buffer, 0, requested)
                        if (read <= 0) throw IllegalStateException("Truncated WAV data")
                        out.write(buffer, 0, read)
                        remaining -= read.toLong()
                    }
                } finally {
                    source.close()
                }
            }

            out.close()
            out = null
            writeHeader(output, totalBytes, expectedRate, expectedChannels)
            success = true
        } catch (_: Exception) {
            success = false
        } finally {
            runCatching { out?.close() }
        }

        return if (success) true else fail()
    }

    private fun writeAscii(target: ByteArray, offset: Int, value: String) {
        for (index in value.indices) {
            target[offset + index] = value[index].code.toByte()
        }
    }

    private fun writeU16(target: ByteArray, offset: Int, value: Int) {
        target[offset] = (value and 0xFF).toByte()
        target[offset + 1] = ((value shr 8) and 0xFF).toByte()
    }

    private fun writeU32(target: ByteArray, offset: Int, value: Long) {
        target[offset] = (value and 0xFFL).toByte()
        target[offset + 1] = ((value shr 8) and 0xFFL).toByte()
        target[offset + 2] = ((value shr 16) and 0xFFL).toByte()
        target[offset + 3] = ((value shr 24) and 0xFFL).toByte()
    }

    private fun ascii(source: ByteArray, offset: Int, count: Int): String =
        String(CharArray(count) { source[offset + it].toInt().toChar() })

    private fun readU16(source: ByteArray, offset: Int): Int =
        (source[offset].toInt() and 0xFF) or ((source[offset + 1].toInt() and 0xFF) shl 8)

    private fun readU32(source: ByteArray, offset: Int): Long =
        (source[offset].toLong() and 0xFFL) or
            ((source[offset + 1].toLong() and 0xFFL) shl 8) or
            ((source[offset + 2].toLong() and 0xFFL) shl 16) or
            ((source[offset + 3].toLong() and 0xFFL) shl 24)
}
