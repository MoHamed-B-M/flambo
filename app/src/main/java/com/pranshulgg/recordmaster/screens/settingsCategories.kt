package com.pranshulgg.recordmaster.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.github.skydoves.colorpicker.compose.ColorPickerController
import com.pranshulgg.recordmaster.R
import com.pranshulgg.recordmaster.RecordMasterApp
import com.pranshulgg.recordmaster.core.data.CustomFontStore
import com.pranshulgg.recordmaster.core.domain.AnimationSpeed
import com.pranshulgg.recordmaster.core.domain.AppFont
import com.pranshulgg.recordmaster.helpers.PreferencesHelper
import com.pranshulgg.recordmaster.ui.components.ColorPickerSheetTheme
import com.pranshulgg.recordmaster.ui.components.SettingSection
import com.pranshulgg.recordmaster.ui.components.SettingTile
import com.pranshulgg.recordmaster.ui.components.SettingsTileIcon
import com.pranshulgg.recordmaster.ui.components.Symbol
import com.pranshulgg.recordmaster.ui.expressive.rememberSystemReducedMotion
import com.pranshulgg.recordmaster.ui.theme.AppThemeMode
import com.pranshulgg.recordmaster.ui.theme.AppearancePreferences
import com.pranshulgg.recordmaster.ui.theme.fontFamilyFor
import com.pranshulgg.recordmaster.ui.theme.parseSeedColor
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

private const val DEFAULT_SEED = "0xff0000FF"

private fun seedToColor(seed: String): Color = parseSeedColor(seed)

private fun snackbarMessenger(state: SnackbarHostState): suspend (String) -> Unit = { message ->
    state.currentSnackbarData?.dismiss()
    state.showSnackbar(message)
}

