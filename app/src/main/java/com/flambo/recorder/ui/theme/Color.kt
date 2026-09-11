package com.flambo.recorder.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Flambo seed — warm ember inspired, expressive but not aggressive.
// On Android 12+ this is overridden by dynamic color when enabled.
private val seed = Color(0xFFD8572A)

// Light scheme derived from seed (hand-tuned for good contrast and M3 tonal balance)
val FlamboLightColors = lightColorScheme(
    primary = Color(0xFF8F4A2E),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDBCB),
    onPrimaryContainer = Color(0xFF3B0B00),
    secondary = Color(0xFF77574B),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDBCB),
    onSecondaryContainer = Color(0xFF2C150E),
    tertiary = Color(0xFF6B5E2F),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF4E0A6),
    onTertiaryContainer = Color(0xFF221B04),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFFFBFF),
    onBackground = Color(0xFF201A19),
    surface = Color(0xFFFFFBFF),
    onSurface = Color(0xFF201A19),
    surfaceVariant = Color(0xFFF5DED6),
    onSurfaceVariant = Color(0xFF53433F),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFF1EC),
    surfaceContainer = Color(0xFFFCEAE3),
    surfaceContainerHigh = Color(0xFFF7E4DD),
    surfaceContainerHighest = Color(0xFFF1DFD8),
    surfaceDim = Color(0xFFE8D6CF),
    surfaceBright = Color(0xFFFFFBFF),
    outline = Color(0xFF85736E),
    outlineVariant = Color(0xFFD8C2BC),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFF362F2C),
    inverseOnSurface = Color(0xFFFBEEE9),
    inversePrimary = Color(0xFFFFB59B),
)

// Dark scheme — same hue, adapted for OLED-friendly tonal surfaces
val FlamboDarkColors = darkColorScheme(
    primary = Color(0xFFFFB59B),
    onPrimary = Color(0xFF55200B),
    primaryContainer = Color(0xFF73341E),
    onPrimaryContainer = Color(0xFFFFDBCB),
    secondary = Color(0xFFE7BDB0),
    onSecondary = Color(0xFF442A20),
    secondaryContainer = Color(0xFF5D4035),
    onSecondaryContainer = Color(0xFFFFDBCB),
    tertiary = Color(0xFFD7C48D),
    onTertiary = Color(0xFF3A3005),
    tertiaryContainer = Color(0xFF52461A),
    onTertiaryContainer = Color(0xFFF4E0A6),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF201A19),
    onBackground = Color(0xFFF1DFD8),
    surface = Color(0xFF201A19),
    onSurface = Color(0xFFF1DFD8),
    surfaceVariant = Color(0xFF53433F),
    onSurfaceVariant = Color(0xFFD8C2BC),
    surfaceContainerLowest = Color(0xFF150E0C),
    surfaceContainerLow = Color(0xFF201A19),
    surfaceContainer = Color(0xFF251E1C),
    surfaceContainerHigh = Color(0xFF2F2826),
    surfaceContainerHighest = Color(0xFF3A3330),
    surfaceDim = Color(0xFF201A19),
    surfaceBright = Color(0xFF4A4442),
    outline = Color(0xFFA08C87),
    outlineVariant = Color(0xFF53433F),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFFF1DFD8),
    inverseOnSurface = Color(0xFF362F2C),
    inversePrimary = Color(0xFF8F4A2E),
)

// Pickable accent families. Each seed reuses Flambo's neutrals and error
// roles and only swaps the primary/secondary/tertiary families, so contrast
// stays safe by construction (40-tone on white / 80-tone on dark in light
// mode, mirrored in dark mode).
data class ThemeSeed(
    val id: String,
    val label: String,
    val swatch: Color,
    val light: ColorScheme,
    val dark: ColorScheme
)

