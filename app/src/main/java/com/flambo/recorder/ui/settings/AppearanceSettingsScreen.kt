package com.flambo.recorder.ui.settings

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.flambo.recorder.data.PreferencesManager
import com.flambo.recorder.ui.settings.components.PreferenceSwitchItem
import com.flambo.recorder.ui.settings.components.PreferenceValueItem
import com.flambo.recorder.ui.settings.components.SectionHeader
import com.flambo.recorder.ui.settings.components.SegmentedPreferenceGroup
import com.flambo.recorder.ui.theme.ShapeLargeIncreased
import com.flambo.recorder.ui.theme.ThemeSeeds
import com.flambo.recorder.ui.theme.themeSeedById
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceSettingsScreen(
    prefs: PreferencesManager,
    scope: CoroutineScope,
    onBack: () -> Unit
) {
    val dynamicColor by prefs.dynamicColorFlow.collectAsState(initial = true)
    val themeSeed by prefs.themeSeedFlow.collectAsState(initial = "ember")
    val darkTheme by prefs.darkThemeFlow.collectAsState(initial = "system")
    val homeLayout by prefs.homeLayoutFlow.collectAsState(initial = "list")
    val tipsEnabled by prefs.tipsEnabledFlow.collectAsState(initial = true)

    var showThemeDialog by remember { mutableStateOf(false) }
    var showLayoutDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Appearance & Theme", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
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
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader(title = "Theme")
                SegmentedPreferenceGroup {
                    item {
                        PreferenceValueItem(
                            icon = when (darkTheme) {
                                "light" -> Icons.Filled.LightMode
                                "dark" -> Icons.Filled.DarkMode
                                else -> Icons.Filled.SettingsBrightness
                            },
                            title = "Theme",
                            value = run {
                                val mode = when (darkTheme) { "light" -> "Light"; "dark" -> "Dark"; else -> "System" }
                                val color = if (dynamicColor) "Dynamic" else themeSeedById(themeSeed).label
                                "$color • $mode"
                            },
                            onClick = { showThemeDialog = true }
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader(title = "Layout")
                SegmentedPreferenceGroup {
                    item {
                        PreferenceValueItem(
                            icon = if (homeLayout == "grid") Icons.Filled.GridView else Icons.Filled.ViewList,
                            title = "Library layout",
                            value = if (homeLayout == "grid") "Grid • compact tap-to-open cards" else "List • full rows with actions",
                            onClick = { showLayoutDialog = true }
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader(title = "Guidance")
                SegmentedPreferenceGroup {
                    item {
                        PreferenceSwitchItem(
                            icon = Icons.Filled.Lightbulb,
                            title = "Show tips",
                            subtitle = "Short how-tos on the home screen",
                            checked = tipsEnabled,
                            onCheckedChange = {
                                scope.launch {
                                    prefs.setTipsEnabled(it)
                                    if (it) prefs.setTipIndex(0)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("Theme", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Dynamic color", style = MaterialTheme.typography.titleSmall)
                            Text("Match your wallpaper (Android 12+)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = dynamicColor,
                            onCheckedChange = { scope.launch { prefs.setDynamicColor(it) } },
                            thumbContent = if (dynamicColor) {
                                { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(SwitchDefaults.IconSize)) }
                            } else null
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Flambo colors",
                            style = MaterialTheme.typography.titleSmall,
                            color = if (dynamicColor) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                            ThemeSeeds.forEach { seed ->
                                val selected = !dynamicColor && themeSeed == seed.id
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(seed.swatch)
                                        .border(
                                            2.dp,
                                            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                            CircleShape
                                        )
                                        .clickable(enabled = !dynamicColor) {
                                            scope.launch { prefs.setThemeSeed(seed.id) }
                                        }
                                ) {
                                    if (selected) {
                                        Icon(Icons.Filled.Check, contentDescription = "${seed.label} selected", tint = MaterialTheme.colorScheme.surface, modifier = Modifier.size(20.dp))
                                    }
                                }
                            }
                        }
                        if (dynamicColor) {
                            Text("Turn dynamic color off to pick a Flambo color.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Text("Brightness", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
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
                                    }, contentDescription = null, modifier = Modifier.size(18.dp)
                                )
                                Text(label, modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }, shapes = ButtonDefaults.shapes()) { Text("Close") }
            },
            shape = ShapeLargeIncreased
        )
    }

    if (showLayoutDialog) {
        AlertDialog(
            onDismissRequest = { showLayoutDialog = false },
            title = { Text("Library layout", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        Triple("list", "List", "Full rows with play, favorite and actions"),
                        Triple("grid", "Grid", "Compact tap-to-open cards, two columns")
                    ).forEachIndexed { index, (value, label, description) ->
                        ToggleButton(
                            checked = homeLayout == value,
                            onCheckedChange = {
                                scope.launch { prefs.setHomeLayout(value) }
                                showLayoutDialog = false
                            },
                            shapes = when (index) {
                                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                else -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(if (value == "grid") Icons.Filled.GridView else Icons.Filled.ViewList, contentDescription = null, modifier = Modifier.size(18.dp))
                            Column(modifier = Modifier.weight(1f).padding(start = 8.dp, top = 4.dp, bottom = 4.dp)) {
                                Text(label, style = MaterialTheme.typography.titleMedium)
                                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLayoutDialog = false }, shapes = ButtonDefaults.shapes()) { Text("Close") }
            },
            shape = ShapeLargeIncreased
        )
    }
}
