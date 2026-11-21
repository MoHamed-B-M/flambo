package com.pranshulgg.recordmaster

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.pranshulgg.recordmaster.core.data.AppDatabase
import com.pranshulgg.recordmaster.core.data.FontPreferences
import com.pranshulgg.recordmaster.core.data.PreferencesManager
import com.pranshulgg.recordmaster.core.data.RecordingRepository
import com.pranshulgg.recordmaster.core.data.StorageVolumes
import com.pranshulgg.recordmaster.core.data.StorageWatcher
import com.pranshulgg.recordmaster.core.playback.PlaybackController
import com.pranshulgg.recordmaster.core.playback.ReviewController
import com.pranshulgg.recordmaster.core.record.AutomationTriggers
import com.pranshulgg.recordmaster.core.record.RecordingController
import com.pranshulgg.recordmaster.core.recovery.CrashRecoveryManager
import com.pranshulgg.recordmaster.core.stt.SpeechModelStore
import com.pranshulgg.recordmaster.core.stt.TranscriptionManager
import com.pranshulgg.recordmaster.helpers.PreferencesHelper
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
    var recordingsFolderUri by mutableStateOf("")
    @Volatile var recordingPrefix: String = PreferencesManager.DEFAULT_PREFIX

    val prefs by lazy { PreferencesManager(this) }
    val fontPrefs by lazy { FontPreferences(this) }

    val repository by lazy {
        RecordingRepository(
            dao = database.recordingDao(),
            dirProvider = { recordingsDir() },
            garbageDirProvider = { garbageDir() },
            appContext = this
        )
    }

    val recorder by lazy { RecordingController(this, repository) }
    val crashRecovery by lazy { CrashRecoveryManager(repository, appScope) }
    val speechModels by lazy { SpeechModelStore(prefs) }
    val transcription by lazy { TranscriptionManager(this, prefs, speechModels) }

    /**
     * One playback engine for the whole process. It outlives Activity
     * recreation so audio keeps playing across rotation/navigation.
     */
    val playback by lazy { PlaybackController(this) }

    /** Player for the paused take's "Scrub & Review" bar; never a media session. */
    val review by lazy { ReviewController(this) }

    internal val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Whether Key Mapper / Tasker / launcher-shortcut triggers are allowed.
     * Backed by [PreferencesHelper] because the receiver and trampoline need a
     * synchronous answer without waiting on DataStore.
     */
    val automationTriggersEnabled: Boolean
        get() = PreferencesHelper.getBool(AutomationTriggers.PREF_KEY) ?: false

    private var storageWatcher: StorageWatcher? = null

    /**
     * Active recordings directory. Prefers a user-picked exact folder (SAF tree
     * resolved to a filesystem path) and otherwise falls back to the selected
     * volume/default, which creates a "Recorder" folder in phone storage.
     */
    fun recordingsDir(): File {
        val custom = StorageVolumes.resolveTreeUriToDir(this, recordingsFolderUri)
        if (custom != null) return custom.apply { mkdirs() }
        return StorageVolumes.resolveDir(this, storageVolumeId).apply { mkdirs() }
    }

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

        PreferencesHelper.init(this)
        // Component state survives process death, but re-apply so a cleared
        // data dir or a manual disable converges back to the stored choice.
        runCatching {
            AutomationTriggers.setEnabled(
                this,
                PreferencesHelper.getBool(AutomationTriggers.PREF_KEY) ?: false
            )
        }

        // The microphone must never pick up speaker output.
        recorder.onRecordingStarted = { runCatching { playback.stop() } }

        // Remove `.part` files left behind if a previous import was killed.
        runCatching { transcription.modelManager.cleanupStaleParts() }

        // Offer to restore a take that was still staged when the process died.
        crashRecovery.scanOnLaunch()

        appScope.launch {
            storageVolumeId = runCatching { prefs.recordingsVolume() }
                .getOrDefault(StorageVolumes.ID_DEFAULT)
            recordingsFolderUri = runCatching { prefs.recordingsFolderUri() }
                .getOrDefault("")
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
