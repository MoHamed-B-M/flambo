package com.pranshulgg.recordmaster.core.stt

import android.content.Context
import com.pranshulgg.recordmaster.core.data.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
        _whisperState.value = WhisperState.IDLE
        ok
    }

    suspend fun transcribeFileWithProgress(
        path: String,
        onProgress: (Float) -> Unit
    ): FileResult = withContext(Dispatchers.IO) {
        try {
            if (ctxPtr == 0L) {
                val loaded = ensureModelLoaded()
                if (!loaded || ctxPtr == 0L) {
                    return@withContext FileResult.Failed(
                        "Whisper model not loaded. Download the model in Settings."
                    )
                }
            }

            _whisperState.value = WhisperState.TRANSCRIBING
            onProgress(0.1f)

            val pcm = PcmDecoder.decodeTo16kMono(path)
            onProgress(0.3f)
            if (pcm.isEmpty()) {
                _whisperState.value = WhisperState.IDLE
                return@withContext FileResult.Failed("No audio data to transcribe")
            }

            val text = whisper.nativeTranscribe(ctxPtr, pcm)
            onProgress(1.0f)
            _whisperState.value = if (text.isBlank()) WhisperState.IDLE else WhisperState.FINAL_RESULT
            if (text.isBlank()) {
                FileResult.Failed("Transcription produced no text")
            } else {
                FileResult.Done(text, offline = true)
            }
        } catch (e: Exception) {
            _whisperState.value = WhisperState.IDLE
            FileResult.Failed(e.message ?: "Transcription failed")
        }
    }

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
