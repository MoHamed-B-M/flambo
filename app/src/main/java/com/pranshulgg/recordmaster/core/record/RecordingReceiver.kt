package com.pranshulgg.recordmaster.core.record

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.pranshulgg.recordmaster.RecordMasterApp
import kotlinx.coroutines.launch

/**
 * Headless automation entry: Key Mapper / Tasker "send broadcast" with one of
 * the actions below. Exported on purpose; disabled unless the user opts in via
 * Settings → Automation, so no other app can drive the recorder by default.
 */
class RecordingReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_TOGGLE = "com.flambo.recorder.ACTION_TOGGLE"
        const val ACTION_START = "com.flambo.recorder.ACTION_START"
        const val ACTION_STOP = "com.flambo.recorder.ACTION_STOP"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != ACTION_TOGGLE && action != ACTION_START && action != ACTION_STOP) return

        val app = context.applicationContext as? RecordMasterApp ?: return
        if (!app.automationTriggersEnabled) return

        val pending = goAsync()
        app.appScope.launch {
            try {
                when (action) {
                    ACTION_STOP -> app.recorder.stop()
                    ACTION_START -> app.recorder.startHeadless()
                    ACTION_TOGGLE -> app.recorder.toggleHeadless()
                }
                val s = app.recorder.state.value
                RecordingShortcut.refresh(app, s.isRecording, s.isPaused)
            } catch (_: SecurityException) {
            } catch (_: Exception) {
            } finally {
                pending.finish()
            }
        }
    }
}
