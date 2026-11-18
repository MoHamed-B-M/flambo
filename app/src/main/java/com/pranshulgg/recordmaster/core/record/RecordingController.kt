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
import com.pranshulgg.recordmaster.core.metadata.MetadataHelper
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
import java.text.SimpleDateFormat
import java.util.Date
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
    private var pendingTitle: String? = null
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
        pendingTitle = generateRecordingName(System.currentTimeMillis())
        return start(quality, noiseReduction)
    }

    /** Automation entry point: start a take, or finish the current one. */
    suspend fun toggleHeadless(): Boolean {
        if (_state.value.isRecording) {
            stop()
            return true
        }
        return startHeadless()
    }

    /**
     * Title the current/next take will get when saved. Cached per active take so
     * the recording screen and finalized file/DB record stay consistent.
     */
    suspend fun peekNextTitle(): String {
        val current = pendingTitle
        if (_state.value.isRecording && current != null) return current
        val name = generateRecordingName(System.currentTimeMillis())
        if (_state.value.isRecording) pendingTitle = name
        return name
    }

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
        val titleToUse = pendingTitle
        _state.value = RecorderState()
        pendingTitle = null
        stopForegroundService()

        if (file != null && file.exists() && file.length() > 0) {
            scope.launch(Dispatchers.IO) {
                finalizeRecording(
                    tempFile = file,
                    peaks = peaks,
                    durationMs = elapsed,
                    qualityName = qualityName,
                    preferredTitle = titleToUse,
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
        pendingTitle = null
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

    private suspend fun generateRecordingName(createdAtMs: Long): String {
        val app = appContext as? RecordMasterApp
        val prefs = app?.prefs
        val pattern = prefs?.recordingNamePattern() ?: PreferencesManager.DEFAULT_NAME_PATTERN
        val prefix = prefs?.recordingPrefix()?.ifBlank { PreferencesManager.DEFAULT_PREFIX }
            ?: PreferencesManager.DEFAULT_PREFIX

        val dbTitles = runCatching { repository.activeTitles() }.getOrDefault(emptyList())
        val fileTitles = runCatching {
            repository.recordingsDir().listFiles()?.map { it.nameWithoutExtension } ?: emptyList()
        }.getOrDefault(emptyList())
        val existing = dbTitles + fileTitles
        val counter = nextCounter(existing, prefix, pattern)
        val resolved = resolvePattern(pattern, prefix, createdAtMs, counter)
        val safe = sanitizeFileName(resolved)
        return safe.ifBlank { "Recording" }
    }

    private fun nextCounter(existing: List<String>, prefix: String, pattern: String): Int {
        val legacy = Regex("^${Regex.escape(prefix)}_(\\d+)$")
        val legacyMax = existing.mapNotNull { legacy.matchEntire(it)?.groupValues?.get(1)?.toIntOrNull() }.maxOrNull()
        if (legacyMax != null) return legacyMax + 1

        val numberRegex = Regex("\\d+")
        val allNumbers = existing.flatMap { name ->
            numberRegex.findAll(name).mapNotNull { it.value.toIntOrNull() }
        }
        val preferred = allNumbers.filter { it in 0..999_999 }.maxOrNull()
        val fallback = allNumbers.maxOrNull()
        return (preferred ?: fallback ?: existing.size) + 1
    }

    private fun resolvePattern(
        pattern: String,
        prefix: String,
        createdAtMs: Long,
        counter: Int
    ): String {
        val date = Date(createdAtMs)
        fun fmt(p: String) = SimpleDateFormat(p, Locale.US).format(date)
        var out = pattern.ifBlank { PreferencesManager.DEFAULT_NAME_PATTERN }
        val replacements = linkedMapOf(
            "{prefix}" to prefix,
            "{custom_name}" to "",
            "{custom}" to "",
            "{datetime}" to fmt("yyyy-MM-dd_HH-mm-ss"),
            "{date}" to fmt("yyyy-MM-dd"),
            "{time}" to fmt("HH-mm-ss"),
            "{year}" to fmt("yyyy"),
            "{month}" to fmt("MM"),
            "{day}" to fmt("dd"),
            "{weekday}" to fmt("EEE"),
            "{timestamp}" to (createdAtMs / 1000L).toString(),
            "{counter}" to String.format(Locale.US, "%03d", counter)
        )
        replacements.forEach { (key, value) ->
            out = out.replace(key, value, ignoreCase = true)
        }
        out = out.replace(Regex("[-_ ]{2,}"), "_")
            .trim('_', '-', ' ', '.')
        return out
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
     * Renames the temporary take to the configured name pattern, keeps the
     * extension, and writes the metadata row. Existing files are never moved or
     * overwritten; collisions get a `(2)` suffix.
     */
    private suspend fun finalizeRecording(
        tempFile: File,
        peaks: List<Float>,
        durationMs: Long,
        qualityName: String,
        preferredTitle: String? = null,
        onSaved: ((Recording) -> Unit)? = null
    ) {
        val peaksString = peaks.takeLast(120)
            .joinToString(",") { String.format(Locale.US, "%.3f", it) }

        val baseTitle = sanitizeFileName(
            preferredTitle ?: generateRecordingName(System.currentTimeMillis())
        )
        var actualFile = tempFile
        try {
            val target = getUniqueFile(
                tempFile.parentFile ?: repository.recordingsDir(),
                baseTitle,
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

        val finalTitle = actualFile.nameWithoutExtension.ifBlank { baseTitle }
        val recording = Recording(
            title = finalTitle,
            filePath = actualFile.absolutePath,
            durationMs = durationMs,
            createdAt = System.currentTimeMillis(),
            amplitudePeaks = peaksString,
            quality = qualityName,
            fileSize = actualFile.length().takeIf { it > 0 } ?: 0L,
            duration = durationMs
        )
        val id = repository.insert(recording)
        val saved = recording.copy(id = id)
        runCatching { MetadataHelper.writeTags(saved) }
        withContext(Dispatchers.Main) {
            onSaved?.invoke(saved)
        }
    }
}
