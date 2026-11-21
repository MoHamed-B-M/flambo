package com.pranshulgg.recordmaster.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Flambo's launcher icon is pure #000000 ink on #FFFFFF paper. These palettes
 * keep that exact relationship: no chroma in any role, black as the accent on
 * light and white as the accent on dark. The intermediate greys are strictly
 * neutral and only carry surface elevation, so layered UI still reads without
 * introducing a hue the icon does not have.
 */
private val Ink = Color(0xFF000000)
private val Paper = Color(0xFFFFFFFF)

private val MonochromeLight = lightColorScheme(
    primary = Ink,
    onPrimary = Paper,
    primaryContainer = Color(0xFF1A1A1A),
    onPrimaryContainer = Paper,
    inversePrimary = Paper,
    secondary = Color(0xFF2E2E2E),
    onSecondary = Paper,
    secondaryContainer = Color(0xFFE4E4E4),
    onSecondaryContainer = Ink,
    tertiary = Color(0xFF1F1F1F),
    onTertiary = Paper,
    tertiaryContainer = Color(0xFFDCDCDC),
    onTertiaryContainer = Ink,
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = Color(0xFFECECEC),
    onSurfaceVariant = Color(0xFF1F1F1F),
    surfaceTint = Ink,
    inverseSurface = Color(0xFF1A1A1A),
    inverseOnSurface = Paper,
    error = Color(0xFF2B2B2B),
    onError = Paper,
    errorContainer = Color(0xFFEFEFEF),
    onErrorContainer = Ink,
    outline = Color(0xFF5E5E5E),
    outlineVariant = Color(0xFFC7C7C7),
    scrim = Ink,
    surfaceBright = Paper,
    surfaceDim = Color(0xFFE3E3E3),
    surfaceContainerLowest = Paper,
    surfaceContainerLow = Color(0xFFF8F8F8),
    surfaceContainer = Color(0xFFF3F3F3),
    surfaceContainerHigh = Color(0xFFEEEEEE),
    surfaceContainerHighest = Color(0xFFE8E8E8),
    primaryFixed = Ink,
    primaryFixedDim = Color(0xFF2A2A2A),
    onPrimaryFixed = Paper,
    onPrimaryFixedVariant = Paper,
    secondaryFixed = Color(0xFF2E2E2E),
    secondaryFixedDim = Color(0xFF151515),
    onSecondaryFixed = Paper,
    onSecondaryFixedVariant = Paper,
    tertiaryFixed = Color(0xFF1F1F1F),
    tertiaryFixedDim = Color(0xFF060606),
    onTertiaryFixed = Paper,
    onTertiaryFixedVariant = Paper,
)

private val MonochromeDark = darkColorScheme(
    primary = Paper,
    onPrimary = Ink,
    primaryContainer = Color(0xFFE6E6E6),
    onPrimaryContainer = Ink,
    inversePrimary = Ink,
    secondary = Color(0xFFE0E0E0),
    onSecondary = Ink,
    secondaryContainer = Color(0xFF3A3A3A),
    onSecondaryContainer = Paper,
    tertiary = Color(0xFFEEEEEE),
    onTertiary = Ink,
    tertiaryContainer = Color(0xFF333333),
    onTertiaryContainer = Paper,
    background = Ink,
    onBackground = Paper,
    surface = Ink,
    onSurface = Paper,
    surfaceVariant = Color(0xFF2A2A2A),
    onSurfaceVariant = Color(0xFFE0E0E0),
    surfaceTint = Paper,
    inverseSurface = Color(0xFFF0F0F0),
    inverseOnSurface = Ink,
    error = Color(0xFFF0F0F0),
    onError = Ink,
    errorContainer = Color(0xFF3A3A3A),
    onErrorContainer = Paper,
    outline = Color(0xFF9A9A9A),
    outlineVariant = Color(0xFF4A4A4A),
    scrim = Ink,
    surfaceBright = Color(0xFF2C2C2C),
    surfaceDim = Ink,
    surfaceContainerLowest = Color(0xFF050505),
    surfaceContainerLow = Color(0xFF0F0F0F),
    surfaceContainer = Color(0xFF181818),
    surfaceContainerHigh = Color(0xFF212121),
    surfaceContainerHighest = Color(0xFF2B2B2B),
    primaryFixed = Color(0xFFE6E6E6),
    primaryFixedDim = Color(0xFFC8C8C8),
    onPrimaryFixed = Ink,
    onPrimaryFixedVariant = Ink,
    secondaryFixed = Color(0xFFE0E0E0),
    secondaryFixedDim = Color(0xFFC2C2C2),
    onSecondaryFixed = Ink,
    onSecondaryFixedVariant = Ink,
    tertiaryFixed = Color(0xFFEEEEEE),
    tertiaryFixedDim = Color(0xFFD0D0D0),
    onTertiaryFixed = Ink,
    onTertiaryFixedVariant = Ink,
)

/** The achromatic palette, independent of the seed/wallpaper colour. */
fun monochromeColorScheme(dark: Boolean): ColorScheme =
    if (dark) MonochromeDark else MonochromeLight

/**
 * AMOLED keeps the current accent and text colours but drops every dark
 * surface to true #000000. Cards keep a few near-black steps so elevation is
 * still visible on panels that are not the main canvas.
 */
fun ColorScheme.withAmoledSurfaces(): ColorScheme = copy(
    background = Ink,
    onBackground = Paper,
    surface = Ink,
    onSurface = Paper,
    surfaceVariant = Color(0xFF161616),
    onSurfaceVariant = Color(0xFFE6E6E6),
    surfaceContainerLowest = Ink,
    surfaceContainerLow = Color(0xFF080808),
    surfaceContainer = Color(0xFF101010),
    surfaceContainerHigh = Color(0xFF181818),
    surfaceContainerHighest = Color(0xFF202020),
    surfaceDim = Ink,
    surfaceBright = Color(0xFF202020),
    inverseSurface = Color(0xFFE6E6E6),
    inverseOnSurface = Ink,
)
