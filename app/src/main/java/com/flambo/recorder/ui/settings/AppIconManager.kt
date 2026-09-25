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
    const val ICON_SOLID = "solid"

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
            // Raster preview on purpose: R.mipmap.ic_launcher resolves to the
            // adaptive-icon XML on API 26+, which painterResource can't render.
            com.flambo.recorder.R.drawable.app_icon_preview_default
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
        ),
        Option(
            ICON_SOLID,
            "Solid Black",
            "Classic mic • solid black on pink",
            com.flambo.recorder.R.drawable.app_icon_preview_solid
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
        if (pm.getComponentEnabledSetting(ComponentName(pkg, "$pkg.LauncherIconSolid")) ==
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        ) return ICON_SOLID
        return ICON_DEFAULT
    }

    /** Applies [id]; returns false when the id is unknown. Launcher refreshes within seconds. */
    fun apply(context: Context, id: String): Boolean {
        if (!isValid(id)) return false
        return try {
            val pm = context.packageManager
            val pkg = context.packageName
            setAlias(pm, pkg, "LauncherIconOutline", id == ICON_OUTLINE)
            setAlias(pm, pkg, "LauncherIconDuo", id == ICON_DUO)
            setAlias(pm, pkg, "LauncherIconSolid", id == ICON_SOLID)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun setAlias(pm: PackageManager, pkg: String, alias: String, enabled: Boolean) {
        val cn = ComponentName(pkg, "$pkg.$alias")
        // Skip no-op writes: every setComponentEnabledSetting call rebroadcasts
        // a package-changed event, which churns launchers for no reason.
        // Manifest default for all aliases is disabled, so DEFAULT counts as off.
        val want = if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        if (pm.getComponentEnabledSetting(cn) == want) return
        pm.setComponentEnabledSetting(cn, want, PackageManager.DONT_KILL_APP)
    }
}
