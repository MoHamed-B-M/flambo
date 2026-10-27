package com.pranshulgg.recordmaster.screens

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import com.pranshulgg.recordmaster.R
import com.pranshulgg.recordmaster.RecordMasterApp
import com.pranshulgg.recordmaster.core.audio.EnhanceStrength
import com.pranshulgg.recordmaster.core.data.StorageVolumes
import com.pranshulgg.recordmaster.core.domain.RecordingQuality
import com.pranshulgg.recordmaster.core.playback.PlaybackController
import com.pranshulgg.recordmaster.ui.components.SettingSection
import com.pranshulgg.recordmaster.ui.components.SettingTile
import com.pranshulgg.recordmaster.ui.components.SettingsTileIcon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RecordingSettingsSection(
    app: RecordMasterApp,
    scope: CoroutineScope,
    showMessage: suspend (String) -> Unit
) {
    val quality by app.prefs.qualityFlow.collectAsState(initial = RecordingQuality.HIGH)
    val noiseReduction by app.prefs.noiseReductionFlow.collectAsState(initial = true)
    val keepOriginal by app.prefs.keepOriginalFlow.collectAsState(initial = true)
    val strengthValue by app.prefs.enhanceStrengthFlow.collectAsState(initial = "balanced")
    val prefix by app.prefs.recordingPrefixFlow.collectAsState(initial = "User")
    val context = LocalContext.current

    SettingSection(
        title = "Recording",
        tiles = listOf(
            SettingTile.DialogOptionTile(
                leading = { SettingsTileIcon(R.drawable.graphic_eq_24px) },
                title = "Recording quality",
                description = quality.label,
                options = RecordingQuality.entries.map { it.label },
                selectedOption = quality.label,
                onOptionSelected = { label ->
                    val selected = RecordingQuality.entries.firstOrNull { it.label == label }
                        ?: RecordingQuality.HIGH
                    scope.launch {
                        app.prefs.setQuality(selected)
                        showMessage("Recording quality: ${selected.label}")
                    }
                }
            ),
            SettingTile.SwitchTile(
                leading = { SettingsTileIcon(R.drawable.filter_list_24px) },
                title = "Noise reduction",
                description = "Reduce background noise while recording",
                checked = noiseReduction,
                onCheckedChange = { enabled ->
                    scope.launch {
                        app.prefs.setNoiseReduction(enabled)
                        showMessage(if (enabled) "Noise reduction enabled" else "Noise reduction disabled")
                    }
                }
            ),
            SettingTile.SwitchTile(
                leading = { SettingsTileIcon(R.drawable.brush_24px) },
                title = "Keep original recording",
                description = "Save unprocessed copy alongside enhanced audio",
                checked = keepOriginal,
                onCheckedChange = { enabled ->
                    scope.launch {
                        app.prefs.setKeepOriginal(enabled)
                        showMessage(if (enabled) "Original will be kept" else "Original won't be kept")
                    }
                }
            ),
            SettingTile.DialogOptionTile(
                leading = { SettingsTileIcon(R.drawable.tune_24px) },
                title = "Enhancement strength",
                description = when (strengthValue) {
                    "light" -> "Light"
                    "strong" -> "Strong"
                    else -> "Balanced"
                },
                options = listOf("Light", "Balanced", "Strong"),
                selectedOption = when (strengthValue) {
                    "light" -> "Light"
                    "strong" -> "Strong"
                    else -> "Balanced"
                },
                onOptionSelected = { label ->
                    val value = when (label) {
                        "Light" -> "light"
                        "Strong" -> "strong"
                        else -> "balanced"
                    }
                    scope.launch {
                        app.prefs.setEnhanceStrength(value)
                        showMessage("Enhancement strength: $label")
                    }
                }
            ),
            SettingTile.TextFieldTile(
                leading = { SettingsTileIcon(R.drawable.text_fields_24px) },
                title = "Recording name prefix",
                description = "Default prefix for new recordings",
                value = prefix,
                placeholder = "User",
                onValueChange = { newPrefix ->
                    scope.launch {
                        app.prefs.setRecordingPrefix(newPrefix.ifBlank { "User" })
                    }
                }
            )
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StorageSettingsSection(
    app: RecordMasterApp,
    scope: CoroutineScope,
    showMessage: suspend (String) -> Unit
) {
    val volumes = remember { StorageVolumes(context = LocalContext.current).getVolumes() }
    val currentVolume by app.prefs.storageVolumeFlow.collectAsState(initial = StorageVolumes.Internal.id)

    SettingSection(
        title = "Storage",
        tiles = listOf(
            SettingTile.DialogOptionTile(
                leading = { SettingsTileIcon(R.drawable.folder_open_24px) },
                title = "Save location",
                description = volumes.firstOrNull { it.id == currentVolume }?.label ?: "Internal storage",
                options = volumes.map { it.label },
                selectedOption = volumes.firstOrNull { it.id == currentVolume }?.label ?: "Internal storage",
                onOptionSelected = { label ->
                    val volume = volumes.firstOrNull { it.label == label } ?: volumes.firstOrNull()
                    volume?.let {
                        scope.launch {
                            app.prefs.setStorageVolume(it.id)
                            showMessage("Save location: ${it.label}")
                        }
                    }
                }
            )
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PlaybackSettingsSection(
    app: RecordMasterApp,
    playback: PlaybackController,
    scope: CoroutineScope,
    showMessage: suspend (String) -> Unit
) {
    val speed by app.prefs.playbackSpeedFlow.collectAsState(initial = 1f)
    val label = if (speed == 1f) "1x" else String.format(Locale.US, "%.2fx", speed)

    SettingSection(
        title = "Playback",
        tiles = listOf(
            SettingTile.DialogOptionTile(
                leading = { SettingsTileIcon(R.drawable.speed_24px) },
                title = "Default speed",
                description = label,
                options = listOf("0.5x", "0.75x", "1x", "1.25x", "1.5x", "1.75x", "2x"),
                selectedOption = if (speed == 1f) "1x" else String.format(Locale.US, "%.2fx", speed),
                onOptionSelected = { value ->
                    val parsed = value.removeSuffix("x").toFloatOrNull() ?: 1f
                    scope.launch {
                        playback.setSpeed(parsed)
                        app.prefs.setPlaybackSpeed(parsed)
                        showMessage("Playback speed: $value")
                    }
                }
            )
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SpeechSettingsSection(
    app: RecordMasterApp,
    scope: CoroutineScope,
    showMessage: suspend (String) -> Unit
) {
    val languageTag by app.prefs.sttLanguageFlow.collectAsState(initial = "")
    val whisperLanguages = listOf(
        "" to "Auto-detect",
        "en" to "English",
        "es" to "Spanish",
        "fr" to "French",
        "de" to "German",
        "it" to "Italian",
        "pt" to "Portuguese",
        "ru" to "Russian",
        "ja" to "Japanese",
        "zh" to "Chinese",
        "ko" to "Korean",
        "ar" to "Arabic",
        "hi" to "Hindi",
        "tr" to "Turkish",
        "pl" to "Polish",
        "nl" to "Dutch"
    )
    val selected = whisperLanguages.firstOrNull { it.first == languageTag } ?: whisperLanguages.first()

    SettingSection(
        title = "Speech-to-text",
        tiles = listOf(
            SettingTile.TextTile(
                leading = { SettingsTileIcon(R.drawable.notes_24px) },
                title = "Engine",
                description = "Whisper \u00B7 fully offline (on-device)"
            ),
            SettingTile.DialogOptionTile(
                leading = { SettingsTileIcon(R.drawable.graphic_eq_24px) },
                title = "Transcription language",
                description = selected.second,
                options = whisperLanguages.map { it.second },
                selectedOption = selected.second,
                onOptionSelected = { label ->
                    val lang = whisperLanguages.firstOrNull { it.second == label } ?: whisperLanguages.first()
                    scope.launch {
                        app.prefs.setSttLanguage(lang.first)
                        showMessage(
                            if (lang.first.isEmpty()) "Language: Auto-detect"
                            else "Language: ${lang.second}"
                        )
                    }
                }
            ),
            SettingTile.TextTile(
                leading = { SettingsTileIcon(R.drawable.download_24px) },
                title = "Default model",
                description = "ggml-tiny.en-q8_0.bin (int8, \u00B77 MB)"
            )
        )
    )
}
