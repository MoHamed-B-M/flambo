package com.flambo.recorder.stt

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer

class ModelMissingException(val code: String) : Exception("Offline model missing: $code")

// Secondary engine: fully offline file transcription with Vosk. Model loading
// is expensive, so loaded models are cached for the process lifetime.
class VoskEngine(private val context: Context) {

    val models = VoskModelManager(context)
    private val loaded = mutableMapOf<String, Model>()

    // Best installed model for a BCP-47 tag, preferring an exact base match,
    // then English, then whatever is installed.
    @Synchronized
    fun resolveModelCode(languageTag: String): String {
        val base = languageTag.substringBefore('-').substringBefore('_').lowercase()
        if (models.isInstalled(base)) return base
        if (models.isInstalled("en")) return "en"
        return models.installedCodes().firstOrNull() ?: base.ifBlank { "en" }
    }

    suspend fun transcribeFile(
        path: String,
        languageTag: String,
        onProgress: (Float) -> Unit = {}
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val code = resolveModelCode(languageTag)
            if (!models.isInstalled(code)) return@withContext Result.failure(ModelMissingException(code))
            val model = loaded.getOrPut(code) { Model(models.modelDir(code).absolutePath) }
            val samples = PcmDecoder.decodeTo16kMono(path)
            if (samples.isEmpty()) {
                return@withContext Result.failure(IllegalStateException("No audio could be read from this recording."))
            }
            val recognizer = Recognizer(model, PcmDecoder.TARGET_RATE.toFloat())
            try {
                val out = StringBuilder()
                var i = 0
                val chunk = 8000
                while (i < samples.size) {
                    val n = minOf(chunk, samples.size - i)
                    val part = samples.copyOfRange(i, i + n)
                    if (recognizer.acceptWaveForm(part, n)) {
                        val piece = textOf(recognizer.result)
                        if (piece.isNotBlank()) out.append(piece).append(' ')
                    }
                    i += n
                    onProgress(i.toFloat() / samples.size)
                }
                val tail = textOf(recognizer.finalResult)
                if (tail.isNotBlank()) out.append(tail)
                val text = out.toString().trim().replace(Regex("\\s+"), " ")
                if (text.isBlank()) {
                    Result.failure(IllegalStateException("Couldn't make out any words — try a clearer take or another language."))
                } else Result.success(text)
            } finally {
                recognizer.close()
            }
        } catch (e: Exception) {
            val failure = e as? ModelMissingException
                ?: IllegalStateException(e.message ?: "Transcription failed.")
            Result.failure(failure)
        }
    }

    private fun textOf(json: String): String =
        runCatching { JSONObject(json).optString("text", "") }.getOrDefault("")

    fun release() = synchronized(this) {
        loaded.values.forEach { runCatching { it.close() } }
        loaded.clear()
    }
}
