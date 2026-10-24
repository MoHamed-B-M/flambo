package com.pranshulgg.recordmaster

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.motionScheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pranshulgg.recordmaster.helpers.PreferencesHelper
import com.pranshulgg.recordmaster.helpers.SnackbarManager
import com.pranshulgg.recordmaster.screens.AboutScreen
import com.pranshulgg.recordmaster.screens.HomeScreen
import com.pranshulgg.recordmaster.screens.PlayRecordingScreen
import com.pranshulgg.recordmaster.screens.PolicyPage
import com.pranshulgg.recordmaster.screens.RecordingScreen
import com.pranshulgg.recordmaster.screens.SettingsPage
import com.pranshulgg.recordmaster.screens.TermsPage
import com.pranshulgg.recordmaster.ui.theme.RecordMasterTheme
import com.pranshulgg.recordmaster.utils.NavTransitions
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        PreferencesHelper.init(this)
        super.onCreate(savedInstanceState)
        val app = application as RecordMasterApp
        val playbackController = app.playback
        runCatching { playbackController.connect() }
        lifecycleScope.launch {
            val speed = runCatching { app.prefs.playbackSpeedFlow.first() }.getOrDefault(1f)
            playbackController.setSpeed(speed)
        }
        enableEdgeToEdge()
        setContent {
            val snackbarHostState = remember { SnackbarHostState() }
            val scope = rememberCoroutineScope()

            val navController = rememberNavController()
            val currentMotionScheme = motionScheme
            val motionScheme = remember(currentMotionScheme) { currentMotionScheme }

            LaunchedEffect(Unit) {
                SnackbarManager.init(snackbarHostState, scope)
            }

            var darkTheme by remember {
                mutableStateOf(
                    PreferencesHelper.getBool("dark_theme") ?: false
                )
            }
            var colorSeed by remember {
                mutableStateOf(
                    PreferencesHelper.getString("seedColor") ?: "0xff0000FF"
                )
            }
            var useDynamicColor by remember {
                mutableStateOf(
                    PreferencesHelper.getBool("useDynamicColors") ?: false
                )
            }
            var useExpressiveColor by remember {
                mutableStateOf(
                    PreferencesHelper.getBool("useExpressiveColor") ?: true
                )
            }

            val context = LocalContext.current;

            RecordMasterTheme(
                darkTheme = darkTheme,
                seedColor = Color(colorSeed.removePrefix("0x").toLong(16).toInt()),
                dynamicColor = useDynamicColor,
                useExpressive = useExpressiveColor
            ) {
                NavHost(
                    modifier = Modifier.background(MaterialTheme.colorScheme.surface),
                    navController = navController, startDestination = "homePage",
                    enterTransition = {
                        NavTransitions.enter(motionScheme)
                    },
                    exitTransition = {
                        NavTransitions.exit(motionScheme)

                    },
                    popEnterTransition = {
                        NavTransitions.popEnter(motionScheme)

                    },
                    popExitTransition = {
                        NavTransitions.popExit(motionScheme)
                    }
                ) {

                    composable("homePage") { HomeScreen(navController, snackbarHostState = snackbarHostState, playback = playbackController) }
                    composable("record") { RecordingScreen(onDone = { navController.popBackStack() }) }
                    composable("OpenSettings") { SettingsPage(
                        navController = navController,
                        context = context,
                        onThemeChanged = { isDark ->
                            darkTheme = isDark
                        },
                        onSeedChanged = { color ->
                            colorSeed = color
                        },
                        onDynamicColorChanged = { useDynamicColors ->
                            useDynamicColor = useDynamicColors
                        },
                        onExpressiveColorChanged = { useExpressiveColors ->
                            useExpressiveColor = useExpressiveColors
                        },
                        snackbarHostState = snackbarHostState,
                        playback = playbackController,
                    ) }
                    composable("play/{path}") { backStackEntry ->
                        val encoded = backStackEntry.arguments?.getString("path")

                        val path = encoded?.let { android.net.Uri.decode(it) }
                        path?.let { PlayRecordingScreen(filePath = it, onDone = { navController.popBackStack() }, navController = navController, playback = playbackController) }
                    }
                    composable(
                        "OpenAboutScreen",

                        ) {
                        AboutScreen(
                            snackbarHostState = snackbarHostState,
                            navController = navController
                        )
                    }
                    composable(
                        "OpenTermsConditionScreen",
                    ) {
                        TermsPage(navController = navController)
                    }
                    composable(
                        "OpenPrivacyPolicyScreen",
                    ) {
                        PolicyPage(navController = navController)
                    }
                }
            }
        }
    }
}



