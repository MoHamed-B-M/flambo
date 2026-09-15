package com.flambo.recorder.record

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.flambo.recorder.FlamboApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Headless entry point for automation apps (Key Mapper, Tasker, MacroDroid).
 *
 * Unlike the launcher shortcuts (which route through MainActivity so permission
 * and projection consent can reuse the UI), these broadcasts never touch an
 * activity — no window opens, the screen stays as it is. Fire them with
 * "Send broadcast" / "Send intent" (broadcast) targeting this receiver:
 *
 * - action `com.flambo.recorder.ACTION_TOGGLE`, `ACTION_START` or `ACTION_STOP`
 * - package `com.flambo.recorder`, class `com.flambo.recorder.record.RecordingReceiver`
 *
 * Starting requires RECORD_AUDIO already granted (a receiver cannot prompt);
 * without it the broadcast is ignored. System-sound source falls back to mic
 * for the same reason (its consent needs UI).
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
        // Prefs reads suspend — hold the broadcast open while we work.
        val pending = goAsync()
        val app = context.applicationContext as FlamboApp
        app.appScope.launch {
            try {
                when (action) {
                    ACTION_STOP -> app.recorder.stop()
                    ACTION_START -> if (!app.recorder.state.value.isRecording) startNow(app)
                    ACTION_TOGGLE -> {
                        if (app.recorder.state.value.isRecording) app.recorder.stop()
                        else startNow(app)
                    }
                }
                val s = app.recorder.state.value
                RecordingShortcut.refresh(app, s.isRecording, s.isPaused)
            } catch (_: Exception) {
                // Headless by design: background FGS start can be refused by
                // the system (API 31+) — nothing useful to show from here.
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun startNow(app: FlamboApp) {
        if (ContextCompat.checkSelfPermission(
                app, Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) return
        val q = app.prefs.qualityFlow.first()
        val nr = app.prefs.noiseReductionFlow.first()
        var source = AudioSource.fromPref(app.prefs.audioSourceFlow.first())
        // System capture needs its consent UI — fall back to mic headlessly.
        if (source == AudioSource.SYSTEM) source = AudioSource.MIC
        runCatching { app.recorder.start(q, source, nr) }
    }
}
