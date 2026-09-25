package com.flambo.recorder.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

class RecordingRepository(
    private val dao: RecordingDao,
    private val dirProvider: () -> File,
    private val appContext: Context? = null,
    private val customFolderUriProvider: (suspend () -> String)? = null
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

    suspend fun moveToDirectory(ids: Collection<Long>, targetDir: File): Int = withContext(Dispatchers.IO) {
        runCatching { targetDir.mkdirs() }
        var moved = 0
        ids.forEach { id ->
            val rec = getById(id) ?: return@forEach
            // SAF-hosted recordings (content://) can't move via File APIs; count them as skipped.
            if (AudioFileStore.isContentUri(rec.filePath)) return@forEach
            val newMain = moveFile(File(rec.filePath), targetDir) ?: return@forEach
            var newEnhanced = rec.enhancedPath
            if (newEnhanced.isNotBlank()) {
                moveFile(File(newEnhanced), targetDir)?.let { newEnhanced = it.absolutePath }
            }
            dao.update(rec.copy(filePath = newMain.absolutePath, enhancedPath = newEnhanced))
            moved++
        }
        moved
    }

    private fun moveFile(src: File, targetDir: File): File? {
        if (!src.exists()) return null
        var dest = File(targetDir, src.name)
        if (dest.absolutePath == src.absolutePath) return dest
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

    private suspend fun deleteSafCopy(fileName: String) {
        val ctx = appContext ?: return
        val uri = try { customFolderUriProvider?.invoke() } catch (_: Exception) { null } ?: return
        if (uri.isBlank()) return
        try {
            if (!SafFolderHelper.isTreeUriValid(ctx, uri)) return
            SafFolderHelper.deleteFile(ctx, uri, fileName)
        } catch (_: Exception) {}
    }

    private fun deleteAudio(path: String) {
        val ctx = appContext
        try {
            if (ctx != null) AudioFileStore.delete(ctx, path)
            else if (path.isNotBlank() && !AudioFileStore.isContentUri(path)) {
                File(path).takeIf { it.exists() }?.delete()
            }
        } catch (_: Exception) {}
    }

    suspend fun deletePermanently(id: Long) = withContext(Dispatchers.IO) {
        val rec = getById(id) ?: return@withContext
        // Delete primary files (File or SAF document)
        deleteAudio(rec.filePath)
        deleteAudio(rec.enhancedPath)
        // Delete SAF export-copies if custom folder set (primaries are already gone above).
        try {
            if (!AudioFileStore.isContentUri(rec.filePath)) {
                val name = File(rec.filePath).name
                if (name.isNotBlank()) deleteSafCopy(name)
            }
            if (!AudioFileStore.isContentUri(rec.enhancedPath)) {
                val enhName = File(rec.enhancedPath).name
                if (enhName.isNotBlank()) deleteSafCopy(enhName)
            }
        } catch (_: Exception) {}
        dao.deletePermanently(id)
    }

    suspend fun emptyTrash() = withContext(Dispatchers.IO) {
        val trash = dao.getTrash()
        trash.forEach { deletePermanently(it.id) }
    }

    suspend fun activeTitles(): List<String> = dao.activeTitles()

    suspend fun saveEnhanced(id: Long, path: String) {
        dao.updateEnhancedPath(id, path)
    }

    suspend fun clearEnhanced(id: Long) = withContext(Dispatchers.IO) {
        getById(id)?.let {
            deleteAudio(it.enhancedPath)
            try {
                if (!AudioFileStore.isContentUri(it.enhancedPath)) {
                    val enhName = File(it.enhancedPath).name
                    if (enhName.isNotBlank()) deleteSafCopy(enhName)
                }
            } catch (_: Exception) {}
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

    suspend fun purgeOldTrash(days: Int = 7) = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - days * 24L * 60 * 60 * 1000
        val old = dao.getTrash().filter { (it.trashedAt ?: 0L) < cutoff }
        old.forEach { rec ->
            deleteAudio(rec.filePath)
            deleteAudio(rec.enhancedPath)
            try {
                if (!AudioFileStore.isContentUri(rec.filePath)) {
                    val name = File(rec.filePath).name
                    if (name.isNotBlank()) deleteSafCopy(name)
                }
            } catch (_: Exception) {}
        }
        dao.purgeOldTrash(cutoff)
    }

    /** IDs whose primary audio is gone from both disk and SAF (e.g. deleted in a file manager). */
    suspend fun findMissingIds(recordings: List<Recording>): Set<Long> = withContext(Dispatchers.IO) {
        val ctx = appContext ?: return@withContext recordings
            .filter { it.filePath.isNotBlank() && !File(it.filePath).exists() }
            .map { it.id }.toSet()
        recordings.filter { !AudioFileStore.exists(ctx, it.filePath) }.map { it.id }.toSet()
    }

    fun recordingsDir(): File = dirProvider().apply { if (!exists()) runCatching { mkdirs() } }
}
