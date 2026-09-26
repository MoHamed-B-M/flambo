package com.flambo.recorder.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DockedSearchBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.TextField
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import com.flambo.recorder.data.PreferencesManager
import com.flambo.recorder.data.StorageVolumes
import com.flambo.recorder.update.ReleaseNotes
import com.flambo.recorder.update.UpdateChecker
import com.flambo.recorder.update.UpdateNotifier
import com.flambo.recorder.update.WhatsNewItem
import kotlinx.coroutines.flow.first
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.flambo.recorder.data.Recording
import com.flambo.recorder.domain.RecordingQuality
import com.flambo.recorder.domain.formatDuration
import com.flambo.recorder.domain.formatRelativeTime
import com.flambo.recorder.playback.PlaybackController
import com.flambo.recorder.record.AudioSource
import com.flambo.recorder.record.RecordingController
import com.flambo.recorder.ui.components.AppTips
import com.flambo.recorder.ui.components.TipCard
import kotlinx.coroutines.launch
import com.flambo.recorder.ui.components.GroupColorRow
import com.flambo.recorder.ui.components.GroupFolder
import com.flambo.recorder.ui.components.GroupFolderCard
import com.flambo.recorder.ui.components.LibraryTabs
import com.flambo.recorder.ui.components.RecordingCard
import com.flambo.recorder.ui.components.RecordingGridTile
import com.flambo.recorder.ui.components.WaveformVisualizer
import com.flambo.recorder.ui.theme.FlamboMotion
import com.flambo.recorder.ui.theme.ShapeFull
import com.flambo.recorder.ui.theme.ShapeLargeIncreased

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    recorder: RecordingController,
    playback: PlaybackController,
    prefs: PreferencesManager,
    quality: RecordingQuality = RecordingQuality.HIGH,
    audioSource: String = "mic",
    noiseReduction: Boolean = true,
    homeLayout: String = "list",
    onOpenDetail: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    onEnableSystemSound: () -> Unit = {},
    onRequestMicPermission: (AudioSource) -> Unit = {},
    modifier: Modifier = Modifier,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    cardExpandAnimEnabled: Boolean = true
) {
    val uiState by viewModel.uiState.collectAsState()
    val recorderState by recorder.state.collectAsState()
    val cardExpandAnim by prefs.cardExpandAnimFlow.collectAsState(initial = true)
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    fun startRecording() {
        val source = AudioSource.fromPref(audioSource)

        if (source == AudioSource.SYSTEM && !AudioSource.SYSTEM_ENABLED) {
            scope.launch {
                snackbarHostState.showSnackbar(
                    "System sound is under development and temporarily disabled",
                    withDismissAction = true
                )
            }
            return
        }

        if (!recorder.hasRecordAudioPermission()) {
            scope.launch {
                val r = snackbarHostState.showSnackbar(
                    message = if (source == AudioSource.SYSTEM)
                        "System sound needs microphone permission too"
                    else "Microphone permission needed to record",
                    actionLabel = "Allow",
                    withDismissAction = true
                )
                if (r == SnackbarResult.ActionPerformed) onRequestMicPermission(source)
            }
            return
        }
        if (!recorder.start(quality, source, noiseReduction)) {
            scope.launch {
                if (source == AudioSource.SYSTEM) {

                    val r = snackbarHostState.showSnackbar(
                        message = "System sound needs permission",
                        actionLabel = "Enable",
                        withDismissAction = true
                    )
                    if (r == SnackbarResult.ActionPerformed) onEnableSystemSound()
                } else {
                    snackbarHostState.showSnackbar("Couldn't start recording — try again")
                }
            }
        }
    }

    var showRenameDialog by remember { mutableStateOf<Recording?>(null) }
    var renameText by remember { mutableStateOf("") }
    var showEmptyTrashConfirm by remember { mutableStateOf(false) }

    var selection by remember { mutableStateOf(setOf<Long>()) }
    val selectionMode = selection.isNotEmpty()
    var showBulkDeleteConfirm by remember { mutableStateOf(false) }
    var showMoveDialog by remember { mutableStateOf(false) }
    var showGroupDialog by remember { mutableStateOf(false) }
    var groupText by remember { mutableStateOf("") }
    var groupColorDraft by remember { mutableStateOf<Int?>(null) }
    var libraryTab by rememberSaveable { mutableIntStateOf(0) }
    var openGroup by rememberSaveable { mutableStateOf<String?>(null) }
    var recolorGroup by remember { mutableStateOf<String?>(null) }

    BackHandler(enabled = selectionMode) { selection = emptySet() }
    var searchExpanded by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    BackHandler(enabled = searchExpanded && !selectionMode) { searchExpanded = false }
    LaunchedEffect(recorderState.isRecording) {
        if (recorderState.isRecording) searchExpanded = false
    }
    var whatsNewVersion by remember { mutableStateOf<String?>(null) }
    var installedVersion by remember { mutableStateOf<String?>(null) }
    val tipsEnabled by prefs.tipsEnabledFlow.collectAsState(initial = true)
    val tipIndex by prefs.tipIndexFlow.collectAsState(initial = 0)
    val groupColors by prefs.groupColorsFlow.collectAsState(initial = emptyMap())
    val groups = remember(uiState.recordings, groupColors) {
        uiState.recordings.flatMap { it.tagList }.distinct().sorted()
            .map { name ->
                GroupFolder(
                    name = name,
                    count = uiState.recordings.count { name in it.tagList },
                    colorArgb = groupColors[name]
                )
            }
    }

    var whatsNewItems by remember { mutableStateOf<List<WhatsNewItem>?>(null) }

    LaunchedEffect(Unit) {
        val (version, code) = UpdateChecker.installed(context)
        installedVersion = version
        val lastSeen = prefs.lastSeenVersionCode()
        if (lastSeen != 0L && code > lastSeen) {
            whatsNewVersion = version
            whatsNewItems = ReleaseNotes.loadWhatsNew(context)
        }
        prefs.setLastSeenVersionCode(code)
    }

    LaunchedEffect(Unit) {
        if (!prefs.autoUpdateCheckFlow.first()) return@LaunchedEffect
        val channel = prefs.updateChannelFlow.first()
        val res = UpdateChecker.check(context, channel)
        if (!res.available || res.release == null) {
            prefs.clearNotifiedUpdateVersion()
            return@LaunchedEffect
        }
        val key = "${res.channel}:${res.release.version}#${res.release.buildNumber}"
        if (prefs.notifiedUpdateVersion() == key) return@LaunchedEffect
        prefs.setNotifiedUpdateVersion(key)
        UpdateNotifier.show(context, res.release)
        val r = snackbarHostState.showSnackbar(
            message = "New update ready: v${res.release.version}",
            actionLabel = "View",
            withDismissAction = true
        )
        if (r == SnackbarResult.ActionPerformed) onOpenSettings()
    }

    val playbackError by playback.error.collectAsState()
    LaunchedEffect(playbackError) {
        val err = playbackError ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(err, withDismissAction = true)
        playback.clearError()
    }

    LaunchedEffect(uiState.lastDeleted) {
        val deleted = uiState.lastDeleted ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = "\"${deleted.title}\" moved to trash",
            actionLabel = "Undo",
            withDismissAction = true
        )
        if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete()
        else viewModel.dismissUndo()
    }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    if (selectionMode) {
                        Column {
                            Text("${selection.size} selected", style = MaterialTheme.typography.displaySmall)
                            Text(
                                "tap cards to toggle",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Column {
                            Text("Flambo", style = MaterialTheme.typography.displaySmall)
                            Text(
                                "${uiState.recordings.size} recordings • tap to play, hold to record",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    if (selectionMode) {
                        IconButton(onClick = { groupText = ""; groupColorDraft = null; showGroupDialog = true }) {
                            Icon(Icons.Filled.Group, contentDescription = "Group")
                        }
                        IconButton(onClick = { showMoveDialog = true }) {
                            Icon(Icons.Filled.DriveFileMove, contentDescription = "Move")
                        }
                        IconButton(onClick = { showBulkDeleteConfirm = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete")
                        }
                        IconButton(onClick = { selection = emptySet() }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear selection")
                        }
                    } else {
                        IconButton(onClick = {
                            installedVersion?.let {
                                whatsNewVersion = it
                                if (whatsNewItems == null) {
                                    scope.launch { whatsNewItems = ReleaseNotes.loadWhatsNew(context) }
                                }
                            }
                        }) {
                            Icon(Icons.Filled.Info, contentDescription = "What's new")
                        }
                        IconButton(
                            onClick = {
                                if (recorderState.isRecording) return@IconButton
                                selection = emptySet()
                                viewModel.toggleTrash(!uiState.showTrash)
                            },
                            enabled = !recorderState.isRecording
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = "Trash")
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Filled.Settings, contentDescription = "Settings")
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = !recorderState.isRecording && !selectionMode && !uiState.showTrash,
                enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)) + fadeIn(spring(dampingRatio = 0.8f)),
                exit = scaleOut(spring(dampingRatio = 0.9f)) + fadeOut()
            ) {

                Button(
                    onClick = { startRecording() },
                    shapes = ButtonDefaults.shapes(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.LargeContainerHeight),
                    modifier = Modifier.heightIn(min = ButtonDefaults.LargeContainerHeight)
                ) {
                    Icon(
                        Icons.Filled.Mic,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.iconSizeFor(ButtonDefaults.LargeContainerHeight))
                    )
                    Spacer(Modifier.size(ButtonDefaults.iconSpacingFor(ButtonDefaults.LargeContainerHeight)))
                    Text("Record", style = ButtonDefaults.textStyleFor(ButtonDefaults.LargeContainerHeight))
                }
            }
        },
        floatingActionButtonPosition = FabPosition.Center,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.surface
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // While recording, only the recording card stays visible.
            if (!recorderState.isRecording) {
            DockedSearchBar(
                inputField = {
                    TextField(
                        value = uiState.query,
                        onValueChange = viewModel::onQueryChange,
                        placeholder = { Text("Search recordings") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        trailingIcon = {
                            if (uiState.query.isNotEmpty()) {
                                IconButton(onClick = viewModel::clearQuery) {
                                    Icon(Icons.Filled.Close, contentDescription = "Clear")
                                }
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                keyboard?.hide()
                                searchExpanded = false
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { if (it.isFocused) searchExpanded = true }
                    )
                },
                expanded = searchExpanded,
                onExpandedChange = { searchExpanded = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 8.dp)
            ) {
                    if (uiState.query.isBlank()) {
                        Text(
                            "Type to search your recordings",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                        )
                    } else if (uiState.recordings.isEmpty()) {
                        Text(
                            "No recordings match",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                        )
                    } else {
                        uiState.recordings.take(8).forEach { rec ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        searchExpanded = false
                                        keyboard?.hide()
                                        onOpenDetail(rec.id)
                                    }
                                    .padding(horizontal = 16.dp, vertical = 10.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Search,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = rec.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${formatDuration(rec.durationMs)} • ${formatRelativeTime(rec.createdAt)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            LibraryTabs(
                selectedIndex = libraryTab,
                onSelect = {
                    libraryTab = it
                    if (it == 1) {
                        viewModel.clearQuery()
                        openGroup = null
                    }
                }
            )

            if (tipsEnabled && !recorderState.isRecording && libraryTab == 0) {
                val tip = AppTips[tipIndex.mod(AppTips.size)]
                TipCard(
                    tip = tip,
                    position = "${tipIndex.mod(AppTips.size) + 1} of ${AppTips.size}",
                    onNext = { scope.launch { prefs.setTipIndex(tipIndex + 1) } },
                    onHide = { scope.launch { prefs.setTipsEnabled(false) } },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            AnimatedVisibility(
                visible = recorderState.isRecording,
                enter = expandVertically(animationSpec = FlamboMotion.ContainerSizeSpring) +
                    fadeIn(animationSpec = FlamboMotion.ContentSpringFloat) +
                    scaleIn(animationSpec = FlamboMotion.ContainerSpatialSpringFloat, initialScale = 0.94f),
                exit = shrinkVertically(animationSpec = FlamboMotion.ContainerSizeSpring) +
                    fadeOut(animationSpec = FlamboMotion.ContentSpringFloat) +
                    scaleOut(animationSpec = FlamboMotion.ContentSpringFloat, targetScale = 0.94f)
            ) {
                ActiveRecordingPanel(
                    elapsedMs = recorderState.elapsedMs,
                    amplitude = recorderState.amplitude,
                    peaks = recorderState.peaks,
                    isPaused = recorderState.isPaused,
                    source = recorderState.source,
                    onPauseResume = { if (recorderState.isPaused) recorder.resume() else recorder.pause() },
                    onStop = { recorder.stop() },
                    onCancel = { recorder.cancel() }
                )
            }



            if (!recorderState.isRecording) {
            if (uiState.showTrash) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Trash", style = MaterialTheme.typography.titleMedium)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (uiState.trash.isNotEmpty()) {
                            FilledTonalButton(
                                onClick = { showEmptyTrashConfirm = true },
                                shapes = ButtonDefaults.shapes()
                            ) { Text("Empty trash") }
                        }
                        TextButton(onClick = { viewModel.toggleTrash(false) }) { Text("Done") }
                    }
                }
                if (uiState.trash.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("Trash is empty", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(uiState.trash, key = { it.id }) { rec ->
                            TrashRow(
                                recording = rec,
                                onRestore = { viewModel.restoreFromTrash(rec.id) },
                                onDeleteForever = {
                                    playback.stopIfCurrent(rec.filePath, rec.enhancedPath)
                                    viewModel.permanentDelete(rec.id)
                                },
                                modifier = Modifier.animateItem(
                                    fadeInSpec = null,
                                    placementSpec = FlamboMotion.PlacementSpring,
                                    fadeOutSpec = null
                                )
                            )
                        }
                    }
                }
            } else if (libraryTab == 0) {

                if (uiState.recordings.isEmpty() && !recorderState.isRecording) {
                    EmptyState(onRecord = { startRecording() }, modifier = Modifier.fillMaxSize())
                } else if (homeLayout == "grid") {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(uiState.recordings, key = { it.id }) { rec ->
                            RecordingGridTile(
                                recording = rec,
                                playback = playback,
                                selected = rec.id in selection,
                                fileMissing = rec.id in uiState.missingIds,
                                sharedTransitionScope = sharedTransitionScope,
                                animatedVisibilityScope = animatedVisibilityScope,
                                expandAnimationEnabled = cardExpandAnimEnabled && cardExpandAnim,
                                onClick = {
                                    if (selectionMode) {
                                        selection = if (rec.id in selection) selection - rec.id else selection + rec.id
                                    } else {
                                        onOpenDetail(rec.id)
                                    }
                                },
                                onLongClick = { selection = selection + rec.id },
                                modifier = Modifier.animateItem(
                                    fadeInSpec = null,
                                    placementSpec = FlamboMotion.PlacementSpring,
                                    fadeOutSpec = null
                                )
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(uiState.recordings, key = { it.id }) { rec ->
                            RecordingCard(
                                recording = rec,
                                playback = playback,
                                fileMissing = rec.id in uiState.missingIds,
                                onClick = {
                                    if (selectionMode) {
                                        selection = if (rec.id in selection) selection - rec.id else selection + rec.id
                                    } else {
                                        onOpenDetail(rec.id)
                                    }
                                },
                                onFavorite = { viewModel.toggleFavorite(rec.id) },
                                onDelete = {
                                    playback.stopIfCurrent(rec.filePath, rec.enhancedPath)
                                    viewModel.softDelete(rec)
                                },
                                onRename = {
                                    showRenameDialog = rec
                                    renameText = rec.title
                                },
                                selected = rec.id in selection,
                                onLongClick = { selection = selection + rec.id },
                                sharedTransitionScope = sharedTransitionScope,
                                animatedVisibilityScope = animatedVisibilityScope,
                                expandAnimationEnabled = cardExpandAnimEnabled && cardExpandAnim,
                                modifier = Modifier.animateItem(
                                    fadeInSpec = null,
                                    placementSpec = FlamboMotion.PlacementSpring,
                                    fadeOutSpec = null
                                )
                            )
                        }
                    }
                }
            } else {
                val group = openGroup
                if (group == null) {
                    if (groups.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No groups yet — long-press recordings, tap Group, and give it a name",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(groups, key = { it.name }) { folder ->
                                GroupFolderCard(
                                    group = folder,
                                    onOpen = { openGroup = folder.name },
                                    onRecolor = {
                                        recolorGroup = folder.name
                                        groupColorDraft = folder.colorArgb
                                    },
                                    modifier = Modifier.animateItem(
                                        fadeInSpec = null,
                                        placementSpec = FlamboMotion.PlacementSpring,
                                        fadeOutSpec = null
                                    )
                                )
                            }
                        }
                    }
                } else {
                    val members = remember(group, uiState.recordings) {
                        uiState.recordings.filter { group in it.tagList }
                    }
                    val folderColor = groups.firstOrNull { it.name == group }?.colorArgb
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(onClick = { openGroup = null }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to groups")
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = group,
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${members.size} recordings",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = {
                            recolorGroup = group
                            groupColorDraft = folderColor
                        }) {
                            Icon(Icons.Filled.Palette, contentDescription = "Folder color")
                        }
                    }
                    if (members.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No recordings in this group",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else if (homeLayout == "grid") {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(members, key = { it.id }) { rec ->
                                RecordingGridTile(
                                    recording = rec,
                                    playback = playback,
                                    selected = rec.id in selection,
                                    fileMissing = rec.id in uiState.missingIds,
                                    sharedTransitionScope = sharedTransitionScope,
                                    animatedVisibilityScope = animatedVisibilityScope,
                                    expandAnimationEnabled = cardExpandAnimEnabled && cardExpandAnim,
                                    onClick = {
                                        if (selectionMode) {
                                            selection = if (rec.id in selection) selection - rec.id else selection + rec.id
                                        } else {
                                            onOpenDetail(rec.id)
                                        }
                                    },
                                    onLongClick = { selection = selection + rec.id },
                                    modifier = Modifier.animateItem(
                                        fadeInSpec = null,
                                        placementSpec = FlamboMotion.PlacementSpring,
                                        fadeOutSpec = null
                                    )
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(members, key = { it.id }) { rec ->
                                RecordingCard(
                                    recording = rec,
                                    playback = playback,
                                    fileMissing = rec.id in uiState.missingIds,
                                    onClick = {
                                        if (selectionMode) {
                                            selection = if (rec.id in selection) selection - rec.id else selection + rec.id
                                        } else {
                                            onOpenDetail(rec.id)
                                        }
                                    },
                                    onFavorite = { viewModel.toggleFavorite(rec.id) },
                                    onDelete = {
                                        playback.stopIfCurrent(rec.filePath, rec.enhancedPath)
                                        viewModel.softDelete(rec)
                                    },
                                    onRename = {
                                        showRenameDialog = rec
                                        renameText = rec.title
                                    },
                                    selected = rec.id in selection,
                                    onLongClick = { selection = selection + rec.id },
                                    sharedTransitionScope = sharedTransitionScope,
                                    animatedVisibilityScope = animatedVisibilityScope,
                                    expandAnimationEnabled = cardExpandAnimEnabled && cardExpandAnim,
                                    modifier = Modifier.animateItem(
                                        fadeInSpec = null,
                                        placementSpec = FlamboMotion.PlacementSpring,
                                        fadeOutSpec = null
                                    )
                                )
                            }
                        }
                    }
                }
            }
            }
        }
    }

    whatsNewVersion?.let { version ->
        WhatsNewSheet(
            version = version,
            highlights = whatsNewItems,
            onDismiss = { whatsNewVersion = null }
        )
    }

    if (showEmptyTrashConfirm) {
        val count = uiState.trash.size
        AlertDialog(
            onDismissRequest = { showEmptyTrashConfirm = false },
            title = { Text("Empty trash?") },
            text = {
                Text(
                    "Permanently delete $count recording${if (count == 1) "" else "s"}? This cannot be undone."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    playback.stopIfCurrent(*uiState.trash.flatMap { listOf(it.filePath, it.enhancedPath) }.toTypedArray())
                    viewModel.emptyTrash()
                    showEmptyTrashConfirm = false
                }) { Text("Delete all", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showEmptyTrashConfirm = false }) { Text("Cancel") } },
            shape = ShapeLargeIncreased
        )
    }

    if (showBulkDeleteConfirm) {
        val count = selection.size
        AlertDialog(
            onDismissRequest = { showBulkDeleteConfirm = false },
            title = { Text("Move to trash?") },
            text = {
                Text("Move $count recording${if (count == 1) "" else "s"} to trash?")
            },
            confirmButton = {
                TextButton(onClick = {
                    val doomed = (uiState.recordings + uiState.trash)
                        .filter { it.id in selection }
                        .flatMap { listOf(it.filePath, it.enhancedPath) }
                    playback.stopIfCurrent(*doomed.toTypedArray())
                    viewModel.softDeleteAll(selection)
                    selection = emptySet()
                    showBulkDeleteConfirm = false
                }) { Text("Move to trash", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showBulkDeleteConfirm = false }) { Text("Cancel") } },
            shape = ShapeLargeIncreased
        )
    }

    if (showMoveDialog) {
        val volumes = remember { StorageVolumes.list(context.applicationContext) }
        AlertDialog(
            onDismissRequest = { showMoveDialog = false },
            title = { Text("Move ${selection.size} to…", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    volumes.forEach { volume ->
                        FilledTonalButton(
                            onClick = {
                                val ids = selection
                                val label = volume.label
                                showMoveDialog = false
                                selection = emptySet()
                                viewModel.moveRecordings(ids, volume.dir) { n ->
                                    scope.launch {
                                        snackbarHostState.showSnackbar(
                                            if (n == ids.size) "Moved $n to $label"
                                            else "Moved $n of ${ids.size} to $label",
                                            withDismissAction = true
                                        )
                                    }
                                }
                            },
                            shapes = ButtonDefaults.shapes(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "${volume.label} • ${StorageVolumes.formatBytes(volume.freeBytes)}",
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showMoveDialog = false },
                    shapes = ButtonDefaults.shapes()
                ) { Text("Cancel") }
            },
            shape = ShapeLargeIncreased
        )
    }

    if (showGroupDialog) {
        AlertDialog(
            onDismissRequest = { showGroupDialog = false },
            title = { Text("Group ${selection.size}", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Adds one shared tag so they stay together in search.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    androidx.compose.material3.OutlinedTextField(
                        value = groupText,
                        onValueChange = { groupText = it },
                        label = { Text("Group name") },
                        placeholder = { Text("e.g. Interview, Ideas") },
                        singleLine = true,
                        shape = ShapeLargeIncreased,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "Folder color",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    GroupColorRow(
                        selectedArgb = groupColorDraft,
                        onSelect = { groupColorDraft = it }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val ids = selection
                        val tag = groupText
                        val color = groupColorDraft
                        showGroupDialog = false
                        selection = emptySet()
                        viewModel.tagRecordings(ids, tag) { n ->
                            scope.launch {
                                if (color != null && tag.isNotBlank()) {
                                    prefs.setGroupColor(tag.trim(), color)
                                }
                                snackbarHostState.showSnackbar(
                                    "Grouped $n as “$tag”",
                                    withDismissAction = true
                                )
                            }
                        }
                    },
                    shapes = ButtonDefaults.shapes()
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showGroupDialog = false }, shapes = ButtonDefaults.shapes()) { Text("Cancel") }
            },
            shape = ShapeLargeIncreased
        )
    }

    recolorGroup?.let { folderName ->
        AlertDialog(
            onDismissRequest = { recolorGroup = null },
            title = { Text(folderName, style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Pick a folder color",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    GroupColorRow(
                        selectedArgb = groupColorDraft,
                        onSelect = { groupColorDraft = it }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val color = groupColorDraft
                        recolorGroup = null
                        if (color != null) {
                            scope.launch { prefs.setGroupColor(folderName, color) }
                        }
                    },
                    shapes = ButtonDefaults.shapes()
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { recolorGroup = null }, shapes = ButtonDefaults.shapes()) { Text("Cancel") }
            },
            shape = ShapeLargeIncreased
        )
    }

    showRenameDialog?.let { rec ->
        AlertDialog(
            onDismissRequest = { showRenameDialog = null },
            title = { Text("Rename recording") },
            text = {
                androidx.compose.material3.OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("Title") },
                    singleLine = true,
                    shape = ShapeLargeIncreased,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (renameText.isNotBlank()) {
                        viewModel.rename(rec.id, renameText)
                    }
                    showRenameDialog = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showRenameDialog = null }) { Text("Cancel") } },
            shape = ShapeLargeIncreased
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ActiveRecordingPanel(
    elapsedMs: Long,
    amplitude: Float,
    peaks: List<Float>,
    isPaused: Boolean,
    source: AudioSource = AudioSource.MIC,
    onPauseResume: () -> Unit,
    onStop: () -> Unit,
    onCancel: () -> Unit
) {
    androidx.compose.material3.Surface(
        shape = ShapeLargeIncreased,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(
                            color = if (isPaused) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.error,
                            shape = ShapeFull
                        )
                ) {}
                Icon(
                    imageVector = Icons.Filled.Mic,
                    contentDescription = null,
                    tint = if (isPaused) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = (if (isPaused) "Paused" else "Recording") +
                        if (source == AudioSource.SYSTEM) " • System sound" else "",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isPaused) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.error
                )
            }

            AnimatedContent(
                targetState = formatDuration(elapsedMs),
                transitionSpec = { fadeIn(tween(180)).togetherWith(fadeOut(tween(180))) },
                label = "timer"
            ) { time ->
                Text(
                    text = time,
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            WaveformVisualizer(
                amplitudes = peaks,
                currentAmplitude = amplitude,
                isPaused = isPaused,
                modifier = Modifier.fillMaxWidth()
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(
                    onClick = onPauseResume,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.weight(1f),
                    contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight)
                ) {
                    Icon(
                        imageVector = if (isPaused) Icons.Filled.Mic else Icons.Filled.Pause,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.iconSizeFor(ButtonDefaults.MediumContainerHeight))
                    )
                    Spacer(modifier = Modifier.size(ButtonDefaults.iconSpacingFor(ButtonDefaults.MediumContainerHeight)))
                    Text(if (isPaused) "Resume" else "Pause", style = ButtonDefaults.textStyleFor(ButtonDefaults.MediumContainerHeight))
                }
                Button(
                    onClick = onStop,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.weight(1f),
                    contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight)
                ) {
                    Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(ButtonDefaults.iconSizeFor(ButtonDefaults.MediumContainerHeight)))
                    Spacer(modifier = Modifier.size(ButtonDefaults.iconSpacingFor(ButtonDefaults.MediumContainerHeight)))
                    Text("Stop & save", style = ButtonDefaults.textStyleFor(ButtonDefaults.MediumContainerHeight))
                }
            }
            TextButton(onClick = onCancel) { Text("Discard", color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun EmptyState(onRecord: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = ShapeLargeIncreased,
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.size(96.dp)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(Icons.Filled.Mic, contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
        Spacer(Modifier.height(20.dp))
        Text("No recordings yet", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            "Tap Record to capture your first moment. Everything stays on your device.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(Modifier.height(20.dp))
        FilledTonalButton(onClick = onRecord, shape = ShapeFull) {
            Icon(Icons.Filled.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text("Start recording")
        }
    }
}

@Composable
private fun TrashRow(
    recording: Recording,
    onRestore: () -> Unit,
    onDeleteForever: () -> Unit,
    modifier: Modifier = Modifier
) {
    androidx.compose.material3.Card(
        shape = ShapeLargeIncreased,
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(recording.title, style = MaterialTheme.typography.titleSmall)
                Text(formatDuration(recording.durationMs), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onRestore) { Text("Restore") }
            TextButton(onClick = onDeleteForever) { Text("Delete", color = MaterialTheme.colorScheme.error) }
        }
    }
}
