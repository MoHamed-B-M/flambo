package com.flambo.recorder.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.flambo.recorder.data.Recording
import com.flambo.recorder.domain.formatDuration
import com.flambo.recorder.domain.formatRelativeTime
import com.flambo.recorder.playback.PlaybackController
import com.flambo.recorder.ui.theme.ShapeLargeIncreased

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecordingCard(
    recording: Recording,
    playback: PlaybackController,
    onClick: () -> Unit,
    onFavorite: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit,
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    fileMissing: Boolean = false
) {
    var showMenu by remember { mutableStateOf(false) }
    val playbackState by playback.state.collectAsState()
    val isCurrent = playbackState.currentPath == recording.filePath
    val isPlaying = isCurrent && playbackState.isPlaying
    val progress = if (isCurrent && playbackState.durationMs > 0) {
        (playbackState.positionMs.toFloat() / playbackState.durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f
    val displayedDuration = when {
        isCurrent && playbackState.durationMs > 0 && playbackState.isPlaying -> "${formatDuration(playbackState.positionMs)} / ${formatDuration(playbackState.durationMs)}"
        isCurrent && playbackState.durationMs > 0 -> "${formatDuration(playbackState.positionMs)} / ${formatDuration(playbackState.durationMs)}"
        else -> formatDuration(recording.durationMs)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(ShapeLargeIncreased)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = ShapeLargeIncreased,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
            else if (isCurrent) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.9f)
            else MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }

            Surface(
                shape = ShapeLargeIncreased,
                color = if (isPlaying) MaterialTheme.colorScheme.primary else if (isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(48.dp),
                onClick = {
                    if (isPlaying) playback.pause()
                    else playback.play(recording.filePath)
                }
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    modifier = Modifier.padding(12.dp),
                    tint = if (isPlaying) MaterialTheme.colorScheme.onPrimary else if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
                )
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = recording.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (recording.isFavorite) {
                        Icon(
                            imageVector = Icons.Filled.Favorite,
                            contentDescription = "Favorite",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (fileMissing) {
                        Text(
                            text = "File missing",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        Text(
                            text = displayedDuration,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text("•", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                    Text(
                        text = formatRelativeTime(recording.createdAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                // Parsed once per recording: peakList splits + parses ~120 floats,
                // and cards recompose on every playback tick while playing.
                val peaks = remember(recording.amplitudePeaks) { recording.peakList }
                if (peaks.isNotEmpty()) {
                    StaticWaveform(
                        peaks = peaks,
                        progress = progress,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                if (recording.tagList.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                        recording.tagList.take(3).forEach { tag ->
                            AssistChip(
                                onClick = {},
                                label = { Text(tag, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }
            }

            IconButton(onClick = onFavorite) {
                Icon(
                    imageVector = if (recording.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = "Favorite",
                    tint = if (recording.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            androidx.compose.foundation.layout.Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "More")
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    shape = RoundedCornerShape(20.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp
                ) {
                    Column(
                        modifier = Modifier
                            .padding(8.dp)
                            .width(208.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        FilledTonalButton(
                            onClick = { showMenu = false; onRename() },
                            shape = RoundedCornerShape(
                                topStart = 16.dp, topEnd = 16.dp,
                                bottomStart = 8.dp, bottomEnd = 8.dp
                            ),
                            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                            Text("Rename", modifier = Modifier.weight(1f))
                        }
                        Button(
                            onClick = { showMenu = false; onDelete() },
                            shape = RoundedCornerShape(
                                topStart = 8.dp, topEnd = 8.dp,
                                bottomStart = 16.dp, bottomEnd = 16.dp
                            ),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            ),
                            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                            Text("Delete", modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecordingGridTile(
    recording: Recording,
    playback: PlaybackController,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    fileMissing: Boolean = false
) {
    val playbackState by playback.state.collectAsState()
    val isCurrent = playbackState.currentPath == recording.filePath
    val isPlaying = isCurrent && playbackState.isPlaying

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(ShapeLargeIncreased)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = ShapeLargeIncreased,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
            else if (isCurrent) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.9f)
            else MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    shape = ShapeLargeIncreased,
                    color = if (isPlaying) MaterialTheme.colorScheme.primary else if (isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.size(44.dp),
                    onClick = {
                        if (isPlaying) playback.pause()
                        else playback.play(recording.filePath)
                    }
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        modifier = Modifier.padding(10.dp),
                        tint = if (isPlaying) MaterialTheme.colorScheme.onPrimary else if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                if (selected) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                } else if (recording.isFavorite) {
                    Icon(
                        imageVector = Icons.Filled.Favorite,
                        contentDescription = "Favorite",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Text(
                text = recording.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                minLines = 2
            )
            val gridDuration = if (isCurrent && playbackState.durationMs > 0) {
                "${formatDuration(playbackState.positionMs)} / ${formatDuration(playbackState.durationMs)}"
            } else formatDuration(recording.durationMs)
            Text(
                text = if (fileMissing) "File missing" else "$gridDuration • ${formatRelativeTime(recording.createdAt)}",
                style = MaterialTheme.typography.labelSmall,
                color = if (fileMissing) MaterialTheme.colorScheme.error else if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val gridPeaks = remember(recording.amplitudePeaks) { recording.peakList }
            if (gridPeaks.isNotEmpty()) {
                val gridProgress = if (isCurrent && playbackState.durationMs > 0) {
                    (playbackState.positionMs.toFloat() / playbackState.durationMs.toFloat()).coerceIn(0f, 1f)
                } else 0f
                StaticWaveform(
                    peaks = gridPeaks,
                    progress = gridProgress,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}
