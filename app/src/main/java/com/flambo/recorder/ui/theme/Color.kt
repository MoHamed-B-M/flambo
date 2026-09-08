package com.flambo.recorder.ui.theme

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
