package com.flambo.recorder.ui.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Forward5
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Replay5
import androidx.compose.material.icons.filled.Share
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.flambo.recorder.FlamboApp
import com.flambo.recorder.domain.formatDuration
import com.flambo.recorder.domain.formatRelativeTime
import com.flambo.recorder.playback.PlaybackController
import com.flambo.recorder.stt.FileTranscription
import com.flambo.recorder.stt.SpeechLanguages
import com.flambo.recorder.stt.SttEngine
import com.flambo.recorder.stt.VoskModelManager
import com.flambo.recorder.ui.components.StaticWaveform
import com.flambo.recorder.ui.theme.ShapeFull
import com.flambo.recorder.ui.theme.ShapeLargeIncreased
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DetailScreen(
    viewModel: DetailViewModel,
    playback: PlaybackController,
    onBack: () -> Unit,
    onDeleted: () -> Unit
) {
    val recording by viewModel.recording.collectAsState()
    val editTitle by viewModel.editTitle.collectAsState()
    val playbackState by playback.state.collectAsState()
    val transcriptionUi by viewModel.transcriptionUi.collectAsState()
    val enginePref by viewModel.enginePref.collectAsState()
    val languagePref by viewModel.languagePref.collectAsState()
    val speechLanguages by viewModel.speechLanguages.collectAsState()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showTagDialog by remember { mutableStateOf(false) }
    var tagInput by remember { mutableStateOf("") }
    var showTranscribeSheet by remember { mutableStateOf(false) }
    var showTranscriptEditor by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { viewModel.loadSpeechLanguages(context) }

    val rec = recording
    if (rec == null) {
        Scaffold(topBar = {
            TopAppBar(title = { Text("Recording") }, navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            })
        }) { padding ->
            androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Loading…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }

    val isThisPlaying = playbackState.isPlaying && playback.isPlayingPath(rec.filePath) || playbackState.positionMs > 0 && playback.isPlayingPath(rec.filePath)
    val progress = if (playbackState.durationMs > 0) (playbackState.positionMs.toFloat() / playbackState.durationMs).coerceIn(0f, 1f) else 0f
    val duration = if (playbackState.durationMs > 0) playbackState.durationMs else rec.durationMs

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Playback", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(onClick = { viewModel.toggleFavorite() }) {
                        Icon(
                            imageVector = if (rec.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (rec.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = {
                        // share
                        try {
                            val file = File(rec.filePath)
                            if (file.exists()) {
                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "audio/*"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, "Share recording"))
                            }
                        } catch (_: Exception) {}
                    }) {
                        Icon(Icons.Filled.Share, contentDescription = "Share")
                    }
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Title — inline editable
            Surface(
                shape = ShapeLargeIncreased,
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (editTitle != null) {
                        OutlinedTextField(
                            value = editTitle ?: "",
                            onValueChange = viewModel::setEditTitle,
                            label = { Text("Title") },
                            singleLine = true,
                            shape = ShapeLargeIncreased,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { viewModel.setEditTitle(null) }) { Text("Cancel") }
                            FilledTonalButton(onClick = { viewModel.saveTitle() }, shape = ShapeFull) { Text("Save") }
                        }
                    } else {
                        Text(rec.title, style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "${formatDuration(rec.durationMs)} • ${formatRelativeTime(rec.createdAt)} • ${rec.quality}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(onClick = { viewModel.setEditTitle(rec.title) }) { Text("Rename") }
                    }
                    if (rec.tagList.isNotEmpty() || true) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                            rec.tagList.forEach { tag ->
                                AssistChip(
                                    onClick = {},
                                    label = { Text(tag) },
                                    shape = ShapeFull,
                                    colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                                )
                            }
                            AssistChip(
                                onClick = { tagInput = rec.tagList.joinToString(", "); showTagDialog = true },
                                label = { Text(if (rec.tagList.isEmpty()) "Add tags" else "Edit tags") },
                                shape = ShapeFull
                            )
                        }
                    }
                }
            }

            // Waveform + scrubber
            Surface(
                shape = ShapeLargeIncreased,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    StaticWaveform(
                        peaks = rec.peakList.ifEmpty { List(40) { 0.35f + (Math.random().toFloat() * 0.5f) } },
                        progress = progress,
                        modifier = Modifier.fillMaxWidth()
                    )
                    // Slider for seeking
                    Slider(
                        value = progress,
                        onValueChange = { p ->
                            val target = (p * duration).toLong()
                            playback.seekTo(target)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(formatDuration(playbackState.positionMs.takeIf { it > 0 } ?: 0L), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(formatDuration(duration), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    // Controls — large expressive pill buttons with bouncy spring
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        FilledTonalIconButton(
                            onClick = { playback.skip(-5000) },
                            modifier = Modifier.size(48.dp),
                            shape = ShapeFull
                        ) {
                            Icon(Icons.Filled.Replay5, contentDescription = "Back 5s")
                        }

                        // Play / pause morphing FAB — bouncy scale
                        androidx.compose.material3.FloatingActionButton(
                            onClick = {
                                if (isThisPlaying && playbackState.isPlaying) playback.pause()
                                else playback.play(rec.filePath)
                            },
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            shape = ShapeFull,
                            modifier = Modifier.size(64.dp)
                        ) {
                            Icon(
                                imageVector = if (playbackState.isPlaying && playback.isPlayingPath(rec.filePath)) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        FilledTonalIconButton(
                            onClick = { playback.skip(10000) },
                            modifier = Modifier.size(48.dp),
                            shape = ShapeFull
                        ) {
                            Icon(Icons.Filled.Forward5, contentDescription = "Forward 10s")
                        }
                    }

                    // Speed control — expressive connected ToggleButtons (bouncy)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "Speed",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 8.dp, top = 10.dp)
                        )
                        val speeds = listOf(0.5f, 1f, 1.5f, 2f)
                        speeds.forEachIndexed { index, speed ->
                            val selected = playbackState.speed == speed
                            ToggleButton(
                                checked = selected,
                                onCheckedChange = { playback.setSpeed(speed) },
                                shapes = when (index) {
                                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                    speeds.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                                },
                                modifier = Modifier
                            ) {
                                Text("${speed}x", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }

            // Transcript — saved text, fresh result, progress, or the entry point
            TranscriptSection(
                savedTranscript = rec.transcriptText,
                transcriptionUi = transcriptionUi,
                onTranscribe = { showTranscribeSheet = true },
                onCancelWork = { viewModel.cancelTranscription() },
                onDismiss = { viewModel.dismissTranscription() },
                onCopy = { text -> clipboard.setText(AnnotatedString(text)) },
                onShareText = { text ->
                    runCatching {
                        context.startActivity(
                            Intent.createChooser(
                                Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, text)
                                },
                                "Share transcript"
                            )
                        )
                    }
                },
                onEdit = { text -> showTranscriptEditor = text },
                onSave = { text -> viewModel.saveTranscript(text) },
                onClearSaved = { viewModel.clearSavedTranscript() }
            )

            // Quick actions row
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                FilledTonalButton(onClick = { showDeleteConfirm = true }, shape = ShapeFull, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Move to trash")
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Move to trash?") },
            text = { Text("\"${rec.title}\" will be moved to trash and auto-deleted after 7 days. You can restore it from the Home trash view.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    viewModel.softDelete()
                    onDeleted()
                }) { Text("Move to trash", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } },
            shape = ShapeLargeIncreased
        )
    }

    if (showTagDialog) {
        AlertDialog(
            onDismissRequest = { showTagDialog = false },
            title = { Text("Tags") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Separate tags with commas", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = tagInput,
                        onValueChange = { tagInput = it },
                        placeholder = { Text("interview, idea, reminder") },
                        shape = ShapeLargeIncreased,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val tags = tagInput.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    viewModel.updateTags(tags)
                    showTagDialog = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showTagDialog = false }) { Text("Cancel") } },
            shape = ShapeLargeIncreased
        )
    }

    if (showTranscribeSheet) {
        TranscribeSheet(
            engine = enginePref,
            languageTag = languagePref,
            languages = speechLanguages,
            onEngineChange = { viewModel.setEngine(it) },
            onLanguageChange = { viewModel.setLanguage(it) },
            onStart = {
                showTranscribeSheet = false
                viewModel.transcribe()
            },
            onDismiss = { showTranscribeSheet = false }
        )
    }

    showTranscriptEditor?.let { initial ->
        var draft by remember(initial) { mutableStateOf(initial) }
        AlertDialog(
            onDismissRequest = { showTranscriptEditor = null },
            title = { Text("Edit transcript") },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    shape = ShapeLargeIncreased,
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.saveTranscript(draft)
                    showTranscriptEditor = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showTranscriptEditor = null }) { Text("Cancel") } },
            shape = ShapeLargeIncreased
        )
    }
}

