package com.flambo.recorder.stt

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WhisperEngine(private val context: Context) {

    val models = WhisperModelManager(context)

    data class Segment(val startMs: Long, val endMs: Long, val text: String)

    suspend fun transcribeFile(
        path: String,
        onProgress: (Float) -> Unit = {}
    ): Result<String> = withContext(Dispatchers.IO) {
        if (!models.isInstalled()) return@withContext Result.failure(ModelMissingException("whisper"))
        try {
            val samples = PcmDecoder.decodeTo16kMono(path)
            if (samples.isEmpty()) return@withContext Result.failure(IllegalStateException("No audio could be read."))
            return@withContext Result.failure(
                IllegalStateException("WHISPER_SCAFFOLD")
            )
        } catch (e: Exception) {
            Result.failure(IllegalStateException(e.message ?: "Whisper failed"))
        }
    }

    fun release() {}
}
