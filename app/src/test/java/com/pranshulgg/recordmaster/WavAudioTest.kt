package com.pranshulgg.recordmaster

import com.pranshulgg.recordmaster.core.audio.WavAudio
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.RandomAccessFile

/** Pure JVM checks for the canonical WAV header writer, inspector and merger. */
class WavAudioTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun writeHeaderThenInspectRoundTrips() {
        val pcm = pcmOf(0x01, 0x00, 0x34, 0x12, 0xFF, 0xFF, 0x00, 0x80)
        val mono = writeChunk("mono.wav", 44_100, 1, pcm)

        assertEquals((44 + pcm.size).toLong(), mono.length())
        val info = WavAudio.inspect(mono)
        assertNotNull(info)
        info!!
        assertEquals(44_100, info.sampleRate)
        assertEquals(1, info.channels)
        assertEquals(44L, info.dataOffset)
        assertEquals(pcm.size.toLong(), info.dataBytes)
        assertArrayEquals(pcm, readPcm(mono, info))

        RandomAccessFile(mono, "r").use { raf ->
            val header = ByteArray(44)
            raf.readFully(header)
            assertEquals("RIFF", String(header, 0, 4))
            assertEquals("WAVE", String(header, 8, 4))
            assertEquals("fmt ", String(header, 12, 4))
            assertEquals("data", String(header, 36, 4))
            assertEquals(1, u16(header, 20)) // WAVE_FORMAT_PCM
            assertEquals(1, u16(header, 22)) // channels
            assertEquals(44_100, u32(header, 24).toInt())
            assertEquals(88_200, u32(header, 28).toInt()) // byte rate
            assertEquals(2, u16(header, 32)) // block align
            assertEquals(16, u16(header, 34))
            assertEquals(pcm.size, u32(header, 40).toInt())
        }

