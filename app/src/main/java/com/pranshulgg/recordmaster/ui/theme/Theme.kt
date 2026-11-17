package com.pranshulgg.recordmaster.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.rememberDynamicColorScheme
import com.pranshulgg.recordmaster.ui.expressive.ExpressiveMotion
import com.pranshulgg.recordmaster.ui.expressive.ExpressiveShapes
import com.pranshulgg.recordmaster.ui.expressive.LocalMotionSpeed
import com.pranshulgg.recordmaster.ui.expressive.LocalReducedMotion
import com.pranshulgg.recordmaster.ui.expressive.rememberSystemReducedMotion

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RecordMasterTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    seedColor: Color = Color.Yellow,
    useExpressive: Boolean = true,
    reduceAnimations: Boolean = false,
    motionSpeed: Float = 1f,
    content: @Composable () -> Unit,
) {
    // The in-app switch and Android's Remove animations both reduce motion;
    // either one wins, and the speed choice only applies when motion is on.
    val systemReducedMotion = rememberSystemReducedMotion()
    val effectiveReducedMotion = reduceAnimations || systemReducedMotion
    val effectiveSpeed = if (effectiveReducedMotion) {
        1f
    } else {
        ExpressiveMotion.speed(motionSpeed)
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        else -> {
            rememberDynamicColorScheme(
                seedColor = seedColor,
                isDark = darkTheme,
                specVersion = if (useExpressive) ColorSpec.SpecVersion.SPEC_2025 else ColorSpec.SpecVersion.SPEC_2021,
                style = if (useExpressive) PaletteStyle.Expressive else PaletteStyle.TonalSpot
            )
        }
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    CompositionLocalProvider(
        LocalReducedMotion provides effectiveReducedMotion,
        LocalMotionSpeed provides effectiveSpeed,
    ) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = ExpressiveShapes,
            motionScheme = ExpressiveMotion.motionScheme(effectiveReducedMotion, effectiveSpeed),
            content = content
        )
    }
}
