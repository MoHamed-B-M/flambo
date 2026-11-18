package com.pranshulgg.recordmaster.screens

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.pranshulgg.recordmaster.R
import com.pranshulgg.recordmaster.RecordMasterApp
import com.pranshulgg.recordmaster.core.audio.EnhanceStrength
import com.pranshulgg.recordmaster.core.data.PreferencesManager
import com.pranshulgg.recordmaster.core.data.StorageVolumes
import com.pranshulgg.recordmaster.core.domain.RecordingQuality
import com.pranshulgg.recordmaster.core.playback.PlaybackController
import com.pranshulgg.recordmaster.core.record.AutomationTriggers
import com.pranshulgg.recordmaster.core.stt.SpeechModelCatalog
import com.pranshulgg.recordmaster.helpers.PreferencesHelper
import com.pranshulgg.recordmaster.ui.components.SettingSection
import com.pranshulgg.recordmaster.ui.components.SettingTile
import com.pranshulgg.recordmaster.ui.components.SettingsTileIcon
import com.pranshulgg.recordmaster.utils.formatBytes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.Locale

/** Language choices shared by the Settings summary row and the picker dialog. */
val WhisperLanguages: List<Pair<String, String>> = listOf(
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

fun whisperLanguageLabel(tag: String): String =
    WhisperLanguages.firstOrNull { it.first == tag }?.second ?: "Auto-detect"

fun playbackSpeedLabel(speed: Float): String =
    if (speed == 1f) "1x" else String.format(Locale.US, "%.2fx", speed)

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
    val prefix by app.prefs.recordingPrefixFlow.collectAsState(initial = PreferencesManager.DEFAULT_PREFIX)
    val namePattern by app.prefs.recordingNamePatternFlow.collectAsState(initial = PreferencesManager.DEFAULT_NAME_PATTERN)
    val context = LocalContext.current

    SettingSection(
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
                leading = { SettingsTileIcon(R.drawable.graphic_eq_24px) },
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
                leading = { SettingsTileIcon(R.drawable.graphic_eq_24px) },
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
                leading = { SettingsTileIcon(R.drawable.graphic_eq_24px) },
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
            SettingTile.DialogTextFieldTile(
                leading = { SettingsTileIcon(R.drawable.notes_24px) },
                title = "Recording name prefix",
                description = "Used by {prefix} in the file naming pattern",
                placeholderTextField = PreferencesManager.DEFAULT_PREFIX,
                placeholder = prefix.ifBlank { PreferencesManager.DEFAULT_PREFIX },
                initialText = prefix,
                onTextSubmitted = { newPrefix ->
                    scope.launch {
                        app.prefs.setRecordingPrefix(newPrefix.ifBlank { PreferencesManager.DEFAULT_PREFIX })
                    }
                }
            ),
            SettingTile.DialogTextFieldTile(
                leading = { SettingsTileIcon(R.drawable.notes_24px) },
                title = "File naming pattern",
                description = namePattern.ifBlank { PreferencesManager.DEFAULT_NAME_PATTERN },
                placeholderTextField = PreferencesManager.DEFAULT_NAME_PATTERN,
                placeholder = PreferencesManager.DEFAULT_NAME_PATTERN,
                initialText = namePattern,
                dialogSupportingText = "Use variables to name new recordings. Available: {date}, {time}, {datetime}, {year}, {month}, {day}, {weekday}, {timestamp}, {prefix}, {counter}, {custom_name}. Example: {date}_{time}_{custom_name}",
                onTextSubmitted = { newPattern ->
                    scope.launch {
                        app.prefs.setRecordingNamePattern(
                            newPattern.ifBlank { PreferencesManager.DEFAULT_NAME_PATTERN }
                        )
                        showMessage("File naming pattern updated")
                    }
                }
            )
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AutomationSettingsSection(
    app: RecordMasterApp,
    scope: CoroutineScope,
    showMessage: suspend (String) -> Unit
) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(app.automationTriggersEnabled) }

    SettingSection(
        tiles = listOf(
            SettingTile.SwitchTile(
                leading = { SettingsTileIcon(R.drawable.fiber_manual_record_24px) },
                title = "Allow automation triggers",
                description = "Let Key Mapper, Tasker and launcher shortcuts start and stop recordings",
                checked = enabled,
                onCheckedChange = { checked ->
                    enabled = checked
                    PreferencesHelper.setBool(AutomationTriggers.PREF_KEY, checked)
                    AutomationTriggers.setEnabled(context, checked)
                    scope.launch {
                        showMessage(
                            if (checked) "Automation triggers enabled"
                            else "Automation triggers disabled"
                        )
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
    val context = LocalContext.current
    val volumes = remember { StorageVolumes.list(context) }
    val currentVolume by app.prefs.recordingsVolumeFlow.collectAsState(initial = StorageVolumes.ID_DEFAULT)

    SettingSection(
        tiles = listOf(
            SettingTile.DialogOptionTile(
                leading = { SettingsTileIcon(R.drawable.storage_24px) },
                title = "Save location",
                description = volumes.firstOrNull { it.id == currentVolume }?.label ?: "Phone storage",
                options = volumes.map { it.label },
                selectedOption = volumes.firstOrNull { it.id == currentVolume }?.label ?: "Phone storage",
                onOptionSelected = { label ->
                    val volume = volumes.firstOrNull { it.label == label } ?: volumes.firstOrNull()
                    volume?.let {
                        scope.launch {
                            app.prefs.setRecordingsVolume(it.id)
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
    val label = playbackSpeedLabel(speed)

    SettingSection(
        tiles = listOf(
            SettingTile.DialogOptionTile(
                leading = { SettingsTileIcon(R.drawable.play_arrow_24px) },
                title = "Default speed",
                description = label,
                options = listOf("0.5x", "0.75x", "1x", "1.25x", "1.5x", "1.75x", "2x"),
                selectedOption = playbackSpeedLabel(speed),
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
    showMessage: suspend (String) -> Unit,
    onOpenModels: () -> Unit
) {
    val languageTag by app.prefs.sttLanguageFlow.collectAsState(initial = "")
    val selectedModel by app.speechModels.selectedModel.collectAsState(initial = null)
    val modelDescription = run {
        val model = selectedModel ?: SpeechModelCatalog.DEFAULT
        buildString {
            append(model.displayName)
            append(" \u00B7 ")
            append(formatBytes(model.sizeBytes))
            if (!app.transcription.modelManager.isInstalled(model)) append(" \u00B7 not downloaded")
        }
    }
    val whisperLanguages = WhisperLanguages
    val selected = whisperLanguages.firstOrNull { it.first == languageTag } ?: whisperLanguages.first()

    SettingSection(
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
            SettingTile.NavigationTile(
                leading = { SettingsTileIcon(R.drawable.notes_24px) },
                title = "Speech model",
                description = modelDescription,
                onClick = onOpenModels
            )
        )
    )
}
