package com.flambo.recorder.stt

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class WhisperModelManager(private val context: Context) {

    companion object {
        const val MODEL_FILE = "ggml-tiny.bin"
        const val MODEL_FILE_Q5 = "ggml-tiny-q5_0.bin"
        const val URL_TINY = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.bin"
        const val URL_TINY_Q5 = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny-q5_0.bin"
        const val SIZE_MB = 75
        const val SIZE_MB_Q5 = 31
    }

    private val _progress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val progress: StateFlow<Map<String, Float>> = _progress.asStateFlow()

    fun modelsRoot(): File = File(context.filesDir, "whisper").apply { mkdirs() }
    fun modelFile(): File = File(modelsRoot(), MODEL_FILE)
    fun isInstalled(): Boolean = modelFile().exists() && modelFile().length() > 10 * 1024 * 1024
    fun modelSizeOnDisk(): Long = modelFile().takeIf { it.exists() }?.length() ?: 0L
    fun delete(): Boolean = runCatching { modelFile().delete() }.getOrDefault(false)

    suspend fun download(useQuantized: Boolean = false): Result<Unit> = withContext(Dispatchers.IO) {
        val urlStr = if (useQuantized) URL_TINY_Q5 else URL_TINY
        val dest = modelFile()
        val tmp = File(modelsRoot(), "${dest.name}.tmp")
        try {
            setProgress("whisper", 0f)
            val url = URL(urlStr)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = true
            }
            try {
                conn.connect()
                require(conn.responseCode in 200..299) { "Download failed HTTP ${conn.responseCode}" }
                val total = conn.contentLengthLong.takeIf { it > 0 } ?: ((if (useQuantized) SIZE_MB_Q5 else SIZE_MB) * 1024L * 1024L)
                conn.inputStream.use { input ->
                    tmp.outputStream().use { output ->
                        val buf = ByteArray(32 * 1024)
                        var done = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            output.write(buf, 0, n)
                            done += n
                            setProgress("whisper", (done.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
            } finally { conn.disconnect() }
            tmp.renameTo(dest)
            setProgress("whisper", 1f)
            Result.success(Unit)
        } catch (e: Exception) {
            runCatching { tmp.delete() }
            Result.failure(IllegalStateException("Download failed: ${e.message}"))
        } finally { clearProgress("whisper") }
    }

    private fun setProgress(code: String, v: Float) { _progress.value = _progress.value + (code to v) }
    private fun clearProgress(code: String) { _progress.value = _progress.value - code }
}
