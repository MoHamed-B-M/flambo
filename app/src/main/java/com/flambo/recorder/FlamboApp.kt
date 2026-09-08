package com.flambo.recorder

import android.app.Application
import com.flambo.recorder.data.AppDatabase
import com.flambo.recorder.data.PreferencesManager
import com.flambo.recorder.data.RecordingRepository
import com.flambo.recorder.record.RecordingController

class FlamboApp : Application() {

    val database by lazy { AppDatabase.get(this) }
    val repository by lazy {
        RecordingRepository(
            dao = database.recordingDao(),
            filesDir = getExternalFilesDir(null) ?: filesDir
        )
    }
    val prefs by lazy { PreferencesManager(this) }
    val recorder by lazy { RecordingController(this, repository) }
}