/**
 * Shared frame for the screens Settings opens into: same large top bar and
 * back affordance as the index, so a group reads as a child of Settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsDetailPage(
    title: String,
    navController: NavController,
    snackbarHostState: SnackbarHostState,
    content: @Composable () -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            LargeTopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(
                        onClick = { navController.popBackStack() }
                    ) {
                        Symbol(
                            R.drawable.arrow_back_24px,
                            desc = "Back",
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(top = innerPadding.calculateTopPadding())
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState())
                .padding(top = 4.dp, bottom = innerPadding.calculateBottomPadding() + 16.dp)
        ) {
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppLooksSettingsPage(
    navController: NavController,
    currentTheme: AppThemeMode,
    snackbarHostState: SnackbarHostState,
    onThemeChanged: (AppThemeMode) -> Unit,
    onSeedChanged: (String) -> Unit,
    onDynamicColorChanged: (Boolean) -> Unit,
    onExpressiveColorChanged: (Boolean) -> Unit,
    onBlackAndWhiteChanged: (Boolean) -> Unit,
    onAmoledModeChanged: (Boolean) -> Unit,
    currentFont: AppFont,
    customFontName: String?,
    onFontSelected: (AppFont) -> Unit,
    onCustomFontSelected: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val app = context.applicationContext as RecordMasterApp
    val customFontStore = remember { CustomFontStore(app) }
    val fontPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                customFontStore.importFont(uri).fold(
                    onSuccess = { name ->
                        onCustomFontSelected(name)
                        snackbarMessenger(snackbarHostState)("Using $name")
                    },
                    onFailure = {
                        snackbarMessenger(snackbarHostState)("Could not import that font file")
                    }
                )
            }
        }
    }
    val isSysDark = isSystemInDarkTheme()
    val darkThemeActive = currentTheme.isDark(isSysDark)
    val activeFontName = if (currentFont == AppFont.CUSTOM) {
        customFontName ?: AppFont.CUSTOM.displayName
    } else {
        currentFont.displayName
    }

    // Every option is rendered in its own face, so the rounded style can be
    // judged in the picker before it is applied. Families are built once here.
    val fontPreviewFamilies = remember {
        AppFont.available.associate { it.displayName to fontFamilyFor(it) }
    }
    val fontPreviewBaseStyle = MaterialTheme.typography.bodyLarge
    val fontOptionStyle = { name: String ->
        fontPreviewBaseStyle.copy(
            fontFamily = fontPreviewFamilies[name] ?: FontFamily.Default
        )
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val controller = remember { ColorPickerController() }

    var showColorSheet by remember { mutableStateOf(false) }
    var seedColor by remember {
        mutableStateOf(PreferencesHelper.getString("seedColor") ?: DEFAULT_SEED)
    }
    val initialColorInt = remember(seedColor) { seedToColor(seedColor) }

    var useCustomColor by remember {
        mutableStateOf(PreferencesHelper.getBool("useCustomColor") ?: false)
    }
    var useDynamicColor by remember {
        mutableStateOf(PreferencesHelper.getBool("useDynamicColors") ?: false)
    }
    var useExpressiveColor by remember {
        mutableStateOf(PreferencesHelper.getBool("useExpressiveColor") ?: true)
    }
    var useBlackAndWhite by remember {
        mutableStateOf(AppearancePreferences.blackAndWhite)
    }
    var useAmoledMode by remember {
        mutableStateOf(AppearancePreferences.amoledMode)
    }

    fun hideColorSheet() {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            if (!sheetState.isVisible) {
                showColorSheet = false
            }
        }
    }

    // The seed colour doubles as the "open the picker" affordance once the
    // custom palette is on, so the row shows what the theme is built from.
    @Composable
    fun SeedSwatch() {
        Surface(
            shape = RoundedCornerShape(50.dp),
            border = BorderStroke(width = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
        ) {
            Box(
                modifier = Modifier
                    .width(24.dp)
                    .height(36.dp)
                    .background(seedToColor(seedColor))
                    .clickable(enabled = !useBlackAndWhite) {
                        showColorSheet = true
                    }
            )
        }
    }

    SettingsDetailPage(
        title = "App looks",
        navController = navController,
        snackbarHostState = snackbarHostState
    ) {
        SettingSection(
            tiles = listOf(
                SettingTile.ThemeModeTile(
                    leading = { SettingsTileIcon(R.drawable.routine_24px) },
                    title = "App theme",
                    current = currentTheme,
                    onSelect = { mode ->
                        AppThemeMode.persist(mode, isSysDark)
                        onThemeChanged(mode)
                    }
                ),
                SettingTile.DialogOptionTile(
                    leading = { SettingsTileIcon(R.drawable.label_24px) },
                    title = "App font",
                    description = if (currentFont == AppFont.CUSTOM) {
                        "Using $activeFontName"
                    } else {
                        null
                    },
                    options = AppFont.available.map { it.displayName },
                    selectedOption = currentFont.takeUnless { it == AppFont.CUSTOM }?.displayName,
                    onOptionSelected = { name ->
                        AppFont.available.firstOrNull { it.displayName == name }
                            ?.let(onFontSelected)
                    },
                    optionStyle = fontOptionStyle,
                    dialogTitle = "App font"
                ),
                SettingTile.ActionTile(
                    leading = { SettingsTileIcon(R.drawable.drive_file_move_24px) },
                    title = "Custom font",
                    description = when {
                        currentFont == AppFont.CUSTOM -> "$activeFontName · Tap to replace"
                        customFontName != null -> "$customFontName imported · Tap to use it"
                        else -> "Import a TTF or OTF file"
                    },
                    onClick = { fontPicker.launch(arrayOf("*/*")) }
                ),
                SettingTile.SwitchTile(
                    leading = { SettingsTileIcon(R.drawable.contrast_24px) },
                    title = "Black and white",
                    description = if (useBlackAndWhite) {
                        "On \u00B7 Matches the app icon"
                    } else {
                        "Pure black and white palette"
                    },
                    checked = useBlackAndWhite,
                    onCheckedChange = { checked ->
                        AppearancePreferences.blackAndWhite = checked
                        useBlackAndWhite = checked
                        onBlackAndWhiteChanged(checked)
                    }
                ),
                SettingTile.SwitchTile(
                    leading = { SettingsTileIcon(R.drawable.dark_mode_24px) },
                    title = "AMOLED mode",
                    description = if (darkThemeActive) {
                        "Pure black surfaces in dark theme"
                    } else {
                        "Applies when dark theme is active"
                    },
                    checked = useAmoledMode,
                    onCheckedChange = { checked ->
                        AppearancePreferences.amoledMode = checked
                        useAmoledMode = checked
                        onAmoledModeChanged(checked)
                    }
                )
            )
        )
        Spacer(Modifier.height(10.dp))
        SettingSection(
            title = "Color palette",
            tiles = listOf(
                SettingTile.SwitchTile(
                    leading = {
                        if (useCustomColor) SeedSwatch() else SettingsTileIcon(
                            R.drawable.colorize_24px
                        )
                    },
                    title = "Use custom color",
                    description = if (useBlackAndWhite) {
                        "Unavailable in black and white"
                    } else {
                        "Select a seed color to generate the theme"
                    },
                    checked = useCustomColor,
                    enabled = !useDynamicColor && !useBlackAndWhite,
                    onCheckedChange = { checked ->
                        PreferencesHelper.setBool("useCustomColor", checked)
                        useCustomColor = checked
                        if (!checked) {
                            PreferencesHelper.setString("seedColor", DEFAULT_SEED)
                            seedColor = DEFAULT_SEED
                            onSeedChanged(DEFAULT_SEED)
                        }
                    }
                ),
                SettingTile.SwitchTile(
                    leading = { SettingsTileIcon(R.drawable.image_24px) },
                    title = "Dynamic colors",
                    description = if (useBlackAndWhite) {
                        "Unavailable in black and white"
                    } else {
                        "Use wallpaper colors"
                    },
                    checked = useDynamicColor,
                    enabled = !useCustomColor && !useBlackAndWhite,
                    onCheckedChange = { checked ->
                        PreferencesHelper.setBool("useDynamicColors", checked)
                        PreferencesHelper.setBool("useCustomColor", !checked)
                        onDynamicColorChanged(checked)
                        if (useCustomColor) {
                            useCustomColor = !checked
                        }
                        useDynamicColor = checked
                    }
                )
            )
        )
    }

    if (showColorSheet) {
        ColorPickerSheetTheme(
            sheetState = sheetState,
            initialColorInt = initialColorInt,
            controller = controller,
            onExpressiveColorChanged = { checked ->
                PreferencesHelper.setBool("useExpressiveColor", checked)
                useExpressiveColor = checked
                onExpressiveColorChanged(checked)
            },
            useExpressiveColor = useExpressiveColor,
            onSeedChanged = { color ->
                seedColor = color
                PreferencesHelper.setString("seedColor", color)
                onSeedChanged(color)
            },
            hideColorSheet = { hideColorSheet() }
        )
    }
}

