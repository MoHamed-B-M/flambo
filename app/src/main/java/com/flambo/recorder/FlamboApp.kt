package com.flambo.recorder

import android.app.Application
import com.flambo.recorder.data.AppDatabase
import com.flambo.recorder.data.PreferencesManager
import com.flambo.recorder.data.RecordingRepository
import com.flambo.recorder.data.StorageVolumes
import com.flambo.recorder.data.StorageWatcher
import com.flambo.recorder.record.RecordingController
import com.flambo.recorder.stt.TranscriptionManager
import com.flambo.recorder.ui.settings.AppIconManager
import com.flambo.recorder.update.ApkInstaller
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

class FlamboApp : Application() {

    val database by lazy { AppDatabase.get(this) }

    @Volatile var storageVolumeId: String = StorageVolumes.ID_DEFAULT
    @Volatile var recordingPrefix: String = "Recording"
    @Volatile var customFolderUri: String = ""
    /** Mirrors the card-expand toggle for nav transitions, which can't collect flows. */
    @Volatile var cardExpandAnim: Boolean = true
    /** Mirrors the haptics slider so taps never wait on DataStore. */
    @Volatile var hapticLevel: Int = 50

    fun recordingsDir(): File = StorageVolumes.resolveDir(this, storageVolumeId)

    val repository by lazy {
        RecordingRepository(
            dao = database.recordingDao(),
            dirProvider = { recordingsDir() },
            appContext = this,
            customFolderUriProvider = { prefs.customFolderUri() }
        )
    }
    val prefs by lazy { PreferencesManager(this) }
    val recorder by lazy { RecordingController(this, repository) }
    val transcription by lazy { TranscriptionManager(this, prefs) }

    internal val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val storageWatcher by lazy {
        StorageWatcher(recordingsDir(), appScope) {
            appScope.launch { runCatching { repository.syncExternalFiles(this@FlamboApp) } }
        }
    }

    /** Re-scan storage (e.g. returning from a file manager) and re-arm the watcher. */
    fun syncStorage() {
        appScope.launch {
            runCatching { repository.syncExternalFiles(this@FlamboApp) }
            runCatching { storageWatcher.start() }
        }
    }

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            storageVolumeId = runCatching { prefs.recordingsVolume() }.getOrDefault(StorageVolumes.ID_DEFAULT)
            recordingPrefix = runCatching { prefs.recordingPrefix() }.getOrDefault("Recording")
            customFolderUri = runCatching { prefs.customFolderUri() }.getOrDefault("")
            cardExpandAnim = runCatching { prefs.cardExpandAnim() }.getOrDefault(true)
            hapticLevel = runCatching { prefs.hapticLevel() }.getOrDefault(50)
        }

        appScope.launch {
            val pending = runCatching { prefs.pendingApkDelete() }.getOrNull()
            runCatching { ApkInstaller.cleanupStale(this@FlamboApp, pending) }
            if (pending != null) runCatching { prefs.clearPendingApkDelete() }
        }
        // Auto-purge trashed recordings older than 7 days (deletes both primary and SAF copies)
        appScope.launch {
            runCatching { repository.purgeOldTrash(7) }
        }
        // Adopt audio files placed from outside (restored/copied via a file
        // manager) and watch the library dir for live add/delete events.
        appScope.launch {
            runCatching { repository.syncExternalFiles(this@FlamboApp) }
            runCatching { storageWatcher.start() }
        }
        // Re-apply the chosen launcher icon (component state persists, this guards fresh installs).
        appScope.launch {
            val icon = runCatching { prefs.appIcon() }.getOrDefault(AppIconManager.ICON_DEFAULT)
            runCatching { AppIconManager.apply(this@FlamboApp, icon) }
        }
    }
}
