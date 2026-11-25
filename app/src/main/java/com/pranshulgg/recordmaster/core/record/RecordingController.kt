package com.pranshulgg.recordmaster.core.record

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.MediaRecorder
import android.os.Build
import androidx.core.content.ContextCompat
import com.pranshulgg.recordmaster.RecordMasterApp
import com.pranshulgg.recordmaster.core.audio.AudioChunkMerger
import com.pranshulgg.recordmaster.core.audio.PcmWavChunkWriter
import com.pranshulgg.recordmaster.core.audio.WavAudio
import com.pranshulgg.recordmaster.core.data.PreferencesManager
import com.pranshulgg.recordmaster.core.data.Recording
import com.pranshulgg.recordmaster.core.data.RecordingRepository
import com.pranshulgg.recordmaster.core.domain.AutoSaveInterval
import com.pranshulgg.recordmaster.core.domain.MicrophoneInput
import com.pranshulgg.recordmaster.core.domain.MicrophoneInputKind
import com.pranshulgg.recordmaster.core.domain.RecordingConfig
import com.pranshulgg.recordmaster.core.domain.RecordingLocation
import com.pranshulgg.recordmaster.core.domain.RecordingFormat
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.coroutineContext
import kotlin.math.log10

/** Why an active take is paused; carried in [RecordingController.RecorderState]. */
enum class PauseReason {
    NONE,
    USER,
    INTERRUPTION
}

/**
 * Single recording pipeline for 2.0. The RecordingScreen observes [state]
 * instead of owning a MediaRecorder, so a take survives leaving the screen and
 * the foreground-service notification stays in sync. One immutable plan is
 * frozen at take start and drives capture, merging, and file naming.
 */
