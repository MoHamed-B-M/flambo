package com.pranshulgg.recordmaster

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.pranshulgg.recordmaster.core.data.AppDatabase
import com.pranshulgg.recordmaster.core.data.PreferencesManager
import com.pranshulgg.recordmaster.core.data.RecordingRepository
import com.pranshulgg.recordmaster.core.data.StorageVolumes
import com.pranshulgg.recordmaster.core.data.StorageWatcher
import com.pranshulgg.recordmaster.core.playback.PlaybackController
import com.pranshulgg.recordmaster.core.record.RecordingController
import com.pranshulgg.recordmaster.core.stt.TranscriptionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

/**
 * Composition root for the 2.0 logic layer.
 *
 * RecordMaster's existing UI still reads the file system directly; this class
 * only owns the shared engines (index, recorder, playback, STT) so the screens
 * can attach to them during Phase 4 without changing their design.
 */
class RecordMasterApp : Application() {

    val database by lazy { AppDatabase.get(this) }

    /** Mirrors prefs for callers that cannot suspend (services, notifications). */
    var storageVolumeId by mutableStateOf(StorageVolumes.ID_DEFAULT)
    @Volatile var recordingPrefix: String = PreferencesManager.DEFAULT_PREFIX

    val prefs by lazy { PreferencesManager(this) }

    val repository by lazy {
        RecordingRepository(
            dao = database.recordingDao(),
            dirProvider = { recordingsDir() },
            garbageDirProvider = { garbageDir() },
            appContext = this
        )
    }

    val recorder by lazy { RecordingController(this, repository) }
    val transcription by lazy { TranscriptionManager(this, prefs) }

    /**
     * One playback engine for the whole process. It outlives Activity
     * recreation so audio keeps playing across rotation/navigation.
     */
    val playback by lazy { PlaybackController(this) }

    internal val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var storageWatcher: StorageWatcher? = null

    /** Defaults to RecordMaster's existing `Music/` tree; never moves old files. */
    fun recordingsDir(): File =
        StorageVolumes.resolveDir(this, storageVolumeId).apply { mkdirs() }

    fun garbageDir(): File =
        File(recordingsDir(), RecordingRepository.GARBAGE_DIR_NAME).apply { mkdirs() }

    /** Re-scan storage (returning from a file manager, volume change, …). */
    fun syncStorage() {
        appScope.launch {
            runCatching { repository.syncExternalFiles() }
            runCatching { armStorageWatcher() }
        }
    }

    @Synchronized
    private fun armStorageWatcher() {
        storageWatcher?.stop()
        storageWatcher = StorageWatcher(recordingsDir(), appScope) {
            appScope.launch { runCatching { repository.syncExternalFiles() } }
        }.also { it.start() }
    }

    override fun onCreate() {
        super.onCreate()

        // The microphone must never pick up speaker output.
        recorder.onRecordingStarted = { runCatching { playback.stop() } }

        appScope.launch {
            storageVolumeId = runCatching { prefs.recordingsVolume() }
                .getOrDefault(StorageVolumes.ID_DEFAULT)
            recordingPrefix = runCatching { prefs.recordingPrefix() }
                .getOrDefault(PreferencesManager.DEFAULT_PREFIX)

            // Index the existing library (and adopt files added outside the app)
            // before the UI tries to observe it.
            runCatching { repository.syncExternalFiles() }
            runCatching { armStorageWatcher() }
        }

        // D3: purging garbage is opt-in and disabled by default.
        appScope.launch {
            val enabled = runCatching { prefs.trashAutoPurgeFlow.first() }.getOrDefault(false)
            if (enabled) {
                val days = runCatching { prefs.trashRetentionDays() }.getOrDefault(7)
                runCatching { repository.purgeOldTrash(days) }
            }
        }
    }
}
