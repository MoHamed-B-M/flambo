package com.flambo.recorder

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.flambo.recorder.data.PreferencesManager
import com.flambo.recorder.record.AudioSource
import com.flambo.recorder.record.MediaProjectionHolder
import com.flambo.recorder.record.RecordingShortcut
import kotlinx.coroutines.flow.first
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.flambo.recorder.ui.navigation.FlamboNavGraph
import com.flambo.recorder.ui.onboarding.OnboardingScreen
import com.flambo.recorder.ui.theme.FlamboTheme
import com.flambo.recorder.ui.theme.ShapeLargeIncreased
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var showRationale by mutableStateOf(false)
    private var micGrantedState by mutableStateOf(false)
    private var notifGrantedState by mutableStateOf(false)

    private lateinit var app: FlamboApp

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val micGranted = grants[Manifest.permission.RECORD_AUDIO] == true
        micGrantedState = micGranted
        if (Build.VERSION.SDK_INT >= 33) {
            notifGrantedState = grants[Manifest.permission.POST_NOTIFICATIONS] == true
        }
        if (!micGranted) showRationale = true
    }

    // Single-permission path for the record button / onboarding: on grant,
    // continue straight into the pending recording instead of stranding
    // the user. Docs require RECORD_AUDIO even for playback capture, and
    // strict skins (ColorOS et al.) enforce it at AudioRecord creation.
    private var pendingStartSource: AudioSource? = null

    private val micLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        micGrantedState = granted
        val src = pendingStartSource
        pendingStartSource = null
        if (!granted) {
            showRationale = true
            return@registerForActivityResult
        }
        lifecycleScope.launch {
            if (src == AudioSource.SYSTEM) {
                if (MediaProjectionHolder.hasGrant()) startSystemNow()
                else {
                    pendingSystemRecord = true
                    requestSystemCapture()
                }
            } else if (src == AudioSource.MIC) {
                val q = app.prefs.qualityFlow.first()
                val nr = app.prefs.noiseReductionFlow.first()
                app.recorder.start(q, AudioSource.MIC, nr)
            }
        }
    }

    fun requestMicAndRecord(source: AudioSource) {
        pendingStartSource = source
        micLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    private val notifLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        notifGrantedState = granted
    }

    private suspend fun startSystemNow() {
        val q = app.prefs.qualityFlow.first()
        val nr = app.prefs.noiseReductionFlow.first()
        app.recorder.start(q, AudioSource.SYSTEM, nr)
    }

    // System-sound capture needs a one-time screen-capture consent,
    // exactly like a screen recorder asks. The grant lives only while the
    // process lives, so anything that needs it must be able to re-ask.
    private var pendingSystemRecord = false

    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val wantRecord = pendingSystemRecord
        pendingSystemRecord = false
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            MediaProjectionHolder.grant(result.resultCode, result.data!!)
            lifecycleScope.launch {
                app.prefs.setAudioSource(PreferencesManager.AUDIO_SYSTEM)
                // Came from the record button: start right away, no second tap.
                if (wantRecord) startSystemNow()
            }
        }
    }

    fun requestSystemCapture() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val mgr = getSystemService(MediaProjectionManager::class.java)
        projectionLauncher.launch(mgr.createScreenCaptureIntent())
    }

    // Home-screen path: reuse a live grant if there is one, otherwise ask
    // the system and auto-start on approval.
    fun requestSystemCaptureAndRecord() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        if (MediaProjectionHolder.hasGrant()) {
            lifecycleScope.launch {
                val q = app.prefs.qualityFlow.first()
                val nr = app.prefs.noiseReductionFlow.first()
                if (!app.recorder.start(q, AudioSource.SYSTEM, nr)) {
                    // Stale token — fall through to a fresh consent.
                    pendingSystemRecord = true
                    requestSystemCapture()
                }
            }
            return
        }
        pendingSystemRecord = true
        requestSystemCapture()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // App class holds datastore prefs
        app = application as FlamboApp
        refreshPermissionStates()

        // No upfront permission prompt — returning users see a disabled record
        // button with a snackbar to grant mic access when they tap it.
        // First-launch users meet permissions inside the onboarding tour.

        // Keep the dynamic shortcut labels in sync no matter where the
        // recording was toggled (in-app UI, launcher shortcut, Key Mapper).
        lifecycleScope.launch {
            app.recorder.state.collect {
                RecordingShortcut.refresh(this@MainActivity, it.isRecording, it.isPaused)
            }
        }
        handleShortcutIntent(intent)

        setContent {
            val dynamicColor by app.prefs.dynamicColorFlow.collectAsState(initial = true)
            val themeSeed by app.prefs.themeSeedFlow.collectAsState(initial = "ember")
            val darkThemePref by app.prefs.darkThemeFlow.collectAsState(initial = "system")
            val darkTheme = when (darkThemePref) {
                "light" -> false
                "dark" -> true
                else -> isSystemInDarkTheme()
            }

            val onboardingDone by app.prefs.onboardingDoneFlow.collectAsState(initial = null)
            var rerunIntro by remember { mutableStateOf(false) }
            val showOnboarding = onboardingDone == false || rerunIntro

            FlamboTheme(darkTheme = darkTheme, dynamicColor = dynamicColor, seedId = themeSeed) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        when {
                            onboardingDone == null -> Box(Modifier.fillMaxSize()) // prefs still loading
                            showOnboarding -> OnboardingScreen(
                                onFinish = {
                                    lifecycleScope.launch { app.prefs.setOnboardingDone(true) }
                                    rerunIntro = false
                                },
                                micGranted = micGrantedState,
                                notifGranted = notifGrantedState,
                                showNotificationsRow = Build.VERSION.SDK_INT >= 33,
                                onGrantMic = { micLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                                onGrantNotifications = {
                                    if (Build.VERSION.SDK_INT >= 33) {
                                        notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                }
                            )
                            else -> FlamboNavGraph(
                                onRerunOnboarding = { rerunIntro = true },
                                onRequestSystemCapture = { requestSystemCapture() },
                                onEnableSystemSound = { requestSystemCaptureAndRecord() },
                                onRequestMicPermission = { requestMicAndRecord(it) }
                            )
                        }
                    }

                    if (showRationale) {
                        AlertDialog(
                            onDismissRequest = { showRationale = false },
                            title = { Text("Microphone access") },
                            text = { Text("Flambo needs microphone permission to record. You can grant it in system settings.") },
                            confirmButton = {
                                TextButton(onClick = {
                                    showRationale = false
                                    checkPermissions()
                                }) { Text("Try again") }
                            },
                            dismissButton = { TextButton(onClick = { showRationale = false }) { Text("Not now") } },
                            shape = ShapeLargeIncreased
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShortcutIntent(intent)
    }

    // Launcher shortcut + Key Mapper entry point.
    // Two intents: START starts a new recording, PAUSE toggles pause/resume.
    // Both work from screen-off via Key Mapper hardware-key bindings.
    private fun handleShortcutIntent(intent: Intent?) {
        val action = intent?.action ?: return
        if (action != RecordingShortcut.ACTION_START_RECORDING &&
            action != RecordingShortcut.ACTION_PAUSE_RECORDING) return
        // Consume so rotation / process recreation doesn't re-fire.
        intent.action = null
        setIntent(intent)
        RecordingShortcut.reportUsed(this,
            if (action == RecordingShortcut.ACTION_START_RECORDING) RecordingShortcut.ID_START
            else RecordingShortcut.ID_PAUSE
        )
        val interactive = isScreenInteractive()
        when (action) {
            RecordingShortcut.ACTION_START_RECORDING -> lifecycleScope.launch { startFromExternal() }
            RecordingShortcut.ACTION_PAUSE_RECORDING -> lifecycleScope.launch { pauseFromExternal() }
        }
        if (!interactive) moveTaskToBack(true)
    }

    // Start recording from shortcut/Key Mapper. If already recording, stop & save.
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
        micGrantedState = micGranted
        val q = app.prefs.qualityFlow.first()
        val nr = app.prefs.noiseReductionFlow.first()
        var source = AudioSource.fromPref(app.prefs.audioSourceFlow.first())
        // System capture is parked — fall back to mic so a hardware key
        // always does something useful.
        if (source == AudioSource.SYSTEM && !AudioSource.SYSTEM_ENABLED) {
            source = AudioSource.MIC
        }
        if (!micGranted) {
            requestMicAndRecord(source)
            return
        }
        if (source == AudioSource.SYSTEM) {
            requestSystemCaptureAndRecord()
            return
        }
        recorder.start(q, AudioSource.MIC, nr)
        RecordingShortcut.refresh(this, true, false)
    }

    // Pause / resume from shortcut/Key Mapper.
    private suspend fun pauseFromExternal() {
        val recorder = app.recorder
        val s = recorder.state.value
        if (!s.isRecording) return
        if (s.isPaused) recorder.resume() else recorder.pause()
    }

    private fun isScreenInteractive(): Boolean = try {
        (getSystemService(POWER_SERVICE) as? PowerManager)?.isInteractive ?: true
    } catch (_: Exception) {
        true
    }

    private fun refreshPermissionStates() {
        micGrantedState = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        notifGrantedState = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun checkPermissions() {
        val needed = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            needed += Manifest.permission.RECORD_AUDIO
        }
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                needed += Manifest.permission.POST_NOTIFICATIONS
            }
        }
        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }
}
