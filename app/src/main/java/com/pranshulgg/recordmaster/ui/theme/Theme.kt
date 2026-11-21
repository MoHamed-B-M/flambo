package com.pranshulgg.recordmaster.ui.theme

import android.app.Activity
import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.Typography as MaterialTypography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.rememberDynamicColorScheme
import com.pranshulgg.recordmaster.core.data.CustomFontStore
import com.pranshulgg.recordmaster.core.data.FontPreferences
import com.pranshulgg.recordmaster.core.domain.AppFont
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
    blackAndWhite: Boolean = false,
    amoledMode: Boolean = false,
    useExpressive: Boolean = true,
    reduceAnimations: Boolean = false,
    motionSpeed: Float = 1f,
    typography: MaterialTypography? = null,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val appContext = context.applicationContext ?: context
    val fontPreferences = remember(appContext) { FontPreferences(appContext) }
    val customFontStore = remember(appContext) { CustomFontStore(appContext) }
    val initialFont = remember(appContext) { fontPreferences.selectedFontImmediate }
    val selectedFont by fontPreferences.selectedFontFlow.collectAsState(
        initial = initialFont
    )
    val customFontName by fontPreferences.customFontNameFlow.collectAsState(initial = null)
    val customFontGeneration by fontPreferences.customFontGenerationFlow.collectAsState(initial = 0L)
    val appTypography = remember(selectedFont, customFontName, customFontGeneration) {
        val customFontFile = if (selectedFont == AppFont.CUSTOM) {
            customFontStore.fontFile
        } else {
            null
        }
        Typography.withFontFamily(fontFamilyFor(selectedFont, customFontFile))
    }

    // The in-app switch and Android's Remove animations both reduce motion;
    // either one wins, and the speed choice only applies when motion is on.
    val systemReducedMotion = rememberSystemReducedMotion()
    val effectiveReducedMotion = reduceAnimations || systemReducedMotion
    val effectiveSpeed = if (effectiveReducedMotion) {
        1f
    } else {
        ExpressiveMotion.speed(motionSpeed)
    }

    // AMOLED is a dark-mode-only treatment: in light mode the toggle is kept
    // but has no effect, so switching themes never needs a second decision.
    val amoledActive = amoledMode && darkTheme

    val baseColorScheme = when {
        blackAndWhite -> monochromeColorScheme(dark = darkTheme)

        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
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
    val colorScheme = if (amoledActive) baseColorScheme.withAmoledSurfaces() else baseColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            // Edge-to-edge lets the window background show through the bars;
            // matching it to the canvas avoids a light rim around AMOLED dark.
            window.setBackgroundDrawable(
                ColorDrawable(if (amoledActive) AndroidColor.BLACK else AndroidColor.TRANSPARENT)
            )
        }
    }

    CompositionLocalProvider(
        LocalReducedMotion provides effectiveReducedMotion,
        LocalMotionSpeed provides effectiveSpeed,
    ) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            typography = typography ?: appTypography,
            shapes = ExpressiveShapes,
            motionScheme = ExpressiveMotion.motionScheme(effectiveReducedMotion, effectiveSpeed),
            content = content
        )
    }
}
