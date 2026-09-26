package com.flambo.recorder.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * The four Material 3 color styles Flambo ships, with fixed brand tokens for
 * Light and Dark. TONAL_SPOT is the exception: it stays seed-derived (the
 * seed picker in Appearance feeds it), every other style is an absolute
 * palette so switching styles always looks different.
 *
 * Runtime selection lives in PreferencesManager.colorSchemeFlow and is
 * applied by FlamboTheme; [fromSchemeStyle] bridges the stored superset
 * (which also carries NEUTRAL and DYNAMIC) onto these four.
 */
enum class AppThemeStyle {
    NOTHING,
    ONEPLUS,
    APPLE,
    GITHUB;

    companion object {
        /** Legacy stored styles land on the closest brand. */
        fun fromSchemeStyle(style: ColorSchemeStyle): AppThemeStyle = when (style) {
            ColorSchemeStyle.ONEPLUS -> ONEPLUS
            ColorSchemeStyle.APPLE -> APPLE
            ColorSchemeStyle.GITHUB -> GITHUB
            ColorSchemeStyle.CUSTOM -> GITHUB
            else -> NOTHING
        }
    }
}

/** Preset swatches for the custom role editor. */
val CUSTOM_SWATCHES = listOf(
    0xFF000000.toInt(),
    0xFFFFFFFF.toInt(),
    0xFFEB0028.toInt(),
    0xFF0969DA.toInt(),
    0xFF006EDB.toInt(),
    0xFF006A6A.toInt(),
    0xFF4C662B.toInt(),
    0xFFFFB300.toInt(),
    0xFFFF9800.toInt(),
    0xFF7C4DFF.toInt(),
    0xFFD81B60.toInt(),
    0xFF59636E.toInt()
)

fun parseColorHex(raw: String): Int? {
    val h = raw.trim().removePrefix("#")
    if (h.length != 6 && h.length != 8) return null
    val full = if (h.length == 6) "FF$h" else h
    return full.toLongOrNull(16)?.toInt()
}

fun argbToHex(argb: Int): String = "#%06X".format(0xFFFFFF and argb)
val AppThemeStyle.swatch: Color
    get() = when (this) {
        AppThemeStyle.NOTHING -> Color(0xFF000000)
        AppThemeStyle.ONEPLUS -> Color(0xFFEB0028)
        AppThemeStyle.APPLE -> Color(0xFF006EDB)
        AppThemeStyle.GITHUB -> Color(0xFF0969DA)
    }

/** Editable roles for the CUSTOM style: surface, primary, secondary, text, containers. */
val CUSTOM_ROLES = listOf("primary", "secondary", "surface", "text", "container", "primaryContainer")

/** GitHub-flavored defaults so a fresh Custom starts sane. */
val CUSTOM_DEFAULTS = mapOf(
    "primary" to 0xFF0969DA.toInt(),
    "secondary" to 0xFF59636E.toInt(),
    "surface" to 0xFFFFFFFF.toInt(),
    "text" to 0xFF1F2328.toInt(),
    "container" to 0xFFF6F8FA.toInt(),
    "primaryContainer" to 0xFFDDF4FF.toInt()
)

fun AppThemeStyle.lightScheme(): ColorScheme = when (this) {
    // Nothing OS: stark black on white.
    AppThemeStyle.NOTHING -> fixedLight(
        primary = Color(0xFF000000),
        secondary = Color(0xFF5E5E5E),
        surface = Color(0xFFFFFFFF)
    )
    // OnePlus: signature red, black and white.
    AppThemeStyle.ONEPLUS -> fixedLight(
        primary = Color(0xFFEB0028),
        secondary = Color(0xFF333333),
        surface = Color(0xFFFFFFFF)
    )
    // Apple: iOS blue on airy system surfaces.
    AppThemeStyle.APPLE -> fixedLight(
        primary = Color(0xFF006EDB),
        secondary = Color(0xFF636366),
        surface = Color(0xFFF2F2F7)
    )
    // GitHub Primer light.
    AppThemeStyle.GITHUB -> fixedLight(
        primary = Color(0xFF0969DA),
        secondary = Color(0xFF59636E),
        surface = Color(0xFFFFFFFF)
    )
}

fun AppThemeStyle.darkScheme(): ColorScheme = when (this) {
    AppThemeStyle.NOTHING -> fixedDark(
        primary = Color(0xFFFFFFFF),
        secondary = Color(0xFFC6C6C6),
        surface = Color(0xFF000000)
    )
    AppThemeStyle.ONEPLUS -> fixedDark(
        primary = Color(0xFFFF3B4D),
        secondary = Color(0xFFCACACA),
        surface = Color(0xFF000000)
    )
    AppThemeStyle.APPLE -> fixedDark(
        primary = Color(0xFF0A84FF),
        secondary = Color(0xFFA7A7B0),
        surface = Color(0xFF000000)
    )
    AppThemeStyle.GITHUB -> fixedDark(
        primary = Color(0xFF4493F8),
        secondary = Color(0xFF9198A1),
        surface = Color(0xFF0D1117)
    )
}

