package com.flambo.recorder.ui.settings

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import com.flambo.recorder.audio.EnhanceStrength
import com.flambo.recorder.data.PreferencesManager
import com.flambo.recorder.domain.RecordingQuality
import com.flambo.recorder.record.AudioSource
import com.flambo.recorder.ui.settings.components.PreferenceSwitchItem
import com.flambo.recorder.ui.settings.components.PreferenceValueItem
import com.flambo.recorder.ui.settings.components.SectionHeader
import com.flambo.recorder.ui.settings.components.SegmentedPreferenceGroup
import com.flambo.recorder.ui.theme.ShapeLargeIncreased
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingSettingsScreen(
    prefs: PreferencesManager,
    scope: CoroutineScope,
    onBack: () -> Unit
) {
    val quality by prefs.qualityFlow.collectAsState(initial = RecordingQuality.HIGH)
    val audioSource by prefs.audioSourceFlow.collectAsState(initial = "mic")
    val noiseReduction by prefs.noiseReductionFlow.collectAsState(initial = true)
    val enhanceStrength by prefs.enhanceStrengthFlow.collectAsState(initial = "balanced")
    val keepOriginal by prefs.keepOriginalFlow.collectAsState(initial = true)
    val reminder by prefs.recordingReminderFlow.collectAsState(initial = true)

    var showQualityDialog by remember { mutableStateOf(false) }
    var showAudioSourceDialog by remember { mutableStateOf(false) }
    var showEnhanceStrengthDialog by remember { mutableStateOf(false) }


    val gestureEnabled by prefs.gestureEnabledFlow.collectAsState(initial = true)
    val scale = remember { Animatable(1f) }
    val corner = remember { Animatable(0f) }
    val alpha = remember { Animatable(1f) }
    val gestureScope = rememberCoroutineScope()
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
                scaleX = scale.value
                scaleY = scale.value
                this.alpha = alpha.value
                shape = RoundedCornerShape(corner.value.dp)
                clip = corner.value > 0f
            }
            .clip(RoundedCornerShape(corner.value.dp))
    ) {
        Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recording & Audio", style = MaterialTheme.typography.titleLarge) },
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
                SectionHeader(title = "Audio Quality")
                SegmentedPreferenceGroup {
                    item {
                        PreferenceValueItem(
                            icon = Icons.Filled.RecordVoiceOver,
                            title = "Quality",
                            value = "${quality.label} • ${quality.description}",
                            onClick = { showQualityDialog = true }
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader(title = "Recording Behavior")
                SegmentedPreferenceGroup {
                    item {
                        PreferenceSwitchItem(
                            icon = Icons.Filled.RecordVoiceOver,
                            title = "Recording reminder",
                            subtitle = "Show a gentle reminder before long recordings",
                            checked = reminder,
                            onCheckedChange = { scope.launch { prefs.setRecordingReminder(it) } }
                        )
                    }
                    item {
                        PreferenceValueItem(
                            icon = if (audioSource == "system") Icons.Filled.MusicNote else Icons.Filled.Mic,
                            title = "Audio source",
                            value = AudioSource.fromPref(audioSource).label,
                            subtitle = if (audioSource == "system") "System sound • Under development" else "Microphone",
                            onClick = { showAudioSourceDialog = true }
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader(title = "Audio Processing")
                SegmentedPreferenceGroup {
                    item {
                        PreferenceSwitchItem(
                            icon = Icons.Filled.Tune,
                            title = "Noise reduction",
                            subtitle = "Live hush + steady levels while recording",
                            checked = noiseReduction,
                            onCheckedChange = { scope.launch { prefs.setNoiseReduction(it) } }
                        )
                    }
                    item {
                        PreferenceValueItem(
                            icon = Icons.Filled.Tune,
                            title = "Enhancement strength",
                            value = EnhanceStrength.fromPref(enhanceStrength).let { "${it.label} • ${it.description}" },
                            onClick = { showEnhanceStrengthDialog = true }
                        )
                    }
                    item {
                        PreferenceSwitchItem(
                            icon = Icons.Filled.Tune,
                            title = "Keep original",
                            subtitle = "Save the cleaned copy next to the original",
                            checked = keepOriginal,
                            onCheckedChange = { scope.launch { prefs.setKeepOriginal(it) } }
                        )
                    }
                }
            }
        }
    }
    }

    if (showQualityDialog) {
        AlertDialog(
            onDismissRequest = { showQualityDialog = false },
            title = { Text("Recording quality", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                TextButton(onClick = { showQualityDialog = false }, shapes = ButtonDefaults.shapes()) { Text("Close") }
            },
            shape = ShapeLargeIncreased
        )
    }

    if (showAudioSourceDialog) {
        AlertDialog(
            onDismissRequest = { showAudioSourceDialog = false },
            title = { Text("Audio source", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "System sound captures music, videos and anything else playing on this phone. Android will show a screen-recording prompt first — Flambo only records sound, never your screen.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    listOf(
                        Triple("mic", "Microphone", "Your voice and the room around you"),
                        Triple("system", "System sound • Under development", "Parked for now — projection capture still has bugs on some phones")
                    ).forEachIndexed { index, (value, label, description) ->
                        val enabled = value == "mic"
                        ToggleButton(
                            checked = audioSource == value,
                            onCheckedChange = {
                                scope.launch { prefs.setAudioSource(value) }
                                showAudioSourceDialog = false
                            },
                            enabled = enabled,
                            shapes = when (index) {
                                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                else -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(if (value == "mic") Icons.Filled.Mic else Icons.Filled.MusicNote, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                            Column(modifier = Modifier.weight(1f).padding(vertical = 4.dp)) {
                                Text(label, style = MaterialTheme.typography.titleMedium)
                                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAudioSourceDialog = false }, shapes = ButtonDefaults.shapes()) { Text("Close") }
            },
            shape = ShapeLargeIncreased
        )
    }

    if (showEnhanceStrengthDialog) {
        AlertDialog(
            onDismissRequest = { showEnhanceStrengthDialog = false },
            title = { Text("Enhancement strength", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    EnhanceStrength.entries.forEachIndexed { index, s ->
                        val selected = s.name.equals(enhanceStrength, ignoreCase = true)
                        ToggleButton(
                            checked = selected,
                            onCheckedChange = {
                                scope.launch { prefs.setEnhanceStrength(s.name.lowercase()) }
                                showEnhanceStrengthDialog = false
                            },
                            shapes = when (index) {
                                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                EnhanceStrength.entries.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(vertical = 4.dp)) {
                                Text(s.label, style = MaterialTheme.typography.titleMedium)
                                Text(s.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showEnhanceStrengthDialog = false }, shapes = ButtonDefaults.shapes()) { Text("Close") }
            },
            shape = ShapeLargeIncreased
        )
    }
}

