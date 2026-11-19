package com.pranshulgg.recordmaster.core.recovery

import android.media.MediaMetadataRetriever
import com.pranshulgg.recordmaster.core.audio.AudioChunkMerger
import com.pranshulgg.recordmaster.core.data.Recording
import com.pranshulgg.recordmaster.core.data.RecordingRepository
import com.pranshulgg.recordmaster.core.metadata.MetadataHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Outcome of a user-confirmed recovery, reported back to the UI. */
sealed interface RecoveryOutcome {
    data class Recovered(val title: String) : RecoveryOutcome
    data class Failed(val message: String) : RecoveryOutcome
}

/**
 * Finds and restores audio left in the internal chunk staging area when the
 * process died mid-take. Phase 3 finalizes a chunk at every auto-save interval;
 * those complete chunks are what can be merged here. The chunk being written at
 * the moment of the crash is usually truncated (MPEG-4 finalizes its index on
 * stop) and is skipped rather than corrupting the whole recovery.
 */
class CrashRecoveryManager(
    private val repository: RecordingRepository,
    private val scope: CoroutineScope
) {

    data class RecoverableSession(
        val dir: File,
        val chunks: List<AudioChunkMerger.Chunk>,
        val createdAt: Long,
        val durationMs: Long
    )

    private val _pending = MutableStateFlow<RecoverableSession?>(null)
    val pending: StateFlow<RecoverableSession?> = _pending.asStateFlow()

    /** Called once from [android.app.Application.onCreate]. */
    fun scanOnLaunch() {
        scope.launch(Dispatchers.IO) {
            _pending.value = findLatestOrphan()
        }
    }

    /** Hide the prompt without deleting anything — it can reappear next launch. */
    fun dismiss() {
        _pending.value = null
    }

    /** User declined: drop the staged chunks for good. */
    suspend fun discard() = withContext(Dispatchers.IO) {
        val session = _pending.value ?: return@withContext
        _pending.value = null
        clear(session)
    }

    /** Merge the pending session into the library, then clear its staging files. */
    suspend fun recover(): RecoveryOutcome {
        val session = _pending.value
            ?: return RecoveryOutcome.Failed("There is nothing to recover.")
        _pending.value = null
        return withContext(Dispatchers.IO) {
            val merged = File(
                repository.recordingsDir(),
                "${RecordingRepository.TEMP_PREFIX}${System.currentTimeMillis()}_recovery.m4a"
            )
            if (!AudioChunkMerger.merge(session.chunks, merged) ||
                !merged.exists() || merged.length() == 0L
            ) {
                runCatching { merged.delete() }
                // Keep the chunks so a later launch can try again.
                RecoveryOutcome.Failed("Couldn't recover this recording.")
            } else {
                val duration = readDurationMs(merged).takeIf { it > 0L } ?: session.durationMs
                val saved = persist(merged, duration, session.createdAt)
                if (saved != null) {
                    clear(session)
                    RecoveryOutcome.Recovered(saved.title)
                } else {
                    runCatching { merged.delete() }
                    RecoveryOutcome.Failed("Couldn't save the recovered recording.")
                }
            }
        }
    }

    private fun findLatestOrphan(): RecoverableSession? {
        val root = repository.tempChunksDir()
        val sessions = root.listFiles { file ->
            file.isDirectory && file.name.startsWith(TAKE_PREFIX)
        }?.sortedByDescending { it.name } ?: return null

        for (dir in sessions) {
            val chunks = dir.listFiles { file -> file.isFile && file.extension == "m4a" }
                ?.sortedBy { it.name }
                ?.mapNotNull { file -> readableChunk(file) }
                ?: emptyList()
            if (chunks.isEmpty()) continue
            return RecoverableSession(
                dir = dir,
                chunks = chunks,
                createdAt = dir.name.removePrefix(TAKE_PREFIX).toLongOrNull()
                    ?: dir.lastModified(),
                durationMs = chunks.sumOf { it.durationMs }
            )
        }
        return null
    }

    private fun readableChunk(file: File): AudioChunkMerger.Chunk? {
        if (!file.exists() || file.length() <= 0L) return null
        val duration = readDurationMs(file)
        // A truncated chunk has bytes but no readable duration — exclude it so
        // one bad file can't fail the entire merge.
        return if (duration > 0L) AudioChunkMerger.Chunk(file, duration) else null
    }

    private fun readDurationMs(file: File): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
        } catch (_: Exception) {
            0L
        } finally {
            runCatching { retriever.release() }
        }
    }

    private suspend fun persist(merged: File, durationMs: Long, createdAt: Long): Recording? {
        val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date(createdAt))
        val base = "Recovered recording $stamp"
        val target = uniqueFile(repository.recordingsDir(), base, "m4a")
        if (merged.absolutePath != target.absolutePath) {
            val moved = if (merged.renameTo(target)) {
                true
            } else {
                runCatching {
                    merged.copyTo(target, overwrite = true)
                    merged.delete()
                    true
                }.getOrDefault(false)
            }
            if (!moved) return null
        }

        val recording = Recording(
            title = target.nameWithoutExtension,
            filePath = target.absolutePath,
            durationMs = durationMs,
            createdAt = createdAt,
            fileSize = target.length().takeIf { it > 0L } ?: 0L,
            duration = durationMs
        )
        val saved = recording.copy(id = repository.insert(recording))
        runCatching { MetadataHelper.writeTags(saved) }
        return saved
    }

    private fun clear(session: RecoverableSession) {
        runCatching { session.dir.deleteRecursively() }
    }

    private fun uniqueFile(dir: File, base: String, ext: String): File {
        var candidate = File(dir, "$base.$ext")
        if (!candidate.exists()) return candidate
        var index = 2
        while (File(dir, "$base ($index).$ext").exists()) index++
        return File(dir, "$base ($index).$ext")
    }

    private companion object {
        // Matches the session folder name written by RecordingController (Phase 3).
        const val TAKE_PREFIX = "take_"
    }
}
