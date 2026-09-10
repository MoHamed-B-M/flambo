package com.flambo.recorder.record

import android.content.Context
import android.content.Intent
import android.media.MediaRecorder
import android.os.Build
import com.flambo.recorder.data.Recording
import com.flambo.recorder.data.RecordingRepository
import com.flambo.recorder.domain.RecordingQuality
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.log10

/**
 * Thin wrapper around MediaRecorder that exposes amplitude sampling and lifecycle.
 * Kept intentionally small — no broad storage permission, files live in app private dir.
 */
class RecordingController(
    private val appContext: Context,
    private val repository: RecordingRepository
) {

    data class RecorderState(
        val isRecording: Boolean = false,
        val isPaused: Boolean = false,
        val elapsedMs: Long = 0L,
        val amplitude: Float = 0f, // 0..1
        val peaks: List<Float> = emptyList(),
        val currentFile: File? = null,
        val quality: RecordingQuality = RecordingQuality.HIGH,
        val source: AudioSource = AudioSource.MIC
    )

    private val _state = MutableStateFlow(RecorderState())
    val state: StateFlow<RecorderState> = _state.asStateFlow()

    private var recorder: MediaRecorder? = null
    private val sysEngine by lazy { SystemAudioEngine(appContext) }
    private var startTimeMs: Long = 0L
    private var pauseAccumMs: Long = 0L
    private var pauseStartMs: Long = 0L
    private var amplitudeJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    companion object {
        var instance: RecordingController? = null
            private set
    }

    init {
        instance = this
    }

    // Returns false when the recording couldn't start (e.g. system capture
    // without a MediaProjection grant) so the UI can explain instead of
    // silently doing nothing.
    fun start(
        quality: RecordingQuality = RecordingQuality.HIGH,
        source: AudioSource = AudioSource.MIC
    ): Boolean {
        if (_state.value.isRecording) return false

        if (source == AudioSource.SYSTEM) return startSystemCapture(quality)

        val dir = repository.recordingsDir()
        val file = File(dir, "FLAMBO_${System.currentTimeMillis()}.m4a")

        val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(appContext) else @Suppress("DEPRECATION") MediaRecorder()
        mr.setAudioSource(MediaRecorder.AudioSource.MIC)
        mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        mr.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        mr.setAudioSamplingRate(quality.sampleRate)
        mr.setAudioEncodingBitRate(quality.bitRate)
        mr.setOutputFile(file.absolutePath)
        try {
            mr.prepare()
            mr.start()
        } catch (e: Exception) {
            try { mr.release() } catch (_: Exception) {}
            return false
        }
        recorder = mr
        startTimeMs = System.currentTimeMillis()
        pauseAccumMs = 0L
        _state.value = RecorderState(
            isRecording = true,
            isPaused = false,
            elapsedMs = 0L,
            amplitude = 0f,
            peaks = emptyList(),
            currentFile = file,
            quality = quality,
            source = AudioSource.MIC
        )
        startForegroundService()
        startSampling()
        return true
    }

    // System-sound path: AudioPlaybackCapture needs Android 10+ and a grant.
    // The user's quality choice is stored as the label (WAV has no bitrate).
    private fun startSystemCapture(quality: RecordingQuality): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val dir = repository.recordingsDir()
        val file = File(dir, "FLAMBO_SYS_${System.currentTimeMillis()}.wav")
        if (!sysEngine.start(file)) return false
        _state.value = RecorderState(
            isRecording = true,
            isPaused = false,
            elapsedMs = 0L,
            amplitude = 0f,
            peaks = emptyList(),
            currentFile = file,
            quality = quality,
            source = AudioSource.SYSTEM
        )
        startForegroundService(AudioSource.SYSTEM)
        startSampling()
        return true
    }

    fun pause() {
        val s = _state.value
        if (!s.isRecording || s.isPaused) return
        if (s.source == AudioSource.SYSTEM) {
            sysEngine.setPaused(true)
            pauseStartMs = System.currentTimeMillis()
            _state.value = s.copy(isPaused = true)
            amplitudeJob?.cancel()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try { recorder?.pause() } catch (_: Exception) { return }
            pauseStartMs = System.currentTimeMillis()
            _state.value = s.copy(isPaused = true)
            amplitudeJob?.cancel()
        }
    }

    fun resume() {
        val s = _state.value
        if (!s.isRecording || !s.isPaused) return
        if (s.source == AudioSource.SYSTEM) {
            sysEngine.setPaused(false)
            pauseAccumMs += System.currentTimeMillis() - pauseStartMs
            _state.value = s.copy(isPaused = false)
            startSampling()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try { recorder?.resume() } catch (_: Exception) { return }
            pauseAccumMs += System.currentTimeMillis() - pauseStartMs
            _state.value = s.copy(isPaused = false)
            startSampling()
        }
    }

    fun stop(onSaved: ((Recording) -> Unit)? = null) {
        val s = _state.value
        if (!s.isRecording) return
        amplitudeJob?.cancel()

        if (s.source == AudioSource.SYSTEM) {
            val res = sysEngine.stop()
            val file = res.file
            val peaks = res.peaks
            _state.value = RecorderState() // reset immediately for UI
            stopForegroundService()
            if (file != null && file.exists()) {
                scope.launch(Dispatchers.IO) {
                    val peaksStr = peaks.takeLast(120).joinToString(",") { String.format("%.3f", it) }
                    val rec = Recording(
                        title = generateTitle(),
                        filePath = file.absolutePath,
                        durationMs = res.durationMs,
                        createdAt = System.currentTimeMillis(),
                        amplitudePeaks = peaksStr,
                        quality = s.quality.name
                    )
                    val id = repository.insert(rec)
                    withContext(Dispatchers.Main) {
                        onSaved?.invoke(rec.copy(id = id))
                    }
                }
            }
            return
        }

        try { recorder?.stop() } catch (_: Exception) { /* may throw if too short */ }
        try { recorder?.release() } catch (_: Exception) {}
        recorder = null

        val elapsed = if (s.isPaused) {
            s.elapsedMs
        } else {
            System.currentTimeMillis() - startTimeMs - pauseAccumMs
        }.coerceAtLeast(300L)

        val file = s.currentFile
        val peaks = s.peaks
        _state.value = RecorderState() // reset immediately for UI

        stopForegroundService()

        if (file != null && file.exists() && file.length() > 0) {
            scope.launch(Dispatchers.IO) {
                val peaksStr = peaks.takeLast(120).joinToString(",") { String.format("%.3f", it) }
                val rec = Recording(
                    title = generateTitle(),
                    filePath = file.absolutePath,
                    durationMs = elapsed,
                    createdAt = System.currentTimeMillis(),
                    amplitudePeaks = peaksStr,
                    quality = s.quality.name
                )
                val id = repository.insert(rec)
                withContext(Dispatchers.Main) {
                    onSaved?.invoke(rec.copy(id = id))
                }
            }
        } else {
            try { file?.delete() } catch (_: Exception) {}
        }
    }

    fun cancel() {
        amplitudeJob?.cancel()
        if (_state.value.source == AudioSource.SYSTEM) {
            sysEngine.cancel()
            _state.value = RecorderState()
            stopForegroundService()
            return
        }
        try { recorder?.stop() } catch (_: Exception) {}
        try { recorder?.release() } catch (_: Exception) {}
        recorder = null
        _state.value.currentFile?.let { try { it.delete() } catch (_: Exception) {} }
        _state.value = RecorderState()
        stopForegroundService()
    }

    private fun startSampling() {
        amplitudeJob?.cancel()
        amplitudeJob = scope.launch {
            val samples = mutableListOf<Float>()
            while (_state.value.isRecording && !_state.value.isPaused) {
                val raw = if (_state.value.source == AudioSource.SYSTEM) {
                    sysEngine.lastMax
                } else {
                    try { recorder?.maxAmplitude ?: 0 } catch (_: Exception) { 0 }
                }
                // normalize 0..1 using log scale for more natural waveform
                val norm = if (raw <= 0) 0f else {
                    val db = 20 * log10(raw / 32768.0)
                    // db in -90..0, map to 0..1
                    ((db + 60) / 60.0).coerceIn(0.0, 1.0).toFloat()
                }
                // add slight liveliness when silent
                val jitter = if (norm < 0.05f) (0.02f + (Math.random().toFloat() * 0.03f)) else norm
                samples += jitter
                if (samples.size > 180) samples.removeAt(0)
                // System engine counts only captured frames, so pauses stay exact.
                val elapsed = if (_state.value.source == AudioSource.SYSTEM) sysEngine.elapsedMs
                else System.currentTimeMillis() - startTimeMs - pauseAccumMs
                _state.value = _state.value.copy(
                    amplitude = jitter,
                    peaks = samples.toList(),
                    elapsedMs = elapsed.coerceAtLeast(0L)
                )
                delay(90)
            }
        }
    }

    private fun startForegroundService(source: AudioSource = AudioSource.MIC) {
        val intent = Intent(appContext, RecordingService::class.java).apply {
            putExtra(RecordingService.EXTRA_FGS_TYPE, source.fgsType)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            appContext.startForegroundService(intent)
        } else {
            appContext.startService(intent)
        }
    }

    private fun stopForegroundService() {
        try {
            appContext.stopService(Intent(appContext, RecordingService::class.java))
        } catch (_: Exception) {}
    }

    private fun generateTitle(): String {
        val count = (System.currentTimeMillis() % 1000).toInt()
        return "Recording ${count.toString().padStart(3, '0')}"
    }
}
