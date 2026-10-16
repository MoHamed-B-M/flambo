package com.flambo.recorder.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.flambo.recorder.data.Recording
import com.flambo.recorder.domain.formatDuration
import com.flambo.recorder.domain.formatRelativeTime
import com.flambo.recorder.playback.PlaybackController
import com.flambo.recorder.ui.haptics.AppHaptics
import com.flambo.recorder.ui.theme.FlamboMotion
import com.flambo.recorder.ui.theme.ShapeLargeIncreased
import kotlinx.coroutines.flow.first

@OptIn(ExperimentalFoundationApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun RecordingCard(
    recording: Recording,
    playback: PlaybackController,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    onFavorite: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit,
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    fileMissing: Boolean = false,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    expandAnimationEnabled: Boolean = true
) {
    var showMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    // Playback pushes a position tick ~8x/s while a track plays, and a raw
    // StateFlow invalidates *every* reader on every tick. This card is a lazy
    // list item, so reading the state directly would recompose the whole list
    // ~8x/s. Instead the subscription is funneled through derived slices (which
    // only recompose the card when their value actually changes) and a
    // draw-phase provider, so cards that are not the playing one stay still.
    val playbackState by playback.state.collectAsState()
    val isCurrent by remember(recording.filePath) {
        derivedStateOf { playbackState.currentPath == recording.filePath }
    }
    val isPlaying by remember(recording.filePath) {
        derivedStateOf { isCurrent && playbackState.isPlaying }
    }
    // Read in the draw phase only: the moving playhead redraws without
    // recomposing the card.
    val progressProvider: () -> Float = remember(recording.filePath) {
        {
            if (isCurrent && playbackState.durationMs > 0) {
                (playbackState.positionMs.toFloat() / playbackState.durationMs.toFloat()).coerceIn(0f, 1f)
            } else 0f
        }
    }

    // Single maximize/minimize morph: the whole card expands to fill the page
    // on press and shrinks back into place on back. State call stays
    // unconditional; only the modifier is gated.
    val sharedContentState = with(sharedTransitionScope) {
        rememberSharedContentState(key = "recording-card-${recording.id}")
    }
    val sharedElementModifier = if (expandAnimationEnabled) {
        with(sharedTransitionScope) {
            Modifier.sharedBounds(
                sharedContentState,
                animatedVisibilityScope,
                boundsTransform = FlamboMotion.CardMorphBoundsTransform
            )
        }
    } else {
        Modifier
    }
    // Light-mass press recoil for the favorite target.
    val favInteraction = remember { MutableInteractionSource() }
    val favPressed by favInteraction.collectIsPressedAsState()
    val favScale by animateFloatAsState(
        targetValue = if (favPressed) 0.82f else 1f,
        animationSpec = FlamboMotion.ActionSpringFloat,
        label = "favPress"
    )

    Card(
        modifier = modifier
            .then(sharedElementModifier)
            .fillMaxWidth()
            .clip(ShapeLargeIncreased)
            // Inline content changes (selection check, waveform, tags) resize
            // with heavy-mass physics so siblings glide instead of snapping.
            .animateContentSize(animationSpec = FlamboMotion.ContainerSizeSpring)
            // Tap selects in selection mode and does nothing otherwise:
            // navigation lives on the dedicated Open button so scrubbing the
            // waveform never misfires into the Playback screen.
            .combinedClickable(onClick = onToggle, onLongClick = onLongClick),
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
                    AppHaptics.tap(context)
                    if (isPlaying) playback.pause()
                    else playback.play(recording.filePath, recording.title)
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
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (fileMissing) {
                        Text(
                            text = "File missing",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        // Pre-measure the max duration width once per recording to avoid
                        // flicker at loop boundaries: measure "total / total" (worst case)
                        // and lock the duration text to that exact width.
                        val textMeasurer = rememberTextMeasurer()
                        val durationStyle = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum")
                        val maxDurationWidthPx = remember(recording.durationMs) {
                            val total = formatDuration(recording.durationMs)
                            textMeasurer.measure(text = "$total / $total", style = durationStyle).size.width
                        }
                        val density = LocalDensity.current

                        PlaybackDurationText(
                            playback = playback,
                            filePath = recording.filePath,
                            fallbackDurationMs = recording.durationMs,
                            style = durationStyle,
                            color = if (isCurrent) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(with(density) { maxDurationWidthPx.toDp() })
                        )
                    }
                    Text("•", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                    Text(
                        text = formatRelativeTime(recording.createdAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
                // Parsed once per recording: peakList splits + parses ~120 floats.
                val peaks = remember(recording.amplitudePeaks) { recording.peakList }
                val seekOnCard = rememberCardSeek(
                    recording = recording,
                    playback = playback,
                    isCurrent = isCurrent,
                    fileMissing = fileMissing
                )
                if (peaks.isNotEmpty()) {
                    StaticWaveform(
                        peaks = peaks,
                        progressProvider = progressProvider,
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        onSeek = seekOnCard
                    )
                } else if (isCurrent && !fileMissing) {
                    // No stored peaks (e.g. files restored from outside):
                    // expand with a seekable linear track so pressing play
                    // always reveals a visible progress bar.
                    SeekableLinearTrack(
                        progressProvider = progressProvider,
                        onSeek = seekOnCard,
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                    )
                }
                if (recording.tagList.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                        val tags = recording.tagList
                        // Indexed loop: `take(3)` allocated a fresh list on every
                        // recomposition, and this runs inside a scrolling list.
                        for (i in 0 until minOf(3, tags.size)) {
                            val tag = tags[i]
                            AssistChip(
                                onClick = {},
                                label = { Text(tag, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }
            }

            IconButton(
                onClick = onFavorite,
                interactionSource = favInteraction,
                modifier = Modifier.graphicsLayer {
                    scaleX = favScale
                    scaleY = favScale
                }
            ) {
                Icon(
                    imageVector = if (recording.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = "Favorite",
                    tint = if (recording.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            androidx.compose.foundation.layout.Box {
                IconButton(onClick = { AppHaptics.tap(context); showMenu = true }) {
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
                        Button(
                            onClick = { showMenu = false; onOpen() },
                            shape = RoundedCornerShape(
                                topStart = 16.dp, topEnd = 16.dp,
                                bottomStart = 8.dp, bottomEnd = 8.dp
                            ),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.ChevronRight, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                            Text("Open details", modifier = Modifier.weight(1f))
                        }
                        Button(
                            onClick = { showMenu = false; onFavorite() },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                            ),
                            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                if (recording.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                            Text(if (recording.isFavorite) "Unfavorite" else "Favorite", modifier = Modifier.weight(1f))
                        }
                        FilledTonalButton(
                            onClick = { showMenu = false; onRename() },
                            shape = RoundedCornerShape(8.dp),
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

/**
 * Card scrub handler: seeks the live track, or starts it and applies the
 * scrub once prepared (seekTo clamps to zero pre-prepare, so a pending
 * fraction is required for cold starts).
 */
@Composable
private fun rememberCardSeek(
    recording: Recording,
    playback: PlaybackController,
    isCurrent: Boolean,
    fileMissing: Boolean
): ((Float) -> Unit)? {
    // Hooks stay unconditional; only the returned lambda is gated.
    var pending by remember { mutableStateOf<Float?>(null) }
    // Keyed on the pending scrub, not on the playback duration: keying on
    // duration restarted this effect on every playback tick.
    LaunchedEffect(isCurrent, pending) {
        val frac = pending ?: return@LaunchedEffect
        if (!isCurrent) return@LaunchedEffect
        // Duration only becomes known shortly after prepare(); suspend until
        // the player reports one instead of polling the state each tick.
        val durationMs = playback.state.first { it.durationMs > 0 }.durationMs
        playback.seekTo((frac * durationMs).toLong())
        pending = null
    }
    val seek: (Float) -> Unit = remember(isCurrent) { { frac: Float ->
        val dur = playback.state.value.durationMs
        if (isCurrent && dur > 0) {
            playback.seekTo((frac * dur).toLong())
        } else if (!isCurrent) {
            pending = frac
            playback.play(recording.filePath, recording.title)
        }
    } }
    return if (fileMissing) null else seek
}

/**
 * Seekable fallback track for cards without stored peaks (e.g. audio
 * restored from outside). Fixed height + full width so it can never
 * measure to zero; tap/drag seeks like the waveform.
 */
@Composable
private fun SeekableLinearTrack(
    progressProvider: () -> Float,
    onSeek: ((Float) -> Unit)?,
    modifier: Modifier = Modifier
) {
    var widthPx by remember { mutableStateOf(0) }
    // Tall touch target, slim visual bar.
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp)
            .onSizeChanged { widthPx = it.width }
            .then(
                if (onSeek != null) {
                    Modifier.pointerInput(onSeek) {
                        detectTapGestures(
                            onTap = { offset ->
                                if (widthPx > 0) onSeek((offset.x / widthPx).coerceIn(0f, 1f))
                            }
                        )
                    }
                } else Modifier
            )
            .then(
                if (onSeek != null) {
                    Modifier.pointerInput(onSeek) {
                        detectHorizontalDragGestures { change, _ ->
                            change.consume()
                            if (widthPx > 0) onSeek((change.position.x / widthPx).coerceIn(0f, 1f))
                        }
                    }
                } else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        LinearProgressIndicator(
            // Provider resolve happens inside the indicator lambda, keeping the
            // state read in the draw phase instead of composition.
            progress = { progressProvider().coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(999.dp)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
    }
}

/**
 * Live "position / duration" label. Isolated into its own composable so the
 * ~8x/s playback ticks recompose only this tiny text and never the whole card
 * (text has no draw-phase provider equivalent). Falls back to the stored
 * duration when a different track is playing.
 */
@Composable
private fun PlaybackDurationText(
    playback: PlaybackController,
    filePath: String,
    fallbackDurationMs: Long,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier
) {
    val state by playback.state.collectAsState()
    val text = if (state.currentPath == filePath && state.durationMs > 0) {
        "${formatDuration(state.positionMs)} / ${formatDuration(state.durationMs)}"
    } else {
        formatDuration(fallbackDurationMs)
    }
    Text(
        text = text,
        style = style,
        color = color,
        maxLines = 1,
        softWrap = false,
        modifier = modifier
    )
}
