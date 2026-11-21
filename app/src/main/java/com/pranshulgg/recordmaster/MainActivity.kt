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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.motionScheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pranshulgg.recordmaster.core.data.PreferencesManager
import com.pranshulgg.recordmaster.core.domain.AnimationSpeed
import com.pranshulgg.recordmaster.core.domain.AppFont
import com.pranshulgg.recordmaster.core.record.RecordingShortcut
import com.pranshulgg.recordmaster.core.recovery.RecoveryOutcome
import com.pranshulgg.recordmaster.helpers.PreferencesHelper
import com.pranshulgg.recordmaster.helpers.SnackbarManager
import com.pranshulgg.recordmaster.screens.AboutScreen
import com.pranshulgg.recordmaster.screens.AccessibilitySettingsPage
import com.pranshulgg.recordmaster.screens.AppLooksSettingsPage
import com.pranshulgg.recordmaster.screens.AutomationSettingsPage
import com.pranshulgg.recordmaster.screens.HomeScreen
import com.pranshulgg.recordmaster.screens.PlayRecordingScreen
import com.pranshulgg.recordmaster.screens.PlaybackSettingsPage
import com.pranshulgg.recordmaster.screens.PolicyPage
import com.pranshulgg.recordmaster.screens.RecordingScreen
import com.pranshulgg.recordmaster.screens.RecordingSettingsPage
import com.pranshulgg.recordmaster.screens.ReliabilitySettingsPage
import com.pranshulgg.recordmaster.screens.SettingsPage
import com.pranshulgg.recordmaster.screens.SpeechModelsScreen
import com.pranshulgg.recordmaster.screens.SpeechSettingsPage
import com.pranshulgg.recordmaster.screens.StorageSettingsPage
import com.pranshulgg.recordmaster.screens.TermsPage
import com.pranshulgg.recordmaster.ui.components.AnnouncementSheet
import com.pranshulgg.recordmaster.ui.components.CrashRecoveryDialog
import com.pranshulgg.recordmaster.ui.theme.AppThemeMode
import com.pranshulgg.recordmaster.ui.theme.AppearancePreferences
import com.pranshulgg.recordmaster.ui.theme.RecordMasterTheme
import com.pranshulgg.recordmaster.ui.theme.parseSeedColor
import com.pranshulgg.recordmaster.utils.NavTransitions
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Numeric dotted-version comparison. Suffixes such as `-beta` are ignored and
 * missing components count as zero, so "1.7.4" < "2.0.0" and "1.10" > "1.9.9".
 */
private fun isVersionBelow(version: String, target: String): Boolean {
    fun parts(value: String): List<Int> = value
        .substringBefore('-')
        .split('.')
        .mapNotNull { it.trim().toIntOrNull() }
    val left = parts(version)
    val right = parts(target)
    for (index in 0 until maxOf(left.size, right.size)) {
        val a = left.getOrElse(index) { 0 }
        val b = right.getOrElse(index) { 0 }
        if (a != b) return a < b
    }
    return false
}

/**
 * Decides whether the 2.0 welcome should be shown on this launch. A fresh
 * install has no recorded version, so it is welcomed as if it came from 1.x.
 * The installed build itself must have reached 2.0 for the announcement to
 * make sense.
 */
