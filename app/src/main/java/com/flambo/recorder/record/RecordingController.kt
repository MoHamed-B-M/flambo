package com.flambo.recorder.record

import android.Manifest
import android.app.ForegroundServiceStartNotAllowedException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import androidx.core.content.ContextCompat
import com.flambo.recorder.FlamboApp
import com.flambo.recorder.data.Recording
import com.flambo.recorder.data.RecordingRepository
import com.flambo.recorder.data.SafFolderHelper
import com.flambo.recorder.domain.RecordingQuality
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.log10

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
        val quality: RecordingQuality = RecordingQuality.HIGH,
        val source: AudioSource = AudioSource.MIC
    )

    private val _state = MutableStateFlow(RecorderState())
    val state: StateFlow<RecorderState> = _state.asStateFlow()

    private var recorder: MediaRecorder? = null
    private val sysEngine by lazy { SystemAudioEngine(appContext) }

    fun hasRecordAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            appContext, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    private var noiseSuppressor: NoiseSuppressor? = null
    private var gainControl: AutomaticGainControl? = null
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

    fun start(
        quality: RecordingQuality = RecordingQuality.HIGH,
        source: AudioSource = AudioSource.MIC,
        noiseReduction: Boolean = true
    ): Boolean {
        if (_state.value.isRecording) return false

        if (source == AudioSource.SYSTEM) return startSystemCapture(quality, noiseReduction)

        val dir = repository.recordingsDir()
        val file = File(dir, "FLAMBO_${System.currentTimeMillis()}.m4a")

        val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(appContext) else @Suppress("DEPRECATION") MediaRecorder()

        mr.setAudioSource(
            if (noiseReduction) MediaRecorder.AudioSource.VOICE_RECOGNITION
            else MediaRecorder.AudioSource.MIC
        )
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
        if (!startForegroundService()) {

            runCatching { recorder?.stop() }
            runCatching { recorder?.release() }
            recorder = null
            releaseVoiceEffects()
            _state.value = RecorderState()
            return false
        }
        startSampling()
        return true
    }

    private fun startSystemCapture(quality: RecordingQuality, noiseReduction: Boolean): Boolean {
        if (!AudioSource.SYSTEM_ENABLED) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val dir = repository.recordingsDir()
        val file = File(dir, "FLAMBO_SYS_${System.currentTimeMillis()}.wav")
        if (!sysEngine.start(file)) return false
        if (noiseReduction) attachVoiceEffects(sysEngine.sessionId)
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
        if (!startForegroundService(AudioSource.SYSTEM)) {
            sysEngine.stop()
            releaseVoiceEffects()
            _state.value = RecorderState()
            return false
        }
        startSampling()
        return true
    }

    suspend fun startHeadless(): Boolean {
        if (_state.value.isRecording) return false
        if (!hasRecordAudioPermission()) return false
        val app = appContext as? FlamboApp ?: return false
        val q = app.prefs.qualityFlow.first()
        val nr = app.prefs.noiseReductionFlow.first()
        var source = AudioSource.fromPref(app.prefs.audioSourceFlow.first())
        if (source == AudioSource.SYSTEM) source = AudioSource.MIC
        return start(q, source, nr)
    }

    suspend fun toggleHeadless(): Boolean {
        if (_state.value.isRecording) {
            stop()
            return true
        }
        return startHeadless()
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

    private fun attachVoiceEffects(sessionId: Int) {
        if (sessionId == 0) return
        runCatching {
            if (NoiseSuppressor.isAvailable()) {
                noiseSuppressor?.release()
                noiseSuppressor = NoiseSuppressor.create(sessionId)?.apply { enabled = true }
            }
        }
        runCatching {
            if (AutomaticGainControl.isAvailable()) {
                gainControl?.release()
                gainControl = AutomaticGainControl.create(sessionId)?.apply { enabled = true }
            }
        }
    }

    private fun releaseVoiceEffects() {
        runCatching { noiseSuppressor?.release() }
        noiseSuppressor = null
        runCatching { gainControl?.release() }
        gainControl = null
    }

    fun stop(onSaved: ((Recording) -> Unit)? = null) {
        val s = _state.value
        if (!s.isRecording) return
        amplitudeJob?.cancel()
        releaseVoiceEffects()

        if (s.source == AudioSource.SYSTEM) {
            val res = sysEngine.stop()
            val file = res.file
            val peaks = res.peaks
            _state.value = RecorderState()
            stopForegroundService()
            if (file != null && file.exists()) {
                scope.launch(Dispatchers.IO) {
                    finalizeRecording(
                        tempFile = file,
                        peaks = peaks,
                        durationMs = res.durationMs,
                        qualityName = s.quality.name,
                        onSaved = onSaved
                    )
                }
            }
            return
        }

        try { recorder?.stop() } catch (_: Exception) {  }
        try { recorder?.release() } catch (_: Exception) {}
        recorder = null

        val elapsed = if (s.isPaused) {
            s.elapsedMs
        } else {
            System.currentTimeMillis() - startTimeMs - pauseAccumMs
        }.coerceAtLeast(300L)

        val file = s.currentFile
        val peaks = s.peaks
        _state.value = RecorderState()

        stopForegroundService()

        if (file != null && file.exists() && file.length() > 0) {
            scope.launch(Dispatchers.IO) {
                finalizeRecording(
                    tempFile = file,
                    peaks = peaks,
                    durationMs = elapsed,
                    qualityName = s.quality.name,
                    onSaved = onSaved
                )
            }
        } else {
            try { file?.delete() } catch (_: Exception) {}
        }
    }

    fun cancel() {
        amplitudeJob?.cancel()
        releaseVoiceEffects()
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

                val norm = if (raw <= 0) 0f else {
                    val db = 20 * log10(raw / 32768.0)

                    ((db + 60) / 60.0).coerceIn(0.0, 1.0).toFloat()
                }

                val jitter = if (norm < 0.05f) (0.02f + (Math.random().toFloat() * 0.03f)) else norm
                samples += jitter
                if (samples.size > 180) samples.removeAt(0)

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

    private fun startForegroundService(source: AudioSource = AudioSource.MIC): Boolean {
        val intent = Intent(appContext, RecordingService::class.java).apply {
            putExtra(RecordingService.EXTRA_FGS_TYPE, source.fgsType)
        }
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                appContext.startForegroundService(intent)
            } else {
                appContext.startService(intent)
            }
            true
        } catch (_: ForegroundServiceStartNotAllowedException) {
            false
        } catch (_: SecurityException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    private fun stopForegroundService() {
        try {
            appContext.stopService(Intent(appContext, RecordingService::class.java))
        } catch (_: Exception) {}
    }

    private suspend fun generateTitle(): String {
        return try {
            val app = appContext as? FlamboApp
            val prefs = app?.prefs
            if (prefs != null) {
                val prefix = prefs.recordingPrefix().ifBlank { "Recording" }

                val pattern = Regex("^${Regex.escape(prefix)}\\s+(\\d+)$")
                val max = repository.activeTitles().mapNotNull {
                    pattern.matchEntire(it)?.groupValues?.get(1)?.toIntOrNull()
                }.maxOrNull() ?: 0
                "$prefix ${max + 1}"
            } else {
                val count = (System.currentTimeMillis() % 1000).toInt()
                "Recording ${count.toString().padStart(3, '0')}"
            }
        } catch (_: Exception) {
            val count = (System.currentTimeMillis() % 1000).toInt()
            "Recording ${count.toString().padStart(3, '0')}"
        }
    }

    private fun sanitizeFileName(title: String): String {
        val sanitized = title.replace(Regex("[/\\\\:*?\"<>|]"), "_").trim().take(80).ifBlank { "Recording" }
        return sanitized
    }

    private fun getUniqueFile(dir: File, baseName: String, ext: String): File {
        val safeExt = ext.let { if (it.isBlank()) "" else ".$it" }
        var candidate = File(dir, baseName + safeExt)
        if (!candidate.exists()) return candidate
        var i = 2
        while (File(dir, "$baseName ($i)$safeExt").exists()) i++
        return File(dir, "$baseName ($i)$safeExt")
    }

    /**
     * Renames the temp take to the generated title, then either stores it in
     * the picked system folder directly (single source of truth) or keeps it
     * in the app folder plus an export copy — never both silently.
     */
    private suspend fun finalizeRecording(
        tempFile: File,
        peaks: List<Float>,
        durationMs: Long,
        qualityName: String,
        onSaved: ((Recording) -> Unit)?
    ) {
        val peaksStr = peaks.takeLast(120).joinToString(",") { String.format("%.3f", it) }
        val title = generateTitle()
        var actualFile = tempFile
        try {
            val safeName = sanitizeFileName(title)
            val target = getUniqueFile(tempFile.parentFile ?: repository.recordingsDir(), safeName, tempFile.extension)
            if (target.absolutePath != tempFile.absolutePath) {
                actualFile = if (tempFile.renameTo(target)) target else {
                    tempFile.copyTo(target, overwrite = true); runCatching { tempFile.delete() }; target
                }
            }
        } catch (_: Exception) {}
        var finalPath = actualFile.absolutePath
        // System folder as the save location: move the take into the picked
        // tree so the file manager and the library show the same file.
        val movedToTree = moveToSaveFolderIfEnabled(actualFile)
        if (movedToTree != null) finalPath = movedToTree
        val rec = Recording(
            title = title,
            filePath = finalPath,
            durationMs = durationMs,
            createdAt = System.currentTimeMillis(),
            amplitudePeaks = peaksStr,
            quality = qualityName
        )
        val id = repository.insert(rec)
        if (!com.flambo.recorder.data.AudioFileStore.isContentUri(finalPath)) {
            copyToCustomFolderIfNeeded(actualFile)
        }
        withContext(Dispatchers.Main) {
            onSaved?.invoke(rec.copy(id = id))
        }
    }

    /** Returns the new document URI string when the take was moved into the picked folder. */
    private suspend fun moveToSaveFolderIfEnabled(file: File): String? {
        return try {
            val app = appContext as? FlamboApp ?: return null
            val enabled = try { app.prefs.saveToCustomFolder() } catch (_: Exception) { app.saveToCustomFolder }
            if (!enabled) return null
            val uri = try { app.prefs.customFolderUri() } catch (_: Exception) { app.customFolderUri }
            if (uri.isBlank()) return null
            if (!SafFolderHelper.isTreeUriValid(appContext, uri)) return null
            val mime = when (file.extension.lowercase()) {
                "wav" -> "audio/wav"
                "m4a" -> "audio/mp4"
                else -> "audio/*"
            }
            val docUri = com.flambo.recorder.data.AudioFileStore.copyFileToTree(appContext, uri, file, mime) ?: return null
            runCatching { file.delete() }
            docUri.toString()
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun copyToCustomFolderIfNeeded(file: File) {
        try {
            val app = appContext as? FlamboApp ?: return

            val uri = try { app.prefs.customFolderUri() } catch (_: Exception) { app.customFolderUri }
            if (uri.isBlank()) return
            if (!SafFolderHelper.isTreeUriValid(appContext, uri)) return
            val mime = when (file.extension.lowercase()) {
                "wav" -> "audio/wav"
                "m4a" -> "audio/mp4"
                else -> "audio/*"
            }
            val docUri = SafFolderHelper.createFile(appContext, uri, file.name, mime) ?: return
            appContext.contentResolver.openOutputStream(docUri)?.use { out ->
                file.inputStream().use { inp -> inp.copyTo(out) }
            }
        } catch (_: Exception) { }
    }
}