@Composable
fun AccessibilitySettingsPage(
    navController: NavController,
    snackbarHostState: SnackbarHostState
) {
    val app = LocalContext.current.applicationContext as RecordMasterApp
    val scope = rememberCoroutineScope()
    val showMessage = snackbarMessenger(snackbarHostState)

    val reduceAnimations by app.prefs.reduceAnimationsFlow.collectAsState(initial = false)
    val animationSpeed by app.prefs.animationSpeedFlow.collectAsState(initial = AnimationSpeed.Default)
    val systemReducedMotion = rememberSystemReducedMotion()
    // Either switch turns motion off; the speed choice only matters when it is on.
    val motionReduced = reduceAnimations || systemReducedMotion

    SettingsDetailPage("Accessibility", navController, snackbarHostState) {
        MotionPreview(
            reduced = motionReduced,
            speed = animationSpeed.multiplier,
            speedLabel = animationSpeed.label,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        Spacer(Modifier.height(10.dp))
        SettingSection(
            title = "Motion",
            tiles = listOf(
                SettingTile.SwitchTile(
                    leading = { SettingsTileIcon(R.drawable.animation_24px) },
                    title = "Reduce app animations",
                    description = if (systemReducedMotion) {
                        "Android is already removing animations"
                    } else {
                        "Skip transitions and animated effects in Flambo"
                    },
                    checked = reduceAnimations,
                    onCheckedChange = { checked ->
                        scope.launch {
                            app.prefs.setReduceAnimations(checked)
                            showMessage(
                                if (checked) "Animations reduced"
                                else "Animations restored"
                            )
                        }
                    }
                ),
                SettingTile.DialogOptionTile(
                    leading = { SettingsTileIcon(R.drawable.speed_24px) },
                    title = "Animation speed",
                    description = if (motionReduced) {
                        "${animationSpeed.label} \u00B7 Paused while motion is reduced"
                    } else {
                        animationSpeed.label
                    },
                    options = AnimationSpeed.entries.map { it.label },
                    selectedOption = animationSpeed.label,
                    enabled = !motionReduced,
                    onOptionSelected = { label ->
                        val speed = AnimationSpeed.entries.firstOrNull { it.label == label }
                            ?: AnimationSpeed.Default
                        scope.launch {
                            app.prefs.setAnimationSpeed(speed)
                            showMessage("Animation speed: ${speed.label}")
                        }
                    }
                )
            )
        )
    }
}

/**
 * The one authored moment on this page: a waveform that sweeps at the speed
 * the app is currently using, so the setting can be judged rather than read.
 */
@Composable
private fun MotionPreview(
    reduced: Boolean,
    speed: Float,
    speedLabel: String,
    modifier: Modifier = Modifier
) {
    var runId by remember { mutableIntStateOf(0) }
    val progress = remember { Animatable(0f) }
    val barCount = 23

    LaunchedEffect(runId, reduced, speed) {
        if (reduced) {
            progress.snapTo(1f)
            return@LaunchedEffect
        }
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = (2_000 / speed).roundToInt().coerceAtLeast(200),
                easing = LinearEasing
            )
        )
    }

    Surface(
        onClick = { runId++ },
        modifier = modifier.fillMaxWidth(),
        enabled = !reduced,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Motion preview",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = if (reduced) "Reduced" else speedLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f)
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(barCount) { index ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(64.dp * waveHeight(index, barCount, progress.value))
                            .background(
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                    .copy(alpha = 0.85f),
                                shape = RoundedCornerShape(50)
                            )
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = if (reduced) "Motion is turned off" else "Tap to replay",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.65f)
            )
        }
    }
}

