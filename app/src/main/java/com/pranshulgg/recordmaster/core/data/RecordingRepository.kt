package com.pranshulgg.recordmaster.core.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Room-backed index over RecordMaster's real file tree.
 *
 * The file system remains the physical truth: folders and `garbage/` keep
 * their existing meaning, and the database caches metadata plus feature state.
 * A sync only adds/repairs rows; it never renames, moves or deletes user files.
 */
class RecordingRepository(
    private val dao: RecordingDao,
    private val dirProvider: () -> File,
    private val garbageDirProvider: () -> File,
    private val appContext: Context
) {
    fun observeRecordings(): Flow<List<Recording>> = dao.observeAll()
    fun observeTrash(): Flow<List<Recording>> = dao.observeTrash()
    fun observeById(id: Long): Flow<Recording?> = dao.observeById(id)

    suspend fun getById(id: Long): Recording? = dao.getById(id)
    suspend fun getByPath(path: String): Recording? = dao.getByPath(path)
    fun observeByPath(path: String): Flow<Recording?> = dao.observeByPath(path)

    fun search(query: String): Flow<List<Recording>> =
        if (query.isBlank()) observeRecordings() else dao.search(query)

    suspend fun insert(recording: Recording): Long = dao.insert(recording)
    suspend fun update(recording: Recording) = dao.update(recording)

    /**
     * Renames the row and the physical file together, preserving the existing
     * extension. Passing a name that already includes the extension is fine.
     */
    suspend fun rename(id: Long, newTitle: String): Boolean = withContext(Dispatchers.IO) {
        val rec = getById(id) ?: return@withContext false
        val trimmed = newTitle.trim()
        if (trimmed.isEmpty()) return@withContext false

        if (AudioFileStore.isContentUri(rec.filePath)) {
            dao.update(rec.copy(title = trimmed))
            return@withContext true
        }

        val src = File(rec.filePath)
        val ext = src.extension
        val inputBase = if (ext.isNotBlank() && trimmed.endsWith(".$ext", ignoreCase = true)) {
            trimmed.dropLast(ext.length + 1)
        } else {
            trimmed
        }
        val safeBase = sanitizeFileName(inputBase)

        var finalPath = rec.filePath
        var renamed = false
        if (src.exists()) {
            val target = uniqueFile(src.parentFile ?: recordingsDir(), safeBase, ext)
            if (target.absolutePath != src.absolutePath) {
                if (!moveTo(src, target)) return@withContext false
                finalPath = target.absolutePath
                renamed = true
            }
        }

        var enhanced = rec.enhancedPath
        if (renamed && enhanced.isNotBlank() && !AudioFileStore.isContentUri(enhanced)) {
            val enhancedFile = File(enhanced)
            if (enhancedFile.exists()) {
                val target = uniqueFile(
                    enhancedFile.parentFile ?: recordingsDir(),
                    safeBase + ENHANCED_SUFFIX,
                    enhancedFile.extension
                )
                if (moveTo(enhancedFile, target)) enhanced = target.absolutePath
            }
        }

        dao.update(rec.copy(title = safeBase, filePath = finalPath, enhancedPath = enhanced))
        true
    }

    suspend fun toggleFavorite(id: Long) {
        getById(id)?.let { dao.update(it.copy(isFavorite = !it.isFavorite)) }
    }

    /** Moves the recording (and its enhanced copy) into `garbage/`. */
    suspend fun softDelete(id: Long): Boolean = withContext(Dispatchers.IO) {
        val rec = getById(id) ?: return@withContext false
        val garbage = garbageDir()
        var path = rec.filePath

        if (!AudioFileStore.isContentUri(path) && !isInside(path, garbage)) {
            val src = File(path)
            if (src.exists()) {
                val target = uniqueFile(garbage, src.nameWithoutExtension, src.extension)
                if (!moveTo(src, target)) return@withContext false
                path = target.absolutePath
            }
        }

        var enhanced = rec.enhancedPath
        if (enhanced.isNotBlank() && !AudioFileStore.isContentUri(enhanced) && !isInside(enhanced, garbage)) {
            val src = File(enhanced)
            if (src.exists()) {
                val target = uniqueFile(garbage, src.nameWithoutExtension, src.extension)
                if (moveTo(src, target)) enhanced = target.absolutePath
            }
        }

        dao.update(
            rec.copy(
                filePath = path,
                enhancedPath = enhanced,
                isTrashed = true,
                trashedAt = System.currentTimeMillis()
            )
        )
        true
    }

    suspend fun softDeleteAll(ids: Collection<Long>) {
        for (id in ids) softDelete(id)
    }

    /** Moves a trashed recording back to the library root and clears its flags. */
    suspend fun restore(id: Long, targetDir: File? = null): Boolean = withContext(Dispatchers.IO) {
        val rec = getById(id) ?: return@withContext false
        val target = targetDir ?: recordingsDir()
        target.mkdirs()
        val garbage = garbageDir()
        var path = rec.filePath

        if (!AudioFileStore.isContentUri(path) && isInside(path, garbage)) {
            val src = File(path)
            if (src.exists()) {
                val dest = uniqueFile(target, src.nameWithoutExtension, src.extension)
                if (!moveTo(src, dest)) return@withContext false
                path = dest.absolutePath
            }
        }

        var enhanced = rec.enhancedPath
        if (enhanced.isNotBlank() && !AudioFileStore.isContentUri(enhanced) && isInside(enhanced, garbage)) {
            val src = File(enhanced)
            if (src.exists()) {
                val dest = uniqueFile(target, src.nameWithoutExtension, src.extension)
                if (moveTo(src, dest)) enhanced = dest.absolutePath
            }
        }

        dao.update(rec.copy(filePath = path, enhancedPath = enhanced, isTrashed = false, trashedAt = null))
        true
    }

    suspend fun moveToDirectory(ids: Collection<Long>, targetDir: File): Int =
        withContext(Dispatchers.IO) {
            targetDir.mkdirs()
            var moved = 0
            for (id in ids) {
                val rec = getById(id) ?: continue
                if (AudioFileStore.isContentUri(rec.filePath)) continue
                val src = File(rec.filePath)
                if (!src.exists()) continue
                if (src.parentFile?.absolutePath == targetDir.absolutePath) continue

                val dest = uniqueFile(targetDir, src.nameWithoutExtension, src.extension)
                if (!moveTo(src, dest)) continue

                var enhanced = rec.enhancedPath
                if (enhanced.isNotBlank() && !AudioFileStore.isContentUri(enhanced)) {
                    val enhancedFile = File(enhanced)
                    if (enhancedFile.exists()) {
                        val enhancedDest = uniqueFile(
                            targetDir,
                            enhancedFile.nameWithoutExtension,
                            enhancedFile.extension
                        )
                        if (moveTo(enhancedFile, enhancedDest)) enhanced = enhancedDest.absolutePath
                    }
                }

                dao.update(rec.copy(filePath = dest.absolutePath, enhancedPath = enhanced))
                moved++
            }
            moved
        }

    suspend fun addTagToAll(ids: Collection<Long>, tag: String): Int {
        val clean = tag.trim().trim(',')
        if (clean.isEmpty()) return 0
        var tagged = 0
        for (id in ids) {
            val rec = getById(id) ?: continue
            if (clean !in rec.tagList) {
                dao.update(rec.copy(tags = (rec.tagList + clean).joinToString(",")))
            }
            tagged++
        }
        return tagged
    }

    /** Removes a tag from every active recording; returns affected ids for undo. */
    suspend fun removeTagFromAll(tag: String): Set<Long> {
        val clean = tag.trim().trim(',')
        if (clean.isEmpty()) return emptySet()
        val touched = mutableSetOf<Long>()
        for (rec in observeRecordings().first()) {
            if (clean in rec.tagList) {
                dao.update(rec.copy(tags = (rec.tagList - clean).joinToString(",")))
                touched += rec.id
            }
        }
        return touched
    }

    suspend fun updateTags(id: Long, tags: List<String>) {
        getById(id)?.let {
            dao.update(it.copy(tags = tags.joinToString(",") { tag -> tag.trim() }.trim(',')))
        }
    }

    suspend fun saveTranscript(id: Long, transcript: String) {
        dao.updateTranscript(id, transcript.trim())
    }

    suspend fun clearTranscript(id: Long) {
        dao.updateTranscript(id, "")
    }

    suspend fun saveEnhanced(id: Long, path: String) {
        dao.updateEnhancedPath(id, path)
    }

    /**
     * Makes the cleaned copy the primary recording (used when "Keep original"
     * is off). Renames it back to the original base name, deletes the old file
     * and clears [Recording.enhancedPath].
     */
    suspend fun replaceWithEnhanced(id: Long): Boolean = withContext(Dispatchers.IO) {
        val rec = getById(id) ?: return@withContext false
        val enhanced = rec.enhancedPath
        if (enhanced.isBlank() || AudioFileStore.isContentUri(enhanced)) {
            return@withContext false
        }
        val enhancedFile = File(enhanced)
        if (!enhancedFile.exists()) return@withContext false

        val originalFile = if (AudioFileStore.isContentUri(rec.filePath)) null else File(rec.filePath)
        val parent = originalFile?.parentFile ?: enhancedFile.parentFile ?: recordingsDir()
        val base = originalFile?.nameWithoutExtension
            ?: enhancedFile.nameWithoutExtension.removeSuffix(ENHANCED_SUFFIX)
        val target = uniqueFile(parent, base, enhancedFile.extension)
        if (target.absolutePath != enhancedFile.absolutePath && !moveTo(enhancedFile, target)) {
            return@withContext false
        }
        if (originalFile != null && originalFile.absolutePath != target.absolutePath) {
            deleteAudio(originalFile.absolutePath)
        }
        dao.update(
            rec.copy(
                filePath = target.absolutePath,
                enhancedPath = "",
                title = base
            )
        )
        true
    }

    suspend fun clearEnhanced(id: Long) = withContext(Dispatchers.IO) {
        getById(id)?.let { deleteAudio(it.enhancedPath) }
        dao.updateEnhancedPath(id, "")
    }

    suspend fun deletePermanently(id: Long) = withContext(Dispatchers.IO) {
        val rec = getById(id) ?: return@withContext
        deleteAudio(rec.filePath)
        deleteAudio(rec.enhancedPath)
        dao.deletePermanently(id)
    }

    suspend fun emptyTrash() = withContext(Dispatchers.IO) {
        for (rec in dao.getTrash()) {
            deleteAudio(rec.filePath)
            deleteAudio(rec.enhancedPath)
            dao.deletePermanently(rec.id)
        }
    }

    /** Opt-in only (D3): default behaviour keeps garbage until the user deletes it. */
    suspend fun purgeOldTrash(days: Int = 7) = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - days * 24L * 60 * 60 * 1000
        val old = dao.getTrash().filter { (it.trashedAt ?: 0L) < cutoff }
        for (rec in old) {
            deleteAudio(rec.filePath)
            deleteAudio(rec.enhancedPath)
        }
        dao.purgeOldTrash(cutoff)
    }

    suspend fun activeTitles(): List<String> = dao.activeTitles()

    /** IDs whose audio file is gone from disk or SAF. */
    suspend fun findMissingIds(recordings: List<Recording>): Set<Long> =
        withContext(Dispatchers.IO) {
            recordings
                .filter { it.filePath.isNotBlank() && !AudioFileStore.exists(appContext, it.filePath) }
                .map { it.id }
                .toSet()
        }

    /** Bumped after every storage sync so missing-file flags recompute. */
    val storageTick = MutableStateFlow(0)

    /**
     * Indexes existing files and adopts files added outside the app.
     *
     * - Recurses into user folders (excluding `garbage/`) and indexes new files.
     * - Indexes `garbage/` contents as trashed rows.
     * - Repairs trash flags for rows whose file moved in or out of `garbage/`.
     * - Never renames, moves, deletes or re-titles an existing file.
     */
    suspend fun syncExternalFiles(): Int = withContext(Dispatchers.IO) {
        runCatching {
            val root = recordingsDir()
            val garbage = garbageDir()
            val known = (dao.getAllPaths() + dao.getAllEnhancedPaths()).toMutableSet()
            var added = 0

            val candidates = mutableListOf<Pair<File, Boolean>>()
            collectAudioFiles(root, trashed = false, out = candidates)
            collectAudioFiles(garbage, trashed = true, out = candidates)

            for ((file, trashed) in candidates) {
                if (file.extension.lowercase() !in AUDIO_EXTENSIONS) continue
                if (file.name.startsWith(TEMP_PREFIX)) continue
                if (file.nameWithoutExtension.endsWith(ENHANCED_SUFFIX)) continue
                val path = file.absolutePath
                if (!known.add(path)) continue
                if (insertForeignFile(file, path, trashed)) added++
            }

            for (rec in dao.getAll()) {
                if (AudioFileStore.isContentUri(rec.filePath)) continue
                val file = File(rec.filePath)
                val insideGarbage = isInside(rec.filePath, garbage)
                if (insideGarbage && !rec.isTrashed) {
                    dao.update(
                        rec.copy(
                            isTrashed = true,
                            trashedAt = rec.trashedAt
                                ?: file.lastModified().takeIf { it > 0 }
                                ?: System.currentTimeMillis()
                        )
                    )
                } else if (!insideGarbage && rec.isTrashed && file.exists()) {
                    dao.update(rec.copy(isTrashed = false, trashedAt = null))
                }
            }

            storageTick.value++
            added
        }.getOrDefault(0)
    }

    fun recordingsDir(): File = dirProvider().apply { mkdirs() }

    fun garbageDir(): File = garbageDirProvider().apply { mkdirs() }

    private fun collectAudioFiles(dir: File, trashed: Boolean, out: MutableList<Pair<File, Boolean>>) {
        val children = dir.listFiles() ?: return
        for (file in children) {
            if (file.isDirectory) {
                if (!trashed && (file.name == GARBAGE_DIR_NAME || file.name.startsWith("."))) continue
                collectAudioFiles(file, trashed, out)
            } else if (file.isFile) {
                out += file to trashed
            }
        }
    }

    private suspend fun insertForeignFile(file: File, path: String, trashed: Boolean): Boolean {
        val modified = file.lastModified().takeIf { it > 0 } ?: System.currentTimeMillis()
        return runCatching {
            dao.insert(
                Recording(
                    title = file.nameWithoutExtension.ifBlank { "Recording" },
                    filePath = path,
                    durationMs = readDuration(path),
                    createdAt = modified,
                    isTrashed = trashed,
                    trashedAt = if (trashed) modified else null,
                    quality = "HIGH"
                )
            )
        }.isSuccess
    }

    private fun readDuration(path: String): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            if (AudioFileStore.isContentUri(path)) {
                retriever.setDataSource(appContext, Uri.parse(path))
            } else {
                retriever.setDataSource(path)
            }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
        } catch (_: Exception) {
            0L
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun deleteAudio(path: String) {
        if (path.isBlank()) return
        runCatching { AudioFileStore.delete(appContext, path) }
    }

    private fun moveTo(src: File, dest: File): Boolean = try {
        if (src.renameTo(dest)) {
            true
        } else {
            src.copyTo(dest, overwrite = true)
            src.delete()
            true
        }
    } catch (_: Exception) {
        false
    }

    private fun uniqueFile(dir: File, baseName: String, ext: String): File {
        val safeExt = if (ext.isBlank()) "" else ".$ext"
        var candidate = File(dir, baseName + safeExt)
        if (!candidate.exists()) return candidate
        var index = 2
        while (File(dir, "$baseName ($index)$safeExt").exists()) index++
        return File(dir, "$baseName ($index)$safeExt")
    }

    private fun sanitizeFileName(name: String): String =
        name.replace(Regex("[/\\\\:*?\"<>|]"), "_").trim().take(80).ifBlank { "Recording" }

    private fun isInside(path: String, dir: File): Boolean {
        val dirPath = runCatching { dir.canonicalPath }.getOrElse { dir.absolutePath }
        val filePath = runCatching { File(path).canonicalPath }.getOrElse { path }
        return filePath == dirPath || filePath.startsWith(dirPath + File.separator)
    }

    companion object {
        const val GARBAGE_DIR_NAME = "garbage"
        const val TEMP_PREFIX = "REC_"
        const val ENHANCED_SUFFIX = "_enhanced"

        private val AUDIO_EXTENSIONS = setOf(
            "m4a", "wav", "mp3", "ogg", "opus", "aac", "flac", "3gp", "amr"
        )
    }
}
