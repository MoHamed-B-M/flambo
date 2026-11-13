package com.pranshulgg.recordmaster.core.stt

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.pranshulgg.recordmaster.utils.formatBytes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/** State of the one import that can run at a time. */
sealed interface ModelImportState {
    data object Idle : ModelImportState

    /** [progress] is null while the size is unknown (or during validation). */
    data class Working(val label: String, val progress: Float?) : ModelImportState
}

/** A validation or storage problem with a user-facing message. */
class ModelImportException(message: String) : IOException(message)

/**
 * Owns the model files under `filesDir/whisper/`: downloading the built-in
 * catalog, importing user files/URLs, deleting imported models.
 *
 * The registry of which models exist lives in [SpeechModelStore]; this class
 * only touches bytes on disk.
 */
class WhisperModelManager(private val context: Context) {

    private val engine = WhisperEngine()

    private val _progress = MutableStateFlow<Map<String, Float>>(emptyMap())

    /** Download progress per model id (0f..1f), for the built-in catalog. */
    val progress: StateFlow<Map<String, Float>> = _progress.asStateFlow()

    private val _importState = MutableStateFlow<ModelImportState>(ModelImportState.Idle)
    val importState: StateFlow<ModelImportState> = _importState.asStateFlow()

    private val modelsDir: File
        get() = File(context.filesDir, "whisper").apply { mkdirs() }

    fun modelFile(model: SpeechModel): File = File(modelsDir, model.fileName)

    fun isInstalled(model: SpeechModel): Boolean =
        modelFile(model).let { it.exists() && it.length() >= MIN_MODEL_BYTES }

    /** Make sure [model] is on disk, downloading a built-in if necessary. */
    suspend fun ensureModel(model: SpeechModel): Result<File> = withContext(Dispatchers.IO) {
        val file = modelFile(model)
        if (isInstalled(model)) {
            deleteLegacyModels()
            return@withContext Result.success(file)
        }
        val url = model.sourceUrl
            ?: return@withContext Result.failure(
                ModelImportException("This model isn't on this device. Import it again.")
            )
        download(model, url)
    }

