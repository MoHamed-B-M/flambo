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
 * finishes inside onCreate — NoDisplay + empty taskAffinity, so no window
 * ever exists and the main task is never foregrounded: no flash, no
 * recents entry, nothing over lock screen and nothing queued for unlock.
 */
class ShortcutHandlerActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as FlamboApp
        // NoDisplay activities must finish inside onCreate — no window is
        // ever created. finishAndRemoveTask() + empty affinity ensures this
        // trampoline never brings Flambo's main task forward, even if it
        // sits in Recents or on the lock screen. The toggle continues on
        // the app scope, which outlives this activity.
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
