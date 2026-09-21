package com.flambo.recorder.stt

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WhisperEngine(private val context: Context) {

    val models = WhisperModelManager(context)

    data class Segment(val startMs: Long, val endMs: Long, val text: String)

    suspend fun transcribeFile(
        path: String,
        language: String? = null,
        onProgress: (Float) -> Unit = {}
    ): Result<String> = withContext(Dispatchers.IO) {
        if (!models.isInstalled()) return@withContext Result.failure(ModelMissingException("whisper"))
        var ctx: Long = 0
        try {
            onProgress(0.05f)
            val shortSamples = PcmDecoder.decodeTo16kMono(path)
            if (shortSamples.isEmpty()) return@withContext Result.failure(IllegalStateException("No audio could be read."))
            val samples = FloatArray(shortSamples.size) { shortSamples[it] / 32768f }
            onProgress(0.15f)
            ctx = WhisperJNI.init(models.modelFile().absolutePath)
            if (ctx == 0L) return@withContext Result.failure(IllegalStateException("Failed to load Whisper model."))
            onProgress(0.25f)
            val nThreads = Runtime.getRuntime().availableProcessors().coerceIn(1, 8)
            val lang = language?.ifBlank { null }?.lowercase()?.takeIf { it != "auto" }
            val ret = WhisperJNI.fullTranscribe(ctx, nThreads, samples, lang)
            if (ret != 0) return@withContext Result.failure(IllegalStateException("Whisper transcription failed code $ret"))
            val n = WhisperJNI.getSegmentCount(ctx)
            val out = StringBuilder()
            for (i in 0 until n) {
                val text = WhisperJNI.getSegmentText(ctx, i).trim()
                if (text.isNotBlank()) {
                    if (out.isNotEmpty()) out.append(' ')
                    out.append(text)
                }
                onProgress(0.25f + 0.75f * (i + 1) / (n.coerceAtLeast(1)))
            }
            val text = out.toString().trim().replace(Regex("\\s+"), " ")
            if (text.isBlank()) Result.failure(IllegalStateException("Couldn't make out any words — try a clearer take."))
            else {
                onProgress(1f)
                Result.success(text)
            }
        } catch (e: UnsatisfiedLinkError) {
            Result.failure(IllegalStateException("Whisper native library not loaded: ${e.message}"))
        } catch (e: Exception) {
            Result.failure(IllegalStateException(e.message ?: "Whisper failed"))
        } finally {
            if (ctx != 0L) runCatching { WhisperJNI.free(ctx) }
        }
    }

    fun release() {}
}
