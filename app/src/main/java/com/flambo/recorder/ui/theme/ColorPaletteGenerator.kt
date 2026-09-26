package com.flambo.recorder.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

enum class ColorSchemeStyle {
    TONAL_SPOT,
    EXPRESSIVE,
    VIBRANT,
    NEUTRAL,
    MONOCHROME,
    DYNAMIC
}

object ColorPaletteGenerator {

    fun scheme(
        seed: ThemeSeed,
        darkTheme: Boolean,
        style: ColorSchemeStyle,
        context: Context? = null,
        dynamicColorEnabled: Boolean = false
    ): ColorScheme {
        // Wallpaper dynamic colors win over every style when enabled — the
        // settings toggle promises "match your wallpaper", not "only if you
        // also picked the Dynamic style".
        if (dynamicColorEnabled && context != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        // Expressive, Vibrant and Monochrome are absolute brand palettes
        // (SchemeTokens); only Tonal Spot follows the selected seed.
        val fixed = when (style) {
            ColorSchemeStyle.EXPRESSIVE -> AppThemeStyle.EXPRESSIVE
            ColorSchemeStyle.VIBRANT -> AppThemeStyle.VIBRANT
            ColorSchemeStyle.MONOCHROME -> AppThemeStyle.MONOCHROME
            else -> null
        }
        if (fixed != null) {
            return if (darkTheme) fixed.darkScheme() else fixed.lightScheme()
        }
        return when (style) {
            ColorSchemeStyle.DYNAMIC -> {
                // Dynamic requested but unavailable (off, no context, pre-S):
                // fall back to the seed's tonal spot.
                if (darkTheme) seed.dark else seed.light
            }
            ColorSchemeStyle.TONAL_SPOT -> if (darkTheme) seed.dark else seed.light
            ColorSchemeStyle.NEUTRAL -> if (darkTheme) neutralDark(seed) else neutralLight(seed)
            else -> if (darkTheme) seed.dark else seed.light
        }
    }

    fun fromString(value: String?): ColorSchemeStyle {
        return try {
            ColorSchemeStyle.valueOf(value?.uppercase() ?: "TONAL_SPOT")
        } catch (_: Exception) {
            ColorSchemeStyle.TONAL_SPOT
        }
    }

    // TONAL_SPOT is seed as-is

    private fun neutralLight(seed: ThemeSeed): ColorScheme {
        // Desaturated neutral variant — muted surfaces, softer contrasts
        val base = seed.light
        return base.copy(
            primary = desaturate(base.primary, 0.35f),
            secondary = desaturate(base.secondary, 0.4f),
            tertiary = desaturate(base.tertiary, 0.3f),
            primaryContainer = desaturate(base.primaryContainer, 0.3f),
            secondaryContainer = desaturate(base.secondaryContainer, 0.3f),
            tertiaryContainer = desaturate(base.tertiaryContainer, 0.3f),
            surfaceContainer = Color(0xFFF3EEE9),
            surfaceContainerHigh = Color(0xFFEDE8E4),
            surfaceContainerHighest = Color(0xFFE7E2DE)
        )
    }

    private fun neutralDark(seed: ThemeSeed): ColorScheme {
        val base = seed.dark
        return base.copy(
            primary = desaturate(base.primary, 0.3f),
            secondary = desaturate(base.secondary, 0.35f),
            tertiary = desaturate(base.tertiary, 0.25f),
            primaryContainer = desaturate(base.primaryContainer, 0.25f),
            secondaryContainer = desaturate(base.secondaryContainer, 0.25f),
            tertiaryContainer = desaturate(base.tertiaryContainer, 0.25f),
            surface = Color(0xFF1A1A1A),
            surfaceContainer = Color(0xFF1E1E1E),
            surfaceContainerHigh = Color(0xFF282828),
            surfaceContainerHighest = Color(0xFF333333)
        )
    }

    private fun monochromeLight(): ColorScheme = lightColorScheme(
        primary = Color(0xFF5F5F5F),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFE0E0E0),
        onPrimaryContainer = Color(0xFF1A1A1A),
        secondary = Color(0xFF6B6B6B),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFEEEEEE),
        onSecondaryContainer = Color(0xFF222222),
        tertiary = Color(0xFF4A4A4A),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFD6D6D6),
        onTertiaryContainer = Color(0xFF121212),
        error = Color(0xFFBA1A1A),
        onError = Color.White,
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        background = Color(0xFFFFFBFF),
        onBackground = Color(0xFF1A1A1A),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF1A1A1A),
        surfaceVariant = Color(0xFFE8E8E8),
        onSurfaceVariant = Color(0xFF444444),
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = Color(0xFFF7F7F7),
        surfaceContainer = Color(0xFFF2F2F2),
        surfaceContainerHigh = Color(0xFFEDEDED),
        surfaceContainerHighest = Color(0xFFE8E8E8),
        surfaceDim = Color(0xFFE0E0E0),
        surfaceBright = Color.White,
        outline = Color(0xFF8A8A8A),
        outlineVariant = Color(0xFFCCCCCC),
        scrim = Color.Black,
        inverseSurface = Color(0xFF2F2F2F),
        inverseOnSurface = Color(0xFFF1F1F1),
        inversePrimary = Color(0xFF9E9E9E)
    )

    private fun monochromeDark(): ColorScheme = darkColorScheme(
        primary = Color(0xFFB0B0B0),
        onPrimary = Color(0xFF2A2A2A),
        primaryContainer = Color(0xFF3A3A3A),
        onPrimaryContainer = Color(0xFFE8E8E8),
        secondary = Color(0xFFA8A8A8),
        onSecondary = Color(0xFF1E1E1E),
        secondaryContainer = Color(0xFF333333),
        onSecondaryContainer = Color(0xFFE0E0E0),
        tertiary = Color(0xFF9A9A9A),
        onTertiary = Color(0xFF121212),
        tertiaryContainer = Color(0xFF2E2E2E),
        onTertiaryContainer = Color(0xFFD6D6D6),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        background = Color(0xFF000000), // pitch-black AMOLED
        onBackground = Color(0xFFE8E8E8),
        surface = Color(0xFF000000),
        onSurface = Color(0xFFE8E8E8),
        surfaceVariant = Color(0xFF2C2C2C),
        onSurfaceVariant = Color(0xFFB8B8B8),
        surfaceContainerLowest = Color.Black,
        surfaceContainerLow = Color(0xFF0E0E0E),
        surfaceContainer = Color(0xFF1A1A1A),
        surfaceContainerHigh = Color(0xFF242424),
        surfaceContainerHighest = Color(0xFF2E2E2E),
        surfaceDim = Color.Black,
        surfaceBright = Color(0xFF3A3A3A),
        outline = Color(0xFF8A8A8A),
        outlineVariant = Color(0xFF3A3A3A),
        scrim = Color.Black,
        inverseSurface = Color(0xFFE8E8E8),
        inverseOnSurface = Color(0xFF2F2F2F),
        inversePrimary = Color(0xFF5F5F5F)
    )

    private fun vibrantLight(seed: ThemeSeed): ColorScheme {
        val base = seed.light
        // Full-chroma pop: boosted primaries plus surfaces tinted with the
        // seed hue so the whole theme reads vivid, not just the buttons.
        return base.copy(
            primary = saturate(base.primary, 1.6f),
            primaryContainer = saturate(base.primaryContainer, 1.6f),
            secondary = saturate(base.secondary, 1.6f),
            secondaryContainer = saturate(base.secondaryContainer, 1.6f),
            tertiary = Color(0xFFE65100), // vivid tertiary accent
            tertiaryContainer = Color(0xFFFFE0B2),
            onTertiaryContainer = Color(0xFF332000),
            surfaceContainerLow = tint(base.surfaceContainerLow, base.primary, 0.08f),
            surfaceContainer = tint(base.surfaceContainer, base.primary, 0.10f),
            surfaceContainerHigh = tint(base.surfaceContainerHigh, base.primary, 0.12f),
            surfaceContainerHighest = tint(base.surfaceContainerHighest, base.primary, 0.14f)
        )
    }

    private fun vibrantDark(seed: ThemeSeed): ColorScheme {
        val base = seed.dark
        return base.copy(
            primary = saturate(base.primary, 1.6f),
            primaryContainer = saturate(base.primaryContainer, 1.6f),
            secondary = saturate(base.secondary, 1.6f),
            secondaryContainer = saturate(base.secondaryContainer, 1.6f),
            tertiary = Color(0xFFFFAB40),
            tertiaryContainer = Color(0xFF5D4037),
            onTertiaryContainer = Color(0xFFFFE0B2),
            surfaceContainerLow = tint(base.surfaceContainerLow, base.primary, 0.10f),
            surfaceContainer = tint(base.surfaceContainer, base.primary, 0.12f),
            surfaceContainerHigh = tint(base.surfaceContainerHigh, base.primary, 0.14f),
            surfaceContainerHighest = tint(base.surfaceContainerHighest, base.primary, 0.16f)
        )
    }

    private fun expressiveLight(seed: ThemeSeed): ColorScheme {
        val base = seed.light
        // Expressive — boosted chroma with violet tertiary bleeding into the
        // surfaces, clearly apart from both tonal spot and vibrant.
        return base.copy(
            primary = saturate(base.primary, 1.45f),
            primaryContainer = saturate(lighten(base.primaryContainer, 0.08f), 1.4f),
            secondary = saturate(base.secondary, 1.45f),
            secondaryContainer = saturate(base.secondaryContainer, 1.45f),
            tertiary = Color(0xFF7C4DFF), // distinct expressive tertiary
            tertiaryContainer = Color(0xFFEDE7F6),
            onTertiaryContainer = Color(0xFF1A0A2E),
            surfaceContainerLow = tint(base.surfaceContainerLow, base.tertiary, 0.08f),
            surfaceContainer = tint(base.surfaceContainer, base.tertiary, 0.10f),
            surfaceContainerHigh = tint(base.surfaceContainerHigh, base.tertiary, 0.12f),
            surfaceContainerHighest = tint(base.surfaceContainerHighest, base.tertiary, 0.14f)
        )
    }

    private fun expressiveDark(seed: ThemeSeed): ColorScheme {
        val base = seed.dark
        return base.copy(
            primary = saturate(base.primary, 1.45f),
            primaryContainer = saturate(base.primaryContainer, 1.4f),
            secondary = saturate(base.secondary, 1.45f),
            secondaryContainer = saturate(base.secondaryContainer, 1.4f),
            tertiary = Color(0xFFB388FF),
            tertiaryContainer = Color(0xFF4A2C82),
            onTertiaryContainer = Color(0xFFEDE7F6),
            surfaceContainerLow = tint(base.surfaceContainerLow, base.tertiary, 0.10f),
            surfaceContainer = tint(base.surfaceContainer, base.tertiary, 0.12f),
            surfaceContainerHigh = tint(base.surfaceContainerHigh, base.tertiary, 0.14f),
            surfaceContainerHighest = tint(base.surfaceContainerHighest, base.tertiary, 0.16f)
        )
    }

    private fun desaturate(color: Color, amount: Float): Color {
        // Simple desaturation via lerp to grey
        val grey = (color.red + color.green + color.blue) / 3f
        return Color(
            red = lerp(color.red, grey, amount),
            green = lerp(color.green, grey, amount),
            blue = lerp(color.blue, grey, amount),
            alpha = color.alpha
        )
    }

    private fun saturate(color: Color, factor: Float): Color {
        val grey = (color.red + color.green + color.blue) / 3f
        return Color(
            red = (grey + (color.red - grey) * factor).coerceIn(0f, 1f),
            green = (grey + (color.green - grey) * factor).coerceIn(0f, 1f),
            blue = (grey + (color.blue - grey) * factor).coerceIn(0f, 1f),
            alpha = color.alpha
        )
    }

    private fun tint(color: Color, tint: Color, amount: Float): Color {
        return Color(
            red = lerp(color.red, tint.red, amount),
            green = lerp(color.green, tint.green, amount),
            blue = lerp(color.blue, tint.blue, amount),
            alpha = color.alpha
        )
    }

    private fun lighten(color: Color, amount: Float): Color {
        return Color(
            red = (color.red + (1f - color.red) * amount).coerceIn(0f, 1f),
            green = (color.green + (1f - color.green) * amount).coerceIn(0f, 1f),
            blue = (color.blue + (1f - color.blue) * amount).coerceIn(0f, 1f),
            alpha = color.alpha
        )
    }

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
}
