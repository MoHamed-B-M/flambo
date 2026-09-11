package com.flambo.recorder

import android.app.Application
import com.flambo.recorder.data.AppDatabase
import com.flambo.recorder.data.PreferencesManager
import com.flambo.recorder.data.RecordingRepository
import com.flambo.recorder.data.StorageVolumes
import com.flambo.recorder.record.RecordingController
import com.flambo.recorder.stt.TranscriptionManager
import com.flambo.recorder.update.ApkInstaller
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class FlamboApp : Application() {

    val database by lazy { AppDatabase.get(this) }

    // Storage volume chosen in Settings. Volatile + preloaded at startup so
    // the sync recordingsDir() path never blocks on DataStore.
    @Volatile var storageVolumeId: String = StorageVolumes.ID_DEFAULT

    fun recordingsDir(): File = StorageVolumes.resolveDir(this, storageVolumeId)

    val repository by lazy {
        RecordingRepository(
            dao = database.recordingDao(),
            dirProvider = { recordingsDir() }
        )
    }
    val prefs by lazy { PreferencesManager(this) }
    val recorder by lazy { RecordingController(this, repository) }
    val transcription by lazy { TranscriptionManager(this, prefs) }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            storageVolumeId = runCatching { prefs.recordingsVolume() }.getOrDefault(StorageVolumes.ID_DEFAULT)
        }
        // The installer holds the APK open while it works, so last run's file
        // can only be deleted now that we're back.
        appScope.launch {
            val pending = runCatching { prefs.pendingApkDelete() }.getOrNull()
            runCatching { ApkInstaller.cleanupStale(this@FlamboApp, pending) }
            if (pending != null) runCatching { prefs.clearPendingApkDelete() }
        }
    }
}
