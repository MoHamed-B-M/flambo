package com.flambo.recorder

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.flambo.recorder.data.PreferencesManager
import com.flambo.recorder.record.MediaProjectionHolder
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

    private lateinit var app: FlamboApp

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val micGranted = grants[Manifest.permission.RECORD_AUDIO] == true
        if (!micGranted) showRationale = true
    }

    // System-sound capture needs a one-time screen-capture consent,
    // exactly like a screen recorder asks.
    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            MediaProjectionHolder.grant(result.resultCode, result.data!!)
            lifecycleScope.launch { app.prefs.setAudioSource(PreferencesManager.AUDIO_SYSTEM) }
        }
    }

    fun requestSystemCapture() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val mgr = getSystemService(MediaProjectionManager::class.java)
        projectionLauncher.launch(mgr.createScreenCaptureIntent())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        checkPermissions()

        // App class holds datastore prefs
        app = application as FlamboApp

        setContent {
            val dynamicColor by app.prefs.dynamicColorFlow.collectAsState(initial = true)
            val darkThemePref by app.prefs.darkThemeFlow.collectAsState(initial = "system")
            val darkTheme = when (darkThemePref) {
                "light" -> false
                "dark" -> true
                else -> isSystemInDarkTheme()
            }

            val onboardingDone by app.prefs.onboardingDoneFlow.collectAsState(initial = null)
            var rerunIntro by remember { mutableStateOf(false) }
            val showOnboarding = onboardingDone == false || rerunIntro

            FlamboTheme(darkTheme = darkTheme, dynamicColor = dynamicColor) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        when {
                            onboardingDone == null -> Box(Modifier.fillMaxSize()) // prefs still loading
                            showOnboarding -> OnboardingScreen(onFinish = {
                                lifecycleScope.launch { app.prefs.setOnboardingDone(true) }
                                rerunIntro = false
                            })
                            else -> FlamboNavGraph(
                                onRerunOnboarding = { rerunIntro = true },
                                onRequestSystemCapture = { requestSystemCapture() }
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
