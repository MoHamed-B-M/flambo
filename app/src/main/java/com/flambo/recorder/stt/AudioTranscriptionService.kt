package com.flambo.recorder.stt

import android.content.Context
import com.flambo.recorder.data.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class AudioTranscriptionService(
    private val context: Context,
    private val prefs: PreferencesManager,
    private val vosk: VoskEngine = VoskEngine(context),
    private val whisper: WhisperEngine = WhisperEngine(context)
) {
    data class Result(val text: String, val segments: List<WhisperEngine.Segment> = emptyList())

    suspend fun transcribe(path: String, onProgress: (Float) -> Unit = {}): kotlin.Result<Result> =
        withContext(Dispatchers.IO) {
            val engine = runCatching { prefs.sttEngineFlow.first() }.getOrDefault("vosk")
            if (engine == "whisper") {
                val tag = runCatching { prefs.sttLanguageFlow.first() }.getOrDefault("").ifBlank { null }
                whisper.transcribeFile(path, tag, onProgress).map { Result(it) }
            } else {
                val tag = runCatching { prefs.sttLanguageFlow.first() }.getOrDefault("")
                vosk.transcribeFile(path, tag, onProgress).map { Result(it) }
            }
        }
}