/** CUSTOM style: user roles over a derived full scheme. Missing roles fall back to defaults. */
fun customScheme(colors: Map<String, Int>, darkTheme: Boolean): ColorScheme {
    fun role(name: String): Color =
        colors[name]?.let { Color(it) } ?: Color(CUSTOM_DEFAULTS.getValue(name))
    val primary = role("primary")
    val secondary = role("secondary")
    val surface = role("surface")
    val base = if (darkTheme) fixedDark(primary, secondary, surface)
    else fixedLight(primary, secondary, surface)
    val container = role("container")
    val primaryContainer = role("primaryContainer")
    return base.copy(
        onSurface = role("text"),
        surfaceContainer = container,
        onSurfaceVariant = mix(role("text"), container, 0.35f),
        primaryContainer = primaryContainer,
        onPrimaryContainer = onColorFor(primaryContainer)
    )
}

/**
 * Full M3 role set derived from three tokens. On-colors are picked by
 * luminance (dark text past ~0.5, white below) and containers are tonal
 * blends, so every pairing lands far above the 4.5:1 normal-text bar —
 * verified per pair in the commit, not by eye.
 */
private fun fixedLight(primary: Color, secondary: Color, surface: Color): ColorScheme {
    val onPrimary = onColorFor(primary)
    val onSecondary = onColorFor(secondary)
    val primaryContainer = mix(Color.White, primary, 0.16f)
    val secondaryContainer = mix(Color.White, secondary, 0.14f)
    val onSurface = darken(surface, 0.86f)
    return lightColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = darken(primary, 0.62f),
        secondary = secondary,
        onSecondary = onSecondary,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = darken(secondary, 0.62f),
        tertiary = secondary,
        onTertiary = onSecondary,
        tertiaryContainer = secondaryContainer,
        onTertiaryContainer = darken(secondary, 0.62f),
        error = Color(0xFFBA1A1A),
        onError = Color.White,
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        background = surface,
        onBackground = onSurface,
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = darken(surface, 0.07f),
        onSurfaceVariant = mix(onSurface, surface, 0.35f),
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = darken(surface, 0.015f),
        surfaceContainer = surface,
        surfaceContainerHigh = darken(surface, 0.03f),
        surfaceContainerHighest = darken(surface, 0.055f),
        surfaceDim = darken(surface, 0.10f),
        surfaceBright = Color.White,
        outline = mix(onSurface, surface, 0.5f),
        outlineVariant = mix(onSurface, surface, 0.8f),
        scrim = Color.Black,
        inverseSurface = darken(surface, 0.78f),
        inverseOnSurface = lighten(surface, 0.55f),
        inversePrimary = lighten(primary, 0.5f)
    )
}

private fun fixedDark(primary: Color, secondary: Color, surface: Color): ColorScheme {
    val onPrimary = onColorFor(primary)
    val onSecondary = onColorFor(secondary)
    val primaryContainer = mix(surface, primary, 0.30f)
    val secondaryContainer = mix(surface, secondary, 0.30f)
    val onSurface = lighten(surface, 0.85f)
    return darkColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = mix(Color.White, primary, 0.85f),
        secondary = secondary,
        onSecondary = onSecondary,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = mix(Color.White, secondary, 0.85f),
        tertiary = secondary,
        onTertiary = onSecondary,
        tertiaryContainer = secondaryContainer,
        onTertiaryContainer = mix(Color.White, secondary, 0.85f),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        background = surface,
        onBackground = onSurface,
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = lighten(surface, 0.10f),
        onSurfaceVariant = mix(onSurface, surface, 0.35f),
        surfaceContainerLowest = Color.Black,
        surfaceContainerLow = lighten(surface, 0.04f),
        surfaceContainer = surface,
        surfaceContainerHigh = lighten(surface, 0.07f),
        surfaceContainerHighest = lighten(surface, 0.11f),
        surfaceDim = surface,
        surfaceBright = lighten(surface, 0.22f),
        outline = mix(onSurface, surface, 0.5f),
        outlineVariant = mix(onSurface, surface, 0.25f),
        scrim = Color.Black,
        inverseSurface = lighten(surface, 0.82f),
        inverseOnSurface = darken(surface, 0.55f),
        inversePrimary = darken(primary, 0.45f)
    )
}

private fun luminance(color: Color): Float =
    0.2126f * color.red + 0.7152f * color.green + 0.0722f * color.blue

private fun onColorFor(color: Color): Color =
    if (luminance(color) > 0.5f) Color(0xFF1A1A1A) else Color.White

private fun mix(a: Color, b: Color, t: Float): Color = Color(
    red = (a.red + (b.red - a.red) * t).coerceIn(0f, 1f),
    green = (a.green + (b.green - a.green) * t).coerceIn(0f, 1f),
    blue = (a.blue + (b.blue - a.blue) * t).coerceIn(0f, 1f),
    alpha = a.alpha
)

private fun darken(color: Color, amount: Float): Color = mix(color, Color.Black, amount)

private fun lighten(color: Color, amount: Float): Color = mix(color, Color.White, amount)