private fun seedLight(
    primary: Color, onPrimaryContainer: Color, primaryContainer: Color,
    secondary: Color, onSecondaryContainer: Color, secondaryContainer: Color,
    tertiary: Color, onTertiaryContainer: Color, tertiaryContainer: Color,
    swatchId: String, swatchLabel: String, swatch: Color
) = ThemeSeed(
    id = swatchId, label = swatchLabel, swatch = swatch,
    light = FlamboLightColors.copy(
        primary = primary, onPrimary = Color(0xFFFFFFFF),
        primaryContainer = primaryContainer, onPrimaryContainer = onPrimaryContainer,
        secondary = secondary, onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = secondaryContainer, onSecondaryContainer = onSecondaryContainer,
        tertiary = tertiary, onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = tertiaryContainer, onTertiaryContainer = onTertiaryContainer,
        inversePrimary = primary
    ),
    dark = FlamboDarkColors.copy(
        primary = primaryContainer, onPrimary = onPrimaryContainer,
        primaryContainer = primary, onPrimaryContainer = primaryContainer,
        secondary = secondaryContainer, onSecondary = onSecondaryContainer,
        secondaryContainer = secondary, onSecondaryContainer = secondaryContainer,
        tertiary = tertiaryContainer, onTertiary = onTertiaryContainer,
        tertiaryContainer = tertiary, onTertiaryContainer = tertiaryContainer,
        inversePrimary = primary
    )
)

val ThemeSeeds = listOf(
    // Ember keeps the hand-tuned Flambo schemes exactly as they were.
    ThemeSeed("ember", "Ember", Color(0xFFD8572A), FlamboLightColors, FlamboDarkColors),
    seedLight(
        primary = Color(0xFF0061A4), onPrimaryContainer = Color(0xFF001D36), primaryContainer = Color(0xFFD1E4FF),
        secondary = Color(0xFF535F70), onSecondaryContainer = Color(0xFF141B24), secondaryContainer = Color(0xFFD9E3F8),
        tertiary = Color(0xFF006874), onTertiaryContainer = Color(0xFF001F26), tertiaryContainer = Color(0xFF9AEFFD),
        swatchId = "ocean", swatchLabel = "Ocean", swatch = Color(0xFF0061A4)
    ),
    seedLight(
        primary = Color(0xFF6750A4), onPrimaryContainer = Color(0xFF21005D), primaryContainer = Color(0xFFEADDFF),
        secondary = Color(0xFF625B71), onSecondaryContainer = Color(0xFF1D192B), secondaryContainer = Color(0xFFE8DEF8),
        tertiary = Color(0xFF7D5260), onTertiaryContainer = Color(0xFF31111D), tertiaryContainer = Color(0xFFFFD8E4),
        swatchId = "grape", swatchLabel = "Grape", swatch = Color(0xFF6750A4)
    ),
    seedLight(
        primary = Color(0xFF4C662B), onPrimaryContainer = Color(0xFF102000), primaryContainer = Color(0xFFCDEDA3),
        secondary = Color(0xFF586249), onSecondaryContainer = Color(0xFF141E0C), secondaryContainer = Color(0xFFD9E7CA),
        tertiary = Color(0xFF386663), onTertiaryContainer = Color(0xFF00201C), tertiaryContainer = Color(0xFFBCECE7),
        swatchId = "forest", swatchLabel = "Forest", swatch = Color(0xFF4C662B)
    ),
    seedLight(
        primary = Color(0xFF984061), onPrimaryContainer = Color(0xFF3E001D), primaryContainer = Color(0xFFFFD9E2),
        secondary = Color(0xFF74565F), onSecondaryContainer = Color(0xFF2B151C), secondaryContainer = Color(0xFFFDD9E3),
        tertiary = Color(0xFF7D5260), onTertiaryContainer = Color(0xFF31111D), tertiaryContainer = Color(0xFFFFD8E4),
        swatchId = "rose", swatchLabel = "Rose", swatch = Color(0xFF984061)
    )
)

fun themeSeedById(id: String): ThemeSeed =
    ThemeSeeds.firstOrNull { it.id == id } ?: ThemeSeeds.first()
