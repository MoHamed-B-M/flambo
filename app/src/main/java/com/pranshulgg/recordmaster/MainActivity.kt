package com.pranshulgg.recordmaster

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.PowerManager
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pranshulgg.recordmaster.core.record.RecordingShortcut
import com.pranshulgg.recordmaster.helpers.PreferencesHelper
import com.pranshulgg.recordmaster.helpers.SnackbarManager
import com.pranshulgg.recordmaster.screens.AboutScreen
import com.pranshulgg.recordmaster.screens.HomeScreen
import com.pranshulgg.recordmaster.screens.PlayRecordingScreen
import com.pranshulgg.recordmaster.screens.PolicyPage
import com.pranshulgg.recordmaster.screens.RecordingScreen
import com.pranshulgg.recordmaster.screens.SettingsPage
import com.pranshulgg.recordmaster.screens.SpeechModelsScreen
import com.pranshulgg.recordmaster.screens.TermsPage
import com.pranshulgg.recordmaster.ui.theme.RecordMasterTheme
import com.pranshulgg.recordmaster.utils.NavTransitions
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var app: RecordMasterApp

    /**
     * Route requested by a shortcut. Set before/while the NavHost composes and
     * consumed by [LaunchedEffect] once the graph is ready.
     */
    private var pendingRoute by mutableStateOf<String?>(null)

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        PreferencesHelper.init(this)
        super.onCreate(savedInstanceState)
        app = application as RecordMasterApp
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
                // A shortcut (launcher long-press, Key Mapper, Tasker) must land
                // on the recorder page, otherwise the take is only visible in
                // the notification. Runs after the graph is set.
                LaunchedEffect(pendingRoute) {
                    val route = pendingRoute ?: return@LaunchedEffect
                    pendingRoute = null
                    runCatching {
                        navController.navigate(route) {
                            launchSingleTop = true
                            popUpTo("homePage") { inclusive = false }
                        }
                    }
                }
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
                        "OpenSpeechModels",
                    ) {
                        SpeechModelsScreen(
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
        handleShortcutIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShortcutIntent(intent)
    }

    /**
     * Automation entry point (launcher shortcuts, Key Mapper, Tasker). The
     * intent action is cleared so a configuration change cannot replay it.
     */
    private fun handleShortcutIntent(intent: Intent?) {
        val target = intent ?: return
        val action = target.action ?: return
        val known = setOf(
            RecordingShortcut.ACTION_START_RECORDING, RecordingShortcut.ACTION_PAUSE_RECORDING,
            RecordingShortcut.ACTION_RECORD, RecordingShortcut.ACTION_PAUSE,
            RecordingShortcut.ACTION_STOP, RecordingShortcut.ACTION_TOGGLE_RECORD_PAUSE,
            RecordingShortcut.ACTION_TOGGLE_RECORD_STOP
        )
        if (action !in known) return

        // Clear first so a configuration change cannot replay the action.
        target.action = null
        setIntent(target)
        if (!app.automationTriggersEnabled) return

        pendingRoute = ROUTE_RECORD
        val interactive = isScreenInteractive()
        lifecycleScope.launch {
            when (action) {
                RecordingShortcut.ACTION_START_RECORDING -> startFromExternal()
                RecordingShortcut.ACTION_PAUSE_RECORDING -> pauseFromExternal()
                RecordingShortcut.ACTION_RECORD -> recordOnlyFromExternal()
                RecordingShortcut.ACTION_PAUSE -> pauseOnlyFromExternal()
                RecordingShortcut.ACTION_STOP -> stopFromExternal()
                RecordingShortcut.ACTION_TOGGLE_RECORD_PAUSE -> toggleRecordPauseFromExternal()
                RecordingShortcut.ACTION_TOGGLE_RECORD_STOP -> startFromExternal()
            }
        }
        if (!interactive) moveTaskToBack(true)
    }

    // Explicit Record: no-op if already recording (vs. startFromExternal's toggle-to-stop).
    private suspend fun recordOnlyFromExternal() {
        if (app.recorder.state.value.isRecording) return
        startFromExternal()
    }

    private suspend fun pauseOnlyFromExternal() {
        val s = app.recorder.state.value
        if (s.isRecording && !s.isPaused) app.recorder.pause()
    }

    private suspend fun stopFromExternal() {
        if (app.recorder.state.value.isRecording) {
            app.recorder.stop()
            RecordingShortcut.refresh(this, false, false)
        }
    }

    private suspend fun toggleRecordPauseFromExternal() {
        val s = app.recorder.state.value
        when {
            !s.isRecording -> recordOnlyFromExternal()
            s.isPaused -> app.recorder.resume()
            else -> app.recorder.pause()
        }
    }

    private suspend fun startFromExternal() {
        val recorder = app.recorder
        if (recorder.state.value.isRecording) {
            recorder.stop()
            RecordingShortcut.refresh(this, false, false)
            return
        }
        val micGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (!micGranted) {
            SnackbarManager.showMessage("Microphone permission is required")
            return
        }
        val quality = app.prefs.qualityFlow.first()
        val noiseReduction = app.prefs.noiseReductionFlow.first()
        recorder.start(quality, noiseReduction)
        RecordingShortcut.refresh(this, true, false)
    }

    private suspend fun pauseFromExternal() {
        val s = app.recorder.state.value
        if (!s.isRecording) return
        if (s.isPaused) app.recorder.resume() else app.recorder.pause()
    }

    private fun isScreenInteractive(): Boolean = try {
        (getSystemService(POWER_SERVICE) as? PowerManager)?.isInteractive ?: true
    } catch (_: Exception) {
        true
    }

    private companion object {
        /** Must match the `composable("record")` route in [setContent]. */
        const val ROUTE_RECORD = "record"
    }
}



