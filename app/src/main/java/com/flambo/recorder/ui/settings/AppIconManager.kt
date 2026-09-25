package com.flambo.recorder.ui.settings

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * Switchable launcher icons via manifest `<activity-alias>` entries.
 *
 * Android has no API to recolor a launcher icon at runtime, so each style
 * is a pre-rendered adaptive-icon set toggled with [PackageManager].
 * `MainActivity` itself always stays enabled; exactly one alias is on.
 */
object AppIconManager {

    const val ICON_DEFAULT = "default"
    const val ICON_OUTLINE = "outline"
    const val ICON_DUO = "duo"

    data class Option(
        val id: String,
        val label: String,
        val subtitle: String,
        /** Preview drawable shown in Settings. */
        val previewRes: Int
    )

    fun options() = listOf(
        Option(
            ICON_DEFAULT,
            "Default",
            "Ember mic • warm pink",
            com.flambo.recorder.R.mipmap.ic_launcher
        ),
        Option(
            ICON_OUTLINE,
            "Outline Navy",
            "Line mic • deep navy on pink",
            com.flambo.recorder.R.drawable.app_icon_preview_outline
        ),
        Option(
            ICON_DUO,
            "Duo Teal",
            "Filled mic • black and teal on pink",
            com.flambo.recorder.R.drawable.app_icon_preview_duo
        )
    )

    fun isValid(id: String): Boolean = options().any { it.id == id }

    fun current(context: Context): String {
        val pm = context.packageManager
        val pkg = context.packageName
        if (pm.getComponentEnabledSetting(ComponentName(pkg, "$pkg.LauncherIconOutline")) ==
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        ) return ICON_OUTLINE
        if (pm.getComponentEnabledSetting(ComponentName(pkg, "$pkg.LauncherIconDuo")) ==
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        ) return ICON_DUO
        return ICON_DEFAULT
    }

    /** Applies [id]; returns false when the id is unknown. Launcher refreshes within seconds. */
    fun apply(context: Context, id: String): Boolean {
        if (!isValid(id)) return false
        val pm = context.packageManager
        val pkg = context.packageName
        setAlias(pm, pkg, "LauncherIconOutline", id == ICON_OUTLINE)
        setAlias(pm, pkg, "LauncherIconDuo", id == ICON_DUO)
        return true
    }

    private fun setAlias(pm: PackageManager, pkg: String, alias: String, enabled: Boolean) {
        runCatching {
            pm.setComponentEnabledSetting(
                ComponentName(pkg, "$pkg.$alias"),
                if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
        }
    }
}
