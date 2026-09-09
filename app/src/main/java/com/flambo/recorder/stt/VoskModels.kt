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
import java.util.zip.ZipFile

// Offline model catalog + manager. Models live in internal storage
// (filesDir/vosk-models/<code>), so no storage permission is needed.
class VoskModelManager(private val context: Context) {

    data class VoskModel(
        val code: String, // ISO-639 base, matches BCP-47 prefix
        val label: String,
        val file: String, // zip name on alphacephei.com
        val sizeMb: Int,
        val large: Boolean = false
    )

    companion object {
        const val BASE_URL = "https://alphacephei.com/vosk/models/"
        val CATALOG = listOf(
            VoskModel("en", "English", "vosk-model-small-en-us-0.15.zip", 40),
            VoskModel("es", "Spanish", "vosk-model-small-es-0.42.zip", 39),
            VoskModel("fr", "French", "vosk-model-small-fr-0.22.zip", 41),
            VoskModel("de", "German", "vosk-model-small-de-0.15.zip", 45),
            VoskModel("it", "Italian", "vosk-model-small-it-0.22.zip", 48),
            VoskModel("pt", "Portuguese", "vosk-model-small-pt-0.3.zip", 31),
            VoskModel("ru", "Russian", "vosk-model-small-ru-0.22.zip", 45),
            VoskModel("hi", "Hindi", "vosk-model-small-hi-0.22.zip", 44),
            VoskModel("ja", "Japanese", "vosk-model-small-ja-0.22.zip", 48),
            VoskModel("zh", "Chinese", "vosk-model-small-cn-0.22.zip", 42),
            VoskModel("tr", "Turkish", "vosk-model-small-tr-0.3.zip", 35),
            VoskModel("ar", "Arabic", "vosk-model-ar-mgb2-0.4.zip", 300, large = true)
        )

        fun forTag(tag: String): VoskModel? {
            val base = tag.substringBefore('-').substringBefore('_').lowercase()
            return CATALOG.firstOrNull { it.code == base }
        }
    }

    // code -> 0..1 download progress
    private val _progress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val progress: StateFlow<Map<String, Float>> = _progress.asStateFlow()

    fun modelsRoot(): File = File(context.filesDir, "vosk-models").apply { mkdirs() }
    fun modelDir(code: String): File = File(modelsRoot(), code)

    fun isInstalled(code: String): Boolean = findModelDir(code) != null

    fun installedCodes(): List<String> =
        modelsRoot().listFiles()?.filter { it.isDirectory && containsFinalMdl(it) }
            ?.map { it.name } ?: emptyList()

    fun modelSizeOnDisk(code: String): Long =
        findModelDir(code)?.walkTopDown()?.filter { it.isFile }?.sumOf { it.length() } ?: 0L

    private fun findModelDir(code: String): File? {
        val direct = modelDir(code)
        if (containsFinalMdl(direct)) return direct
        // Be lenient: accept the raw unzipped folder name too.
        return modelsRoot().listFiles()
            ?.firstOrNull { it.isDirectory && (it.name == code || containsFinalMdl(it)) }
    }

    private fun containsFinalMdl(dir: File): Boolean {
        if (!dir.isDirectory) return false
        if (File(dir, "final.mdl").exists() || File(dir, "am/final.mdl").exists()) return true
        return dir.walkTopDown().maxDepth(3).any { it.isFile && it.name == "final.mdl" }
    }

    fun delete(code: String): Boolean =
        runCatching { modelDir(code).deleteRecursively() }.getOrDefault(false)

    suspend fun download(code: String): Result<Unit> = withContext(Dispatchers.IO) {
        val model = CATALOG.firstOrNull { it.code == code }
            ?: return@withContext Result.failure(IllegalArgumentException("Unknown language model."))
        val zip = File(modelsRoot(), "${model.file}.tmp")
        try {
            setProgress(code, 0f)
            val url = URL(BASE_URL + model.file)
            (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = true
            }.use { conn ->
                conn.connect()
                require(conn.responseCode in 200..299) { "Download failed (HTTP ${conn.responseCode})." }
                val total = conn.contentLengthLong.takeIf { it > 0 } ?: (model.sizeMb * 1024L * 1024L)
                conn.inputStream.use { input ->
                    zip.outputStream().use { output ->
                        val buf = ByteArray(32 * 1024)
                        var done = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            output.write(buf, 0, n)
                            done += n
                            setProgress(code, (done.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
            }
            unzipIntoPlace(zip, code)
            setProgress(code, 1f)
            Result.success(Unit)
        } catch (e: Exception) {
            runCatching { zip.delete() }
            Result.failure(IllegalStateException("Download failed: ${e.message}"))
        } finally {
            clearProgress(code)
        }
    }

    private fun unzipIntoPlace(zip: File, code: String) {
        val staging = File(modelsRoot(), ".staging-$code").apply {
            deleteRecursively()
            mkdirs()
        }
        ZipFile(zip).use { zf ->
            val entries = zf.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val out = File(staging, entry.name)
                if (!out.canonicalPath.startsWith(staging.canonicalPath)) continue
                if (entry.isDirectory) out.mkdirs()
                else {
                    out.parentFile?.mkdirs()
                    zf.getInputStream(entry).use { it.copyTo(out.outputStream()) }
                }
            }
        }
        // The zip wraps the model in a single top-level folder — normalize to <code>/.
        val root = staging.listFiles()?.firstOrNull { it.isDirectory && containsFinalMdl(it) }
            ?: staging.takeIf { containsFinalMdl(it) }
            ?: throw IllegalStateException("That archive didn't contain a usable model.")
        val dest = modelDir(code)
        dest.deleteRecursively()
        require(root.renameTo(dest)) { "Couldn't install the model." }
        staging.deleteRecursively()
        zip.delete()
    }

    private fun setProgress(code: String, value: Float) {
        _progress.value = _progress.value + (code to value)
    }

    private fun clearProgress(code: String) {
        _progress.value = _progress.value - code
    }
}
