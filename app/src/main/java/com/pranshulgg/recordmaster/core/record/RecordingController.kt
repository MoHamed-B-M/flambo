package com.pranshulgg.recordmaster.core.record

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import androidx.core.content.ContextCompat
import com.pranshulgg.recordmaster.RecordMasterApp
import com.pranshulgg.recordmaster.core.data.PreferencesManager
import com.pranshulgg.recordmaster.core.data.Recording
import com.pranshulgg.recordmaster.core.data.RecordingRepository
import com.pranshulgg.recordmaster.core.domain.RecordingQuality
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.math.log10

/**
 * Single recording pipeline for 2.0. The existing RecordingScreen (Phase 4)
 * will observe [state] instead of owning a MediaRecorder, so a take survives
 * leaving the screen and the foreground-service notification stays in sync.
 */
class RecordingController(
    private val appContext: Context,
    private val repository: RecordingRepository
) {

    data class RecorderState(
        val isRecording: Boolean = false,
        val isPaused: Boolean = false,
        val elapsedMs: Long = 0L,
        val amplitude: Float = 0f,
        val peaks: List<Float> = emptyList(),
        val currentFile: File? = null,
        val quality: RecordingQuality = RecordingQuality.HIGH
    )

    private val _state = MutableStateFlow(RecorderState())
    val state: StateFlow<RecorderState> = _state.asStateFlow()

    private var recorder: MediaRecorder? = null
    private var startTimeMs: Long = 0L
    private var pauseAccumMs: Long = 0L
    private var pauseStartMs: Long = 0L
    private var amplitudeJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    companion object {
        var instance: RecordingController? = null
            private set
    }

    /**
     * Invoked after a recording starts. The UI uses this to stop playback so
     * the microphone never picks up speaker output.
     */
    var onRecordingStarted: (() -> Unit)? = null

    init {
        instance = this
    }

    fun hasRecordAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

    fun start(
        quality: RecordingQuality = RecordingQuality.HIGH,
        noiseReduction: Boolean = true
    ): Boolean {
        if (_state.value.isRecording) return false
        if (!hasRecordAudioPermission()) return false

        val dir = repository.recordingsDir()
        val file = File(
            dir,
            "${RecordingRepository.TEMP_PREFIX}${System.currentTimeMillis()}.m4a"
        )

        val mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(appContext)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

        try {
            mediaRecorder.setAudioSource(
                if (noiseReduction) MediaRecorder.AudioSource.VOICE_RECOGNITION
                else MediaRecorder.AudioSource.MIC
            )
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            mediaRecorder.setAudioSamplingRate(quality.sampleRate)
            mediaRecorder.setAudioEncodingBitRate(quality.bitRate)
            mediaRecorder.setOutputFile(file.absolutePath)
            mediaRecorder.prepare()
            mediaRecorder.start()
        } catch (_: Exception) {
            runCatching { mediaRecorder.release() }
            runCatching { file.delete() }
            return false
        }

        recorder = mediaRecorder
        startTimeMs = System.currentTimeMillis()
        pauseAccumMs = 0L
        _state.value = RecorderState(
            isRecording = true,
            isPaused = false,
            elapsedMs = 0L,
            amplitude = 0f,
            peaks = emptyList(),
            currentFile = file,
            quality = quality
        )

        if (!startForegroundService()) {
            runCatching { recorder?.stop() }
            runCatching { recorder?.release() }
            recorder = null
            runCatching { file.delete() }
            _state.value = RecorderState()
            return false
        }

        startSampling()
        runCatching { onRecordingStarted?.invoke() }
        return true
    }

    suspend fun startHeadless(): Boolean {
        if (_state.value.isRecording) return false
        if (!hasRecordAudioPermission()) return false
        val app = appContext as? RecordMasterApp ?: return false
        val quality = app.prefs.qualityFlow.first()
        val noiseReduction = app.prefs.noiseReductionFlow.first()
        return start(quality, noiseReduction)
    }

    /**
     * Title the current take will get when it is saved. Used by the recording
     * screen to keep its existing `User_###` top-bar title without owning the
     * naming logic.
     */
    suspend fun peekNextTitle(): String = sanitizeFileName(generateTitle())

    fun pause() {
        val current = _state.value
        if (!current.isRecording || current.isPaused) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                recorder?.pause()
            } catch (_: Exception) {
                return
            }
            pauseStartMs = System.currentTimeMillis()
            _state.value = current.copy(isPaused = true)
            amplitudeJob?.cancel()
        }
    }

    fun resume() {
        val current = _state.value
        if (!current.isRecording || !current.isPaused) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                recorder?.resume()
            } catch (_: Exception) {
                return
            }
            pauseAccumMs += System.currentTimeMillis() - pauseStartMs
            _state.value = current.copy(isPaused = false)
            startSampling()
        }
    }

    fun stop(onSaved: ((Recording) -> Unit)? = null) {
        val current = _state.value
        if (!current.isRecording) return

        amplitudeJob?.cancel()
        try {
            recorder?.stop()
        } catch (_: Exception) {
        }
        try {
            recorder?.release()
        } catch (_: Exception) {
        }
        recorder = null

        val elapsed = if (current.isPaused) {
            current.elapsedMs
        } else {
            System.currentTimeMillis() - startTimeMs - pauseAccumMs
        }.coerceAtLeast(300L)

        val file = current.currentFile
        val peaks = current.peaks
        val qualityName = current.quality.name
        _state.value = RecorderState()
        stopForegroundService()

        if (file != null && file.exists() && file.length() > 0) {
            scope.launch(Dispatchers.IO) {
                finalizeRecording(
                    tempFile = file,
                    peaks = peaks,
                    durationMs = elapsed,
                    qualityName = qualityName,
                    onSaved = onSaved
                )
            }
        } else {
            runCatching { file?.delete() }
        }
    }

    fun cancel() {
        amplitudeJob?.cancel()
        try {
            recorder?.stop()
        } catch (_: Exception) {
        }
        try {
            recorder?.release()
        } catch (_: Exception) {
        }
        recorder = null
        _state.value.currentFile?.let { runCatching { it.delete() } }
        _state.value = RecorderState()
        stopForegroundService()
    }

    private fun startSampling() {
        amplitudeJob?.cancel()
        amplitudeJob = scope.launch {
            val samples = mutableListOf<Float>()
            while (_state.value.isRecording && !_state.value.isPaused) {
                val raw = try {
                    recorder?.maxAmplitude ?: 0
                } catch (_: Exception) {
                    0
                }

                val normalized = if (raw <= 0) {
                    0f
                } else {
                    val db = 20 * log10(raw / 32768.0)
                    ((db + 60) / 60.0).coerceIn(0.0, 1.0).toFloat()
                }

                val jitter = if (normalized < 0.05f) {
                    0.02f + (Math.random().toFloat() * 0.03f)
                } else {
                    normalized
                }
                samples += jitter
                if (samples.size > 180) samples.removeAt(0)

                val elapsed = System.currentTimeMillis() - startTimeMs - pauseAccumMs
                _state.value = _state.value.copy(
                    amplitude = jitter,
                    peaks = samples.toList(),
                    elapsedMs = elapsed.coerceAtLeast(0L)
                )
                delay(90)
            }
        }
    }

    private fun startForegroundService(): Boolean {
        val intent = Intent(appContext, RecordingService::class.java)
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                appContext.startForegroundService(intent)
            } else {
                appContext.startService(intent)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun stopForegroundService() {
        try {
            appContext.stopService(Intent(appContext, RecordingService::class.java))
        } catch (_: Exception) {
        }
    }

    private suspend fun generateTitle(): String {
        val prefix = runCatching {
            (appContext as? RecordMasterApp)?.prefs?.recordingPrefix()
        }.getOrNull()?.ifBlank { PreferencesManager.DEFAULT_PREFIX }
            ?: PreferencesManager.DEFAULT_PREFIX

        val pattern = Regex("^${Regex.escape(prefix)}_(\\d+)$")
        val dbTitles = runCatching { repository.activeTitles() }.getOrDefault(emptyList())
        val fileTitles = runCatching {
            repository.recordingsDir().listFiles()
                ?.map { it.nameWithoutExtension }
                ?: emptyList()
        }.getOrDefault(emptyList())

        val max = (dbTitles + fileTitles)
            .mapNotNull { pattern.matchEntire(it)?.groupValues?.get(1)?.toIntOrNull() }
            .maxOrNull() ?: 0
        return String.format(Locale.US, "%s_%03d", prefix, max + 1)
    }

    private fun sanitizeFileName(title: String): String =
        title.replace(Regex("[/\\\\:*?\"<>|]"), "_").trim().take(80).ifBlank { "Recording" }

    private fun getUniqueFile(dir: File, baseName: String, ext: String): File {
        val safeExt = if (ext.isBlank()) "" else ".$ext"
        var candidate = File(dir, baseName + safeExt)
        if (!candidate.exists()) return candidate
        var index = 2
        while (File(dir, "$baseName ($index)$safeExt").exists()) index++
        return File(dir, "$baseName ($index)$safeExt")
    }

    /**
     * Renames the temporary take to RecordMaster's `User_###` style, keeps the
     * extension, and writes the metadata row. Existing files are never moved or
     * overwritten; collisions get a `(2)` suffix.
     */
    private suspend fun finalizeRecording(
        tempFile: File,
        peaks: List<Float>,
        durationMs: Long,
        qualityName: String,
        onSaved: ((Recording) -> Unit)?
    ) {
        val peaksString = peaks.takeLast(120)
            .joinToString(",") { String.format(Locale.US, "%.3f", it) }

        val title = sanitizeFileName(generateTitle())
        var actualFile = tempFile
        try {
            val target = getUniqueFile(
                tempFile.parentFile ?: repository.recordingsDir(),
                title,
                tempFile.extension
            )
            if (target.absolutePath != tempFile.absolutePath) {
                actualFile = if (tempFile.renameTo(target)) {
                    target
                } else {
                    tempFile.copyTo(target, overwrite = true)
                    runCatching { tempFile.delete() }
                    target
                }
            }
        } catch (_: Exception) {
        }

        val recording = Recording(
            title = title,
            filePath = actualFile.absolutePath,
            durationMs = durationMs,
            createdAt = System.currentTimeMillis(),
            amplitudePeaks = peaksString,
            quality = qualityName
        )
        val id = repository.insert(recording)
        withContext(Dispatchers.Main) {
            onSaved?.invoke(recording.copy(id = id))
        }
    }
}
