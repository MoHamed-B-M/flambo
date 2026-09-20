package com.flambo.recorder.ui

import android.app.Activity
import android.os.Bundle
import com.flambo.recorder.FlamboApp
import com.flambo.recorder.record.RecordingShortcut
import kotlinx.coroutines.launch

class ShortcutHandlerActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as FlamboApp

        app.appScope.launch {
            try {
                app.recorder.toggleHeadless()
            } catch (_: Exception) {
            } finally {
                val s = app.recorder.state.value
                runCatching { RecordingShortcut.refresh(app, s.isRecording, s.isPaused) }
            }
        }
        finishAndRemoveTask()
    }
}
