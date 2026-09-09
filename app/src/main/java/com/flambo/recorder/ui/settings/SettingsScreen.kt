package com.flambo.recorder.ui.settings

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
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
                title = { Text("Settings", style = MaterialTheme.typography.titleLarge) },
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
                .padding(horizontal = 16.dp)
                .animateContentSize(spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("Recording", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))

            // Quality as expressive tonal surface + leading icon + bouncy
            Surface(
                shape = ShapeLargeIncreased,
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                ListItem(
                    headlineContent = { Text("Quality") },
                    supportingContent = { Text("${quality.label} • ${quality.description}") },
                    leadingContent = { Icon(Icons.Filled.RecordVoiceOver, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    trailingContent = {
                        TextButton(
                            onClick = { showQualityDialog = true },
                            shapes = ButtonDefaults.shapes()
                        ) { Text("Change") }
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                )
            }

            Surface(
                shape = ShapeLargeIncreased,
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                ListItem(
                    headlineContent = { Text("Recording reminder") },
                    supportingContent = { Text("Show a gentle reminder before long recordings") },
                    trailingContent = {
                        Switch(
                            checked = reminder,
                            onCheckedChange = { scope.launch { prefs.setRecordingReminder(it) } },
                            thumbContent = if (reminder) {
                                { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(SwitchDefaults.IconSize)) }
                            } else null
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)

            Text("Appearance", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)

            Surface(
                shape = ShapeLargeIncreased,
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                ListItem(
                    headlineContent = { Text("Dynamic color") },
                    supportingContent = { Text("Use Material You colors from your wallpaper (Android 12+)") },
                    leadingContent = { Icon(Icons.Filled.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary) },
                    trailingContent = {
                        Switch(
                            checked = dynamicColor,
                            onCheckedChange = { scope.launch { prefs.setDynamicColor(it) } },
                            thumbContent = if (dynamicColor) {
                                { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(SwitchDefaults.IconSize)) }
                            } else null
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                )
            }

            Surface(
                shape = ShapeLargeIncreased,
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                ListItem(
                    headlineContent = { Text("Theme") },
                    supportingContent = { Text(when (darkTheme) { "light" -> "Light"; "dark" -> "Dark"; else -> "System default" }) },
                    leadingContent = {
                        Icon(
                            when (darkTheme) {
                                "light" -> Icons.Filled.LightMode
                                "dark" -> Icons.Filled.DarkMode
                                else -> Icons.Filled.SettingsBrightness
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingContent = {
                        TextButton(
                            onClick = { showThemeDialog = true },
                            shapes = ButtonDefaults.shapes()
                        ) { Text("Change") }
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)

            Text("About", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Surface(
                shape = ShapeLargeIncreased,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                ListItem(
                    headlineContent = { Text("Flambo") },
                    supportingContent = { Text("A calm, expressive voice recorder. Your recordings stay on your device.\nVersion 1.0 • Built with Material 3 Expressive (1.5.0-alpha26) + bouncy motion") },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                )
            }
        }
    }

    if (showQualityDialog) {
        AlertDialog(
            onDismissRequest = { showQualityDialog = false },
            title = { Text("Recording quality", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Expressive ToggleButton group for quality
                    RecordingQuality.entries.forEachIndexed { index, q ->
                        val selected = q == quality
                        ToggleButton(
                            checked = selected,
                            onCheckedChange = {
                                scope.launch { prefs.setQuality(q) }
                                showQualityDialog = false
                            },
                            shapes = when (index) {
                                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                RecordingQuality.entries.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(vertical = 4.dp)) {
                                Text(q.label, style = MaterialTheme.typography.titleMedium)
                                Text(q.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showQualityDialog = false },
                    shapes = ButtonDefaults.shapes()
                ) { Text("Close") }
            },
            shape = ShapeLargeIncreased
        )
    }

    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("Theme", style = MaterialTheme.typography.titleLarge) },
            text = {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val options = listOf("system" to "System", "light" to "Light", "dark" to "Dark")
                    options.forEachIndexed { index, (value, label) ->
                        ToggleButton(
                            checked = darkTheme == value,
                            onCheckedChange = {
                                scope.launch { prefs.setDarkTheme(value) }
                                showThemeDialog = false
                            },
                            shapes = when (index) {
                                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                when (value) {
                                    "light" -> Icons.Filled.LightMode
                                    "dark" -> Icons.Filled.DarkMode
                                    else -> Icons.Filled.SettingsBrightness
                                },
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(label, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showThemeDialog = false },
                    shapes = ButtonDefaults.shapes()
                ) { Text("Close") }
            },
            shape = ShapeLargeIncreased
        )
    }
}