class RecordingController(
    private val appContext: Context,
    private val repository: RecordingRepository
) {

    data class RecorderState(
        val isRecording: Boolean = false,
        val isPaused: Boolean = false,
        val pauseReason: PauseReason = PauseReason.NONE,
        val elapsedMs: Long = 0L,
        val amplitude: Float = 0f,
        val peaks: List<Float> = emptyList(),
        val currentFile: File? = null,
        val quality: RecordingQuality = RecordingQuality.HIGH,
        val format: RecordingFormat = RecordingFormat.DEFAULT,
        val input: MicrophoneInput = MicrophoneInput.DEFAULT
    )

    private val _state = MutableStateFlow(RecorderState())
    val state: StateFlow<RecorderState> = _state.asStateFlow()

    private val _lastStartError = MutableStateFlow<String?>(null)
    /** Explanation for the most recent refused start, for screens/automation to show. */
    val lastStartError: StateFlow<String?> = _lastStartError.asStateFlow()

    private val capabilityProber = RecordingCapabilityProber(appContext)
    private val locationResolver = OneShotLocationResolver(appContext)

    private var recorder: MediaRecorder? = null
    private var wavWriter: PcmWavChunkWriter? = null
    private var activePlan: RecordingPipelinePlan? = null
    private var activeInput: MicrophoneInput = MicrophoneInput.DEFAULT
    private var activeInputDevice: AudioDeviceInfo? = null
    private var activeLocation: RecordingLocation? = null
    private var currentChunkFile: File? = null
    private val chunks = mutableListOf<AudioChunkMerger.Chunk>()
    private var reviewFile: File? = null
    private var totalRecordedMs: Long = 0L
    private var chunkStartMs: Long = 0L
    private var chunkIndex = 0
    private var chunkRotationJob: Job? = null
    private var activeChunkIntervalMs: Long = 0L
    private var chunkSessionDir: File? = null
    private var activeQuality: RecordingQuality = RecordingQuality.HIGH
    private var activeNoiseReduction: Boolean = true
    private var amplitudeJob: Job? = null
    private var reviewJob: Job? = null
    private var pendingTitle: String? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _pendingBookmarks = MutableStateFlow<List<Long>>(emptyList())
    /** Marker offsets of the active take, published before the DB row exists. */
    val pendingBookmarks: StateFlow<List<Long>> = _pendingBookmarks.asStateFlow()

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

    private fun hasBluetoothConnectPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED

    /** Fresh input list for pickers, with the active input included even if unplugged. */
    suspend fun availableMicrophones(): List<MicrophoneInput> {
        val current = if (_state.value.isRecording) _state.value.input else activeInput
        return (listOf(MicrophoneInput.DEFAULT, current) + capabilityProber.availableInputs())
            .distinctBy { it.id }
            .sortedWith(compareBy({ it.kind.ordinal }, { it.displayName }))
    }

    /**
     * Switches the input for future chunks of the active take. The frozen plan
     * still owns format, quality, channels, and location. Returns null on
     * success, or a message suitable for a snackbar.
     */
    suspend fun switchMicrophone(input: MicrophoneInput): String? {
        val current = _state.value
        if (!current.isRecording) return "No active recording to switch."
        if (!hasRecordAudioPermission()) return "Microphone permission is required."
        if (input.kind == MicrophoneInputKind.BLUETOOTH && !hasBluetoothConnectPermission()) {
            return "Bluetooth permission is required for that microphone."
        }
        val plan = activePlan ?: return "No active recording to switch."
        if (!input.isDefault && plan.effectiveConfig.channels.channelCount > input.maxChannels) {
            return "${input.displayName} does not support stereo."
        }
        val device = if (input.isDefault) {
            null
        } else {
            capabilityProber.resolveInput(input.id)
                ?: return "${input.displayName} is no longer available."
        }

        activeInput = input
        activeInputDevice = device
        _state.value = current.copy(input = input)

        // A paused take has no live backend; the override applies on resume.
        if (current.isPaused) return null
        return if (rotateChunk()) {
            null
        } else {
            "Couldn't switch to ${input.displayName}. Recording paused."
        }
    }

    /** Compatibility entry point for callers that only know the legacy quality setting. */
    suspend fun start(
        quality: RecordingQuality = RecordingQuality.HIGH,
        noiseReduction: Boolean = true
    ): Boolean = start(RecordingConfig(quality = quality), noiseReduction)

    /**
     * Starts a take from an immutable preference snapshot. Capabilities are
     * probed once here; live settings changes cannot alter the active file.
     */
    suspend fun start(config: RecordingConfig, noiseReduction: Boolean = true): Boolean {
        if (_state.value.isRecording) return false
        if (!hasRecordAudioPermission()) return false

        val plan = RecordingPipelinePlanner.plan(config, capabilityProber.probe())
        val input = plan.effectiveConfig.input
        val device = if (input.isDefault) {
            null
        } else {
            if (input.kind == MicrophoneInputKind.BLUETOOTH && !hasBluetoothConnectPermission()) {
                _lastStartError.value = "Bluetooth permission is required to use that microphone."
                return false
            }
            capabilityProber.resolveInput(input.id).also {
                if (it == null) {
                    _lastStartError.value = "${input.displayName} is no longer available."
                    return false
                }
            }
        }
        // Location is best-effort and captured before the audio input opens; a
        // denied permission or missing fix never prevents the take.
        val location = if (plan.effectiveConfig.locationTaggingEnabled) {
            locationResolver.current()
        } else {
            null
        }
        return startWithPlan(plan, noiseReduction, input, device, location)
    }

    private fun startWithPlan(
        plan: RecordingPipelinePlan,
        noiseReduction: Boolean,
        input: MicrophoneInput,
        device: AudioDeviceInfo?,
        location: RecordingLocation?
    ): Boolean {
        if (!plan.canStart) {
            _lastStartError.value = plan.primaryError
                ?: "This recording configuration isn't available on this device."
            return false
        }

        _lastStartError.value = null
        activePlan = plan
        activeInput = input
        activeInputDevice = device
        activeLocation = location
        activeQuality = plan.effectiveConfig.quality
        activeNoiseReduction = noiseReduction
        chunks.clear()
        reviewFile = null
        totalRecordedMs = 0L
        chunkIndex = 0
        activeChunkIntervalMs = 0L
        chunkRotationJob?.cancel()
        _pendingBookmarks.value = emptyList()
        // Each take gets its own folder so a crash leaves an orphaned session
        // that recovery can attribute to one recording.
        chunkSessionDir = File(repository.tempChunksDir(), "take_${System.currentTimeMillis()}")
            .apply { mkdirs() }

        if (!startNewChunk()) {
            failStart("Couldn't open the audio input for this recording.")
            return false
        }

        _state.value = RecorderState(
            isRecording = true,
            isPaused = false,
            elapsedMs = 0L,
            amplitude = 0f,
            peaks = emptyList(),
            currentFile = currentChunkFile,
            quality = activeQuality,
            format = plan.effectiveConfig.format,
            input = activeInput
        )

        if (!startForegroundService()) {
            failStart("Couldn't start the recording foreground service.")
            return false
        }

        startSampling()
        startChunkRotation()
        runCatching { onRecordingStarted?.invoke() }
        return true
    }

    private fun failStart(message: String) {
        stopActiveCapture()
        runCatching { currentChunkFile?.delete() }
        currentChunkFile = null
        runCatching { chunkSessionDir?.deleteRecursively() }
        chunkSessionDir = null
        activePlan = null
        activeInput = MicrophoneInput.DEFAULT
        activeInputDevice = null
        activeLocation = null
        _lastStartError.value = message
        _state.value = RecorderState()
    }

    /** Opens the next chunk using the capture strategy frozen at take start. */
    private fun startNewChunk(): Boolean {
        val plan = activePlan ?: return false
        val sessionDir = chunkSessionDir ?: repository.tempChunksDir().also { chunkSessionDir = it }
        // Zero-padded so files sort chronologically for recovery/merge.
        val file = File(
            sessionDir,
            "c${String.format(Locale.US, "%04d", chunkIndex)}.${plan.chunkExtension}"
        )
        return when (plan.captureStrategy) {
            RecordingCaptureStrategy.MEDIA_RECORDER_AAC -> startMediaRecorderChunk(file, plan)
            RecordingCaptureStrategy.PCM_WAV -> startPcmChunk(file, plan)
            RecordingCaptureStrategy.UNSUPPORTED -> false
        }
    }

    private fun startMediaRecorderChunk(file: File, plan: RecordingPipelinePlan): Boolean {
        val mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(appContext)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

        try {
            mediaRecorder.setAudioSource(
                if (activeNoiseReduction) MediaRecorder.AudioSource.VOICE_RECOGNITION
                else MediaRecorder.AudioSource.MIC
            )
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            mediaRecorder.setAudioSamplingRate(plan.effectiveConfig.quality.sampleRate)
            mediaRecorder.setAudioEncodingBitRate(plan.effectiveConfig.quality.bitRate)
            if (plan.effectiveConfig.channels.channelCount > 1) {
                mediaRecorder.setAudioChannels(plan.effectiveConfig.channels.channelCount)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val device = activeInputDevice
                if (device != null && !mediaRecorder.setPreferredDevice(device)) {
                    throw IllegalStateException("MediaRecorder rejected the selected microphone")
                }
            }
            mediaRecorder.setOutputFile(file.absolutePath)
            mediaRecorder.prepare()
            mediaRecorder.start()
        } catch (_: Exception) {
            runCatching { mediaRecorder.release() }
            runCatching { file.delete() }
            return false
        }

        recorder = mediaRecorder
        currentChunkFile = file
        chunkStartMs = System.currentTimeMillis()
        chunkIndex++
        return true
    }

    private fun startPcmChunk(file: File, plan: RecordingPipelinePlan): Boolean {
        val writer = PcmWavChunkWriter()
        val source = if (activeNoiseReduction) {
            MediaRecorder.AudioSource.VOICE_RECOGNITION
        } else {
            MediaRecorder.AudioSource.MIC
        }
        val started = runCatching {
            writer.start(
                file = file,
                sampleRate = plan.effectiveConfig.quality.sampleRate,
                channels = plan.effectiveConfig.channels.channelCount,
                audioSource = source,
                preferredDevice = activeInputDevice
            )
        }.getOrDefault(false)

        if (!started) {
            runCatching { writer.stop() }
            runCatching { file.delete() }
            return false
        }

        wavWriter = writer
        currentChunkFile = file
        chunkStartMs = System.currentTimeMillis()
        chunkIndex++
        return true
    }

    private fun stopRecorderSafely() {
        val active = recorder
        recorder = null
        runCatching { active?.stop() }
        runCatching { active?.release() }
    }

    /** Stops whichever capture backend the active plan selected. */
    private fun stopActiveCapture() {
        stopRecorderSafely()
        wavWriter?.let { runCatching { it.stop() } }
        wavWriter = null
    }

    /** Closes the live chunk and accounts for its share of the timeline. */
    private fun finalizeCurrentChunk() {
        closeCurrentChunk()
    }

    /** Stops the active capture and records the finished chunk on the timeline. */
    private fun closeCurrentChunk(): Long {
        val elapsedInChunk = (System.currentTimeMillis() - chunkStartMs).coerceAtLeast(0L)
        val pcmStrategy = activePlan?.captureStrategy == RecordingCaptureStrategy.PCM_WAV
        val pcmDuration = if (pcmStrategy) {
            val duration = wavWriter?.let { runCatching { it.stop() }.getOrDefault(0L) } ?: 0L
            wavWriter = null
            duration
        } else {
            stopActiveCapture()
            0L
        }
        val duration = if (pcmStrategy && pcmDuration > 0L) pcmDuration else elapsedInChunk
        val file = currentChunkFile
        if (file != null) {
            val usable = if (pcmStrategy) {
                runCatching { (WavAudio.inspect(file)?.dataBytes ?: 0L) > 0L }
                    .getOrDefault(false)
            } else {
                file.exists() && file.length() > 0L
            }
            if (usable) {
                chunks += AudioChunkMerger.Chunk(file, duration.coerceAtLeast(300L))
            } else {
                runCatching { file.delete() }
            }
        }
        currentChunkFile = null
        totalRecordedMs += duration
        return duration
    }

    /**
     * Time-based auto-save: swap to a fresh chunk without pausing the take.
     * [AudioChunkMerger] later concatenates the encoded samples by timestamp,
     * so no silence is inserted at the boundary — the saved file is seamless.
     */
    private fun rotateChunk(): Boolean {
        closeCurrentChunk()
        if (!startNewChunk()) {
            // Never keep "recording" with no output file — that silently drops
            // audio. Surface it as a paused take the user can stop or resume.
            pause(PauseReason.INTERRUPTION)
            return false
        }
        _state.value = _state.value.copy(currentFile = currentChunkFile)
        return true
    }

    /** Reads the chosen interval and starts the rotation loop for a new take. */
    private fun startChunkRotation() {
        chunkRotationJob?.cancel()
        val app = appContext as? RecordMasterApp ?: return
        chunkRotationJob = scope.launch {
            val interval = withContext(Dispatchers.IO) {
                runCatching { app.prefs.autoSaveInterval() }
                    .getOrDefault(AutoSaveInterval.Default)
            }
            // A stop + restart can supersede this job while the pref loads;
            // don't let the stale read clobber the newer take's interval.
            if (!coroutineContext.isActive) return@launch
            activeChunkIntervalMs = interval.minutes * 60_000L
            runChunkRotationLoop()
        }
    }

    /** Restarts rotation on resume using the interval captured at take start. */
    private fun resumeChunkRotation() {
        chunkRotationJob?.cancel()
        if (activeChunkIntervalMs <= 0L) return
        chunkRotationJob = scope.launch { runChunkRotationLoop() }
    }

    private suspend fun runChunkRotationLoop() {
        while (coroutineContext.isActive) {
            delay(activeChunkIntervalMs)
            val current = _state.value
            if (current.isRecording && !current.isPaused) rotateChunk()
        }
    }

    suspend fun startHeadless(): Boolean = startFromPreferences()

    /** Reads one immutable preference snapshot, then hands it to the planner. */
    suspend fun startFromPreferences(): Boolean {
        if (_state.value.isRecording) return false
        if (!hasRecordAudioPermission()) return false
        val app = appContext as? RecordMasterApp ?: return false
        val config = app.prefs.recordingConfig()
        val noiseReduction = app.prefs.noiseReductionFlow.first()
        pendingTitle = generateRecordingName(System.currentTimeMillis())
        return start(config, noiseReduction)
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

    /** Drops a marker at the current audible position of the active take. */
    fun addBookmark(): Long? {
        val current = _state.value
        if (!current.isRecording) return null
        val timestamp = current.elapsedMs.coerceAtLeast(0L)
        _pendingBookmarks.value = _pendingBookmarks.value + timestamp
        return timestamp
    }

    /** [reason] lets the UI explain *why* a take is paused (user vs. interruption). */
    fun pause(reason: PauseReason = PauseReason.USER) {
        val current = _state.value
        if (!current.isRecording || current.isPaused) return

        chunkRotationJob?.cancel()
        finalizeCurrentChunk()
        _state.value = current.copy(
            isPaused = true,
            pauseReason = reason,
            elapsedMs = totalRecordedMs,
            currentFile = chunks.lastOrNull()?.file
        )
        amplitudeJob?.cancel()
        prepareReview()
    }

    fun resume() {
        val current = _state.value
        if (!current.isRecording || !current.isPaused) return

        reviewJob?.cancel()
        (appContext as? RecordMasterApp)?.review?.stop()
        if (!startNewChunk()) return

        _state.value = current.copy(
            isPaused = false,
            pauseReason = PauseReason.NONE,
            currentFile = currentChunkFile
        )
        startSampling()
        resumeChunkRotation()
    }

    /** Merges every finalized chunk with the strategy frozen at take start. */
    private fun mergeChunks(
        chunks: List<AudioChunkMerger.Chunk>,
        target: File,
        strategy: RecordingMergeStrategy
    ): Boolean = when (strategy) {
        RecordingMergeStrategy.WAV_PCM_CONCAT -> WavAudio.merge(chunks.map { it.file }, target)
        RecordingMergeStrategy.MPEG4_STREAM_COPY -> AudioChunkMerger.merge(chunks, target)
        RecordingMergeStrategy.UNSUPPORTED -> false
    }

    /** Merges every finalized chunk into one file the review player can scrub. */
    private fun prepareReview() {
        reviewJob?.cancel()
        val app = appContext as? RecordMasterApp ?: return
        val extension = activePlan?.finalExtension ?: RecordingFormat.DEFAULT.extension
        val target = reviewFile ?: File(
            repository.recordingsDir(),
            "${RecordingRepository.TEMP_PREFIX}${System.currentTimeMillis()}_review.$extension"
        ).also { reviewFile = it }
        val snapshot = chunks.toList()
        val strategy = activePlan?.mergeStrategy ?: RecordingMergeStrategy.MPEG4_STREAM_COPY

        reviewJob = scope.launch(Dispatchers.IO) {
            val ok = mergeChunks(snapshot, target, strategy)
            if (!ok || !target.exists() || target.length() == 0L) return@launch
            withContext(Dispatchers.Main) {
                // The take may have been resumed or saved while merging.
                if (_state.value.isRecording && _state.value.isPaused) {
                    app.review.prepare(target.absolutePath)
                }
            }
        }
    }

    fun stop(
        onSaved: ((Recording) -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        val current = _state.value
        if (!current.isRecording) return

        amplitudeJob?.cancel()
        reviewJob?.cancel()
        chunkRotationJob?.cancel()
        if (!current.isPaused) finalizeCurrentChunk()
        (appContext as? RecordMasterApp)?.review?.stop()

        val finalized = chunks.toList()
        val bookmarks = _pendingBookmarks.value
        val peaks = current.peaks
        val qualityName = current.quality.name
        val location = activeLocation
        val titleToUse = pendingTitle
        val duration = totalRecordedMs.coerceAtLeast(300L)
        val oldReviewFile = reviewFile
        val sessionDir = chunkSessionDir
        val mergeStrategy = activePlan?.mergeStrategy ?: RecordingMergeStrategy.MPEG4_STREAM_COPY
        val finalExtension = activePlan?.finalExtension ?: RecordingFormat.DEFAULT.extension

        _state.value = RecorderState()
        _pendingBookmarks.value = emptyList()
        chunks.clear()
        reviewFile = null
        pendingTitle = null
        chunkSessionDir = null
        activeInput = MicrophoneInput.DEFAULT
        activeInputDevice = null
        activeLocation = null
        stopForegroundService()

        if (finalized.isEmpty()) {
            oldReviewFile?.let { runCatching { it.delete() } }
            runCatching { sessionDir?.deleteRecursively() }
            scope.launch {
                onError?.invoke("Nothing to save — the recording was empty.")
            }
            return
        }

        scope.launch(Dispatchers.IO) {
            val merged = File(
                repository.recordingsDir(),
                "${RecordingRepository.TEMP_PREFIX}${System.currentTimeMillis()}_merged.$finalExtension"
            )
            if (mergeChunks(finalized, merged, mergeStrategy)) {
                finalizeRecording(
                    tempFile = merged,
                    peaks = peaks,
                    durationMs = duration,
                    qualityName = qualityName,
                    preferredTitle = titleToUse,
                    bookmarks = bookmarks,
                    location = location,
                    onSaved = onSaved
                )
                finalized.forEach { runCatching { it.file.delete() } }
                oldReviewFile?.let { runCatching { it.delete() } }
                // Take is safely on disk: clear the staging session. On merge
                // failure the chunks stay for crash recovery to pick up.
                runCatching { sessionDir?.deleteRecursively() }
            } else {
                runCatching { merged.delete() }
                withContext(Dispatchers.Main) {
                    onError?.invoke("Couldn't save this recording. Please try again.")
                }
            }
        }
    }

    fun cancel() {
        amplitudeJob?.cancel()
        reviewJob?.cancel()
        chunkRotationJob?.cancel()
        stopActiveCapture()
        (appContext as? RecordMasterApp)?.review?.stop()
        chunks.forEach { runCatching { it.file.delete() } }
        chunks.clear()
        currentChunkFile?.let { runCatching { it.delete() } }
        currentChunkFile = null
        reviewFile?.let { runCatching { it.delete() } }
        reviewFile = null
        // Explicit discard: remove the whole staging session, not just the
        // chunks we already finalized (the live one may not be in `chunks`).
        chunkSessionDir?.let { runCatching { it.deleteRecursively() } }
        chunkSessionDir = null
        activePlan = null
        activeInput = MicrophoneInput.DEFAULT
        activeInputDevice = null
        activeLocation = null
        _lastStartError.value = null
        _state.value = RecorderState()
        _pendingBookmarks.value = emptyList()
        pendingTitle = null
        stopForegroundService()
    }

    private fun startSampling() {
        amplitudeJob?.cancel()
        amplitudeJob = scope.launch {
            val samples = mutableListOf<Float>()
            while (_state.value.isRecording && !_state.value.isPaused) {
                val raw = if (activePlan?.captureStrategy == RecordingCaptureStrategy.PCM_WAV) {
                    wavWriter?.peekAmplitude() ?: 0
                } else {
                    try {
                        recorder?.maxAmplitude ?: 0
                    } catch (_: Exception) {
                        0
                    }
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

                val elapsed = totalRecordedMs + (System.currentTimeMillis() - chunkStartMs)
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
        bookmarks: List<Long> = emptyList(),
        location: RecordingLocation? = null,
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
            duration = durationMs,
            latitude = location?.latitude,
            longitude = location?.longitude,
            locatedAt = location?.capturedAtMs
        )
        val id = repository.insert(recording)
        val saved = recording.copy(id = id)
        // Markers were collected while the take had no DB id; link them now that
        // the final file and its row exist.
        bookmarks.forEach { timestamp ->
            runCatching { repository.addBookmark(id, timestamp) }
        }
        runCatching { MetadataHelper.writeTags(saved) }
        withContext(Dispatchers.Main) {
            onSaved?.invoke(saved)
        }
    }
}
