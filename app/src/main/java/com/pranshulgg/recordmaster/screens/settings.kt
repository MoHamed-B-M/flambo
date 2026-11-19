package com.pranshulgg.recordmaster.screens

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.pranshulgg.recordmaster.R
import com.pranshulgg.recordmaster.RecordMasterApp
import com.pranshulgg.recordmaster.core.data.StorageVolumes
import com.pranshulgg.recordmaster.core.domain.AnimationSpeed
import com.pranshulgg.recordmaster.core.domain.AutoSaveInterval
import com.pranshulgg.recordmaster.core.domain.RecordingQuality
import com.pranshulgg.recordmaster.core.record.AutomationTriggers
import com.pranshulgg.recordmaster.helpers.PreferencesHelper
import com.pranshulgg.recordmaster.ui.components.SettingSection
import com.pranshulgg.recordmaster.ui.components.SettingTile
import com.pranshulgg.recordmaster.ui.components.SettingsTileIcon
import com.pranshulgg.recordmaster.ui.components.Symbol
import com.pranshulgg.recordmaster.ui.expressive.rememberSystemReducedMotion
import com.pranshulgg.recordmaster.ui.theme.AppThemeMode

/**
 * Settings is an index, not a form: every group of controls is one row that
 * opens its own screen (see [settingsCategories.kt]). Each row keeps showing
 * the group's current value so the top level stays readable without a tap.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsPage(
    navController: NavController,
    context: Context,
    currentTheme: AppThemeMode,
    snackbarHostState: SnackbarHostState,
    onRerunAnnouncement: () -> Unit
) {
    val app = context.applicationContext as RecordMasterApp
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val volumes = remember { StorageVolumes.list(context) }

    val quality by app.prefs.qualityFlow.collectAsState(initial = RecordingQuality.HIGH)
    val noiseReduction by app.prefs.noiseReductionFlow.collectAsState(initial = true)
    val volumeId by app.prefs.recordingsVolumeFlow.collectAsState(initial = StorageVolumes.ID_DEFAULT)
    val folderUri by app.prefs.recordingsFolderUriFlow.collectAsState(initial = "")
    val speed by app.prefs.playbackSpeedFlow.collectAsState(initial = 1f)
    val autoSaveInterval by app.prefs.autoSaveIntervalFlow.collectAsState(
        initial = AutoSaveInterval.Default
    )
    val languageTag by app.prefs.sttLanguageFlow.collectAsState(initial = "")
    val reduceAnimations by app.prefs.reduceAnimationsFlow.collectAsState(initial = false)
    val animationSpeed by app.prefs.animationSpeedFlow.collectAsState(initial = AnimationSpeed.Default)
    val systemReducedMotion = rememberSystemReducedMotion()

    // SharedPreferences has no flow. Reading it without `remember` means the
    // row refreshes when this screen recomposes on the way back from a detail.
    val automationOn = PreferencesHelper.getBool(AutomationTriggers.PREF_KEY) ?: false
    val wallpaperColor = PreferencesHelper.getBool("useDynamicColors") ?: false
    val customColor = PreferencesHelper.getBool("useCustomColor") ?: false

    val colorSource = when {
        wallpaperColor -> "Wallpaper colors"
        customColor -> "Custom color"
        else -> "Default color"
    }
    val volumeLabel = if (folderUri.isNotBlank()) {
        StorageVolumes.folderDisplayName(context, folderUri)
    } else {
        volumes.firstOrNull { it.id == volumeId }?.label ?: "Phone storage (Recorder)"
    }
    val motionLabel = when {
        systemReducedMotion -> "Android reduced motion"
        reduceAnimations -> "Reduced motion"
        else -> "Full motion \u00B7 ${animationSpeed.label}"
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            LargeTopAppBar(
                title = { Text("Settings") },
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
        LazyColumn(
            modifier = Modifier
                .padding(top = innerPadding.calculateTopPadding())
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                top = 4.dp,
                bottom = innerPadding.calculateBottomPadding() + 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                SettingSection(
                    title = "Appearance",
                    tiles = listOf(
                        SettingTile.NavigationTile(
                            leading = { SettingsTileIcon(R.drawable.routine_24px) },
                            title = "App looks",
                            description = "${currentTheme.storageValue} theme \u00B7 $colorSource",
                            onClick = { navController.navigate("SettingsAppLooks") }
                        )
                    )
                )
            }
            item {
                SettingSection(
                    title = "Recording",
                    tiles = listOf(
                        SettingTile.NavigationTile(
                            leading = { SettingsTileIcon(R.drawable.graphic_eq_24px) },
                            title = "Recording",
                            description = "${quality.label} \u00B7 Noise reduction ${if (noiseReduction) "on" else "off"}",
                            onClick = { navController.navigate("SettingsRecording") }
                        ),
                        SettingTile.NavigationTile(
                            leading = { SettingsTileIcon(R.drawable.fiber_manual_record_24px) },
                            title = "Automation",
                            description = if (automationOn) "On \u00B7 Shortcuts allowed" else "Off \u00B7 Shortcuts blocked",
                            onClick = { navController.navigate("SettingsAutomation") }
                        ),
                        SettingTile.NavigationTile(
                            leading = { SettingsTileIcon(R.drawable.storage_24px) },
                            title = "Storage",
                            description = volumeLabel,
                            onClick = { navController.navigate("SettingsStorage") }
                        )
                    )
                )
            }
            item {
                SettingSection(
                    title = "Reliability",
                    tiles = listOf(
                        SettingTile.NavigationTile(
                            leading = { SettingsTileIcon(R.drawable.privacy_tip_24px) },
                            title = "Auto-backup",
                            description = "Crash recovery \u00B7 ${autoSaveInterval.label}",
                            onClick = { navController.navigate("SettingsReliability") }
                        )
                    )
                )
            }
            item {
                SettingSection(
                    title = "Playback & speech",
                    tiles = listOf(
                        SettingTile.NavigationTile(
                            leading = { SettingsTileIcon(R.drawable.play_arrow_24px) },
                            title = "Playback",
                            description = "Default speed ${playbackSpeedLabel(speed)}",
                            onClick = { navController.navigate("SettingsPlayback") }
                        ),
                        SettingTile.NavigationTile(
                            leading = { SettingsTileIcon(R.drawable.notes_24px) },
                            title = "Speech-to-text",
                            description = "Whisper \u00B7 ${whisperLanguageLabel(languageTag)}",
                            onClick = { navController.navigate("SettingsSpeech") }
                        )
                    )
                )
            }
            item {
                SettingSection(
                    title = "Accessibility",
                    tiles = listOf(
                        SettingTile.NavigationTile(
                            leading = { SettingsTileIcon(R.drawable.accessibility_new_24px) },
                            title = "Motion & animation",
                            description = motionLabel,
                            onClick = { navController.navigate("SettingsAccessibility") }
                        )
                    )
                )
            }
            item {
                SettingSection(
                    title = "Additional",
                    tiles = listOf(
                        SettingTile.NavigationTile(
                            leading = { SettingsTileIcon(R.drawable.info_24px) },
                            title = "About app",
                            description = "Terms, Version, License, and More",
                            onClick = { navController.navigate("OpenAboutScreen") }
                        )
                    )
                )
            }
            item {
                SettingSection(
                    title = "Debug",
                    tiles = listOf(
                        SettingTile.ActionTile(
                            leading = { SettingsTileIcon(R.drawable.bug_report_24px) },
                            title = "Rerun 2.0 Announcement",
                            description = "Reset to 1.7.4 and show the welcome now",
                            onClick = onRerunAnnouncement
                        )
                    )
                )
            }
        }
    }
}
