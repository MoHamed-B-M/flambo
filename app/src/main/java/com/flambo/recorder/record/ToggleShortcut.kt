package com.flambo.recorder.record

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.flambo.recorder.MainActivity
import com.flambo.recorder.R

/**
 * Launcher shortcut + external intent entry for toggling voice recording.
 *
 * Fired from the static shortcut (`res/xml/shortcuts.xml`, long-press on the
 * launcher icon) or from automation apps like Key Mapper via an explicit
 * intent with [ACTION_TOGGLE_RECORDING]. Handling lives in MainActivity
 * (onCreate + onNewIntent) so permission / projection consent can reuse the
 * existing launchers; the actual start/stop goes through [RecordingController].
 */
object ToggleShortcut {

    const val ACTION_TOGGLE_RECORDING = "com.flambo.recorder.ACTION_TOGGLE_RECORDING"
    const val SHORTCUT_ID = "toggle_recording"

    fun toggleIntent(context: Context): Intent =
        Intent(context, MainActivity::class.java).apply {
            action = ACTION_TOGGLE_RECORDING
            // Reuse the existing task instead of stacking activities per tap.
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

    /**
     * Swap the dynamic shortcut label/icon to match recorder state:
     * "Stop recording" while recording, "Record / Stop" otherwise.
     * Same ID as the static shortcut so the launcher shows one entry.
     * Rate-limited by the system — failures are silently ignored.
     */
    fun refresh(context: Context, isRecording: Boolean) {
        try {
            val shortcut = ShortcutInfoCompat.Builder(context, SHORTCUT_ID)
                .setShortLabel(
                    context.getString(
                        if (isRecording) R.string.shortcut_stop_short
                        else R.string.shortcut_toggle_short
                    )
                )
                .setLongLabel(
                    context.getString(
                        if (isRecording) R.string.shortcut_stop_long
                        else R.string.shortcut_toggle_long
                    )
                )
                .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_launcher))
                .setIntent(toggleIntent(context))
                .setRank(0)
                .build()
            ShortcutManagerCompat.setDynamicShortcuts(context, listOf(shortcut))
        } catch (_: Exception) {
        }
    }

    fun reportUsed(context: Context) {
        try {
            ShortcutManagerCompat.reportShortcutUsed(context, SHORTCUT_ID)
        } catch (_: Exception) {
        }
    }
}
