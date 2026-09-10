package com.flambo.recorder.data

import kotlinx.coroutines.flow.Flow
import java.io.File

class RecordingRepository(
    private val dao: RecordingDao,
    private val filesDir: File
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

    fun recordingsDir(): File = filesDir.apply { if (!exists()) mkdirs() }
}
