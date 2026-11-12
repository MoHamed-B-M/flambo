package com.pranshulgg.recordmaster.core.record

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.pranshulgg.recordmaster.ui.ShortcutConfigActivity
import com.pranshulgg.recordmaster.ui.ShortcutHandlerActivity

/**
 * Gates the exported automation components. They are declared
 * `android:enabled="false"` in the manifest so that, by default, no other app
 * can start or stop a recording. Turning the setting on enables them; turning
 * it off removes the surface again.
 */
object AutomationTriggers {

    /** SharedPreferences key; kept in sync by the Settings switch. */
    const val PREF_KEY = "automation_triggers"

    private val components = listOf(
        ShortcutConfigActivity::class.java,
        ShortcutHandlerActivity::class.java,
        RecordingReceiver::class.java
    )

    fun setEnabled(context: Context, enabled: Boolean) {
        val state = if (enabled) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        components.forEach { component ->
            runCatching {
                context.packageManager.setComponentEnabledSetting(
                    ComponentName(context, component),
                    state,
                    PackageManager.DONT_KILL_APP
                )
            }
        }
    }
}