        val stereo = writeChunk("stereo.wav", 48_000, 2, pcmOf(1, 0, 2, 0, 3, 0, 4, 0))
        val stereoInfo = WavAudio.inspect(stereo)!!
        assertEquals(48_000, stereoInfo.sampleRate)
        assertEquals(2, stereoInfo.channels)
    }

    @Test
    fun mergePreservesFormatAndConcatenatesPcmInOrder() {
        val firstPcm = pcmOf(1, 0, 2, 0, 3, 0, 4, 0)
        val secondPcm = pcmOf(5, 0, 6, 0, 7, 0, 8, 0)
        val first = writeChunk("first.wav", 16_000, 1, firstPcm)
        val second = writeChunk("second.wav", 16_000, 1, secondPcm)
        val output = File(temporaryFolder.root, "merged.wav")

        assertTrue(WavAudio.merge(listOf(first, second), output))

        val info = WavAudio.inspect(output)!!
        assertEquals(16_000, info.sampleRate)
        assertEquals(1, info.channels)
        assertEquals(44L, info.dataOffset)
        assertEquals((firstPcm.size + secondPcm.size).toLong(), info.dataBytes)
        assertEquals((44 + firstPcm.size + secondPcm.size).toLong(), output.length())
        assertArrayEquals(firstPcm + secondPcm, readPcm(output, info))

        // The source chunks must not be modified by the merge.
        assertArrayEquals(firstPcm, readPcm(first, WavAudio.inspect(first)!!))
        assertArrayEquals(secondPcm, readPcm(second, WavAudio.inspect(second)!!))
    }

    @Test
    fun mergeReplacesPreExistingOutput() {
        val pcm = pcmOf(11, 0, 22, 0, 33, 0)
        val chunk = writeChunk("solo.wav", 8_000, 1, pcm)
        val output = File(temporaryFolder.root, "replaced.wav")
        output.writeBytes(ByteArray(200) { 0x7F.toByte() })

        assertTrue(WavAudio.merge(listOf(chunk), output))
        assertArrayEquals(pcm, readPcm(output, WavAudio.inspect(output)!!))
    }

    @Test
    fun inspectRejectsMalformedAndTruncatedFiles() {
        val empty = temporaryFolder.newFile("empty.wav")
        assertNull(WavAudio.inspect(empty))

        val badMagic = writeChunk("bad-magic.wav", 44_100, 1, pcmOf(9, 0))
        RandomAccessFile(badMagic, "rw").use { raf ->
            raf.seek(0L)
            raf.write(byteArrayOf(0x58, 0x58, 0x58, 0x58)) // "XXXX"
        }
        assertNull(WavAudio.inspect(badMagic))

        val nonPcm = writeChunk("float.wav", 44_100, 1, pcmOf(9, 0))
        RandomAccessFile(nonPcm, "rw").use { raf ->
            raf.seek(20L)
            raf.write(byteArrayOf(3, 0)) // IEEE float, not PCM
        }
        assertNull(WavAudio.inspect(nonPcm))

        val wrongBits = writeChunk("wrong-bits.wav", 44_100, 1, pcmOf(9, 0))
        RandomAccessFile(wrongBits, "rw").use { raf ->
            raf.seek(34L)
            raf.write(byteArrayOf(8, 0)) // 8-bit
        }
        assertNull(WavAudio.inspect(wrongBits))

        val truncated = writeChunk("truncated.wav", 44_100, 1, pcmOf(1, 0, 2, 0, 3, 0, 4, 0))
        RandomAccessFile(truncated, "rw").use { it.setLength(46L) } // header still claims 8 data bytes
        assertNull(WavAudio.inspect(truncated))
    }

    @Test
    fun mergeRejectsInvalidAndMismatchedInputs() {
        val good = writeChunk("good.wav", 44_100, 2, pcmOf(1, 0, 2, 0, 3, 0, 4, 0))
        val bad = writeChunk("bad.wav", 44_100, 2, pcmOf(9, 0))
        RandomAccessFile(bad, "rw").use { raf -> raf.write(byteArrayOf(0x42, 0x41, 0x44, 0x00)) }

        val differentRate = writeChunk("rate.wav", 48_000, 2, pcmOf(5, 0, 6, 0, 7, 0, 8, 0))
        val differentChannels = writeChunk("channels.wav", 44_100, 1, pcmOf(5, 0, 6, 0))
        val empty = temporaryFolder.newFile("empty-input.wav")
        val output = File(temporaryFolder.root, "bad-merge.wav")
        output.writeBytes(ByteArray(64) { 0x7F.toByte() }) // stale output must be removed

        assertFalse(WavAudio.merge(emptyList(), output))
        assertFalse(output.exists())

        assertFalse(WavAudio.merge(listOf(good, bad), output))
        assertFalse(output.exists())

        assertFalse(WavAudio.merge(listOf(good, empty), output))
        assertFalse(output.exists())

        assertFalse(WavAudio.merge(listOf(good, differentRate), output))
        assertFalse(output.exists())

        assertFalse(WavAudio.merge(listOf(good, differentChannels), output))
        assertFalse(output.exists())
    }

    private fun writeChunk(name: String, sampleRate: Int, channels: Int, pcm: ByteArray): File {
        val file = temporaryFolder.newFile(name)
        file.writeBytes(ByteArray(44)) // reserve the canonical header
        WavAudio.writeHeader(file, pcm.size.toLong(), sampleRate, channels)
        RandomAccessFile(file, "rw").use { raf ->
            raf.seek(44L)
            raf.write(pcm)
        }
        return file
    }

    private fun readPcm(file: File, info: WavAudio.Info): ByteArray =
        RandomAccessFile(file, "r").use { raf ->
            raf.seek(info.dataOffset)
            val bytes = ByteArray(info.dataBytes.toInt())
            raf.readFully(bytes)
            bytes
        }

    private fun pcmOf(vararg bytes: Int): ByteArray = ByteArray(bytes.size) { bytes[it].toByte() }

    private fun u16(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)

    private fun u32(bytes: ByteArray, offset: Int): Long =
        (bytes[offset].toLong() and 0xFFL) or
            ((bytes[offset + 1].toLong() and 0xFFL) shl 8) or
            ((bytes[offset + 2].toLong() and 0xFFL) shl 16) or
            ((bytes[offset + 3].toLong() and 0xFFL) shl 24)
}
