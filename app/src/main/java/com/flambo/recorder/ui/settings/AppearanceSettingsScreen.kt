package com.flambo.recorder.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlin.math.max
import androidx.compose.ui.unit.dp
import com.flambo.recorder.data.PreferencesManager
import com.flambo.recorder.ui.components.PlaybackProgressBar
import com.flambo.recorder.ui.components.ProgressBarStyle
import com.flambo.recorder.ui.settings.components.PreferenceSwitchItem
import com.flambo.recorder.ui.settings.components.PreferenceValueItem
import com.flambo.recorder.ui.settings.components.SectionHeader
import com.flambo.recorder.ui.settings.components.SegmentedPreferenceGroup
import com.flambo.recorder.ui.theme.ColorPaletteGenerator
import com.flambo.recorder.ui.theme.ColorSchemeStyle
import com.flambo.recorder.ui.theme.ShapeLargeIncreased
import com.flambo.recorder.ui.theme.ThemeSeeds
import com.flambo.recorder.ui.theme.themeSeedById
import kotlinx.coroutines.CancellationException
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
    val colorSchemeStyle by prefs.colorSchemeFlow.collectAsState(initial = "TONAL_SPOT")
    val gestureEnabled by prefs.gestureEnabledFlow.collectAsState(initial = true)
    val appIcon by prefs.appIconFlow.collectAsState(initial = AppIconManager.ICON_DEFAULT)
    val progressStyle by prefs.progressStyleFlow.collectAsState(initial = ProgressBarStyle.SLIDER)

    var showThemeDialog by remember { mutableStateOf(false) }
    var showLayoutDialog by remember { mutableStateOf(false) }
    var showColorSchemeDialog by remember { mutableStateOf(false) }
    var showAppIconDialog by remember { mutableStateOf(false) }
    var showProgressBarDialog by remember { mutableStateOf(false) }

    val scale = remember { Animatable(1f) }
    val corner = remember { Animatable(0f) }
    val alpha = remember { Animatable(1f) }
    val offsetX = remember { Animatable(0f) }
    val gestureScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val velocityTracker = remember { VelocityTracker() }
    var showSwipeTip by remember { mutableStateOf(false) }
    var prevGestureEnabled by remember { mutableStateOf(gestureEnabled) }
    val tipOffset = remember { Animatable(0f) }
    LaunchedEffect(gestureEnabled) {
        if (gestureEnabled && !prevGestureEnabled) {
            showSwipeTip = true
            tipOffset.snapTo(0f)
            repeat(2) {
                tipOffset.animateTo(with(density) { 32.dp.toPx() }, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow))
                kotlinx.coroutines.delay(250)
                tipOffset.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow))
                kotlinx.coroutines.delay(500)
            }
        } else if (!gestureEnabled) {
            showSwipeTip = false
            tipOffset.snapTo(0f)
        }
        prevGestureEnabled = gestureEnabled
    }
    PredictiveBackHandler(enabled = gestureEnabled) { progress ->
        try {
            progress.collect { event ->
                val p = event.progress
                scale.snapTo((1f - p * 0.05f).coerceIn(0.95f, 1f))
                corner.snapTo(p * 24f)
                alpha.snapTo((1f - p * 0.15f).coerceIn(0.85f, 1f))
            }
            onBack()
        } catch (e: CancellationException) {
            gestureScope.launch {
                scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioLowBouncy))
                corner.animateTo(0f, spring(dampingRatio = Spring.DampingRatioLowBouncy))
                alpha.animateTo(1f, spring(dampingRatio = Spring.DampingRatioLowBouncy))
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationX = offsetX.value
                scaleX = scale.value
                scaleY = scale.value
                this.alpha = alpha.value
                shape = RoundedCornerShape(corner.value.dp)
                clip = corner.value > 0f
            }
            .clip(RoundedCornerShape(corner.value.dp))
            .pointerInput(gestureEnabled) {
                if (!gestureEnabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { velocityTracker.resetTracking() },
                    onDragEnd = {
                        val offset = offsetX.value
                        val velocity = runCatching { velocityTracker.calculateVelocity().x }.getOrDefault(0f)
                        val dismissDistance = with(density) { 100.dp.toPx() }
                        val velocityThreshold = with(density) { 400.dp.toPx() }
                        val shouldDismiss = offset > dismissDistance || velocity > velocityThreshold
                        if (shouldDismiss) {
                            gestureScope.launch { offsetX.animateTo(with(density) { 1080.dp.toPx() }, spring(stiffness = Spring.StiffnessMedium)) }
                            gestureScope.launch { kotlinx.coroutines.delay(80); if (offsetX.value >= with(density) { 1080.dp.toPx() } * 0.5f) onBack() }
                        } else {
                            gestureScope.launch {
                                offsetX.animateTo(0f, spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow))
                                scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioLowBouncy))
                                corner.animateTo(0f, spring(dampingRatio = Spring.DampingRatioLowBouncy))
                                alpha.animateTo(1f, spring(dampingRatio = Spring.DampingRatioLowBouncy))
                            }
                        }
                    },
                    onDragCancel = {
                        gestureScope.launch {
                            offsetX.animateTo(0f, spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow))
                            scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioLowBouncy))
                            corner.animateTo(0f, spring(dampingRatio = Spring.DampingRatioLowBouncy))
                            alpha.animateTo(1f, spring(dampingRatio = Spring.DampingRatioLowBouncy))
                        }
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        velocityTracker.addPosition(change.uptimeMillis, change.position)
                        val newOffset = max(0f, offsetX.value + dragAmount)
                        gestureScope.launch {
                            offsetX.snapTo(newOffset)
                            val progress = (newOffset / with(density) { 1080.dp.toPx() }).coerceIn(0f, 1f)
                            scale.snapTo((1f - progress * 0.05f).coerceIn(0.95f, 1f))
                            corner.snapTo(progress * 24f)
                            alpha.snapTo((1f - progress * 0.15f).coerceIn(0.85f, 1f))
                        }
                    }
                )
            }
    ) {
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
                .verticalScroll(rememberSaveable(saver = ScrollState.Saver) { ScrollState(0) })
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
                    item {
                        PreferenceValueItem(
                            icon = Icons.Filled.Palette,
                            title = "Color Scheme",
                            value = ColorPaletteGenerator.fromString(colorSchemeStyle).name.replace('_', ' ').lowercase().replaceFirstChar { it.titlecase() } + " • ${if (darkTheme == "system") "System" else darkTheme}",
                            subtitle = when (ColorPaletteGenerator.fromString(colorSchemeStyle)) {
                                ColorSchemeStyle.DYNAMIC -> "Material You • Android 12+"
                                ColorSchemeStyle.MONOCHROME -> "Greyscale • AMOLED black"
                                ColorSchemeStyle.VIBRANT -> "High-chroma containers"
                                ColorSchemeStyle.EXPRESSIVE -> "Expressive accents"
                                ColorSchemeStyle.NEUTRAL -> "Muted • Low chroma"
                                ColorSchemeStyle.TONAL_SPOT -> "Seed-tonal • Balanced"
                            },
                            onClick = { showColorSchemeDialog = true }
                        )
                    }
                    item {
                        PreferenceValueItem(
                            icon = Icons.Filled.PlayArrow,
                            title = "Progress bar",
                            value = ProgressBarStyle.label(progressStyle),
                            subtitle = "Playback seek bar style",
                            onClick = { showProgressBarDialog = true }
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
                SectionHeader(title = "App icon")
                SegmentedPreferenceGroup {
                    item {
                        val current = AppIconManager.options().firstOrNull { it.id == appIcon }
                            ?: AppIconManager.options().first()
                        PreferenceValueItem(
                            icon = Icons.Filled.Palette,
                            title = "Launcher icon",
                            value = current.label,
                            subtitle = "Color and style • launcher refreshes in a moment",
                            onClick = { showAppIconDialog = true }
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader(title = "Gestures")
                SegmentedPreferenceGroup {
                    item {
                        ListItem(
                            headlineContent = {
                                androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Swipe to dismiss", style = MaterialTheme.typography.titleMedium)
                                    Surface(
                                        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                                        color = androidx.compose.ui.graphics.Color(0xFFFF9800).copy(alpha = 0.15f),
                                        modifier = Modifier.padding(start = 4.dp)
                                    ) {
                                        Text(
                                            "Under dev • may contain bugs",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = androidx.compose.ui.graphics.Color(0xFFFF9800),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            },
                            supportingContent = {
                                Text(
                                    if (gestureEnabled) "Right-swipe detail card to go back • 100dp / 400dp/s" else "Gesture disabled — use back arrow",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            leadingContent = {
                                Icon(Icons.Filled.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                            },
                            trailingContent = {
                                Switch(
                                    checked = gestureEnabled,
                                    onCheckedChange = { scope.launch { prefs.setGestureEnabled(it) } },
                                    thumbContent = if (gestureEnabled) {
                                        { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(SwitchDefaults.IconSize)) }
                                    } else null
                                )
                            },
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = showSwipeTip,
                enter = fadeIn() + scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)),
                exit = fadeOut()
            ) {
                Surface(
                    shape = ShapeLargeIncreased,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Filled.Lightbulb, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                Text("Try it out", style = MaterialTheme.typography.titleSmall)
                            }
                            TextButton(onClick = { showSwipeTip = false }) { Text("Got it") }
                        }
                        Text("Swipe any card to the right to go back — just like this:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                                .padding(8.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text("← swipe", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier
                                    .fillMaxWidth(0.85f)
                                    .height(48.dp)
                                    .graphicsLayer { translationX = tipOffset.value }
                            ) {
                                Row(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(18.dp))
                                    Text("Playback", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                }
                            }
                        }
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

    if (showAppIconDialog) {
        val context = LocalContext.current
        AlertDialog(
            onDismissRequest = { showAppIconDialog = false },
            title = { Text("Launcher icon", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Pick a color and style. Your launcher refreshes in a moment.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    AppIconManager.options().forEachIndexed { index, option ->
                        ToggleButton(
                            checked = appIcon == option.id,
                            onCheckedChange = {
                                scope.launch {
                                    runCatching {
                                        prefs.setAppIcon(option.id)
                                        AppIconManager.apply(context, option.id)
                                    }
                                }
                                showAppIconDialog = false
                            },
                            shapes = when (index) {
                                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                AppIconManager.options().lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Image(
                                painter = painterResource(id = option.previewRes),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            )
                            Column(modifier = Modifier.weight(1f).padding(start = 8.dp, top = 4.dp, bottom = 4.dp)) {
                                Text(option.label, style = MaterialTheme.typography.titleMedium)
                                Text(option.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAppIconDialog = false }, shapes = ButtonDefaults.shapes()) { Text("Close") }
            },
            shape = ShapeLargeIncreased
        )
    }

    if (showProgressBarDialog) {
        AlertDialog(
            onDismissRequest = { showProgressBarDialog = false },
            title = { Text("Progress bar", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Seek bar style used in Playback. Bars seek on tap.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    listOf(
                        Triple(ProgressBarStyle.SLIDER, "Slider", "Default • draggable thumb"),
                        Triple(ProgressBarStyle.LINEAR, "Linear", "Flat bar • tap to seek"),
                        Triple(ProgressBarStyle.WAVY, "Wavy", "Expressive wave • tap to seek")
                    ).forEachIndexed { index, (value, label, description) ->
                        ToggleButton(
                            checked = progressStyle == value,
                            onCheckedChange = {
                                scope.launch { prefs.setProgressStyle(value) }
                                showProgressBarDialog = false
                            },
                            shapes = when (index) {
                                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                2 -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(start = 8.dp, top = 8.dp, bottom = 8.dp)) {
                                Text(label, style = MaterialTheme.typography.titleMedium)
                                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                PlaybackProgressBar(
                                    progress = 0.4f,
                                    onSeek = {},
                                    enabled = false,
                                    style = value,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showProgressBarDialog = false }, shapes = ButtonDefaults.shapes()) { Text("Close") }
            },
            shape = ShapeLargeIncreased
        )
    }

    if (showColorSchemeDialog) {
        val context = androidx.compose.ui.platform.LocalContext.current
        val isDark = when (darkTheme) {
            "light" -> false
            "dark" -> true
            else -> androidx.compose.foundation.isSystemInDarkTheme()
        }
        AlertDialog(
            onDismissRequest = { showColorSchemeDialog = false },
            title = { Text("Color Scheme", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Expressive palettes • preview shows Primary • Secondary • Tertiary",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ColorSchemeStyle.entries.forEach { style ->
                            val selected = style.name == colorSchemeStyle
                            val seed = themeSeedById(themeSeed)
                            val scheme = ColorPaletteGenerator.scheme(seed, isDark, style, context, dynamicColor)
                            val colors = listOf(scheme.primary, scheme.secondary, scheme.tertiary)
                            Surface(
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                                color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                                modifier = Modifier.fillMaxWidth().clickable {
                                    scope.launch { prefs.setColorScheme(style.name) }
                                    showColorSchemeDialog = false
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
                                        colors.forEach { c ->
                                            Box(
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(CircleShape)
                                                    .background(c)
                                                    .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                                            )
                                        }
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(style.name.replace('_', ' ').lowercase().replaceFirstChar { it.titlecase() }, style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            when (style) {
                                                ColorSchemeStyle.DYNAMIC -> "Material You • Android 12+"
                                                ColorSchemeStyle.MONOCHROME -> "Greyscale • AMOLED"
                                                ColorSchemeStyle.VIBRANT -> "High chroma"
                                                ColorSchemeStyle.EXPRESSIVE -> "Expressive • vivid"
                                                ColorSchemeStyle.NEUTRAL -> "Muted • Low chroma"
                                                ColorSchemeStyle.TONAL_SPOT -> "Tonal Spot • Balanced"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showColorSchemeDialog = false }, shapes = ButtonDefaults.shapes()) { Text("Close") }
            },
            shape = ShapeLargeIncreased
        )
    }
}

