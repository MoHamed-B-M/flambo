package com.flambo.recorder.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.flambo.recorder.data.PreferencesManager
import com.flambo.recorder.domain.RecordingQuality
import com.flambo.recorder.ui.theme.ShapeLargeIncreased
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    prefs: PreferencesManager,
    scope: CoroutineScope,
    onBack: () -> Unit
) {
    val quality by prefs.qualityFlow.collectAsState(initial = RecordingQuality.HIGH)
    val dynamicColor by prefs.dynamicColorFlow.collectAsState(initial = true)
    val reminder by prefs.recordingReminderFlow.collectAsState(initial = true)
    val darkTheme by prefs.darkThemeFlow.collectAsState(initial = "system")

    var showQualityDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("Recording", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))

            ListItem(
                headlineContent = { Text("Quality") },
                supportingContent = { Text("${quality.label} • ${quality.description}") },
                trailingContent = { TextButton(onClick = { showQualityDialog = true }) { Text("Change") } },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier.padding(vertical = 4.dp)
            )

            ListItem(
                headlineContent = { Text("Recording reminder") },
                supportingContent = { Text("Show a gentle reminder before long recordings") },
                trailingContent = {
                    Switch(checked = reminder, onCheckedChange = { scope.launch { prefs.setRecordingReminder(it) } })
                },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)

            Text("Appearance", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)

            ListItem(
                headlineContent = { Text("Dynamic color") },
                supportingContent = { Text("Use Material You colors from your wallpaper (Android 12+)") },
                trailingContent = {
                    Switch(checked = dynamicColor, onCheckedChange = { scope.launch { prefs.setDynamicColor(it) } })
                },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            )

            ListItem(
                headlineContent = { Text("Theme") },
                supportingContent = { Text(when (darkTheme) { "light" -> "Light"; "dark" -> "Dark"; else -> "System default" }) },
                trailingContent = { TextButton(onClick = { showThemeDialog = true }) { Text("Change") } },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)

            Text("About", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            ListItem(
                headlineContent = { Text("Flambo") },
                supportingContent = { Text("A calm, expressive voice recorder. Your recordings stay on your device. \nVersion 1.0 • Made with Material 3 Expressive") },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            )
        }
    }

    if (showQualityDialog) {
        AlertDialog(
            onDismissRequest = { showQualityDialog = false },
            title = { Text("Recording quality") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    RecordingQuality.entries.forEach { q ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(q.label, style = MaterialTheme.typography.titleMedium)
                                Text(q.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            RadioButton(selected = q == quality, onClick = {
                                scope.launch { prefs.setQuality(q) }
                                showQualityDialog = false
                            })
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showQualityDialog = false }) { Text("Close") } },
            shape = ShapeLargeIncreased
        )
    }

    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("Theme") },
            text = {
                Column {
                    listOf("system" to "System default", "light" to "Light", "dark" to "Dark").forEach { (value, label) ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(label, modifier = Modifier.weight(1f))
                            RadioButton(selected = darkTheme == value, onClick = {
                                scope.launch { prefs.setDarkTheme(value) }
                                showThemeDialog = false
                            })
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showThemeDialog = false }) { Text("Close") } },
            shape = ShapeLargeIncreased
        )
    }
}
