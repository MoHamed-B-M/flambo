package com.pranshulgg.recordmaster.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.github.skydoves.colorpicker.compose.ColorPickerController
import com.pranshulgg.recordmaster.R
import com.pranshulgg.recordmaster.RecordMasterApp
import com.pranshulgg.recordmaster.helpers.PreferencesHelper
import com.pranshulgg.recordmaster.ui.components.ColorPickerSheetTheme
import com.pranshulgg.recordmaster.ui.components.SettingSection
import com.pranshulgg.recordmaster.ui.components.SettingTile
import com.pranshulgg.recordmaster.ui.components.SettingsTileIcon
import com.pranshulgg.recordmaster.ui.components.Symbol
import com.pranshulgg.recordmaster.ui.theme.AppThemeMode
import com.pranshulgg.recordmaster.ui.theme.parseSeedColor
import kotlinx.coroutines.launch

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
    onExpressiveColorChanged: (Boolean) -> Unit
) {
    val scope = rememberCoroutineScope()
    val isSysDark = isSystemInDarkTheme()
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
                    .clickable {
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
                SettingTile.SwitchTile(
                    leading = {
                        if (useCustomColor) SeedSwatch() else SettingsTileIcon(
                            R.drawable.colorize_24px
                        )
                    },
                    title = "Use custom color",
                    description = "Select a seed color to generate the theme",
                    checked = useCustomColor,
                    enabled = !useDynamicColor,
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
                    description = "Use wallpaper colors",
                    checked = useDynamicColor,
                    enabled = !useCustomColor,
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
