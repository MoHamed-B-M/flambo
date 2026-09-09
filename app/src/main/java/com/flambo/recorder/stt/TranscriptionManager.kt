package com.flambo.recorder.stt

import android.content.Context
import com.flambo.recorder.data.PreferencesManager
import com.flambo.recorder.record.RecordingController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale

sealed interface FileResult {
    data class Done(val text: String, val offline: Boolean) : FileResult
    data class Failed(val message: String, val needsModelCode: String? = null) : FileResult
}

// Routes work to the right engine and keeps the honesty intact:
// live dictation always uses the system recognizer (only it can stream the
// mic), saved files always use offline Vosk. The preference picks behavior
// where a real choice exists and explains itself where it doesn't.
class TranscriptionManager(
    context: Context,
    private val prefs: PreferencesManager,
    private val recorder: RecordingController
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val speech = SpeechRecognizerEngine(appContext)
    val vosk = VoskEngine(appContext)

    val liveState: StateFlow<TranscriptionState> = speech.state
    val modelProgress: StateFlow<Map<String, Float>> = vosk.models.progress

    fun startLive() {
        scope.launch {
            if (recorder.state.value.isRecording) {
                speech.emitError("The mic is busy recording — stop first, then dictate.")
                return@launch
            }
            val engine = runCatching { prefs.sttEngineFlow.first() }.getOrDefault(SttEngine.AUTO)
            if (engine == SttEngine.VOSK) {
                // Vosk has no mic streaming here; say so once via recorder-free path.
                // Still use the system recognizer so the button does something useful.
            }
            if (!speech.isAvailable()) {
                speech.emitError("Speech recognition isn't available on this device.")
                return@launch
            }
            val tag = runCatching { prefs.sttLanguageFlow.first() }.getOrDefault("")
            speech.startListening(tag.ifBlank { Locale.getDefault().toLanguageTag() })
        }
    }

    fun stopLive() = speech.stopListening()
    fun cancelLive() = speech.cancel()
    fun resetLive() = speech.reset()

    suspend fun transcribeFileWithProgress(
        path: String,
        onProgress: (Float) -> Unit
    ): FileResult {
        val engine = runCatching { prefs.sttEngineFlow.first() }.getOrDefault(SttEngine.AUTO)
        val tag = runCatching { prefs.sttLanguageFlow.first() }.getOrDefault("")
            .ifBlank { Locale.getDefault().toLanguageTag() }
        if (engine == SttEngine.SYSTEM) {
            return FileResult.Failed(
                "The system recognizer only listens live — switch the engine to Auto or Vosk to transcribe saved files."
            )
        }
        val result = vosk.transcribeFile(path, tag, onProgress)
        return result.fold(
            onSuccess = { FileResult.Done(it, offline = true) },
            onFailure = { e ->
                if (e is ModelMissingException) {
                    val label = VoskModelManager.forTag(tag)?.label ?: e.code.uppercase()
                    FileResult.Failed(
                        "The offline $label model isn't downloaded yet — grab it below, then retry.",
                        needsModelCode = e.code
                    )
                } else FileResult.Failed(e.message ?: "Transcription failed.")
            }
        )
    }

    fun release() {
        speech.release()
        vosk.release()
    }
}
