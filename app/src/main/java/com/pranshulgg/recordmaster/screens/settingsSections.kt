package com.pranshulgg.recordmaster.screens

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.pranshulgg.recordmaster.R
import com.pranshulgg.recordmaster.RecordMasterApp
import com.pranshulgg.recordmaster.core.audio.EnhanceStrength
import com.pranshulgg.recordmaster.core.data.StorageVolumes
import com.pranshulgg.recordmaster.core.domain.RecordingQuality
import com.pranshulgg.recordmaster.core.playback.PlaybackController
import com.pranshulgg.recordmaster.core.stt.VoskModelManager
import com.pranshulgg.recordmaster.ui.components.SettingSection
import com.pranshulgg.recordmaster.ui.components.SettingTile
import com.pranshulgg.recordmaster.ui.components.SettingsTileIcon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Additive 2.0 settings rows. They reuse the exact RecordMaster tile
 * components, shapes and spacing; the existing "App looks" section is
 * untouched.
 */
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

    val strength = EnhanceStrength.fromPref(strengthValue)

    SettingSection(
        title = "Recording",
        tiles = listOf(
            SettingTile.DialogOptionTile(
                leading = { SettingsTileIcon(R.drawable.graphic_eq_24px) },
                title = "Recording quality",
                description = quality.description,
                options = RecordingQuality.entries.map { it.label },
                selectedOption = quality.label,
                onOptionSelected = { label ->
                    val selected = RecordingQuality.entries
                        .firstOrNull { it.label == label } ?: RecordingQuality.HIGH
                    scope.launch {
                        app.prefs.setQuality(selected)
                        showMessage("Quality: ${selected.label}")
                    }
                }
            ),
            SettingTile.SwitchTile(
                leading = { SettingsTileIcon(R.drawable.colorize_24px) },
                title = "Noise reduction",
                description = "Use the voice-recognition microphone source",
                checked = noiseReduction,
                onCheckedChange = { enabled ->
                    scope.launch { app.prefs.setNoiseReduction(enabled) }
                }
            ),
            SettingTile.SwitchTile(
                leading = { SettingsTileIcon(R.drawable.auto_fix_24px) },
                title = "Keep original after cleaning",
                description = "Off replaces the take with the cleaned copy",
                checked = keepOriginal,
                onCheckedChange = { keep ->
                    scope.launch { app.prefs.setKeepOriginal(keep) }
                }
            ),
            SettingTile.DialogOptionTile(
                leading = { SettingsTileIcon(R.drawable.auto_fix_24px) },
                title = "Cleanup strength",
                description = strength.description,
                options = EnhanceStrength.entries.map { it.label },
                selectedOption = strength.label,
                onOptionSelected = { label ->
                    val selected = EnhanceStrength.entries
                        .firstOrNull { it.label == label } ?: EnhanceStrength.BALANCED
                    scope.launch {
                        app.prefs.setEnhanceStrength(selected.name.lowercase(Locale.US))
                        showMessage("Cleanup: ${selected.label}")
                    }
                }
            ),
            SettingTile.DialogTextFieldTile(
                leading = { SettingsTileIcon(R.drawable.edit_24px) },
                title = "Recording name prefix",
                description = "New takes are named ${prefix}_###",
                initialText = prefix,
                onTextSubmitted = { value ->
                    scope.launch {
                        app.prefs.setRecordingPrefix(value)
                        app.recordingPrefix = app.prefs.recordingPrefix()
                        showMessage("Prefix updated")
                    }
                },
                placeholder = "Prefix",
                placeholderTextField = "User"
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
    val volumeId by app.prefs.recordingsVolumeFlow.collectAsState(initial = StorageVolumes.ID_DEFAULT)
    val autoPurge by app.prefs.trashAutoPurgeFlow.collectAsState(initial = false)
    val retentionDays by app.prefs.trashRetentionDaysFlow.collectAsState(initial = 7)

    val volumes = remember {
        StorageVolumes.list(context).map { volume ->
            volume.copy(
                label = if (volume.id == StorageVolumes.ID_DEFAULT) {
                    "${volume.label} (default)"
                } else {
                    volume.label
                }
            )
        }
    }
    val selectedVolume = volumes.firstOrNull { it.id == volumeId } ?: volumes.firstOrNull()

    SettingSection(
        title = "Storage",
        tiles = listOf(
            SettingTile.DialogOptionTile(
                leading = { SettingsTileIcon(R.drawable.storage_24px) },
                title = "Storage location",
                description = selectedVolume?.let { "${it.label} · ${it.dir.absolutePath}" }
                    ?: "Phone storage",
                options = volumes.map { it.label },
                selectedOption = selectedVolume?.label,
                onOptionSelected = { label ->
                    val selected = volumes.firstOrNull { it.label == label }
                    if (selected != null) {
                        scope.launch {
                            app.prefs.setRecordingsVolume(selected.id)
                            app.storageVolumeId = selected.id
                            app.syncStorage()
                            showMessage("New recordings will be saved in ${selected.label}")
                        }
                    }
                }
            ),
            SettingTile.ActionTile(
                leading = { SettingsTileIcon(R.drawable.refresh_24px) },
                title = "Sync storage now",
                description = "Index files added outside the app",
                onClick = {
                    app.syncStorage()
                    scope.launch { showMessage("Storage sync started") }
                }
            ),
            SettingTile.SwitchTile(
                leading = { SettingsTileIcon(R.drawable.folder_delete_24px) },
                title = "Auto-delete old trash",
                description = "Permanently remove items after the retention period",
                checked = autoPurge,
                onCheckedChange = { enabled ->
                    scope.launch {
                        app.prefs.setTrashAutoPurge(enabled)
                        if (enabled) {
                            app.repository.purgeOldTrash(retentionDays)
                            app.syncStorage()
                        }
                    }
                }
            ),
            SettingTile.DialogOptionTile(
                leading = { SettingsTileIcon(R.drawable.clock_loader_60_24px) },
                title = "Trash retention",
                description = "$retentionDays days",
                options = listOf("7 days", "14 days", "30 days", "60 days", "90 days"),
                selectedOption = "$retentionDays days",
                onOptionSelected = { label ->
                    val days = label.substringBefore(' ').toIntOrNull() ?: 7
                    scope.launch {
                        app.prefs.setTrashRetentionDays(days)
                        showMessage("Trash retention: $days days")
                    }
                }
            ),
            SettingTile.ActionTile(
                leading = { SettingsTileIcon(R.drawable.delete_forever_24px, dangerColor = true) },
                title = "Empty Recently deleted",
                description = "Permanently delete everything in the trash",
                danger = true,
                onClick = {
                    scope.launch {
                        app.repository.emptyTrash()
                        app.syncStorage()
                        showMessage("Recently deleted emptied")
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
    val label = if (speed == 1f) "Normal (1x)" else String.format(Locale.US, "%.2fx", speed)

    SettingSection(
        title = "Playback",
        tiles = listOf(
            SettingTile.DialogOptionTile(
                leading = { SettingsTileIcon(R.drawable.routine_24px) },
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
    val progress by app.transcription.modelProgress.collectAsState()
    val models = app.transcription.vosk.models

    val catalog = VoskModelManager.CATALOG
    val selectedModel = VoskModelManager.forTag(languageTag.ifBlank {
        Locale.getDefault().toLanguageTag()
    }) ?: catalog.first()
    val installed = remember(progress) { models.installedCodes().toSet() }

    SettingSection(
        title = "Speech-to-text",
        tiles = listOf(
            SettingTile.TextTile(
                leading = { SettingsTileIcon(R.drawable.notes_24px) },
                title = "Engine",
                description = "Vosk · fully offline"
            ),
            SettingTile.DialogOptionTile(
                leading = { SettingsTileIcon(R.drawable.graphic_eq_24px) },
                title = "Transcription language",
                description = selectedModel.label,
                options = catalog.map { it.label },
                selectedOption = selectedModel.label,
                onOptionSelected = { label ->
                    val model = catalog.firstOrNull { it.label == label } ?: catalog.first()
                    scope.launch {
                        app.prefs.setSttLanguage(model.code)
                        showMessage(
                            if (models.isInstalled(model.code)) {
                                "Transcription language: ${model.label}"
                            } else {
                                "Download the ${model.label} model below"
                            }
                        )
                    }
                }
            )
        ) + catalog.map { model ->
            when {
                model.code in installed -> SettingTile.TextTile(
                    leading = { SettingsTileIcon(R.drawable.check) },
                    title = "${model.label} model",
                    description = "Installed · ${
                        String.format(
                            Locale.US,
                            "%.1f MB",
                            models.modelSizeOnDisk(model.code) / (1024f * 1024f)
                        )
                    }"
                )

                progress[model.code] != null -> SettingTile.TextTile(
                    leading = { SettingsTileIcon(R.drawable.clock_loader_60_24px) },
                    title = "Downloading ${model.label}",
                    description = "${
                        ((progress[model.code] ?: 0f) * 100f).toInt()
                    }% · ${model.sizeMb} MB"
                )

                else -> SettingTile.ActionTile(
                    leading = { SettingsTileIcon(R.drawable.notes_24px) },
                    title = "Download ${model.label} model",
                    description = "${model.sizeMb} MB · offline transcription",
                    onClick = {
                        scope.launch {
                            val result = models.download(model.code)
                            result.fold(
                                onSuccess = {
                                    showMessage("${model.label} model installed")
                                },
                                onFailure = {
                                    showMessage(it.message ?: "Download failed")
                                }
                            )
                        }
                    }
                )
            }
        }
    )
}

