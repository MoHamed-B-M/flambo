package com.flambo.recorder.ui.settings

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.dp
import com.flambo.recorder.data.PreferencesManager
import com.flambo.recorder.stt.TranscriptionManager
import com.flambo.recorder.stt.VoskModelManager
import com.flambo.recorder.ui.settings.components.PreferenceValueItem
import com.flambo.recorder.ui.settings.components.SectionHeader
import com.flambo.recorder.ui.settings.components.SegmentedPreferenceGroup
import com.flambo.recorder.ui.theme.ShapeLargeIncreased
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SttSettingsScreen(
    prefs: PreferencesManager,
    scope: CoroutineScope,
    transcription: TranscriptionManager,
    onBack: () -> Unit
) {
    val sttLanguage by prefs.sttLanguageFlow.collectAsState(initial = "")
    val sttEngine by prefs.sttEngineFlow.collectAsState(initial = "vosk")
    val modelProgress by transcription.modelProgress.collectAsState()
    val whisperProgress by transcription.whisperProgress.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showSttLanguageDialog by remember { mutableStateOf(false) }
    var showVoskModelsDialog by remember { mutableStateOf(false) }
    var showEngineDialog by remember { mutableStateOf(false) }
    var modelsTick by remember { mutableStateOf(0) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Speech-to-Text", style = MaterialTheme.typography.titleLarge) },
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
                SectionHeader(title = "Engine")
                SegmentedPreferenceGroup {
                    item {
                        PreferenceValueItem(
                            icon = Icons.Filled.RecordVoiceOver,
                            title = "Engine",
                            value = if (sttEngine == "whisper") "Whisper • multilingual 95+ langs" else "Vosk • per-language models",
                            onClick = { showEngineDialog = true }
                        )
                    }
                    item {
                        PreferenceValueItem(
                            icon = Icons.Filled.RecordVoiceOver,
                            title = "Transcription language",
                            value = if (sttEngine == "whisper") {
                                when (sttLanguage) {
                                    "" -> "Auto (detect)"
                                    "en" -> "English"
                                    "id" -> "Indonesian"
                                    "ar" -> "Arabic"
                                    "fr" -> "French"
                                    "es" -> "Spanish"
                                    "de" -> "German"
                                    "it" -> "Italian"
                                    "pt" -> "Portuguese"
                                    "ru" -> "Russian"
                                    "zh" -> "Chinese"
                                    "ja" -> "Japanese"
                                    "ko" -> "Korean"
                                    "hi" -> "Hindi"
                                    "tr" -> "Turkish"
                                    else -> sttLanguage.ifBlank { "Auto (detect)" }
                                }
                            } else {
                                VoskModelManager.forTag(sttLanguage.ifBlank { "en" })?.label
                                    ?: if (sttLanguage.isBlank()) "Best installed model" else sttLanguage
                            },
                            onClick = { showSttLanguageDialog = true }
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader(title = "Offline Models")
                SegmentedPreferenceGroup {
                    item {
                        val installedCount = remember(modelsTick) { transcription.vosk.models.installedCodes().size }
                        PreferenceValueItem(
                            icon = Icons.Filled.Download,
                            title = "Vosk offline models",
                            value = if (installedCount == 0) "None yet — download one to transcribe offline" else "$installedCount language${if (installedCount > 1) "s" else ""} ready offline",
                            onClick = { showVoskModelsDialog = true }
                        )
                    }
                    if (sttEngine == "whisper") {
                        item {
                            val whisperInstalled = remember(modelsTick, whisperProgress) { transcription.whisper.models.isInstalled() }
                            val prog = whisperProgress["whisper"]
                            PreferenceValueItem(
                                icon = Icons.Filled.Download,
                                title = "Whisper tiny model",
                                value = when {
                                    prog != null -> "Downloading ${(prog * 100).toInt()}%"
                                    whisperInstalled -> "Ready offline • 95+ languages • ~75 MB"
                                    else -> "Not installed — 75 MB multilingual"
                                },
                                onClick = {
                                    if (prog == null && !whisperInstalled) {
                                        scope.launch {
                                            val res = transcription.whisper.models.download()
                                            modelsTick++
                                            res.onFailure { scope.launch { snackbarHostState.showSnackbar(it.message ?: "Download failed") } }
                                            res.onSuccess { scope.launch { snackbarHostState.showSnackbar("Whisper ready • 95+ languages") } }
                                        }
                                    } else if (whisperInstalled) {
                                        scope.launch { transcription.whisper.models.delete(); modelsTick++; snackbarHostState.showSnackbar("Whisper model deleted") }
                                    }
                                }
                            )
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader(title = "About")
                SegmentedPreferenceGroup {
                    item {
                        PreferenceValueItem(
                            icon = Icons.Filled.RecordVoiceOver,
                            title = "Offline transcription",
                            value = "Vosk and Whisper run fully offline — no cloud, no account",
                            onClick = {}
                        )
                    }
                }
            }
        }
    }

    if (showEngineDialog) {
        AlertDialog(
            onDismissRequest = { showEngineDialog = false },
            title = { Text("Transcription engine", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Whisper is multilingual (95+ languages) via ggml-tiny • Vosk uses per-language models", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    listOf(
                        Triple("vosk", "Vosk", "Per-language models • proven offline"),
                        Triple("whisper", "Whisper", "Multilingual 95+ • ggml-tiny 75 MB")
                    ).forEachIndexed { index, (value, label, desc) ->
                        ToggleButton(
                            checked = sttEngine == value,
                            onCheckedChange = {
                                scope.launch { prefs.setSttEngine(value) }
                                showEngineDialog = false
                            },
                            shapes = when (index) {
                                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                else -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(vertical = 4.dp)) {
                                Text(label, style = MaterialTheme.typography.titleMedium)
                                Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showEngineDialog = false }, shapes = ButtonDefaults.shapes()) { Text("Close") } },
            shape = ShapeLargeIncreased
        )
    }

    if (showSttLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showSttLanguageDialog = false },
            title = { Text("Transcription language", style = MaterialTheme.typography.titleLarge) },
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (sttEngine == "whisper") {
                        val whisperLangs = listOf(
                            "" to "Auto (detect)", "en" to "English", "id" to "Indonesian", "ar" to "Arabic", "fr" to "French",
                            "es" to "Spanish", "de" to "German", "it" to "Italian", "pt" to "Portuguese", "ru" to "Russian",
                            "zh" to "Chinese", "ja" to "Japanese", "ko" to "Korean", "hi" to "Hindi", "tr" to "Turkish",
                            "nl" to "Dutch", "pl" to "Polish", "vi" to "Vietnamese", "th" to "Thai", "ms" to "Malay",
                            "fa" to "Persian", "ur" to "Urdu"
                        )
                        items(whisperLangs, key = { it.first.ifBlank { "auto" } }) { (code, label) ->
                            ToggleButton(
                                checked = sttLanguage == code,
                                onCheckedChange = {
                                    scope.launch { prefs.setSttLanguage(code) }
                                    showSttLanguageDialog = false
                                },
                                shapes = ButtonGroupDefaults.connectedMiddleButtonShapes(),
                                modifier = Modifier.fillMaxWidth()
                            ) { Text(label, modifier = Modifier.weight(1f)) }
                        }
                    } else {
                        item {
                            ToggleButton(
                                checked = sttLanguage.isBlank(),
                                onCheckedChange = {
                                    scope.launch { prefs.setSttLanguage("") }
                                    showSttLanguageDialog = false
                                },
                                shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Best installed model") }
                        }
                        items(VoskModelManager.CATALOG, key = { it.code }) { model ->
                            ToggleButton(
                                checked = sttLanguage == model.code,
                                onCheckedChange = {
                                    scope.launch { prefs.setSttLanguage(model.code) }
                                    showSttLanguageDialog = false
                                },
                                shapes = ButtonGroupDefaults.connectedMiddleButtonShapes(),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(model.label, modifier = Modifier.weight(1f))
                                Text(if (transcription.vosk.models.isInstalled(model.code)) "ready" else "~${model.sizeMb} MB", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSttLanguageDialog = false }, shapes = ButtonDefaults.shapes()) { Text("Close") } },
            shape = ShapeLargeIncreased
        )
    }

    if (showVoskModelsDialog) {
        AlertDialog(
            onDismissRequest = { showVoskModelsDialog = false },
            title = { Text("Offline models", style = MaterialTheme.typography.titleLarge) },
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(VoskModelManager.CATALOG, key = { it.code }) { model ->
                        val installed = remember(modelsTick, modelProgress) { transcription.vosk.models.isInstalled(model.code) }
                        val prog = modelProgress[model.code]
                        Surface(shape = ShapeLargeIncreased, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(model.label, style = MaterialTheme.typography.titleSmall)
                                        Text(if (installed) "Ready offline" else "~${model.sizeMb} MB one-time download" + if (model.large) " • large" else "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    when {
                                        prog != null -> Text("${(prog * 100).toInt()}%", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                        installed -> IconButton(onClick = { scope.launch { transcription.vosk.models.delete(model.code); modelsTick++ } }) {
                                            Icon(Icons.Filled.Delete, contentDescription = "Delete ${model.label} model", tint = MaterialTheme.colorScheme.error)
                                        }
                                        else -> FilledTonalButton(onClick = { scope.launch { transcription.vosk.models.download(model.code); modelsTick++ } }, shapes = ButtonDefaults.shapes()) {
                                            Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text("Get")
                                        }
                                    }
                                }
                                if (prog != null) LinearProgressIndicator(progress = { prog }, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showVoskModelsDialog = false }, shapes = ButtonDefaults.shapes()) { Text("Done") } },
            shape = ShapeLargeIncreased
        )
    }
}
