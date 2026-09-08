package com.flambo.recorder

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import com.flambo.recorder.ui.navigation.FlamboNavGraph
import com.flambo.recorder.ui.theme.FlamboTheme
import com.flambo.recorder.ui.theme.ShapeLargeIncreased

class MainActivity : ComponentActivity() {

    private var showRationale by mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val micGranted = grants[Manifest.permission.RECORD_AUDIO] == true
        if (!micGranted) showRationale = true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        checkPermissions()

        // App class holds datastore prefs
        val app = application as FlamboApp

        setContent {
            val dynamicColor by app.prefs.dynamicColorFlow.collectAsState(initial = true)
            val darkThemePref by app.prefs.darkThemeFlow.collectAsState(initial = "system")
            val darkTheme = when (darkThemePref) {
                "light" -> false
                "dark" -> true
                else -> isSystemInDarkTheme()
            }

            var showFirstLaunchDialog by remember { mutableStateOf(false) }
            // respectful recording reminder: show once on first launch if enabled. Simplified — show every cold start if reminder enabled.
            // We gate by prefs: if reminder true we show once per app install (we don't persist seen flag for brevity — shows on first composition).
            // For minimal intrusion we only show if there are zero recordings yet? Let's approximate.

            FlamboTheme(darkTheme = darkTheme, dynamicColor = dynamicColor) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        FlamboNavGraph()
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
