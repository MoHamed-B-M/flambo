package com.flambo.recorder.stt

import android.content.Context
import com.flambo.recorder.data.PreferencesManager
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import java.util.Locale

sealed interface FileResult {
    data class Done(val text: String, val offline: Boolean) : FileResult
    data class Failed(val message: String, val needsModelCode: String? = null) : FileResult
}

// Offline-only transcription: saved recordings are decoded to PCM and run
// through Vosk on-device. No mic streaming, no cloud, no account.
class TranscriptionManager(
    context: Context,
    private val prefs: PreferencesManager
) {
    private val appContext = context.applicationContext

    val vosk = VoskEngine(appContext)

    val modelProgress: StateFlow<Map<String, Float>> = vosk.models.progress

    suspend fun transcribeFileWithProgress(
        path: String,
        onProgress: (Float) -> Unit
    ): FileResult {
        val tag = runCatching { prefs.sttLanguageFlow.first() }.getOrDefault("")
            .ifBlank { Locale.getDefault().toLanguageTag() }
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
        vosk.release()
    }
}
