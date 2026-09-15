package com.flambo.recorder.record

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.flambo.recorder.MainActivity
import com.flambo.recorder.R

/**
 * Launcher shortcuts + external intent entry points for controlling
 * the recorder from outside the app (long-press icon, Key Mapper, Tasker, etc.).
 *
 * Two intents:
 * - [ACTION_START_RECORDING] — start a new recording (no-op if already recording).
 * - [ACTION_PAUSE_RECORDING] — toggle pause/resume while recording.
 *
 * Handling lives in [MainActivity] (onCreate + onNewIntent) so permission /
 * projection consent can reuse the existing launchers.
 */
object RecordingShortcut {

    const val ACTION_START_RECORDING = "com.flambo.recorder.ACTION_START_RECORDING"
    const val ACTION_PAUSE_RECORDING = "com.flambo.recorder.ACTION_PAUSE_RECORDING"

    const val ID_START = "start_recording"
    const val ID_PAUSE = "pause_recording"

    fun startIntent(context: Context): Intent =
        Intent(context, MainActivity::class.java).apply {
            action = ACTION_START_RECORDING
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

    fun pauseIntent(context: Context): Intent =
        Intent(context, MainActivity::class.java).apply {
            action = ACTION_PAUSE_RECORDING
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

    /**
     * Update dynamic shortcuts to reflect current recorder state.
     * - While idle: both "Start recording" and "Pause / Resume" shown,
     *   pause is greyed out (disabled).
     * - While recording: "Start" disabled, "Pause / Resume" active
     *   with label reflecting pause/resume state.
     * - While paused: same as recording but label says "Resume".
     */
    fun refresh(context: Context, isRecording: Boolean, isPaused: Boolean) {
        try {
            val startLabel = if (isRecording) context.getString(R.string.shortcut_stop_short)
                else context.getString(R.string.shortcut_start_short)
            val pauseLabel = when {
                !isRecording -> context.getString(R.string.shortcut_pause_short)
                isPaused -> context.getString(R.string.shortcut_start_long) // "Resume"
                else -> context.getString(R.string.shortcut_pause_short)
            }

            val start = ShortcutInfoCompat.Builder(context, ID_START)
                .setShortLabel(startLabel)
                .setLongLabel(startLabel)
                .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_launcher))
                .setIntent(startIntent(context))
                .setRank(0)
                .build()

            val pause = ShortcutInfoCompat.Builder(context, ID_PAUSE)
                .setShortLabel(pauseLabel)
                .setLongLabel(pauseLabel)
                .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_launcher))
                .setIntent(pauseIntent(context))
                .setRank(1)
                .build()

            ShortcutManagerCompat.setDynamicShortcuts(context, listOf(start, pause))
        } catch (_: Exception) {
        }
    }

    fun reportUsed(context: Context, id: String) {
        try {
            ShortcutManagerCompat.reportShortcutUsed(context, id)
        } catch (_: Exception) {
        }
    }
}
