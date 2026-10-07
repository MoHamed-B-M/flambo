package com.flambo.recorder.record

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.flambo.recorder.MainActivity
import com.flambo.recorder.R

object RecordingShortcut {

    const val ACTION_START_RECORDING = "com.flambo.recorder.ACTION_START_RECORDING"
    const val ACTION_PAUSE_RECORDING = "com.flambo.recorder.ACTION_PAUSE_RECORDING"

    // Discrete Key Mapper / automation actions (static shortcuts): unlike the
    // two toggles above, these never relabel and each does exactly one thing.
    const val ACTION_RECORD = "com.flambo.recorder.ACTION_RECORD"
    const val ACTION_PAUSE = "com.flambo.recorder.ACTION_PAUSE"
    const val ACTION_STOP = "com.flambo.recorder.ACTION_STOP"
    const val ACTION_TOGGLE_RECORD_PAUSE = "com.flambo.recorder.ACTION_TOGGLE_RECORD_PAUSE"
    const val ACTION_TOGGLE_RECORD_STOP = "com.flambo.recorder.ACTION_TOGGLE_RECORD_STOP"

    const val ID_START = "start_recording"
    const val ID_PAUSE = "pause_recording"

    /** Extra carrying the chosen discrete action into ShortcutHandlerActivity. */
    const val EXTRA_ACTION = "com.flambo.recorder.EXTRA_SHORTCUT_ACTION"

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

    fun refresh(context: Context, isRecording: Boolean, isPaused: Boolean) {
        try {
            val startLabel = if (isRecording) context.getString(R.string.shortcut_stop_short)
                else context.getString(R.string.shortcut_start_short)
            val pauseLabel = when {
                !isRecording -> context.getString(R.string.shortcut_pause_short)
                isPaused -> context.getString(R.string.shortcut_start_long)
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