/** A travelling sine wave inside a soft envelope, 0..1 per bar. */
private fun waveHeight(index: Int, count: Int, progress: Float): Float {
    val center = (count - 1) / 2f
    val envelope = 1f - (abs(index - center) / (center + 1f)) * 0.55f
    val phase = progress * (Math.PI * 2).toFloat() * 1.5f - index * 0.55f
    val wave = 0.35f + 0.65f * abs(sin(phase))
    return (wave * envelope).coerceIn(0.12f, 1f)
}

@Composable
fun RecordingSettingsPage(
    navController: NavController,
    snackbarHostState: SnackbarHostState
) {
    val app = LocalContext.current.applicationContext as RecordMasterApp
    val scope = rememberCoroutineScope()
    val showMessage = snackbarMessenger(snackbarHostState)

    SettingsDetailPage("Recording", navController, snackbarHostState) {
        RecordingSettingsSection(app = app, scope = scope, showMessage = showMessage)
    }
}

@Composable
fun ReliabilitySettingsPage(
    navController: NavController,
    snackbarHostState: SnackbarHostState
) {
    val app = LocalContext.current.applicationContext as RecordMasterApp
    val scope = rememberCoroutineScope()
    val showMessage = snackbarMessenger(snackbarHostState)

    SettingsDetailPage("Reliability", navController, snackbarHostState) {
        ReliabilitySettingsSection(app = app, scope = scope, showMessage = showMessage)
    }
}

@Composable
fun AutomationSettingsPage(
    navController: NavController,
    snackbarHostState: SnackbarHostState
) {
    val app = LocalContext.current.applicationContext as RecordMasterApp
    val scope = rememberCoroutineScope()
    val showMessage = snackbarMessenger(snackbarHostState)

    SettingsDetailPage("Automation", navController, snackbarHostState) {
        AutomationSettingsSection(app = app, scope = scope, showMessage = showMessage)
    }
}

@Composable
fun StorageSettingsPage(
    navController: NavController,
    snackbarHostState: SnackbarHostState
) {
    val app = LocalContext.current.applicationContext as RecordMasterApp
    val scope = rememberCoroutineScope()
    val showMessage = snackbarMessenger(snackbarHostState)

    SettingsDetailPage("Storage", navController, snackbarHostState) {
        StorageSettingsSection(app = app, scope = scope, showMessage = showMessage)
    }
}

@Composable
fun PlaybackSettingsPage(
    navController: NavController,
    snackbarHostState: SnackbarHostState
) {
    val app = LocalContext.current.applicationContext as RecordMasterApp
    val scope = rememberCoroutineScope()
    val showMessage = snackbarMessenger(snackbarHostState)

    SettingsDetailPage("Playback", navController, snackbarHostState) {
        PlaybackSettingsSection(
            app = app,
            playback = app.playback,
            scope = scope,
            showMessage = showMessage
        )
    }
}

@Composable
fun SpeechSettingsPage(
    navController: NavController,
    snackbarHostState: SnackbarHostState
) {
    val app = LocalContext.current.applicationContext as RecordMasterApp
    val scope = rememberCoroutineScope()
    val showMessage = snackbarMessenger(snackbarHostState)

    SettingsDetailPage("Speech-to-text", navController, snackbarHostState) {
        SpeechSettingsSection(
            app = app,
            scope = scope,
            showMessage = showMessage,
            onOpenModels = { navController.navigate("OpenSpeechModels") }
        )
    }
}
