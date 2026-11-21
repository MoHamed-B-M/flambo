package com.pranshulgg.recordmaster.core.recovery

import android.media.MediaMetadataRetriever
import com.pranshulgg.recordmaster.core.audio.AudioChunkMerger
import com.pranshulgg.recordmaster.core.audio.WavAudio
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
 * process died mid-take. Every auto-save rotation finalizes a chunk, and those
 * complete chunks are what can be merged here. The chunk being written at the
 * moment of the crash is usually truncated (MPEG-4 finalizes its index on stop,
 * and a WAV header may still be unfinalized) and is skipped rather than
 * corrupting the whole recovery. The chunk extension identifies the pipeline
 * strategy, so no separate session metadata is required.
 */
class CrashRecoveryManager(
    private val repository: RecordingRepository,
    private val scope: CoroutineScope
) {

    data class RecoverableSession(
        val dir: File,
        val chunks: List<AudioChunkMerger.Chunk>,
        val createdAt: Long,
        val durationMs: Long,
        val extension: String
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
                "${RecordingRepository.TEMP_PREFIX}${System.currentTimeMillis()}_recovery.${session.extension}"
            )
            val ok = if (session.extension == WAV_EXTENSION) {
                WavAudio.merge(session.chunks.map { it.file }, merged)
            } else {
                AudioChunkMerger.merge(session.chunks, merged)
            }
            if (!ok || !merged.exists() || merged.length() == 0L) {
                runCatching { merged.delete() }
                // Keep the chunks so a later launch can try again.
                RecoveryOutcome.Failed("Couldn't recover this recording.")
            } else {
                val duration = chunkDurationMs(merged, session.extension)
                    .takeIf { it > 0L } ?: session.durationMs
                val saved = persist(merged, duration, session.createdAt, session.extension)
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
            val extension = sessionExtension(dir) ?: continue
            val chunks = dir.listFiles { file ->
                file.isFile && file.extension.equals(extension, ignoreCase = true)
            }
                ?.sortedBy { it.name }
                ?.mapNotNull { file -> readableChunk(file, extension) }
                ?: emptyList()
            if (chunks.isEmpty()) continue
            return RecoverableSession(
                dir = dir,
                chunks = chunks,
                createdAt = dir.name.removePrefix(TAKE_PREFIX).toLongOrNull()
                    ?: dir.lastModified(),
                durationMs = chunks.sumOf { it.durationMs },
                extension = extension
            )
        }
        return null
    }

    /** A session only ever uses one capture strategy, so its chunks share an extension. */
    private fun sessionExtension(dir: File): String? =
        dir.listFiles { file -> file.isFile && file.extension.lowercase(Locale.US) in RECOVERABLE_EXTENSIONS }
            ?.map { it.extension.lowercase(Locale.US) }
            ?.groupingBy { it }
            ?.eachCount()
            ?.maxByOrNull { it.value }
            ?.key

    private fun readableChunk(file: File, extension: String): AudioChunkMerger.Chunk? {
        if (!file.exists() || file.length() <= 0L) return null
        val duration = chunkDurationMs(file, extension)
        // A truncated chunk has bytes but no readable duration — exclude it so
        // one bad file can't fail the entire merge.
        return if (duration > 0L) AudioChunkMerger.Chunk(file, duration) else null
    }

    private fun chunkDurationMs(file: File, extension: String): Long {
        if (extension == WAV_EXTENSION) {
            val info = runCatching { WavAudio.inspect(file) }.getOrNull() ?: return 0L
            val bytesPerFrame = info.channels * 2L
            if (info.sampleRate <= 0 || bytesPerFrame <= 0L) return 0L
            return info.dataBytes * 1000L / (info.sampleRate * bytesPerFrame)
        }
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

    private suspend fun persist(
        merged: File,
        durationMs: Long,
        createdAt: Long,
        extension: String
    ): Recording? {
        val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date(createdAt))
        val base = "Recovered recording $stamp"
        val target = uniqueFile(repository.recordingsDir(), base, extension)
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
        // Matches the session folder name written by RecordingController.
        const val TAKE_PREFIX = "take_"
        const val WAV_EXTENSION = "wav"
        val RECOVERABLE_EXTENSIONS = setOf("m4a", WAV_EXTENSION)
    }
}
