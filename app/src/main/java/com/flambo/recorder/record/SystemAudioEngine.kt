package com.flambo.recorder.record

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.log10

// Records device playback (music, video, anything the playing app leaves
// capturable) straight to a WAV file. Needs a MediaProjection grant, which
// the user confirms once — just like a screen recorder.
@RequiresApi(Build.VERSION_CODES.Q)
class SystemAudioEngine(private val context: Context) {

    data class SysResult(
        val file: File?,
        val durationMs: Long,
        val peaks: List<Float>
    )

    companion object {
        const val SAMPLE_RATE = 44100
        private const val CHANNELS = 2
    }

    private var record: AudioRecord? = null
    private var raf: RandomAccessFile? = null
    private var outFile: File? = null
    private var projection: MediaProjection? = null
    private var job: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile var lastMax: Int = 0
        private set

    @Volatile private var reading = false
    @Volatile private var paused = false
    @Volatile private var countedFrames = 0L
    private val secondPeaks = mutableListOf<Float>()
    private var windowMax = 0
    private var windowFrames = 0

    val elapsedMs: Long get() = countedFrames * 1000 / SAMPLE_RATE

    // Audio session for built-in effects (AGC/NS). 0 = not recording.
    val sessionId: Int get() = record?.audioSessionId ?: 0

    fun start(file: File): Boolean {
        if (record != null) return false
        val proj = MediaProjectionHolder.take(context) ?: return false
        projection = proj
        return try {
            val capture = AudioPlaybackCaptureConfiguration.Builder(proj)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                .build()
            val format = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
                .build()
            val minBuf = AudioRecord.getMinBufferSize(
                SAMPLE_RATE, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT
            )
            val rec = AudioRecord.Builder()
                .setAudioFormat(format)
                .setAudioPlaybackCaptureConfig(capture)
                .setBufferSizeInBytes((minBuf * 4).coerceAtLeast(64 * 1024))
                .build()
            if (rec.state != AudioRecord.STATE_INITIALIZED) {
                runCatching { rec.release() }
                releaseProjection()
                return false
            }
            raf = RandomAccessFile(file, "rw").also {
                it.setLength(0)
                it.write(ByteArray(44)) // WAV header goes in on stop()
            }
            outFile = file
            countedFrames = 0L
            secondPeaks.clear()
            lastMax = 0
            windowMax = 0
            windowFrames = 0
            paused = false
            record = rec
            rec.startRecording()
            if (rec.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                abort()
                return false
            }
            reading = true
            job = scope.launch { readLoop() }
            true
        } catch (_: Exception) {
            abort()
            false
        }
    }

    fun setPaused(value: Boolean) {
        paused = value
        if (value) lastMax = 0
    }

    fun stop(): SysResult {
        reading = false
        val rec = record
        runCatching { rec?.stop() }
        runBlocking { runCatching { job?.join() } }
        job = null
        val frames = countedFrames
        val file = outFile
        try {
            if (file != null && frames > 0) {
                raf?.let { writeWavHeader(it, frames) }
            }
        } catch (_: Exception) { /* header best-effort */ }
        runCatching { raf?.close() }
        runCatching { rec?.release() }
        record = null
        raf = null
        releaseProjection()
        if (file == null || frames <= 0) {
            file?.let { runCatching { it.delete() } }
            return SysResult(null, 0L, emptyList())
        }
        return SysResult(file, frames * 1000 / SAMPLE_RATE, secondPeaks.toList())
    }

    fun cancel() {
        reading = false
        runCatching { record?.stop() }
        runBlocking { runCatching { job?.join() } }
        job = null
        runCatching { raf?.close() }
        runCatching { record?.release() }
        record = null
        raf = null
        outFile?.let { runCatching { it.delete() } }
        outFile = null
        releaseProjection()
    }

    private fun abort() {
        reading = false
        runCatching { record?.stop() }
        runCatching { record?.release() }
        record = null
        runCatching { raf?.close() }
        raf = null
        outFile?.let { runCatching { it.delete() } }
        outFile = null
        releaseProjection()
    }

    private fun releaseProjection() {
        runCatching { projection?.stop() }
        projection = null
    }

    private fun readLoop() {
        val rec = record ?: return
        val out = raf ?: return
        val shorts = ShortArray(8192)
        val bytes = ByteArray(8192 * 2)
        val byteBuf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        while (reading) {
            val n = try {
                rec.read(shorts, 0, shorts.size)
            } catch (_: Exception) {
                break
            }
            if (n <= 0) continue
            if (paused) {
                lastMax = 0
                continue // keep draining so resume stays in sync
            }
            var max = 0
            for (i in 0 until n) {
                val v = shorts[i].toInt()
                val abs = if (v < 0) -v else v
                if (abs > max) max = abs
            }
            lastMax = max
            byteBuf.clear()
            for (i in 0 until n) byteBuf.putShort(shorts[i])
            try {
                out.write(bytes, 0, n * 2)
            } catch (_: Exception) {
                break
            }
            countedFrames += n / CHANNELS
            // One normalized peak per second for the saved waveform preview.
            if (max > windowMax) windowMax = max
            windowFrames += n / CHANNELS
            if (windowFrames >= SAMPLE_RATE) {
                val db = 20 * log10(windowMax.coerceAtLeast(1) / 32768.0)
                secondPeaks += ((db + 60) / 60.0).coerceIn(0.02, 1.0).toFloat()
                windowMax = 0
                windowFrames = 0
            }
        }
    }

    private fun writeWavHeader(raf: RandomAccessFile, frames: Long) {
        val dataBytes = frames * CHANNELS * 2
        val buf = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        buf.put("RIFF".toByteArray())
        buf.putInt((36 + dataBytes).toInt())
        buf.put("WAVE".toByteArray())
        buf.put("fmt ".toByteArray())
        buf.putInt(16)
        buf.putShort(1) // PCM
        buf.putShort(CHANNELS.toShort())
        buf.putInt(SAMPLE_RATE)
        buf.putInt(SAMPLE_RATE * CHANNELS * 2)
        buf.putShort((CHANNELS * 2).toShort())
        buf.putShort(16)
        buf.put("data".toByteArray())
        buf.putInt(dataBytes.toInt())
        raf.seek(0)
        raf.write(buf.array())
    }
}
