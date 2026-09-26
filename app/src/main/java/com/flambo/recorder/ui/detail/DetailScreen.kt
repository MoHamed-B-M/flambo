package com.flambo.recorder.ui.detail

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Storage
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.filled.Edit
import kotlinx.coroutines.CancellationException
import kotlin.math.max
import androidx.compose.material.icons.filled.Forward5
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Replay5
import androidx.compose.material.icons.filled.Share
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
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
import com.flambo.recorder.audio.EnhanceStrength
import com.flambo.recorder.playback.PlaybackController
import com.flambo.recorder.stt.FileTranscription
import com.flambo.recorder.stt.VoskModelManager
import com.flambo.recorder.ui.components.PlaybackProgressBar
import com.flambo.recorder.ui.components.StaticWaveform
import com.flambo.recorder.ui.theme.FlamboMotion
import com.flambo.recorder.ui.theme.ShapeFull
import com.flambo.recorder.ui.theme.ShapeLargeIncreased
import com.flambo.recorder.data.StorageVolumes
import android.content.Intent
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun DetailScreen(
    viewModel: DetailViewModel,
    playback: PlaybackController,
    onBack: () -> Unit,
    onDeleted: () -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    cardExpandAnimEnabled: Boolean = true
) {
    val recording by viewModel.recording.collectAsState()
    val editTitle by viewModel.editTitle.collectAsState()
    val playbackState by playback.state.collectAsState()
    val transcriptionUi by viewModel.transcriptionUi.collectAsState()
    val enhanceUi by viewModel.enhanceUi.collectAsState()
    val enhanceStrength by viewModel.enhanceStrength.collectAsState()
    val languagePref by viewModel.languagePref.collectAsState()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    fun shareAudio(path: String, chooserTitle: String) {
        try {
            val uri = com.flambo.recorder.data.AudioFileStore.playableUri(context, path) ?: return
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "audio/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, chooserTitle))
        } catch (_: Exception) {}
    }

    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showTagDialog by remember { mutableStateOf(false) }
    var tagInput by remember { mutableStateOf("") }
    var showTranscribeSheet by remember { mutableStateOf(false) }
    var showTranscriptEditor by remember { mutableStateOf<String?>(null) }
    var showDetailsCard by remember { mutableStateOf(false) }

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

    val isCurrentTrack = playbackState.currentPath == rec.filePath
    val isThisPlaying = isCurrentTrack && playbackState.isPlaying
    val progress = if (isCurrentTrack && playbackState.durationMs > 0) (playbackState.positionMs.toFloat() / playbackState.durationMs).coerceIn(0f, 1f) else 0f
    val duration = if (isCurrentTrack && playbackState.durationMs > 0) playbackState.durationMs else rec.durationMs
    val positionForUi = if (isCurrentTrack) playbackState.positionMs else 0L
    val fileMissing = remember(rec.filePath) {
        !com.flambo.recorder.data.AudioFileStore.exists(context, rec.filePath)
    }

    val gestureEnabled by viewModel.gestureEnabled.collectAsState()
    val progressStyle by viewModel.progressStyle.collectAsState()
    val scale = remember { Animatable(1f) }
    val corner = remember { Animatable(0f) }
    val alpha = remember { Animatable(1f) }
    val offsetX = remember { Animatable(0f) }
    val gestureScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val velocityTracker = remember { VelocityTracker() }
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
                translationX = offsetX.value
                scaleX = scale.value
                scaleY = scale.value
                this.alpha = alpha.value
                shape = RoundedCornerShape(corner.value.dp)
                clip = corner.value > 0f
            }
            .clip(RoundedCornerShape(corner.value.dp))
            .pointerInput(gestureEnabled) {
                if (!gestureEnabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { velocityTracker.resetTracking() },
                    onDragEnd = {
                        val offset = offsetX.value
                        val velocity = runCatching { velocityTracker.calculateVelocity().x }.getOrDefault(0f)
                        val dismissDistance = with(density) { 100.dp.toPx() }
                        val velocityThreshold = with(density) { 400.dp.toPx() }
                        val shouldDismiss = offset > dismissDistance || velocity > velocityThreshold
                        if (shouldDismiss) {
                            gestureScope.launch {
                                offsetX.animateTo(with(density) { 1080.dp.toPx() }, spring(stiffness = Spring.StiffnessMedium))
                            }
                            gestureScope.launch {
                                kotlinx.coroutines.delay(80)
                                if (offsetX.value >= with(density) { 1080.dp.toPx() } * 0.5f) onBack()
                            }
                        } else {
                            gestureScope.launch {
                                offsetX.animateTo(0f, spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow))
                                scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioLowBouncy))
                                corner.animateTo(0f, spring(dampingRatio = Spring.DampingRatioLowBouncy))
                                alpha.animateTo(1f, spring(dampingRatio = Spring.DampingRatioLowBouncy))
                            }
                        }
                    },
                    onDragCancel = {
                        gestureScope.launch {
                            offsetX.animateTo(0f, spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow))
                            scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioLowBouncy))
                            corner.animateTo(0f, spring(dampingRatio = Spring.DampingRatioLowBouncy))
                            alpha.animateTo(1f, spring(dampingRatio = Spring.DampingRatioLowBouncy))
                        }
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        velocityTracker.addPosition(change.uptimeMillis, change.position)
                        val newOffset = max(0f, offsetX.value + dragAmount)
                        gestureScope.launch {
                            offsetX.snapTo(newOffset)
                            val progress = (newOffset / with(density) { 1080.dp.toPx() }).coerceIn(0f, 1f)
                            scale.snapTo((1f - progress * 0.05f).coerceIn(0.95f, 1f))
                            corner.snapTo(progress * 24f)
                            alpha.snapTo((1f - progress * 0.15f).coerceIn(0.85f, 1f))
                        }
                    }
                )
            }
    ) {
        // Granular shared targets matching the home cards: the shell uses
        // heavy-mass bounds while title/meta/waveform track it and the play
        // control flies on the overlay. Applied on the Scaffold — inside the
        // swipe-gesture Box — so dismiss and morph never fight on one node.
        // All states stay unconditional; only modifiers are gated.
        val containerState = with(sharedTransitionScope) {
            rememberSharedContentState(key = "container_${rec.id}")
        }
        val actionState = with(sharedTransitionScope) {
            rememberSharedContentState(key = "action_${rec.id}")
        }
        val titleState = with(sharedTransitionScope) {
            rememberSharedContentState(key = "title_${rec.id}")
        }
        val metaState = with(sharedTransitionScope) {
            rememberSharedContentState(key = "meta_${rec.id}")
        }
        val waveformState = with(sharedTransitionScope) {
            rememberSharedContentState(key = "waveform_${rec.id}")
        }
        val containerModifier = if (cardExpandAnimEnabled) {
            with(sharedTransitionScope) {
                Modifier.sharedBounds(
                    containerState,
                    animatedVisibilityScope,
                    boundsTransform = FlamboMotion.ContainerBoundsTransform
                )
            }
        } else {
            Modifier
        }
        val actionModifier = if (cardExpandAnimEnabled) {
            with(sharedTransitionScope) {
                Modifier
                    .sharedElement(
                        actionState,
                        animatedVisibilityScope,
                        boundsTransform = FlamboMotion.ActionBoundsTransform
                    )
                    .renderInSharedTransitionScopeOverlay()
            }
        } else {
            Modifier
        }
        val titleModifier = if (cardExpandAnimEnabled) {
            with(sharedTransitionScope) {
                Modifier
                    .sharedElement(
                        titleState,
                        animatedVisibilityScope,
                        boundsTransform = FlamboMotion.ContentBoundsTransform
                    )
                    .skipToLookaheadSize()
            }
        } else {
            Modifier
        }
        val metaModifier = if (cardExpandAnimEnabled) {
            with(sharedTransitionScope) {
                Modifier
                    .sharedElement(
                        metaState,
                        animatedVisibilityScope,
                        boundsTransform = FlamboMotion.ContentBoundsTransform
                    )
                    .skipToLookaheadSize()
            }
        } else {
            Modifier
        }
        val waveformModifier = if (cardExpandAnimEnabled) {
            with(sharedTransitionScope) {
                Modifier.sharedElement(
                    waveformState,
                    animatedVisibilityScope,
                    boundsTransform = FlamboMotion.ContentBoundsTransform
                )
            }
        } else {
            Modifier
        }
        // Light-mass pop when the favorite state flips.
        val favPop by animateFloatAsState(
            targetValue = if (rec.isFavorite) 1.18f else 1f,
            animationSpec = FlamboMotion.ActionSpringFloat,
            label = "detailFavPop"
        )
        Scaffold(
            modifier = Modifier.then(containerModifier),
            topBar = {
                TopAppBar(
                    title = { Text("Playback", style = MaterialTheme.typography.titleLarge) },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                    },
                    actions = {
                        IconButton(onClick = { showDetailsCard = !showDetailsCard }) {
                            Icon(Icons.Filled.Info, contentDescription = "Details", tint = if (showDetailsCard) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(
                            onClick = { viewModel.toggleFavorite() },
                            modifier = Modifier.graphicsLayer {
                                scaleX = favPop
                                scaleY = favPop
                            }
                        ) {
                            Icon(
                                imageVector = if (rec.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                contentDescription = "Favorite",
                                tint = if (rec.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { shareAudio(rec.filePath, "Share recording") }, enabled = !fileMissing) {
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
            if (fileMissing) {
                Surface(
                    shape = ShapeLargeIncreased,
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Audio file not found", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onErrorContainer)
                        Text(
                            "This recording was deleted outside the app (e.g. in a file manager). Playback, sharing, transcription and enhancement are unavailable — delete this entry or restore the file to fix it.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
            AnimatedVisibility(
                visible = showDetailsCard,
                enter = expandVertically(animationSpec = FlamboMotion.ContainerSizeSpring) +
                    fadeIn(animationSpec = FlamboMotion.ContentSpringFloat) +
                    scaleIn(animationSpec = FlamboMotion.ContentSpringFloat, initialScale = 0.96f),
                exit = shrinkVertically(animationSpec = FlamboMotion.ContainerSizeSpring) +
                    fadeOut(animationSpec = FlamboMotion.ContentSpringFloat) +
                    scaleOut(animationSpec = FlamboMotion.ContentSpringFloat, targetScale = 0.96f)
            ) {
                val detailsName = remember(rec.filePath) { com.flambo.recorder.data.AudioFileStore.displayName(context, rec.filePath).ifBlank { "—" } }
                val fileSize = remember(rec.filePath) { com.flambo.recorder.data.AudioFileStore.length(context, rec.filePath) }
                val fileDir = remember(rec.filePath) { com.flambo.recorder.data.AudioFileStore.locationLabel(context, rec.filePath).ifBlank { "—" } }
                Surface(
                    shape = ShapeLargeIncreased,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                Text("Recording details", style = MaterialTheme.typography.titleMedium)
                            }
                            IconButton(onClick = { showDetailsCard = false }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Filled.Close, contentDescription = "Hide details", modifier = Modifier.size(18.dp))
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Location", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(fileDir, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                                Text(detailsName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.Storage, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                            Column {
                                Text("Size", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(if (fileSize > 0) StorageVolumes.formatBytes(fileSize) else "—", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.CalendarToday, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                            Column {
                                Text("Date", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${formatRelativeTime(rec.createdAt)} • ${java.text.SimpleDateFormat("dd MMM yyyy, HH:mm", java.util.Locale.getDefault()).format(java.util.Date(rec.createdAt))}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                            Column {
                                Text("Duration", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(formatDuration(rec.durationMs) + " • ${rec.quality}", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }

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
                        Text(
                            rec.title,
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = titleModifier
                        )
                        Text(
                            "${formatDuration(rec.durationMs)} • ${formatRelativeTime(rec.createdAt)} • ${rec.quality}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = metaModifier
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

            Surface(
                shape = ShapeLargeIncreased,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    StaticWaveform(
                        peaks = remember(rec.peakList) { rec.peakList.ifEmpty { List(40) { 0.35f + (Math.random().toFloat() * 0.5f) } } },
                        progress = progress,
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(waveformModifier)
                            .graphicsLayer {
                                clip = true
                                shape = RoundedCornerShape(16.dp)
                            }
                    )

                    PlaybackProgressBar(
                        progress = progress,
                        enabled = !fileMissing,
                        style = progressStyle,
                        onSeek = { p ->
                            if (isCurrentTrack) {
                                val target = (p * duration).toLong()
                                playback.seekTo(target)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(formatDuration(positionForUi), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(formatDuration(duration), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val skipBackPress = rememberTransportPress(enabled = isCurrentTrack && !fileMissing)
                        FilledTonalButton(
                            onClick = { if (isCurrentTrack) playback.skip(-5000) },
                            enabled = isCurrentTrack && !fileMissing,
                            shape = RoundedCornerShape(
                                topStart = 20.dp, topEnd = 8.dp,
                                bottomStart = 20.dp, bottomEnd = 8.dp
                            ),
                            contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight),
                            interactionSource = skipBackPress.interaction,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = ButtonDefaults.MediumContainerHeight)
                                .then(skipBackPress.modifier)
                        ) {
                            Icon(
                                Icons.Filled.Replay5,
                                contentDescription = "Back 5s",
                                modifier = Modifier.size(ButtonDefaults.iconSizeFor(ButtonDefaults.MediumContainerHeight))
                            )
                        }

                        val playPress = rememberTransportPress(enabled = !fileMissing)
                        androidx.compose.material3.Button(
                            onClick = {
                                if (fileMissing) return@Button
                                if (isThisPlaying) playback.pause()
                                else playback.play(rec.filePath)
                            },
                            enabled = !fileMissing,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight),
                            interactionSource = playPress.interaction,
                            modifier = Modifier
                                .weight(1.4f)
                                .heightIn(min = ButtonDefaults.MediumContainerHeight)
                                .then(playPress.modifier)
                                .then(actionModifier)
                        ) {
                            AnimatedContent(
                                targetState = isThisPlaying,
                                transitionSpec = {
                                    (scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn()) togetherWith
                                        (scaleOut(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeOut())
                                },
                                label = "playpause"
                            ) { playing ->
                                Icon(
                                    imageVector = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    contentDescription = if (playing) "Pause" else "Play",
                                    modifier = Modifier.size(ButtonDefaults.iconSizeFor(ButtonDefaults.MediumContainerHeight))
                                )
                            }
                        }

                        val skipForwardPress = rememberTransportPress(enabled = isCurrentTrack && !fileMissing)
                        FilledTonalButton(
                            onClick = { if (isCurrentTrack) playback.skip(5000) },
                            enabled = isCurrentTrack && !fileMissing,
                            shape = RoundedCornerShape(
                                topStart = 8.dp, topEnd = 20.dp,
                                bottomStart = 8.dp, bottomEnd = 20.dp
                            ),
                            contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight),
                            interactionSource = skipForwardPress.interaction,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = ButtonDefaults.MediumContainerHeight)
                                .then(skipForwardPress.modifier)
                        ) {
                            Icon(
                                Icons.Filled.Forward5,
                                contentDescription = "Forward 5s",
                                modifier = Modifier.size(ButtonDefaults.iconSizeFor(ButtonDefaults.MediumContainerHeight))
                            )
                        }
                    }

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
                            // Light-mass elastic pop when a speed becomes active.
                            val speedPop by animateFloatAsState(
                                targetValue = if (selected) 1.1f else 1f,
                                animationSpec = FlamboMotion.ActionSpringFloat,
                                label = "speedPop$speed"
                            )
                            ToggleButton(
                                checked = selected,
                                onCheckedChange = { playback.setSpeed(speed) },
                                shapes = when (index) {
                                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                    speeds.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                                },
                                modifier = Modifier.graphicsLayer {
                                    scaleX = speedPop
                                    scaleY = speedPop
                                }
                            ) {
                                Text("${speed}x", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }

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
                onClearSaved = { viewModel.clearSavedTranscript() },
                fileMissing = fileMissing
            )

            EnhanceSection(
                savedEnhancedPath = rec.enhancedPath,
                enhanceUi = enhanceUi,
                strengthLabel = EnhanceStrength.fromPref(enhanceStrength).label,
                playback = playback,
                onEnhance = { viewModel.enhance() },
                onCancel = { viewModel.cancelEnhance() },
                onDismiss = { viewModel.dismissEnhance() },
                onShareEnhanced = { path -> shareAudio(path, "Share cleaned recording") },
                onDeleteEnhanced = { viewModel.deleteEnhanced() },
                fileMissing = fileMissing,
                progressStyle = progressStyle
            )

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
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Move to trash?") },
            text = { Text("\"${rec.title}\" will be moved to trash and auto-deleted after 7 days. You can restore it from the Home trash view.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    playback.stopIfCurrent(rec.filePath, rec.enhancedPath)
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
            languageTag = languagePref,
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

private class TransportPress(
    val interaction: MutableInteractionSource,
    val modifier: Modifier
)

/**
 * Press-and-shrink feedback for the transport buttons. Returns the
 * interaction source to feed the button plus a scale modifier — GPU-only,
 * no remeasure. Uses only long-stable APIs.
 */
@Composable
private fun rememberTransportPress(enabled: Boolean): TransportPress {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.93f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "transportPress"
    )
    return TransportPress(
        interaction = interaction,
        modifier = Modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
    )
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
    onClearSaved: () -> Unit,
    fileMissing: Boolean = false
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
                    enabled = !fileMissing,
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
private fun EnhanceSection(
    savedEnhancedPath: String,
    enhanceUi: EnhanceUi,
    strengthLabel: String,
    playback: com.flambo.recorder.playback.PlaybackController,
    onEnhance: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
    onShareEnhanced: (String) -> Unit,
    onDeleteEnhanced: () -> Unit,
    fileMissing: Boolean = false,
    progressStyle: String = "slider"
) {
    when (enhanceUi) {
        is EnhanceUi.Working -> {
            Surface(
                shape = ShapeLargeIncreased,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.AutoFixHigh, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Cleaning audio…", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        TextButton(onClick = onCancel) { Text("Cancel") }
                    }
                    LinearProgressIndicator(
                        progress = { enhanceUi.progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "${(enhanceUi.progress * 100).toInt()}% — decoding, hush, level",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        is EnhanceUi.Done -> {
            androidx.compose.material3.ElevatedCard(
                shape = ShapeLargeIncreased,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.AutoFixHigh, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            if (enhanceUi.replaced) "Original replaced with the cleaned version"
                            else "Sparkling clean",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onDismiss) { Text("Dismiss") }
                    }
                    Text(
                        "Cleaned audio ready — opened in a dedicated player below",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            CleanedPlayerCard(playback = playback, filePath = enhanceUi.path, onShare = onShareEnhanced, progressStyle = progressStyle)
        }
        is EnhanceUi.Error -> {
            Surface(
                shape = ShapeLargeIncreased,
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Couldn't clean this one", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onErrorContainer)
                    Text(enhanceUi.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = onEnhance, shape = ShapeFull) { Text("Try again") }
                        TextButton(onClick = onDismiss) { Text("Dismiss") }
                    }
                }
            }
        }
        is EnhanceUi.Idle -> {
            if (savedEnhancedPath.isNotBlank()) {
                androidx.compose.material3.ElevatedCard(
                    shape = ShapeLargeIncreased,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.AutoFixHigh, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("Cleaned copy saved", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        }
                        Text(
                            "Tap play to listen in a dedicated player",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                CleanedPlayerCard(playback = playback, filePath = savedEnhancedPath, onShare = onShareEnhanced, onDelete = onDeleteEnhanced, progressStyle = progressStyle)
            } else {
                OutlinedButton(
                    onClick = onEnhance,
                    enabled = !fileMissing,
                    shape = ShapeFull,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.AutoFixHigh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Clean audio • $strengthLabel")
                }
            }
        }
    }
}

@Composable
private fun CleanedPlayerCard(
    playback: com.flambo.recorder.playback.PlaybackController,
    filePath: String,
    onShare: (String) -> Unit,
    onDelete: (() -> Unit)? = null,
    progressStyle: String = "slider"
) {
    val playbackState by playback.state.collectAsState()
    val isCurrent = playbackState.currentPath == filePath
    val isPlaying = isCurrent && playbackState.isPlaying
    val progress = if (isCurrent && playbackState.durationMs > 0) (playbackState.positionMs.toFloat() / playbackState.durationMs).coerceIn(0f, 1f) else 0f
    val duration = if (isCurrent && playbackState.durationMs > 0) playbackState.durationMs else 0L
    val position = if (isCurrent) playbackState.positionMs else 0L

    androidx.compose.material3.ElevatedCard(
        shape = com.flambo.recorder.ui.theme.ShapeLargeIncreased,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.AutoFixHigh, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Text("Cleaned playback", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                IconButton(onClick = { onShare(filePath) }) { Icon(Icons.Filled.Share, contentDescription = "Share cleaned") }
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge) }
                }
            }
            PlaybackProgressBar(
                progress = progress,
                style = progressStyle,
                onSeek = { p ->
                    if (isCurrent) {
                        val target = (p * duration).toLong()
                        playback.seekTo(target)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(com.flambo.recorder.domain.formatDuration(position), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(com.flambo.recorder.domain.formatDuration(duration), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                modifier = Modifier.fillMaxWidth()
            ) {
                FilledTonalButton(
                    onClick = { if (isPlaying) playback.pause() else playback.play(filePath) },
                    shape = com.flambo.recorder.ui.theme.ShapeFull,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (isPlaying) "Pause" else "Play", maxLines = 1)
                }
                OutlinedButton(
                    onClick = { onShare(filePath) },
                    shape = com.flambo.recorder.ui.theme.ShapeFull,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Share", maxLines = 1)
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
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                androidx.compose.material3.AssistChip(onClick = {}, label = { Text(badge, maxLines = 1) })
                androidx.compose.foundation.layout.Box(modifier = Modifier.weight(1f))
                if (!saved) {
                    TextButton(onClick = onDiscard) { Text("Discard", maxLines = 1) }
                    FilledTonalButton(onClick = { onSave(text) }, shape = ShapeFull, contentPadding = ButtonDefaults.ButtonWithIconContentPadding) {
                        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Save", style = MaterialTheme.typography.labelLarge, maxLines = 1)
                    }
                } else {
                    TextButton(onClick = onClearSaved) {
                        Text("Delete", color = MaterialTheme.colorScheme.error, maxLines = 1)
                    }
                }
            }
            Text(text, style = MaterialTheme.typography.bodyLarge)
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = { onCopy(text); justCopied = true },
                    shape = ShapeFull,
                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        if (justCopied) Icons.Filled.Check else Icons.Filled.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(if (justCopied) "Copied" else "Copy", style = MaterialTheme.typography.labelLarge, maxLines = 1)
                }
                OutlinedButton(
                    onClick = { onShareText(text) },
                    shape = ShapeFull,
                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Share", style = MaterialTheme.typography.labelLarge, maxLines = 1)
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
    languageTag: String,
    onLanguageChange: (String) -> Unit,
    onStart: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    var langExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val app = context.applicationContext as FlamboApp
    val modelProgress by app.transcription.modelProgress.collectAsState()
    val whisperProgress by app.transcription.whisperProgress.collectAsState()
    val sttEngine by app.prefs.sttEngineFlow.collectAsState(initial = "vosk")
    val isWhisper = sttEngine == "whisper"
    val scope = rememberCoroutineScope()
    var downloading by remember { mutableStateOf<String?>(null) }

    val currentBase = languageTag.substringBefore('-').substringBefore('_').lowercase().ifBlank { "en" }
    val modelEntry = VoskModelManager.forTag(currentBase)
    val installed = remember(languageTag, modelProgress, whisperProgress, isWhisper) {
        if (isWhisper) app.transcription.whisper.models.isInstalled()
        else app.transcription.vosk.models.isInstalled(currentBase)
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
                if (isWhisper) "Whisper turns this file into text on your device — no cloud, no account. One tiny model covers 95+ languages."
                else "Vosk turns this file into text on your device — no cloud, no account. Download the language model once, reuse forever.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text("Language", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            ExposedDropdownMenuBox(expanded = langExpanded, onExpandedChange = { langExpanded = it }) {
                val displayLabel = if (isWhisper) {
                    when (languageTag) {
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
                        else -> languageTag.ifBlank { "Auto (detect)" }
                    }
                } else modelEntry?.label ?: languageTag.ifBlank { "Choose a language" }
                OutlinedTextField(
                    value = displayLabel,
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = langExpanded) },
                    shape = ShapeLargeIncreased,
                    modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                )
                ExposedDropdownMenu(expanded = langExpanded, onDismissRequest = { langExpanded = false }) {
                    if (isWhisper) {
                        val whisperLangs = listOf(
                            "" to "Auto (detect)",
                            "en" to "English",
                            "id" to "Indonesian",
                            "ar" to "Arabic",
                            "fr" to "French",
                            "es" to "Spanish",
                            "de" to "German",
                            "it" to "Italian",
                            "pt" to "Portuguese",
                            "ru" to "Russian",
                            "zh" to "Chinese",
                            "ja" to "Japanese",
                            "ko" to "Korean",
                            "hi" to "Hindi",
                            "tr" to "Turkish",
                            "nl" to "Dutch",
                            "pl" to "Polish",
                            "vi" to "Vietnamese",
                            "th" to "Thai",
                            "ms" to "Malay"
                        )
                        whisperLangs.forEach { (code, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    onLanguageChange(code)
                                    langExpanded = false
                                }
                            )
                        }
                    } else {
                        VoskModelManager.CATALOG.forEach { model ->
                            DropdownMenuItem(
                                text = { Text(model.label) },
                                trailingIcon = {
                                    Text(
                                        if (app.transcription.vosk.models.isInstalled(model.code)) "Ready"
                                        else "~${model.sizeMb} MB",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                onClick = {
                                    onLanguageChange(model.code)
                                    langExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (isWhisper) {
                        Text(
                            if (installed) "Whisper tiny model ready • 95+ languages"
                            else "Whisper tiny model • ~75 MB",
                            style = MaterialTheme.typography.titleSmall
                        )
                        val prog = whisperProgress["whisper"]
                        if (prog != null) {
                            LinearProgressIndicator(progress = { prog }, modifier = Modifier.fillMaxWidth())
                            Text("Downloading… ${(prog * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else if (!installed) {
                            Text("One-time download, then fully offline.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
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
                }
                if (!installed && downloading == null) {
                    if (isWhisper) {
                        FilledTonalButton(
                            onClick = {
                                downloading = "whisper"
                                scope.launch {
                                    app.transcription.whisper.models.download()
                                    downloading = null
                                }
                            },
                            shape = ShapeFull
                        ) {
                            Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Get")
                        }
                    } else if (modelEntry != null) {
                        FilledTonalButton(
                            onClick = {
                                downloading = currentBase
                                scope.launch {
                                    val result = app.transcription.vosk.models.download(currentBase)
                                    downloading = null
                                    if (result.isFailure) {

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
            }

            FilledTonalButton(
                onClick = onStart,
                enabled = installed && downloading == null,
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
