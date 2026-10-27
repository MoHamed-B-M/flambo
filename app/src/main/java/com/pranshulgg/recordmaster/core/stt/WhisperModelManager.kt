package com.pranshulgg.recordmaster.core.stt

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class WhisperModel(
    val code: String,
    val label: String,
    val filename: String,
    val url: String,
    val sizeMb: Int
)

object WhisperModelCatalog {
    // Default: GGML int8-quantized tiny.en model. No FP16 models are shipped/selected.
    val DEFAULT = WhisperModel(
        code = "tiny.en",
        label = "English (tiny, int8)",
        filename = "ggml-tiny.en-q8_0.bin",
        url = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.en-q8_0.bin",
        sizeMb = 77
    )

    val CATALOG = listOf(DEFAULT)
}

class WhisperModelManager(private val context: Context) {
    private val _progress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val progress: StateFlow<Map<String, Float>> = _progress.asStateFlow()

    private val modelsDir: File
        get() = File(context.filesDir, "whisper").apply { mkdirs() }

    fun modelFile(code: String = WhisperModelCatalog.DEFAULT.code): File {
        val model = WhisperModelCatalog.CATALOG.firstOrNull { it.code == code } ?: WhisperModelCatalog.DEFAULT
        return File(modelsDir, model.filename)
    }

    fun isInstalled(code: String = WhisperModelCatalog.DEFAULT.code): Boolean {
        val f = modelFile(code)
        return f.exists() && f.length() > 1024 * 1024
    }

    fun modelPath(code: String = WhisperModelCatalog.DEFAULT.code): String = modelFile(code).absolutePath

    suspend fun ensureDefaultModel(): Result<File> = downloadIfMissing(WhisperModelCatalog.DEFAULT.code)

    suspend fun downloadIfMissing(code: String): Result<File> = withContext(Dispatchers.IO) {
        val model = WhisperModelCatalog.CATALOG.firstOrNull { it.code == code }
            ?: return@withContext Result.failure(IllegalArgumentException("Unknown model: $code"))
        val outFile = File(modelsDir, model.filename)

        if (outFile.exists() && outFile.length() > 1024 * 1024) {
            return@withContext Result.success(outFile)
        }

        try {
            val url = URL(model.url)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 30000
                readTimeout = 60000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Flambo-Whisper/2.0")
            }
            conn.connect()
            val codeHttp = conn.responseCode
            if (codeHttp !in 200..299) {
                conn.disconnect()
                return@withContext Result.failure(Exception("Download failed: HTTP $codeHttp"))
            }

            val total = conn.contentLengthLong
            conn.inputStream.use { input ->
                FileOutputStream(outFile).use { output ->
                    val buffer = ByteArray(8192)
                    var downloaded: Long = 0
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        downloaded += read.toLong()
                        if (total > 0) {
                            val p = (downloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                            _progress.value = _progress.value + (model.code to p)
                        }
                    }
                    output.flush()
                }
            }
            conn.disconnect()
            _progress.value = _progress.value - model.code
            if (outFile.exists() && outFile.length() > 1024 * 1024) {
                Result.success(outFile)
            } else {
                Result.failure(Exception("Downloaded file is too small or missing"))
            }
        } catch (e: Exception) {
            _progress.value = _progress.value - code
            if (outFile.exists()) {
                outFile.delete()
            }
            Result.failure(e)
        }
    }
}
