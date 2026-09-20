package com.flambo.recorder.record

import android.app.ForegroundServiceStartNotAllowedException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.flambo.recorder.FlamboApp
import kotlinx.coroutines.launch

class RecordingReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_TOGGLE = "com.flambo.recorder.ACTION_TOGGLE"
        const val ACTION_START = "com.flambo.recorder.ACTION_START"
        const val ACTION_STOP = "com.flambo.recorder.ACTION_STOP"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != ACTION_TOGGLE && action != ACTION_START && action != ACTION_STOP) return

        val pending = goAsync()
        val app = context.applicationContext as FlamboApp
        app.appScope.launch {
            try {
                when (action) {
                    ACTION_STOP -> app.recorder.stop()
                    ACTION_START -> app.recorder.startHeadless()
                    ACTION_TOGGLE -> app.recorder.toggleHeadless()
                }
                val s = app.recorder.state.value
                RecordingShortcut.refresh(app, s.isRecording, s.isPaused)
            } catch (_: ForegroundServiceStartNotAllowedException) {

            } catch (_: SecurityException) {

            } catch (_: Exception) {
            } finally {
                pending.finish()
            }
        }
    }
}
