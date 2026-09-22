package com.flambo.recorder.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FlamboTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    seedId: String = "ember",
    colorSchemeStyle: String = "TONAL_SPOT",
    expressive: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val seed = themeSeedById(seedId)

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> schemeColorScheme(seed.swatch, darkTheme, colorSchemeStyle)
    }

    if (expressive) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            typography = FlamboTypography,
            shapes = FlamboShapes,
            motionScheme = MotionScheme.expressive(),
            content = content
        )
    } else {
        androidx.compose.material3.MaterialTheme(
            colorScheme = colorScheme,
            typography = FlamboTypography,
            shapes = FlamboShapes,
            content = content
        )
    }
}

fun schemeColorScheme(seed: Color, darkTheme: Boolean, style: String): ColorScheme {
    val s = ThemeSeeds.find { it.swatch == seed } ?: ThemeSeeds.first()
    return when (style.uppercase()) {
        "EXPRESSIVE", "VIBRANT", "SPRITZ", "RAINBOW", "FRUIT_SALAD", "MONOCHROME" -> if (darkTheme) s.dark else s.light
        else -> if (darkTheme) s.dark else s.light
    }
}
