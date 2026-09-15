package com.flambo.recorder.ui

import android.app.Activity
import android.os.Bundle
import com.flambo.recorder.FlamboApp
import com.flambo.recorder.record.RecordingShortcut
import kotlinx.coroutines.launch

/**
 * Transparent bridge for hosts (Key Mapper, Tasker) that execute
 * CREATE_SHORTCUT results exclusively via startActivity(): a receiver
 * target crashes them with "Error opening this app shortcut", so the
 * shortcut points here instead. Toggles recording through
 * [com.flambo.recorder.record.RecordingController.toggleHeadless] and
 * finishes immediately — translucent, no recents entry, nothing visible.
 */
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
                finish()
            }
        }
    }
}