    private suspend fun download(model: SpeechModel, url: String): Result<File> {
        val outFile = modelFile(model)
        val partFile = File(modelsDir, model.fileName + ".part")
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 30_000
                readTimeout = 60_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Flambo-Whisper/2.0")
            }
            conn.connect()
            val httpCode = conn.responseCode
            if (httpCode !in 200..299) {
                return Result.failure(IOException("Download failed: HTTP $httpCode"))
            }
            val total = conn.contentLengthLong
            requireSpace(total)
            conn.inputStream.use { input ->
                FileOutputStream(partFile).use { output ->
                    copyWithProgress(input, output, total) { copied ->
                        if (total > 0) {
                            val p = (copied.toFloat() / total).coerceIn(0f, 1f)
                            _progress.value = _progress.value + (model.id to p)
                        }
                    }
                }
            }
            val complete = partFile.length() >= MIN_MODEL_BYTES &&
                (total <= 0 || partFile.length() == total)
            if (!complete) {
                partFile.delete()
                return Result.failure(ModelImportException("The download was incomplete. Try again."))
            }
            if (!partFile.renameTo(outFile)) {
                partFile.delete()
                return Result.failure(IOException("Could not save the model."))
            }
            deleteLegacyModels()
            Result.success(outFile)
        } catch (e: CancellationException) {
            partFile.delete()
            throw e
        } catch (e: Exception) {
            partFile.delete()
            Result.failure(e)
        } finally {
            _progress.value = _progress.value - model.id
            conn?.disconnect()
        }
    }

    /** Display name reported by the system picker, if it has one. */
    fun displayNameFor(uri: Uri): String? = runCatching {
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0) else null
        }
    }.getOrNull()

    suspend fun importFromUri(uri: Uri): Result<SpeechModel> = withContext(Dispatchers.IO) {
        val total = querySize(uri)
        val name = displayNameFor(uri) ?: "Imported model"
        importInternal(name, total, sourceUrl = null) { output, onBytes ->
            val input = context.contentResolver.openInputStream(uri)
                ?: throw ModelImportException("Could not read the selected file.")
            input.use { copyWithProgress(it, output, total, onBytes) }
        }
    }

    suspend fun importFromUrl(rawUrl: String): Result<SpeechModel> = withContext(Dispatchers.IO) {
        val url = runCatching { URL(rawUrl.trim()) }.getOrNull()
            ?: return@withContext Result.failure(
                ModelImportException("Enter a valid https:// link.")
            )
        if (!url.protocol.equals("https", ignoreCase = true)) {
            return@withContext Result.failure(ModelImportException("Enter an https:// link."))
        }

        var conn: HttpURLConnection? = null
        try {
            conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 30_000
                readTimeout = 60_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Flambo-Whisper/2.0")
            }
            conn.connect()
            val httpCode = conn.responseCode
            if (httpCode !in 200..299) {
                return@withContext Result.failure(
                    IOException("Download failed: HTTP $httpCode")
                )
            }
            val total = conn.contentLengthLong
            val name = url.path.substringAfterLast('/')
                .substringBefore('?')
                .ifBlank { "Imported model" }
            conn.inputStream.use { input ->
                importInternal(name, total, url.toString()) { output, onBytes ->
                    copyWithProgress(input, output, total, onBytes)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            conn?.disconnect()
        }
    }

    private suspend fun importInternal(
        displayName: String,
        total: Long,
        sourceUrl: String?,
        stream: suspend (FileOutputStream, (Long) -> Unit) -> Unit
    ): Result<SpeechModel> {
        val label = SpeechModelStore.normalizeName(displayName, "Imported model")
        _importState.value = ModelImportState.Working(
            label,
            if (total > 0) 0f else null
        )
        val id = "imported-" + UUID.randomUUID().toString().take(8)
        val fileName = "${sanitizeFileName(label)}-$id.bin"
        val partFile = File(modelsDir, "$fileName.part")
        val outFile = File(modelsDir, fileName)
        return try {
            requireSpace(total)
            FileOutputStream(partFile).use { output ->
                stream(output) { copied ->
                    if (total > 0) {
                        _importState.value = ModelImportState.Working(
                            label,
                            (copied.toFloat() / total).coerceIn(0f, 1f)
                        )
                    }
                }
            }
            if (partFile.length() < MIN_MODEL_BYTES) {
                throw ModelImportException("That file is too small to be a speech model.")
            }
            rejectGguf(partFile)

            // Loading the model is the real validation: it proves whisper.cpp
            // can parse it and tells us the type and multilingual flag.
            _importState.value = ModelImportState.Working(label, null)
            val inspected = engine.nativeInspect(partFile.absolutePath)
                ?: throw ModelImportException(
                    "This isn't a Whisper GGML model, or the file is incomplete. " +
                        "Import a .bin file such as ggml-small-q8_0.bin."
                )
            val fields = inspected.split('|')
            val modelType = fields.getOrNull(0)?.ifBlank { "unknown" } ?: "unknown"
            val multilingual = fields.getOrNull(1) == "1"

            if (!partFile.renameTo(outFile)) {
                throw IOException("Could not save the model.")
            }

            Result.success(
                SpeechModel(
                    id = id,
                    displayName = label,
                    fileName = fileName,
                    source = SpeechModelSource.IMPORTED,
                    sizeBytes = outFile.length(),
                    modelType = modelType,
                    multilingual = multilingual,
                    sourceUrl = sourceUrl,
                    importedAt = System.currentTimeMillis()
                )
            )
        } catch (e: CancellationException) {
            partFile.delete()
            throw e
        } catch (e: Exception) {
            partFile.delete()
            Result.failure(e)
        } finally {
            _importState.value = ModelImportState.Idle
        }
    }

    suspend fun delete(model: SpeechModel): Result<Unit> = withContext(Dispatchers.IO) {
        if (model.isBuiltIn) {
            return@withContext Result.failure(
                IllegalArgumentException("Built-in models can't be deleted.")
            )
        }
        val file = modelFile(model)
        if (file.exists() && !file.delete()) {
            Result.failure(IOException("Could not delete the model file."))
        } else {
            Result.success(Unit)
        }
    }

    /** Remove `.part` files left behind by a killed process. */
    fun cleanupStaleParts() {
        val cutoff = System.currentTimeMillis() - 24L * 60 * 60 * 1000
        runCatching {
            modelsDir.listFiles()?.forEach { file ->
                if (file.name.endsWith(".part") && file.lastModified() < cutoff) {
                    file.delete()
                }
            }
        }
    }

    private fun querySize(uri: Uri): Long = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else -1L
            } ?: -1L
    }.getOrDefault(-1L)

    private suspend fun copyWithProgress(
        input: InputStream,
        output: FileOutputStream,
        total: Long,
        onBytes: (Long) -> Unit
    ): Long {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var copied = 0L
        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            currentCoroutineContext().ensureActive()
            copied += read
            if (copied > MAX_MODEL_BYTES) {
                throw ModelImportException("This model is larger than 4 GB.")
            }
            output.write(buffer, 0, read)
            onBytes(copied)
        }
        output.flush()
        return copied
    }

    private fun requireSpace(total: Long) {
        if (total > MAX_MODEL_BYTES) {
            throw ModelImportException("This model is larger than 4 GB.")
        }
        val usable = modelsDir.usableSpace
        if (total > 0 && usable > 0 && total + MIN_FREE_MARGIN > usable) {
            throw ModelImportException(
                "Not enough storage. This model needs ${formatBytes(total)}; " +
                    "${formatBytes(usable)} is free."
            )
        }
    }

    private fun rejectGguf(file: File) {
        val header = ByteArray(4)
        val read = file.inputStream().use { it.read(header) }
        if (read < 4) return
        val gguf = header[0] == 'G'.code.toByte() &&
            header[1] == 'G'.code.toByte() &&
            header[2] == 'U'.code.toByte() &&
            header[3] == 'F'.code.toByte()
        if (gguf) {
            throw ModelImportException(
                "GGUF models aren't supported yet. Use a GGML .bin model."
            )
        }
    }

    private fun sanitizeFileName(name: String): String {
        val base = name.substringBeforeLast('.', name)
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .trim('_', '.', '-')
            .take(40)
        return base.ifBlank { "model" }
    }

    private fun deleteLegacyModels() {
        SpeechModelCatalog.LEGACY_FILENAMES.forEach { name ->
            runCatching { File(modelsDir, name).takeIf { it.exists() }?.delete() }
        }
    }

    private companion object {
        const val MIN_MODEL_BYTES = 1024L * 1024
        const val MAX_MODEL_BYTES = 4L * 1024 * 1024 * 1024
        const val MIN_FREE_MARGIN = 200L * 1024 * 1024
    }
}
