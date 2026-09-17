package com.flambo.recorder.data

import kotlinx.coroutines.flow.Flow
import java.io.File

class RecordingRepository(
    private val dao: RecordingDao,
    private val dirProvider: () -> File
) {
    fun observeRecordings(): Flow<List<Recording>> = dao.observeAll()
    fun observeTrash(): Flow<List<Recording>> = dao.observeTrash()
    fun observeById(id: Long): Flow<Recording?> = dao.observeById(id)
    suspend fun getById(id: Long): Recording? = dao.getById(id)

    fun search(query: String): Flow<List<Recording>> =
        if (query.isBlank()) observeRecordings() else dao.search(query)

    suspend fun insert(recording: Recording): Long = dao.insert(recording)

    suspend fun update(recording: Recording) = dao.update(recording)

    suspend fun rename(id: Long, newTitle: String) {
        getById(id)?.let { dao.update(it.copy(title = newTitle.trim().ifBlank { it.title })) }
    }

    suspend fun toggleFavorite(id: Long) {
        getById(id)?.let { dao.update(it.copy(isFavorite = !it.isFavorite)) }
    }

    suspend fun softDelete(id: Long) {
        getById(id)?.let { dao.update(it.copy(isTrashed = true, trashedAt = System.currentTimeMillis())) }
    }

    suspend fun softDeleteAll(ids: Collection<Long>) {
        ids.forEach { softDelete(it) }
    }

    // Shared tag ("group") for several recordings at once.
    suspend fun addTagToAll(ids: Collection<Long>, tag: String): Int {
        val clean = tag.trim().trim(',')
        if (clean.isEmpty()) return 0
        var tagged = 0
        ids.forEach { id ->
            getById(id)?.let { rec ->
                if (clean !in rec.tagList) {
                    dao.update(rec.copy(tags = (rec.tagList + clean).joinToString(",")))
                }
                tagged++
            }
        }
        return tagged
    }

    // Moves audio files (+ cleaned copies) into targetDir and rewrites rows.
    // Works across volumes (copy + delete fallback). Returns moved count.
    suspend fun moveToDirectory(ids: Collection<Long>, targetDir: File): Int {
        runCatching { targetDir.mkdirs() }
        var moved = 0
        ids.forEach { id ->
            val rec = getById(id) ?: return@forEach
            val newMain = moveFile(File(rec.filePath), targetDir) ?: return@forEach
            var newEnhanced = rec.enhancedPath
            if (newEnhanced.isNotBlank()) {
                moveFile(File(newEnhanced), targetDir)?.let { newEnhanced = it.absolutePath }
            }
            dao.update(rec.copy(filePath = newMain.absolutePath, enhancedPath = newEnhanced))
            moved++
        }
        return moved
    }

    private fun moveFile(src: File, targetDir: File): File? {
        if (!src.exists()) return null
        var dest = File(targetDir, src.name)
        if (dest.absolutePath == src.absolutePath) return dest // already there
        if (dest.exists()) {
            val base = src.nameWithoutExtension
            val ext = src.extension.let { if (it.isBlank()) "" else ".$it" }
            var i = 2
            while (File(targetDir, "$base ($i)$ext").exists()) i++
            dest = File(targetDir, "$base ($i)$ext")
        }
        return try {
            if (src.renameTo(dest)) dest
            else {
                src.copyTo(dest, overwrite = true)
                src.delete()
                dest
            }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun restore(id: Long) {
        getById(id)?.let { dao.update(it.copy(isTrashed = false, trashedAt = null)) }
    }

    suspend fun deletePermanently(id: Long) {
        val rec = getById(id)
        rec?.let {
            // delete files best-effort (original + cleaned copy, if any)
            try { File(it.filePath).takeIf { f -> f.exists() }?.delete() } catch (_: Exception) {}
            try { File(it.enhancedPath).takeIf { f -> f.exists() }?.delete() } catch (_: Exception) {}
            dao.deletePermanently(id)
        }
    }

    // Permanent delete of everything in trash (files + rows).
    suspend fun emptyTrash() {
        dao.getTrash().forEach { deletePermanently(it.id) }
    }

    suspend fun activeTitles(): List<String> = dao.activeTitles()

    suspend fun saveEnhanced(id: Long, path: String) {
        dao.updateEnhancedPath(id, path)
    }

    suspend fun clearEnhanced(id: Long) {
        getById(id)?.let {
            try { File(it.enhancedPath).takeIf { f -> f.exists() }?.delete() } catch (_: Exception) {}
        }
        dao.updateEnhancedPath(id, "")
    }

    suspend fun saveTranscript(id: Long, transcript: String) {
        dao.updateTranscript(id, transcript.trim())
    }

    suspend fun clearTranscript(id: Long) {
        dao.updateTranscript(id, "")
    }

    suspend fun updateTags(id: Long, tags: List<String>) {
        getById(id)?.let {
            dao.update(it.copy(tags = tags.joinToString(",") { t -> t.trim() }.trim(',')))
        }
    }

    suspend fun purgeOldTrash(days: Int = 7) {
        val cutoff = System.currentTimeMillis() - days * 24L * 60 * 60 * 1000
        val purged = dao.purgeOldTrash(cutoff)
        // files already soft-deleted; actual file deletion occurs on permanent delete
        if (purged > 0) {
            // no-op
        }
    }

    fun recordingsDir(): File = dirProvider().apply { if (!exists()) mkdirs() }
}
