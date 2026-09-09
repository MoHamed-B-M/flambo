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
import androidx.compose.material.icons.filled.Forward5
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.flambo.recorder.domain.formatDuration
import com.flambo.recorder.domain.formatRelativeTime
import com.flambo.recorder.playback.PlaybackController
import com.flambo.recorder.ui.components.StaticWaveform
import com.flambo.recorder.ui.theme.ShapeFull
import com.flambo.recorder.ui.theme.ShapeLargeIncreased
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

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
    val context = LocalContext.current

    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showTagDialog by remember { mutableStateOf(false) }
    var tagInput by remember { mutableStateOf("") }

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
}
