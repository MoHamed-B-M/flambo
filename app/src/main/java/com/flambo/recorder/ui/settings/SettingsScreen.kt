package com.flambo.recorder.ui.settings

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.flambo.recorder.data.PreferencesManager
import com.flambo.recorder.record.AudioSource
import com.flambo.recorder.stt.TranscriptionManager
import com.flambo.recorder.update.UpdateDownloadState
import com.flambo.recorder.ui.settings.components.PreferenceCategoryItemClickable
import com.flambo.recorder.ui.settings.components.SectionHeader
import com.flambo.recorder.ui.settings.components.SegmentedPreferenceGroup
import kotlinx.coroutines.CoroutineScope

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    prefs: PreferencesManager,
    scope: CoroutineScope,
    transcription: TranscriptionManager,
    updateDownload: UpdateDownloadState = UpdateDownloadState(),
    onBack: () -> Unit,
    onRerunOnboarding: () -> Unit = {},
    onRequestSystemCapture: () -> Unit = {},
    onNavigateRecording: () -> Unit = {},
    onNavigateAppearance: () -> Unit = {},
    onNavigateStt: () -> Unit = {},
    onNavigateStorage: () -> Unit = {},
    onNavigateUpdates: () -> Unit = {},
    onNavigateAbout: () -> Unit = {}
) {
    val quality by prefs.qualityFlow.collectAsState(initial = com.flambo.recorder.domain.RecordingQuality.HIGH)
    val audioSource by prefs.audioSourceFlow.collectAsState(initial = "mic")
    val sttEngine by prefs.sttEngineFlow.collectAsState(initial = "vosk")
    val sttLanguage by prefs.sttLanguageFlow.collectAsState(initial = "")
    val dynamicColor by prefs.dynamicColorFlow.collectAsState(initial = true)
    val themeSeed by prefs.themeSeedFlow.collectAsState(initial = "ember")
    val darkTheme by prefs.darkThemeFlow.collectAsState(initial = "system")

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
        // Saveable scroll preserves position across predictive back re-composition
        val scrollState = rememberSaveable(saver = ScrollState.Saver) { ScrollState(0) }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .animateContentSize(spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader(title = "Recording & Audio")
                SegmentedPreferenceGroup {
                    item {
                        PreferenceCategoryItemClickable(
                            icon = Icons.Filled.Mic,
                            title = "Recording & Audio",
                            subtitle = "${quality.label} • ${AudioSource.fromPref(audioSource).label} • ${if (sttEngine == "whisper") "Whisper" else "Vosk"}",
                            onClick = onNavigateRecording
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader(title = "Appearance & Theme")
                SegmentedPreferenceGroup {
                    item {
                        val mode = when (darkTheme) { "light" -> "Light"; "dark" -> "Dark"; else -> "System" }
                        val color = if (dynamicColor) "Dynamic" else com.flambo.recorder.ui.theme.themeSeedById(themeSeed).label
                        PreferenceCategoryItemClickable(
                            icon = Icons.Filled.Palette,
                            title = "Appearance",
                            subtitle = "$color • $mode • Layout",
                            onClick = onNavigateAppearance
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader(title = "Speech-to-Text")
                SegmentedPreferenceGroup {
                    item {
                        val langLabel = if (sttLanguage.isBlank()) "Auto" else sttLanguage
                        PreferenceCategoryItemClickable(
                            icon = Icons.Filled.RecordVoiceOver,
                            title = "Speech-to-Text",
                            subtitle = if (sttEngine == "whisper") "Whisper • $langLabel • 95+ langs" else "Vosk • $langLabel • offline",
                            onClick = onNavigateStt
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader(title = "Storage & Data")
                SegmentedPreferenceGroup {
                    item {
                        PreferenceCategoryItemClickable(
                            icon = Icons.Filled.Folder,
                            title = "Storage & Data",
                            subtitle = "Folders • Naming • Custom folder",
                            onClick = onNavigateStorage
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader(title = "Updates & About")
                SegmentedPreferenceGroup {
                    item {
                        PreferenceCategoryItemClickable(
                            icon = Icons.Filled.Download,
                            title = "Updates",
                            subtitle = "Channel • Auto-check • Install",
                            onClick = onNavigateUpdates
                        )
                    }
                    item {
                        PreferenceCategoryItemClickable(
                            icon = Icons.Filled.Info,
                            title = "About",
                            subtitle = "Tips • Version • Credits • GitHub",
                            onClick = onNavigateAbout
                        )
                    }
                }
            }
        }
    }
}
