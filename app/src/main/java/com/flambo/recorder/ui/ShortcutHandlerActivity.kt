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
 * finishes inside onCreate — NoDisplay, so no window ever exists:
 * no flash, no recents entry, nothing over the lock screen.
 */
class ShortcutHandlerActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as FlamboApp
        // NoDisplay activities must finish inside onCreate — no window is
        // ever created, so nothing flashes, nothing enters recents, and
        // nothing pops over the lock screen. The toggle continues on the
        // app scope, which outlives this activity.
        app.appScope.launch {
            try {
                app.recorder.toggleHeadless()
            } catch (_: Exception) {
            } finally {
                val s = app.recorder.state.value
                runCatching { RecordingShortcut.refresh(app, s.isRecording, s.isPaused) }
            }
        }
        finish()
    }
}
