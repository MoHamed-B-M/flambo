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
            onProgress(0.5f)
            kotlinx.coroutines.delay(600)
            onProgress(1f)
            Result.failure(
                IllegalStateException(
                    "Whisper engine scaffold — native whisper.cpp not yet linked. " +
                        "Tap Settings > Vosk to use offline transcription, or wait for the full Whisper build. " +
                        "Model found at ${models.modelFile().absolutePath} (${samples.size} samples decoded)."
                )
            )
        } catch (e: Exception) {
            Result.failure(IllegalStateException(e.message ?: "Whisper failed"))
        }
    }

    fun release() {}
}
