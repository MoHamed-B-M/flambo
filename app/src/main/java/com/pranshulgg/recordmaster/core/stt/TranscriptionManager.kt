package com.pranshulgg.recordmaster.core.stt

import android.content.Context
import com.pranshulgg.recordmaster.core.data.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

sealed interface FileResult {
    data class Done(val text: String, val offline: Boolean) : FileResult
    data class Failed(val message: String) : FileResult
}

enum class WhisperState {
    IDLE,
    MODEL_LOADING,
    LISTENING,
    TRANSCRIBING,
    FINAL_RESULT
}

/**
 * Offline transcription entry point backed by whisper.cpp.
 */
class TranscriptionManager(
    context: Context,
    private val prefs: PreferencesManager,
    private val modelStore: SpeechModelStore
) {
    private val appContext = context.applicationContext

    val modelManager = WhisperModelManager(appContext)
    val whisper = WhisperEngine()

    private val _whisperState = MutableStateFlow(WhisperState.IDLE)
    val whisperState: StateFlow<WhisperState> = _whisperState.asStateFlow()

    private val loadMutex = Mutex()
    private var ctxPtr: Long = 0L
    private var loadedModelId: String? = null

    /**
     * Loads the selected model, or falls back to the built-in default when the
     * selection is missing/corrupt. A different selection always reloads.
     */
    suspend fun ensureModelLoaded(): Boolean = loadMutex.withLock {
        withContext(Dispatchers.IO) {
            val selected = runCatching { modelStore.selectedModel.first() }.getOrNull()
                ?: SpeechModelCatalog.DEFAULT

            if (ctxPtr != 0L && loadedModelId == selected.id) {
                _whisperState.value = WhisperState.IDLE
                return@withContext true
            }

            freeContext()
            _whisperState.value = WhisperState.MODEL_LOADING

            val candidates = listOf(selected, SpeechModelCatalog.DEFAULT)
                .distinctBy { it.id }
            for (model in candidates) {
                var file = modelManager.ensureModel(model).getOrNull() ?: continue
                var ptr = whisper.nativeLoadModel(file.absolutePath)
                if (ptr == 0L && model.isBuiltIn) {
                    // A corrupt built-in download is disposable; fetch it once
                    // more. Imported files are never deleted here.
                    runCatching { file.delete() }
                    file = modelManager.ensureModel(model).getOrNull() ?: continue
                    ptr = whisper.nativeLoadModel(file.absolutePath)
                }
                if (ptr != 0L) {
                    ctxPtr = ptr
                    loadedModelId = model.id
                    if (model.id != selected.id) {
                        runCatching { modelStore.select(model.id) }
                    }
                    _whisperState.value = WhisperState.IDLE
                    return@withContext true
                }
            }

            _whisperState.value = WhisperState.IDLE
            false
        }
    }

    suspend fun transcribeFileWithProgress(
        path: String,
        onProgress: (Float) -> Unit
    ): FileResult = withContext(Dispatchers.IO) {
        try {
            // Surface a model download on the same progress bar instead of
            // leaving it frozen at 0%.
            val downloadProgress = launch {
                modelManager.progress.collect { map ->
                    map.values.maxOrNull()?.let { onProgress(it * 0.2f) }
                }
            }
            val loaded = try {
                ensureModelLoaded()
            } finally {
                downloadProgress.cancel()
            }
            onProgress(0.2f)
            if (!loaded || ctxPtr == 0L) {
                return@withContext FileResult.Failed(
                    "No speech model available. Download one or import a model " +
                        "in Settings \u2192 Speech models."
                )
            }

            _whisperState.value = WhisperState.TRANSCRIBING
            onProgress(0.3f)

            val pcm = PcmDecoder.decodeTo16kMono(path)
            onProgress(0.5f)
            if (pcm.isEmpty()) {
                _whisperState.value = WhisperState.IDLE
                return@withContext FileResult.Failed("This recording contains no audio.")
            }

            val text = whisper.nativeTranscribe(ctxPtr, pcm, languageTag())
            onProgress(1.0f)
            _whisperState.value = if (text.isBlank()) WhisperState.IDLE else WhisperState.FINAL_RESULT
            if (text.isBlank()) {
                FileResult.Failed("No speech detected in this recording.")
            } else {
                FileResult.Done(text.trim(), offline = true)
            }
        } catch (e: Exception) {
            _whisperState.value = WhisperState.IDLE
            FileResult.Failed(e.message ?: "Transcription failed")
        }
    }

    /**
     * Transcribe a short live PCM chunk (16 kHz mono). Returns "" when no
     * speech was recognised.
     */
    suspend fun transcribePcm(pcm: ShortArray): String = withContext(Dispatchers.IO) {
        if (!ensureModelLoaded()) return@withContext ""
        if (ctxPtr == 0L || pcm.isEmpty()) return@withContext ""
        try {
            whisper.nativeTranscribe(ctxPtr, pcm, languageTag()).trim()
        } catch (_: Exception) {
            ""
        }
    }

    private suspend fun languageTag(): String = prefs.sttLanguageFlow.first()

    fun release() {
        freeContext()
        _whisperState.value = WhisperState.IDLE
    }

    private fun freeContext() {
        if (ctxPtr != 0L) {
            try {
                whisper.nativeFree(ctxPtr)
            } catch (_: Exception) {
            }
            ctxPtr = 0L
        }
        loadedModelId = null
    }
}
