package com.pranshulgg.recordmaster

import com.pranshulgg.recordmaster.core.audio.WavAudio
import com.pranshulgg.recordmaster.core.audio.WavPcmReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileOutputStream

/** Pure JVM checks for the canonical WAV reader shared by the final encoders. */
class WavPcmReaderTest {

    private fun wavFile(rate: Int, channels: Int, frames: Int): File {
        val file = File.createTempFile("wav-reader", ".wav")
        val data = ByteArray(frames * channels * 2)
        for (frame in 0 until frames) {
            for (channel in 0 until channels) {
                val value = ((frame + 1) * 100 + channel * 1_000) and 0x7FFF
                val offset = (frame * channels + channel) * 2
                data[offset] = (value and 0xFF).toByte()
                data[offset + 1] = ((value shr 8) and 0xFF).toByte()
            }
        }
        FileOutputStream(file).use { out ->
            out.write(ByteArray(44))
            out.write(data)
        }
        WavAudio.writeHeader(file, data.size.toLong(), rate, channels)
        return file
    }

    @Test
    fun directReadReturnsInterleavedFramesUntilExhausted() {
        val file = wavFile(rate = 44_100, channels = 2, frames = 16)
        WavPcmReader(file).use { reader ->
            val out = ShortArray(12)
            assertEquals(6, reader.readFrames(out, 6))
            assertEquals(100, out[0].toInt())
            assertEquals(1_100, out[1].toInt())
            assertEquals(200, out[2].toInt())

            assertEquals(10, reader.readFrames(out, 10))
            assertEquals(0, reader.readFrames(out, 10))
        }
        file.delete()
    }

    @Test
    fun resamplingProducesTheExpectedFrameCountThenEnds() {
        val file = wavFile(rate = 44_100, channels = 1, frames = 4_410)
        WavPcmReader(file, targetSampleRate = 48_000).use { reader ->
            val out = ShortArray(4_800)
            assertEquals(4_800, reader.readFrames(out, 4_800))
            assertEquals(0, reader.readFrames(out, 4_800))
            assertTrue(out.all { it.toInt() in 0..Short.MAX_VALUE })
        }
        file.delete()
    }

    @Test
    fun invalidWavIsRejected() {
        val file = File.createTempFile("wav-invalid", ".wav")
        try {
            var rejected = false
            try {
                WavPcmReader(file)
            } catch (_: IllegalArgumentException) {
                rejected = true
            }
            assertTrue(rejected)
        } finally {
            file.delete()
        }
    }
}