@Composable
private fun TranscriptSection(
    savedTranscript: String,
    transcriptionUi: FileTranscription,
    onTranscribe: () -> Unit,
    onCancelWork: () -> Unit,
    onDismiss: () -> Unit,
    onCopy: (String) -> Unit,
    onShareText: (String) -> Unit,
    onEdit: (String) -> Unit,
    onSave: (String) -> Unit,
    onClearSaved: () -> Unit
) {
    when (transcriptionUi) {
        is FileTranscription.Working -> {
            Surface(
                shape = ShapeLargeIncreased,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.RecordVoiceOver, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Transcribing offline…", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        TextButton(onClick = onCancelWork) { Text("Cancel") }
                    }
                    LinearProgressIndicator(
                        progress = { transcriptionUi.progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "${(transcriptionUi.progress * 100).toInt()}% — decoding, then recognizing",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        is FileTranscription.Done -> TranscriptCard(
            text = transcriptionUi.text,
            badge = if (transcriptionUi.offline) "Offline • Vosk" else "Transcript",
            saved = false,
            onCopy = onCopy,
            onShareText = onShareText,
            onEdit = onEdit,
            onSave = onSave,
            onDiscard = onDismiss,
            onClearSaved = {}
        )
        is FileTranscription.Error -> {
            Surface(
                shape = ShapeLargeIncreased,
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Couldn't transcribe", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onErrorContainer)
                    Text(transcriptionUi.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = onTranscribe, shape = ShapeFull) { Text("Try again") }
                        TextButton(onClick = onDismiss) { Text("Dismiss") }
                    }
                    if (transcriptionUi.needsModelCode != null) {
                        Text(
                            "Tip: download the model in Settings → Speech-to-text → Vosk models, then retry. No account, no cloud.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f)
                        )
                    }
                }
            }
        }
        is FileTranscription.Idle -> {
            if (savedTranscript.isNotBlank()) {
                TranscriptCard(
                    text = savedTranscript,
                    badge = "Saved",
                    saved = true,
                    onCopy = onCopy,
                    onShareText = onShareText,
                    onEdit = onEdit,
                    onSave = {},
                    onDiscard = {},
                    onClearSaved = onClearSaved
                )
            } else {
                OutlinedButton(
                    onClick = onTranscribe,
                    shape = ShapeFull,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.RecordVoiceOver, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Transcribe this recording")
                }
            }
        }
    }
}

@Composable
private fun TranscriptCard(
    text: String,
    badge: String,
    saved: Boolean,
    onCopy: (String) -> Unit,
    onShareText: (String) -> Unit,
    onEdit: (String) -> Unit,
    onSave: (String) -> Unit,
    onDiscard: () -> Unit,
    onClearSaved: () -> Unit
) {
    var justCopied by remember { mutableStateOf(false) }
    androidx.compose.material3.ElevatedCard(
        shape = ShapeLargeIncreased,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.AssistChip(onClick = {}, label = { Text(badge) })
                Spacer(Modifier.weight(1f))
                if (!saved) {
                    TextButton(onClick = onDiscard) { Text("Discard") }
                    FilledTonalButton(onClick = { onSave(text) }, shape = ShapeFull) {
                        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Save")
                    }
                } else {
                    TextButton(onClick = onClearSaved) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            Text(text, style = MaterialTheme.typography.bodyLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { onCopy(text); justCopied = true },
                    shape = ShapeFull,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        if (justCopied) Icons.Filled.Check else Icons.Filled.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(if (justCopied) "Copied" else "Copy")
                }
                OutlinedButton(
                    onClick = { onShareText(text) },
                    shape = ShapeFull,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Share")
                }
                androidx.compose.material3.IconButton(onClick = { onEdit(text) }) {
                    Icon(Icons.Filled.Edit, contentDescription = "Edit transcript")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TranscribeSheet(
    engine: String,
    languageTag: String,
    languages: List<Locale>,
    onEngineChange: (String) -> Unit,
    onLanguageChange: (String) -> Unit,
    onStart: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    var langExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val app = context.applicationContext as FlamboApp
    val modelProgress by app.transcription.modelProgress.collectAsState()
    val scope = rememberCoroutineScope()
    var downloading by remember { mutableStateOf<String?>(null) }

    val currentBase = languageTag.substringBefore('-').substringBefore('_').lowercase().ifBlank { "en" }
    val modelEntry = VoskModelManager.forTag(currentBase)
    val installed = remember(languageTag, modelProgress) {
        app.transcription.vosk.models.isInstalled(currentBase)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = ShapeLargeIncreased
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Transcribe offline", style = MaterialTheme.typography.titleLarge)
            Text(
                "Vosk turns this file into text on your device — no cloud, no account. Download the language model once, reuse forever.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text("Engine", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                modifier = Modifier.fillMaxWidth()
            ) {
                listOf(SttEngine.AUTO to "Auto", SttEngine.VOSK to "Vosk offline", SttEngine.SYSTEM to "System live").forEachIndexed { index, (value, label) ->
                    ToggleButton(
                        checked = engine == value,
                        onCheckedChange = { onEngineChange(value) },
                        shapes = when (index) {
                            0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                            2 -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text(label) }
                }
            }
            if (engine == SttEngine.SYSTEM) {
                Text(
                    "Heads-up: the system recognizer only dictates live from the mic — it can't read saved files. Pick Auto or Vosk here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Text("Language", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            ExposedDropdownMenuBox(expanded = langExpanded, onExpandedChange = { langExpanded = it }) {
                OutlinedTextField(
                    value = languages.firstOrNull { it.toLanguageTag() == languageTag }?.let { SpeechLanguages.displayName(it) }
                        ?: modelEntry?.label
                        ?: languageTag.ifBlank { "System default" },
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = langExpanded) },
                    shape = ShapeLargeIncreased,
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(expanded = langExpanded, onDismissRequest = { langExpanded = false }) {
                    languages.forEach { locale ->
                        DropdownMenuItem(
                            text = { Text(SpeechLanguages.displayName(locale)) },
                            onClick = {
                                onLanguageChange(locale.toLanguageTag())
                                langExpanded = false
                            }
                        )
                    }
                }
            }

            // Model status for the chosen language
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        if (installed) "${modelEntry?.label ?: currentBase.uppercase()} model ready"
                        else "${modelEntry?.label ?: currentBase.uppercase()} model ${modelEntry?.let { "· ~${it.sizeMb} MB" } ?: ""}",
                        style = MaterialTheme.typography.titleSmall
                    )
                    val prog = modelProgress[currentBase]
                    if (prog != null) {
                        LinearProgressIndicator(progress = { prog }, modifier = Modifier.fillMaxWidth())
                        Text("Downloading… ${(prog * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else if (!installed) {
                        Text("One-time download, then fully offline.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (!installed && modelEntry != null && downloading == null) {
                    FilledTonalButton(
                        onClick = {
                            downloading = currentBase
                            scope.launch {
                                val result = app.transcription.vosk.models.download(currentBase)
                                downloading = null
                                if (result.isFailure) {
                                    // surface via snackbar-less inline note next render
                                }
                            }
                        },
                        shape = ShapeFull
                    ) {
                        Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Get")
                    }
                }
            }

            FilledTonalButton(
                onClick = onStart,
                enabled = engine != SttEngine.SYSTEM && installed && downloading == null,
                shape = ShapeFull,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.RecordVoiceOver, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (installed) "Start transcription" else "Download the model first")
            }
        }
    }
}
