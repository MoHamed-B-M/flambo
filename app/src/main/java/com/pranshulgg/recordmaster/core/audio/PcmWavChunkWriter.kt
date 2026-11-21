package com.pranshulgg.recordmaster.core.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import java.io.File
import java.io.RandomAccessFile

/**
 * Records one PCM chunk directly into a WAV file. A 44-byte header is reserved
 * up front and finalized with the real data size in [stop].
 */
class PcmWavChunkWriter {

    @Volatile
    private var running = false

    @Volatile
    private var peakAmplitude = 0

    private var audioRecord: AudioRecord? = null
    private var randomAccessFile: RandomAccessFile? = null
    private var readerThread: Thread? = null
    private var outputFile: File? = null
    private var dataBytes = 0L
    private var sampleRate = 0
    private var channels = 0

    val isActive: Boolean get() = running

    // RecordingController checks RECORD_AUDIO before it opens any capture backend.
    // This writer is internal to that pipeline and never starts a take on its own.
    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    fun start(file: File, sampleRate: Int, channels: Int, audioSource: Int): Boolean {
        if (running || readerThread?.isAlive == true) return false
        if (sampleRate <= 0 || (channels != 1 && channels != 2)) return false

        val channelConfig = if (channels == 1) {
            AudioFormat.CHANNEL_IN_MONO
        } else {
            AudioFormat.CHANNEL_IN_STEREO
        }
        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate,
            channelConfig,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuffer <= 0) return false

        // Roughly 100 ms of audio, never smaller than the platform minimum.
        val bufferSize = maxOf(minBuffer, sampleRate * channels * 2 / 10)

        var record: AudioRecord? = null
        var raf: RandomAccessFile? = null
        try {
            file.parentFile?.let { parent ->
                if (!parent.exists() && !parent.mkdirs()) {
                    throw IllegalStateException("Cannot create chunk directory")
                }
            }
            if (file.exists() && !file.delete()) {
                throw IllegalStateException("Cannot replace chunk file")
            }

            raf = RandomAccessFile(file, "rw")
            raf.setLength(HEADER_SIZE.toLong())
            raf.seek(HEADER_SIZE.toLong())

            record = AudioRecord(
                audioSource,
                sampleRate,
                channelConfig,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                throw IllegalStateException("AudioRecord failed to initialize")
            }
            record.startRecording()
            if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                throw IllegalStateException("AudioRecord failed to start")
            }

            val activeRecord = record
            val activeRaf = raf
            this.audioRecord = activeRecord
            this.randomAccessFile = activeRaf
            this.outputFile = file
            this.sampleRate = sampleRate
            this.channels = channels
            this.dataBytes = 0L
            this.peakAmplitude = 0
            this.running = true

            val thread = Thread { readLoop(activeRecord, activeRaf, bufferSize) }
            thread.isDaemon = true
            this.readerThread = thread
            thread.start()
            return true
        } catch (_: Exception) {
            releaseQuietly(record, raf)
            this.audioRecord = null
            this.randomAccessFile = null
            this.readerThread = null
            this.outputFile = null
            this.sampleRate = 0
            this.channels = 0
            this.dataBytes = 0L
            this.peakAmplitude = 0
            this.running = false
            runCatching { file.delete() }
            return false
        }
    }

    /** Stops capture, joins the reader, writes the final header and returns ms. */
    @Synchronized
    fun stop(): Long {
        val record = audioRecord
        val raf = randomAccessFile
        val thread = readerThread
        val file = outputFile
        val rate = sampleRate
        val count = channels

        running = false
        if (record == null && raf == null && thread == null && file == null) return 0L

        // stop() unblocks a read() currently waiting for a full buffer.
        runCatching { record?.stop() }
        if (thread != null && thread.isAlive) {
            try {
                thread.join(STOP_JOIN_TIMEOUT_MS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }

        val bytes = dataBytes
        runCatching { record?.release() }
        runCatching { raf?.close() }
        if (file != null && rate > 0 && count > 0) {
            runCatching { WavAudio.writeHeader(file, bytes, rate, count) }
        }

        audioRecord = null
        randomAccessFile = null
        readerThread = null
        outputFile = null
        sampleRate = 0
        channels = 0
        dataBytes = 0L
        peakAmplitude = 0
        running = false

        if (rate <= 0 || count <= 0) return 0L
        val frames = bytes / (count * 2L)
        return (frames * 1000L / rate).coerceAtLeast(0L)
    }

    /** Latest 16-bit peak observed by the reader loop, in 0..32767. */
    fun peekAmplitude(): Int = peakAmplitude.coerceIn(0, 32767)

    private fun readLoop(record: AudioRecord, raf: RandomAccessFile, bufferSize: Int) {
        val buffer = ByteArray(bufferSize)
        try {
            while (running) {
                val read = record.read(buffer, 0, buffer.size)
                if (read > 0) {
                    raf.write(buffer, 0, read)
                    dataBytes += read.toLong()
                    peakAmplitude = peakOf(buffer, read)
                } else if (read < 0) {
                    break
                }
            }
        } catch (_: Exception) {
            // A closed record/file during stop() ends the loop; no extra cleanup here.
        } finally {
            running = false
        }
    }

    private fun peakOf(buffer: ByteArray, length: Int): Int {
        var peak = 0
        var index = 0
        while (index + 1 < length) {
            val raw = (buffer[index].toInt() and 0xFF) or
                ((buffer[index + 1].toInt() and 0xFF) shl 8)
            val sample = raw.toShort().toInt()
            val magnitude = if (sample < 0) -sample else sample
            if (magnitude > peak) peak = magnitude
            index += 2
        }
        return peak.coerceAtMost(32767)
    }

    private fun releaseQuietly(record: AudioRecord?, raf: RandomAccessFile?) {
        runCatching { record?.stop() }
        runCatching { record?.release() }
        runCatching { raf?.close() }
    }

    private companion object {
        const val HEADER_SIZE = 44
        const val STOP_JOIN_TIMEOUT_MS = 5_000L
    }
}
