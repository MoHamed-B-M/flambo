package com.pranshulgg.recordmaster.core.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.pranshulgg.recordmaster.core.metadata.MetadataHelper
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

    fun observeWithBookmarks(id: Long): Flow<RecordingWithBookmarks?> = dao.observeWithBookmarks(id)
    fun observeBookmarks(recordingId: Long): Flow<List<Bookmark>> = dao.observeBookmarks(recordingId)

    suspend fun getBookmarks(recordingId: Long): List<Bookmark> = dao.getBookmarks(recordingId)

    suspend fun addBookmark(
        recordingId: Long,
        timestampMs: Long,
        note: String? = null
    ): Bookmark {
        val bookmark = Bookmark(
            recordingId = recordingId,
            timestampMs = timestampMs.coerceAtLeast(0L),
            note = note?.takeIf { it.isNotBlank() }
        )
        val id = dao.insertBookmark(bookmark)
        return bookmark.copy(id = id)
    }

    suspend fun deleteBookmark(bookmark: Bookmark) = dao.deleteBookmark(bookmark)

    suspend fun deleteBookmarks(recordingId: Long) = dao.deleteBookmarks(recordingId)

    suspend fun getById(id: Long): Recording? = dao.getById(id)
    suspend fun getByPath(path: String): Recording? = dao.getByPath(path)
    fun observeByPath(path: String): Flow<Recording?> = dao.observeByPath(path)

    fun search(query: String): Flow<List<Recording>> =
        if (query.isBlank()) observeRecordings() else dao.search(query)

    suspend fun insert(recording: Recording): Long = dao.insert(recording)

    suspend fun update(recording: Recording) = withContext(Dispatchers.IO) {
        dao.update(recording)
        runCatching { MetadataHelper.writeTags(recording) }
    }

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

        val updated = rec.copy(title = safeBase, filePath = finalPath, enhancedPath = enhanced)
        dao.update(updated)
        runCatching { MetadataHelper.writeTags(updated) }
        true
    }

    suspend fun toggleFavorite(id: Long): Boolean = withContext(Dispatchers.IO) {
        val rec = getById(id) ?: return@withContext false
        val newFavorite = !rec.isFavorite
        val updatedRec = rec.copy(isFavorite = newFavorite)
        val targetDir = when {
            newFavorite -> favoritesDir()
            updatedRec.tagList.isNotEmpty() -> tagsDir()
            else -> recordingsDir()
        }
        val moved = moveRecordingFilesTo(updatedRec, targetDir)
        dao.update(moved)
        newFavorite
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

    suspend fun updateTags(id: Long, tags: List<String>) = withContext(Dispatchers.IO) {
        val rec = getById(id) ?: return@withContext
        val cleanTags = tags.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val updatedRec = rec.copy(tags = cleanTags.joinToString(","))
        val targetDir = when {
            cleanTags.isNotEmpty() -> tagsDir()
            updatedRec.isFavorite -> favoritesDir()
            else -> recordingsDir()
        }
        val moved = moveRecordingFilesTo(updatedRec, targetDir)
        dao.update(moved)
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
     * is off). Moves the cleaned file into the "Cleaned audio" folder, deletes
     * the old file and clears [Recording.enhancedPath].
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
        val base = originalFile?.nameWithoutExtension
            ?: enhancedFile.nameWithoutExtension.removeSuffix(ENHANCED_SUFFIX)
        val target = uniqueFile(cleanedAudioDir(), base, enhancedFile.extension)
        if (target.absolutePath != enhancedFile.absolutePath && !moveTo(enhancedFile, target)) {
            return@withContext false
        }
        if (originalFile != null && originalFile.absolutePath != target.absolutePath) {
            deleteAudio(originalFile.absolutePath)
        }
        val updated = rec.copy(
            filePath = target.absolutePath,
            enhancedPath = "",
            title = base,
            fileSize = target.length().takeIf { it > 0 } ?: rec.fileSize,
            durationMs = readDuration(target.absolutePath).takeIf { it > 0 } ?: rec.durationMs,
            duration = readDuration(target.absolutePath).takeIf { it > 0 } ?: rec.duration
        )
        dao.update(updated)
        runCatching { MetadataHelper.writeTags(updated) }
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
                var updated = rec

                if (insideGarbage && !rec.isTrashed) {
                    updated = updated.copy(
                        isTrashed = true,
                        trashedAt = rec.trashedAt
                            ?: file.lastModified().takeIf { it > 0 }
                            ?: System.currentTimeMillis()
                    )
                } else if (!insideGarbage && rec.isTrashed && file.exists()) {
                    updated = updated.copy(isTrashed = false, trashedAt = null)
                }

                if (file.exists()) {
                    val actualSize = file.length().takeIf { it > 0 } ?: 0L
                    val actualDuration = if (updated.durationMs <= 0L || updated.duration <= 0L) {
                        readDuration(updated.filePath)
                    } else {
                        0L
                    }
                    val resolvedDuration = when {
                        actualDuration > 0L -> actualDuration
                        updated.durationMs > 0L -> updated.durationMs
                        updated.duration > 0L -> updated.duration
                        else -> 0L
                    }
                    if (updated.fileSize != actualSize ||
                        updated.durationMs != resolvedDuration ||
                        updated.duration != resolvedDuration
                    ) {
                        updated = updated.copy(
                            fileSize = actualSize,
                            durationMs = resolvedDuration,
                            duration = resolvedDuration
                        )
                    }
                }

                if (updated != rec) {
                    dao.update(updated)
                }
            }

            storageTick.value++
            added
        }.getOrDefault(0)
    }

    fun recordingsDir(): File = dirProvider().apply { mkdirs() }

    /**
     * Private, internal staging area for in-progress audio chunks. Kept under
     * [android.content.Context.getFilesDir] rather than [recordingsDir] because
     * the latter may be an external/SAF folder that the library sync scans.
     * Orphaned sessions left here after a crash are picked up by recovery.
     */
    fun tempChunksDir(): File = File(appContext.filesDir, TEMP_CHUNKS_DIR_NAME).apply { mkdirs() }

    fun garbageDir(): File = garbageDirProvider().apply { mkdirs() }

    fun cleanedAudioDir(): File = File(recordingsDir(), CLEANED_AUDIO_DIR_NAME).apply { mkdirs() }

    fun favoritesDir(): File = File(recordingsDir(), FAVORITES_DIR_NAME).apply { mkdirs() }

    fun tagsDir(): File = File(recordingsDir(), TAGS_DIR_NAME).apply { mkdirs() }

    private fun moveRecordingFilesTo(rec: Recording, targetDir: File): Recording {
        if (AudioFileStore.isContentUri(rec.filePath)) return rec
        targetDir.mkdirs()
        var path = rec.filePath
        val src = File(path)
        if (src.exists() && src.parentFile?.absolutePath != targetDir.absolutePath) {
            val dest = uniqueFile(targetDir, src.nameWithoutExtension, src.extension)
            if (moveTo(src, dest)) path = dest.absolutePath
        }
        var enhanced = rec.enhancedPath
        if (enhanced.isNotBlank() && !AudioFileStore.isContentUri(enhanced)) {
            val eSrc = File(enhanced)
            val keepInCleanedDir = runCatching {
                eSrc.parentFile?.canonicalPath == cleanedAudioDir().canonicalPath
            }.getOrDefault(false)
            if (eSrc.exists() && !keepInCleanedDir && eSrc.parentFile?.absolutePath != targetDir.absolutePath) {
                val eDest = uniqueFile(targetDir, eSrc.nameWithoutExtension, eSrc.extension)
                if (moveTo(eSrc, eDest)) enhanced = eDest.absolutePath
            }
        }
        return rec.copy(filePath = path, enhancedPath = enhanced)
    }

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
        val durationMs = readDuration(path)
        return runCatching {
            dao.insert(
                Recording(
                    title = file.nameWithoutExtension.ifBlank { "Recording" },
                    filePath = path,
                    durationMs = durationMs,
                    createdAt = modified,
                    isTrashed = trashed,
                    trashedAt = if (trashed) modified else null,
                    quality = "HIGH",
                    fileSize = file.length().takeIf { it > 0 } ?: 0L,
                    duration = durationMs
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
        const val TEMP_CHUNKS_DIR_NAME = ".tmp_chunks"
        const val TEMP_PREFIX = "REC_"
        const val ENHANCED_SUFFIX = "_enhanced"
        const val CLEANED_AUDIO_DIR_NAME = "Cleaned audio"
        const val FAVORITES_DIR_NAME = "Favorites"
        const val TAGS_DIR_NAME = "Tags"

        private val AUDIO_EXTENSIONS = setOf(
            "m4a", "wav", "mp3", "ogg", "opus", "aac", "flac", "3gp", "amr"
        )
    }
}