private fun shouldShowAnnouncement(
    lastRunVersion: String,
    currentVersion: String,
    hasSeen: Boolean
): Boolean {
    if (hasSeen) return false
    val previous = lastRunVersion.ifBlank { "0.0.0" }
    if (!isVersionBelow(previous, PreferencesManager.ANNOUNCEMENT_VERSION)) return false
    return !isVersionBelow(currentVersion, PreferencesManager.ANNOUNCEMENT_VERSION)
}

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

            // The installed version drives the 2.0 welcome gate. It is also the
            // value stamped into lastRunVersion once the dialog is closed.
            val currentVersion = BuildConfig.VERSION_NAME
            var showAnnouncementDialog by remember { mutableStateOf(false) }

            LaunchedEffect(currentVersion) {
                val lastRun = runCatching { app.prefs.lastRunVersion() }.getOrDefault("")
                val seen = runCatching { app.prefs.hasSeen2_0Announcement() }.getOrDefault(false)
                showAnnouncementDialog = shouldShowAnnouncement(lastRun, currentVersion, seen)
            }

            // Every exit path stamps the current version; otherwise the welcome
            // returns on the next launch. A ticked checkbox is honoured on
            // dismissal too (Back / outside tap / swipe), not just on "Let's Go".
            fun closeAnnouncement(dontShowAgain: Boolean) {
                showAnnouncementDialog = false
                scope.launch {
                    app.prefs.setLastRunVersion(currentVersion)
                    if (dontShowAgain) app.prefs.setHasSeen2_0Announcement(true)
                }
            }

            // Debug-only replay: restore the pre-2.0 state and show the welcome
            // immediately. Dismissing it re-stamps the current version as usual.
            fun rerunAnnouncement() {
                showAnnouncementDialog = true
                scope.launch {
                    app.prefs.setHasSeen2_0Announcement(false)
                    app.prefs.setLastRunVersion(PreferencesManager.DEBUG_RESET_VERSION)
                }
            }

            LaunchedEffect(Unit) {
                SnackbarManager.init(snackbarHostState, scope)
            }

            var themeMode by remember {
                mutableStateOf(AppThemeMode.current())
            }
            val systemInDarkTheme = isSystemInDarkTheme()
            val darkTheme = themeMode.isDark(systemInDarkTheme)
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
            var useBlackAndWhite by remember {
                mutableStateOf(AppearancePreferences.blackAndWhite)
            }
            var useAmoledMode by remember {
                mutableStateOf(AppearancePreferences.amoledMode)
            }
            val reduceAnimations by app.prefs.reduceAnimationsFlow.collectAsState(initial = false)
            val animationSpeed by app.prefs.animationSpeedFlow.collectAsState(
                initial = AnimationSpeed.Default
            )
            val selectedFont by app.fontPrefs.selectedFontFlow.collectAsState(
                initial = AppFont.DEFAULT
            )
            val customFontName by app.fontPrefs.customFontNameFlow.collectAsState(
                initial = null
            )

            val context = LocalContext.current;

            RecordMasterTheme(
                darkTheme = darkTheme,
                seedColor = parseSeedColor(colorSeed),
                dynamicColor = useDynamicColor,
                blackAndWhite = useBlackAndWhite,
                amoledMode = useAmoledMode,
                useExpressive = useExpressiveColor,
                reduceAnimations = reduceAnimations,
                motionSpeed = animationSpeed.multiplier,
            ) {
                // Read the scheme inside the theme so NavHost transitions follow
                // the reduced-motion scheme when "Remove animations" is on.
                val motionScheme = MaterialTheme.motionScheme

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
                        currentTheme = themeMode,
                        snackbarHostState = snackbarHostState,
                        onRerunAnnouncement = { rerunAnnouncement() },
                    ) }
                    composable("SettingsAppLooks") { AppLooksSettingsPage(
                        navController = navController,
                        currentTheme = themeMode,
                        snackbarHostState = snackbarHostState,
                        onThemeChanged = { mode ->
                            themeMode = mode
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
                        onBlackAndWhiteChanged = { enabled ->
                            useBlackAndWhite = enabled
                        },
                        onAmoledModeChanged = { enabled ->
                            useAmoledMode = enabled
                        },
                        currentFont = selectedFont,
                        customFontName = customFontName,
                        onFontSelected = { font ->
                            scope.launch { app.fontPrefs.setSelectedFont(font) }
                        },
                        onCustomFontSelected = { name ->
                            scope.launch { app.fontPrefs.setCustomFont(name) }
                        },
                    ) }
                    composable("SettingsAccessibility") {
                        AccessibilitySettingsPage(
                            navController = navController,
                            snackbarHostState = snackbarHostState
                        )
                    }
                    composable("SettingsRecording") {
                        RecordingSettingsPage(
                            navController = navController,
                            snackbarHostState = snackbarHostState
                        )
                    }
                    composable("SettingsReliability") {
                        ReliabilitySettingsPage(
                            navController = navController,
                            snackbarHostState = snackbarHostState
                        )
                    }
                    composable("SettingsAutomation") {
                        AutomationSettingsPage(
                            navController = navController,
                            snackbarHostState = snackbarHostState
                        )
                    }
                    composable("SettingsStorage") {
                        StorageSettingsPage(
                            navController = navController,
                            snackbarHostState = snackbarHostState
                        )
                    }
                    composable("SettingsPlayback") {
                        PlaybackSettingsPage(
                            navController = navController,
                            snackbarHostState = snackbarHostState
                        )
                    }
                    composable("SettingsSpeech") {
                        SpeechSettingsPage(
                            navController = navController,
                            snackbarHostState = snackbarHostState
                        )
                    }
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

                // Crash recovery is offered once per staged take on launch.
                // Dismissing keeps the chunks; Recover/Discard clear them.
                val recoverySession by app.crashRecovery.pending.collectAsState()
                if (recoverySession != null) {
                    CrashRecoveryDialog(
                        onRecover = {
                            scope.launch {
                                when (val outcome = app.crashRecovery.recover()) {
                                    is RecoveryOutcome.Recovered ->
                                        SnackbarManager.showMessage("Recovered \"${outcome.title}\"")
                                    is RecoveryOutcome.Failed ->
                                        SnackbarManager.showMessage(outcome.message)
                                }
                            }
                        },
                        onDiscard = {
                            scope.launch {
                                app.crashRecovery.discard()
                                SnackbarManager.showMessage("Discarded the unfinished recording")
                            }
                        },
                        onDismiss = { app.crashRecovery.dismiss() }
                    )
                }

                // The 2.0 welcome is topmost, but never stacked on the crash
                // prompt: recovery takes priority, the welcome returns after it.
                if (showAnnouncementDialog && recoverySession == null) {
                    AnnouncementSheet(
                        onStart = { dontShowAgain -> closeAnnouncement(dontShowAgain) },
                        onDismiss = { dontShowAgain -> closeAnnouncement(dontShowAgain) },
                    )
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



