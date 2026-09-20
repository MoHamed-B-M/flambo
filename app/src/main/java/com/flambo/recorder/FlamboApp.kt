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
import java.io.File

class FlamboApp : Application() {

    val database by lazy { AppDatabase.get(this) }

    @Volatile var storageVolumeId: String = StorageVolumes.ID_DEFAULT
    @Volatile var recordingPrefix: String = "Recording"
    @Volatile var customFolderUri: String = ""

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

    internal val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            storageVolumeId = runCatching { prefs.recordingsVolume() }.getOrDefault(StorageVolumes.ID_DEFAULT)
            recordingPrefix = runCatching { prefs.recordingPrefix() }.getOrDefault("Recording")
            customFolderUri = runCatching { prefs.customFolderUri() }.getOrDefault("")
        }

        appScope.launch {
            val pending = runCatching { prefs.pendingApkDelete() }.getOrNull()
            runCatching { ApkInstaller.cleanupStale(this@FlamboApp, pending) }
            if (pending != null) runCatching { prefs.clearPendingApkDelete() }
        }
    }
}
