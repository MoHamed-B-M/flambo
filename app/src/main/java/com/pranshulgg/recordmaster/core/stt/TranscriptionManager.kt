package com.pranshulgg.recordmaster.core.stt

import android.content.Context
import com.pranshulgg.recordmaster.core.data.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
    private val prefs: PreferencesManager
) {
    private val appContext = context.applicationContext

    val modelManager = WhisperModelManager(appContext)
    val whisper = WhisperEngine()

    private val _modelProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val modelProgress: StateFlow<Map<String, Float>> = _modelProgress.asStateFlow()

    private val _whisperState = MutableStateFlow(WhisperState.IDLE)
    val whisperState: StateFlow<WhisperState> = _whisperState.asStateFlow()

    private var ctxPtr: Long = 0L

    suspend fun ensureModelLoaded(): Boolean = withContext(Dispatchers.IO) {
        if (ctxPtr != 0L) {
            _whisperState.value = WhisperState.IDLE
            return@withContext true
        }

        _whisperState.value = WhisperState.MODEL_LOADING
        val modelResult = modelManager.ensureDefaultModel()
        if (modelResult.isFailure) {
            _whisperState.value = WhisperState.IDLE
            return@withContext false
        }

        val path = modelResult.getOrNull()?.absolutePath ?: modelManager.modelPath()
        ctxPtr = whisper.nativeLoadModel(path)
        val ok = ctxPtr != 0L
        if (!ok) {
            // The file exists but whisper could not parse it (corrupt or
            // incomplete download). Drop it so the next attempt re-downloads.
            runCatching { java.io.File(path).takeIf { it.exists() }?.delete() }
        }
        _whisperState.value = WhisperState.IDLE
        ok
    }

    suspend fun transcribeFileWithProgress(
        path: String,
        onProgress: (Float) -> Unit
    ): FileResult = withContext(Dispatchers.IO) {
        try {
            if (ctxPtr == 0L) {
                // Surface the one-time model download on the same progress bar
                // instead of leaving it frozen at 0% while 42 MB arrives.
                val downloadProgress = launch {
                    modelManager.progress.collect { map ->
                        map[WhisperModelCatalog.DEFAULT.code]?.let { onProgress(it * 0.2f) }
                    }
                }
                val loaded = try {
                    ensureModelLoaded()
                } finally {
                    downloadProgress.cancel()
                }
                onProgress(0.25f)
                if (!loaded || ctxPtr == 0L) {
                    return@withContext FileResult.Failed(
                        "Speech model unavailable. Connect to the internet once to download it, then try again."
                    )
                }
            }

            _whisperState.value = WhisperState.TRANSCRIBING
            onProgress(0.1f)

            val pcm = PcmDecoder.decodeTo16kMono(path)
            onProgress(0.3f)
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
        if (ctxPtr == 0L && !ensureModelLoaded()) return@withContext ""
        if (ctxPtr == 0L || pcm.isEmpty()) return@withContext ""
        try {
            whisper.nativeTranscribe(ctxPtr, pcm, languageTag()).trim()
        } catch (_: Exception) {
            ""
        }
    }

    private suspend fun languageTag(): String = prefs.sttLanguageFlow.first()

    fun release() {
        if (ctxPtr != 0L) {
            try {
                whisper.nativeFree(ctxPtr)
            } catch (_: Exception) {
            }
            ctxPtr = 0L
        }
        _whisperState.value = WhisperState.IDLE
    }
}
