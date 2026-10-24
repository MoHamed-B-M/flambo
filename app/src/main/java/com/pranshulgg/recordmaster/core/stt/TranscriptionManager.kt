package com.pranshulgg.recordmaster.core.stt

import android.content.Context
import com.pranshulgg.recordmaster.core.data.PreferencesManager
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import java.util.Locale

sealed interface FileResult {
    data class Done(val text: String, val offline: Boolean) : FileResult
    data class Failed(
        val message: String,
        val needsModelCode: String? = null
    ) : FileResult
}

/**
 * Offline transcription entry point. Whisper is deferred (D4); Vosk covers
 * 2.0. The interface stays small so a second engine can be added later.
 */
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
        val tag = runCatching { prefs.sttLanguageFlow.first() }
            .getOrDefault("")
            .ifBlank { Locale.getDefault().toLanguageTag() }

        val result = vosk.transcribeFile(path, tag, onProgress)
        return result.fold(
            onSuccess = { FileResult.Done(it, offline = true) },
            onFailure = { error ->
                if (error is ModelMissingException) {
                    val label = VoskModelManager.forTag(tag)?.label
                        ?: error.code.uppercase()
                    FileResult.Failed(
                        "The offline $label model isn't downloaded yet — grab it in Settings, then retry.",
                        needsModelCode = error.code
                    )
                } else {
                    FileResult.Failed(error.message ?: "Transcription failed.")
                }
            }
        )
    }

    fun release() {
        vosk.release()
    }
}
