package com.flambo.recorder.ui

import android.app.Activity
import android.os.Bundle
import com.flambo.recorder.FlamboApp
import com.flambo.recorder.record.RecordingShortcut
import kotlinx.coroutines.launch

/**
 * Invisible trampoline that performs the discrete action chosen in
 * ShortcutConfigActivity (or the legacy toggle for old pinned shortcuts).
 */
class ShortcutHandlerActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as FlamboApp
        // Old pinned shortcuts carry no extra: keep them on the toggle.
        val action = intent?.getStringExtra(RecordingShortcut.EXTRA_ACTION)
            ?: RecordingShortcut.ACTION_TOGGLE_RECORD_STOP

        app.appScope.launch {
            try {
                val recorder = app.recorder
                when (action) {
                    RecordingShortcut.ACTION_RECORD -> recorder.startHeadless()
                    RecordingShortcut.ACTION_PAUSE -> recorder.pause()
                    RecordingShortcut.ACTION_STOP -> recorder.stop()
                    RecordingShortcut.ACTION_TOGGLE_RECORD_PAUSE -> {
                        val s = recorder.state.value
                        when {
                            !s.isRecording -> recorder.startHeadless()
                            s.isPaused -> recorder.resume()
                            else -> recorder.pause()
                        }
                    }
                    RecordingShortcut.ACTION_TOGGLE_RECORD_STOP -> recorder.toggleHeadless()
                }
            } catch (_: Exception) {
            } finally {
                val s = app.recorder.state.value
                runCatching { RecordingShortcut.refresh(app, s.isRecording, s.isPaused) }
            }
        }
        finishAndRemoveTask()
    }
}
