package com.pranshulgg.recordmaster.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.motionScheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.navigation.NavController
import com.pranshulgg.recordmaster.R
import com.pranshulgg.recordmaster.RecordMasterApp
import com.pranshulgg.recordmaster.core.data.RecordingRepository
import com.pranshulgg.recordmaster.core.playback.PlaybackController
import com.pranshulgg.recordmaster.ui.components.ConfirmDialog
import com.pranshulgg.recordmaster.ui.components.DrawerContent
import com.pranshulgg.recordmaster.ui.components.EmptyContainerPlaceholder
import com.pranshulgg.recordmaster.ui.components.RecordingRow
import com.pranshulgg.recordmaster.ui.components.Symbol
import com.pranshulgg.recordmaster.ui.components.TopBarWithSearch
import com.pranshulgg.recordmaster.utils.computeDirKey
import com.pranshulgg.recordmaster.utils.moveFileToDir
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun File.isLibraryAudio(): Boolean =
    isFile &&
        !name.startsWith(RecordingRepository.TEMP_PREFIX) &&
        !nameWithoutExtension.endsWith(RecordingRepository.ENHANCED_SUFFIX)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreen(
    navController: NavController,
    snackbarHostState: SnackbarHostState,
    playback: PlaybackController
) {
    val context = LocalContext.current
    val app = remember(context) { context.applicationContext as RecordMasterApp }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    var currentTab by remember { mutableStateOf("home") }
    val scope = rememberCoroutineScope()
    var selectedFolderName by remember { mutableStateOf<String?>(null) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }

    val playbackState by playback.state.collectAsState()
    val recordings by app.repository.observeRecordings().collectAsState(initial = emptyList())
    val recordingsByPath = remember(recordings) { recordings.associateBy { it.filePath } }

    val playingPath = playbackState.currentPath
    val isPlaying = playbackState.isPlaying
    val currentPos = playbackState.positionMs.toInt()
    val duration = playbackState.durationMs.toInt()

    var selectedPaths by remember { mutableStateOf(setOf<String>()) }

    BackHandler(enabled = showSearch) {
        searchQuery = ""
        showSearch = false
    }

    BackHandler(enabled = selectedPaths.isNotEmpty()) {
        selectedPaths = emptySet()
    }

    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by rememberSaveable { mutableStateOf("") }
    var foldersExpanded by remember { mutableStateOf(true) }

    var showDeleteFolderDialog by remember { mutableStateOf(false) }
    var folderToDeleteName by remember { mutableStateOf<String?>(null) }
    var selectedFolderKey by remember { mutableStateOf(0L) }

    val showMessage: suspend (String) -> Unit = { msg ->
        if (snackbarHostState != null) {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(msg)
        } else {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Follow the storage choice from Settings while keeping RecordMaster's
    // existing Music/ tree as the default.
    val musicDir = remember(app.storageVolumeId) {
        app.recordingsDir()
    }

    val garbageDir = remember(musicDir) {
        File(musicDir, RecordingRepository.GARBAGE_DIR_NAME).apply { if (!exists()) mkdirs() }
    }

    var rootDirKey by remember { mutableStateOf(0L) }
    var garbageDirKey by remember { mutableStateOf(0L) }

    fun refreshLibrary() {
        rootDirKey = computeDirKey(musicDir)
        garbageDirKey = computeDirKey(garbageDir)
        if (!selectedFolderName.isNullOrEmpty()) {
            selectedFolderKey = computeDirKey(File(musicDir, selectedFolderName!!))
        }
    }

    LaunchedEffect(Unit) {
        refreshLibrary()
        app.syncStorage()
    }

    LaunchedEffect(recordings) {
        refreshLibrary()
    }

    fun toggleSelection(path: String) {
        selectedPaths = if (selectedPaths.contains(path)) selectedPaths - path else selectedPaths + path
    }
    var showGarbageConfirmDeleteSelectedDialog by remember { mutableStateOf(false) }
    var showGarbageConfirmDeleteDialog by remember { mutableStateOf(false) }
    var filePendingDelete by remember { mutableStateOf<File?>(null) }


    fun shareSelected() {
        if (selectedPaths.isEmpty()) return
        try {
            val uris = ArrayList<Uri>()
            selectedPaths.forEach { path ->
                val f = File(path)
                val uri: Uri = try {
                    FileProvider.getUriForFile(context, "${context.packageName}.provider", f)
                } catch (_: Exception) {
                    Uri.fromFile(f)
                }
                uris.add(uri)
            }

            val intent = if (uris.size == 1) {
                Intent(Intent.ACTION_SEND).apply {
                    type = "audio/*"
                    putExtra(Intent.EXTRA_STREAM, uris[0])
                }
            } else {
                Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = "audio/*"
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                }
            }
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(Intent.createChooser(intent, "Share audio"))
        } catch (e: Exception) {
            scope.launch {
                if (snackbarHostState != null) snackbarHostState.showSnackbar("Failed to share files")
                else Toast.makeText(context, "Failed to share files", Toast.LENGTH_SHORT).show()
            }
        } finally {
            selectedPaths = emptySet()
        }
    }

    var filesToDelete: List<File> by remember { mutableStateOf(emptyList()) }

    fun deleteSelected() {
        if (selectedPaths.isEmpty()) return

        if (currentTab == "garbage") {
            filesToDelete = selectedPaths.map { File(it) }
            showGarbageConfirmDeleteSelectedDialog = true
        } else {
            val paths = selectedPaths.toList()
            selectedPaths = emptySet()
            scope.launch {
                paths.forEach { path ->
                    playback.stopIfCurrent(path)
                    val rec = recordingsByPath[path] ?: app.repository.getByPath(path)
                    if (rec != null) {
                        app.repository.softDelete(rec.id)
                    } else {
                        val file = File(path)
                        if (file.exists()) runCatching { moveFileToDir(file, garbageDir) }
                    }
                }
                refreshLibrary()
                app.syncStorage()
                showMessage("Moved to garbage")
            }
        }
    }

    if (showGarbageConfirmDeleteSelectedDialog) {
        ConfirmDialog(
            title = "Delete recordings",
            message = "The selected recording(s) will be permanently deleted and cannot be undone",
            confirmText = "Delete",
            cancelText = "Cancel",
            onConfirm = {
                val files = filesToDelete
                filesToDelete = emptyList()
                showGarbageConfirmDeleteSelectedDialog = false
                scope.launch {
                    files.forEach { file ->
                        playback.stopIfCurrent(file.absolutePath)
                        val rec = recordingsByPath[file.absolutePath]
                            ?: app.repository.getByPath(file.absolutePath)
                        if (rec != null) {
                            app.repository.deletePermanently(rec.id)
                        } else {
                            runCatching { file.delete() }
                        }
                    }
                    refreshLibrary()
                    app.syncStorage()
                }
            },
            onDismiss = { showGarbageConfirmDeleteSelectedDialog = false }
        )
    }

    if (showGarbageConfirmDeleteDialog) {
        val file = filePendingDelete
        ConfirmDialog(
            title = "Delete recording",
            message = "The recording will be permanently deleted and cannot be undone",
            confirmText = "Delete",
            cancelText = "Cancel",
            onConfirm = {
                showGarbageConfirmDeleteDialog = false
                filePendingDelete = null
                if (file != null) {
                    scope.launch {
                        playback.stopIfCurrent(file.absolutePath)
                        val rec = recordingsByPath[file.absolutePath]
                            ?: app.repository.getByPath(file.absolutePath)
                        if (rec != null) {
                            app.repository.deletePermanently(rec.id)
                        } else {
                            runCatching { file.delete() }
                        }
                        refreshLibrary()
                        app.syncStorage()
                    }
                }
            },
            onDismiss = {
                showGarbageConfirmDeleteDialog = false
                filePendingDelete = null
            }
        )
    }

    LaunchedEffect(musicDir.absolutePath) {
        refreshLibrary()
    }

    LaunchedEffect(selectedFolderName, musicDir.absolutePath) {
        if (!selectedFolderName.isNullOrEmpty()) {
            val folder = File(musicDir, selectedFolderName!!)
            selectedFolderKey = computeDirKey(folder)
        } else {
            selectedFolderKey = 0L
        }
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    ModalNavigationDrawer(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        drawerState = drawerState,
        drawerContent = {
            DrawerContent(
                currentTab = currentTab,
                selectedFolderName = selectedFolderName,
                rootDirKey = rootDirKey,
                musicDir = musicDir,
                onSelectTab = { tab, folder ->
                    currentTab = tab
                    selectedFolderName = folder
                    scope.launch { drawerState.close() }
                },
                onRequestCreateFolder = { showCreateFolderDialog = true },
                onRequestDeleteFolder = { name ->
                    folderToDeleteName = name
                    showDeleteFolderDialog = true
                },
                onfoldersExpanded = { value ->
                    foldersExpanded = value
                },
                foldersExpanded = foldersExpanded,
                navController = navController,
                closeDrawer = {
                    scope.launch { drawerState.close() }
                }
            )
        }
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            floatingActionButtonPosition = FabPosition.Center,
            floatingActionButton = {
                AnimatedVisibility(
                    visible =  currentTab == "home" && !showSearch,
                    enter = scaleIn(
                        initialScale = 0.8f,
                        animationSpec = motionScheme.defaultSpatialSpec()
                    ) + fadeIn(),
                    exit = scaleOut(
                        targetScale = 0.8f,
                        animationSpec = motionScheme.defaultSpatialSpec()
                    ) + fadeOut(),
                ) {
                    FloatingActionButton(
                        modifier = Modifier.size(96.dp),
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        onClick = { navController.navigate("record") },
                        shape = CircleShape
                    ) {
                        Symbol(
                            R.drawable.fiber_manual_record_24px,
                            size = 36.dp,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            },
            topBar = {
                TopBarWithSearch(
                    showSearch = showSearch,
                    searchQuery = searchQuery,
                    onSearchChange = { searchQuery = it },
                    onToggleSearch = { showSearch = !showSearch },
                    openDrawer = { scope.launch { drawerState.open() } },
                    currentTab = currentTab,
                    selectedFolderName = selectedFolderName,
                    clearSearchQuery = {searchQuery = ""},
                    onRequestDeleteFolder = { name ->
                        folderToDeleteName = name
                        showDeleteFolderDialog = true
                    },
                    onBack = {
                        searchQuery = ""
                        showSearch = false
                    },
                    scrollBehavior = scrollBehavior,
                    isSelecting = selectedPaths.isNotEmpty(),
                    onShareSelected = {
                        shareSelected()
                    },
                    onDeleteSelected = {
                        deleteSelected()

                    },
                    onCloseSelection = {
                        selectedPaths = emptySet()
                    },
                    selectedCount = selectedPaths.size.toString()
                )

            }

        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .padding(top = innerPadding.calculateTopPadding())
                    .fillMaxSize()
            ) {


                val rootFiles by remember(rootDirKey) {
                    mutableStateOf(
                        (musicDir.listFiles() ?: emptyArray())
                            .filter { it.isLibraryAudio() }
                            .sortedByDescending { it.lastModified() }
                    )
                }

                val garbageFiles by remember(garbageDirKey) {
                    mutableStateOf(
                        (garbageDir.listFiles() ?: emptyArray())
                            .filter { it.isLibraryAudio() }
                            .sortedByDescending { it.lastModified() }
                    )
                }

                val folderFiles by remember(selectedFolderKey, selectedFolderName) {
                    mutableStateOf(
                        if (!selectedFolderName.isNullOrEmpty()) {
                            val f = File(musicDir, selectedFolderName!!)
                            (f.listFiles() ?: emptyArray())
                                .filter { it.isLibraryAudio() }
                                .sortedByDescending { it.lastModified() }
                        } else emptyList()
                    )
                }

                val displayedRecordings = if (searchQuery.isBlank()) {
                    when (currentTab) {
                        "home" -> rootFiles
                        "garbage" -> garbageFiles
                        "folder" -> folderFiles
                        else -> rootFiles
                    }
                } else {
                    val allCandidates = mutableListOf<File>()
                    allCandidates += rootFiles
                    allCandidates += garbageFiles
                    val allSubfolderFiles = (musicDir.listFiles() ?: emptyArray())
                        .filter { it.isDirectory && it.name != RecordingRepository.GARBAGE_DIR_NAME }
                        .flatMap { dir ->
                            (dir.listFiles() ?: emptyArray()).filter { it.isLibraryAudio() }
                        }
                    allCandidates += allSubfolderFiles
                    val query = searchQuery.trim()
                    allCandidates
                        .distinctBy { it.absolutePath }
                        .filter { file ->
                            val rec = recordingsByPath[file.absolutePath]
                            file.name.contains(query, ignoreCase = true) ||
                                rec?.title?.contains(query, ignoreCase = true) == true ||
                                rec?.tagList?.any { it.contains(query, ignoreCase = true) } == true ||
                                rec?.transcriptText?.contains(query, ignoreCase = true) == true
                        }
                        .sortedByDescending { it.lastModified() }
                }




                if (displayedRecordings.isEmpty()) {
                    EmptyContainerPlaceholder(R.drawable.graphic_eq_24px, "No recordings")
                } else {
                    val sdfMonth = SimpleDateFormat("MMMM", Locale.getDefault())
                    val grouped = displayedRecordings
                        .groupBy { sdfMonth.format(Date(it.lastModified())) }
                        .entries
                        .sortedByDescending { entry -> entry.value.maxOf { it.lastModified() } }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        grouped.forEach { (month, files) ->
                            item {
                                    Text(
                                        text = month,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.W700,
                                        fontSize = 16.sp

                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                            }

                            items(files, key = { it.absolutePath }) { file ->
                                RecordingRow(
                                    file = file,
                                    isThisPlaying = playingPath == file.absolutePath && isPlaying,
                                    progress = if (playingPath == file.absolutePath && duration > 0) (currentPos.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f,
                                    currentPositionMillis = currentPos,
                                    currentDurationMillis = duration,
                                    onItemClick = {
                                        if (selectedPaths.isNotEmpty()) {
                                            toggleSelection(file.absolutePath)
                                        } else {
                                            val encoded = Uri.encode(file.absolutePath)
                                            navController.navigate("play/$encoded")
                                        }
                                    },
                                    onLongClick = {
                                        toggleSelection(file.absolutePath)
                                    },
                                    onPlayPause = {
                                        if (playingPath == file.absolutePath) {
                                            playback.toggle()
                                        } else {
                                            playback.play(
                                                file.absolutePath,
                                                file.nameWithoutExtension
                                            )
                                        }
                                    },

                                    isGarbage = currentTab == "garbage",
                                    musicDir = musicDir,
                                    onDelete = {
                                        if (currentTab == "garbage") {
                                            filePendingDelete = file
                                            showGarbageConfirmDeleteDialog = true
                                        } else {
                                            scope.launch {
                                                playback.stopIfCurrent(file.absolutePath)
                                                val rec = recordingsByPath[file.absolutePath]
                                                    ?: app.repository.getByPath(file.absolutePath)
                                                if (rec != null) {
                                                    app.repository.softDelete(rec.id)
                                                } else {
                                                    runCatching { moveFileToDir(file, garbageDir) }
                                                }
                                                selectedPaths = selectedPaths - file.absolutePath
                                                refreshLibrary()
                                                app.syncStorage()
                                                showMessage("Moved to garbage")
                                            }
                                        }
                                    },

                                    isSelected = selectedPaths.contains(file.absolutePath),

                                    onRestore = {
                                        scope.launch {
                                            val rec = recordingsByPath[file.absolutePath]
                                                ?: app.repository.getByPath(file.absolutePath)
                                            if (rec != null) {
                                                app.repository.restore(rec.id)
                                            } else {
                                                runCatching { moveFileToDir(file, musicDir) }
                                            }
                                            refreshLibrary()
                                            app.syncStorage()
                                        }
                                    },

                                    onMoved = {
                                        refreshLibrary()
                                        app.syncStorage()
                                    },

                                    onRenamed = {
                                        refreshLibrary()
                                        app.syncStorage()
                                    },
                                    onShowMessage = { message ->
                                        scope.launch {
                                            showMessage(message)
                                        }
                                    },
                                    currentTab = currentTab,
                                    recording = recordingsByPath[file.absolutePath],
                                    onMetadataChanged = {
                                        refreshLibrary()
                                    }

                                )
                            }



                            item {
                                Spacer(modifier = Modifier.height(140.dp))
                            }
                        }
                    }
                }

                if (showCreateFolderDialog) {
                    AlertDialog(
                        onDismissRequest = { showCreateFolderDialog = false; newFolderName = "" },
                        title = { Text("Create new folder") },
                        text = {
                                OutlinedTextField(
                                    value = newFolderName,
                                    placeholder = {Text("Folder name")},
                                    onValueChange = { newFolderName = it },
                                    singleLine = true
                                )
                        },
                        confirmButton = {
                            TextButton(
                                shapes = ButtonDefaults.shapes(),
                                onClick = {
                                val name = newFolderName.trim()
                                if (name.isNotEmpty()) {
                                    val newDir = File(musicDir, name)
                                    val created = if (!newDir.exists()) newDir.mkdirs() else true
                                    if (created) {
                                        scope.launch {
                                            showMessage("Folder \"$name\" created")
                                        }
                                        rootDirKey = computeDirKey(musicDir)
                                    } else {
                                        scope.launch {
                                            showMessage("Failed to create folder")
                                        }
                                    }
                                }
                                showCreateFolderDialog = false
                                newFolderName = ""
                                foldersExpanded = false
                            }) { Text("Create", fontWeight = FontWeight.W600, fontSize = 16.sp) }
                        },
                        dismissButton = {
                            TextButton(
                                shapes = ButtonDefaults.shapes(),
                                onClick = { showCreateFolderDialog = false; newFolderName = "" }) {
                                Text("Cancel", fontWeight = FontWeight.W600, fontSize = 16.sp)
                            }
                        }
                    )
                }

                if (showDeleteFolderDialog && !folderToDeleteName.isNullOrEmpty()) {
                    AlertDialog(
                        onDismissRequest = { showDeleteFolderDialog = false; folderToDeleteName = null },
                        title = { Text("Delete folder \"${folderToDeleteName}\"?") },
                        text = {
                            Text("Contents will be moved to Recently deleted, then the folder will be removed.")
                        },
                        confirmButton = {
                            TextButton(
                                shapes = ButtonDefaults.shapes(),
                                onClick = {
                                val name = folderToDeleteName!!
                                val target = File(musicDir, name)
                                try {
                                    (target.listFiles() ?: emptyArray()).filter { it.isFile }.forEach { f ->
                                        try { moveFileToDir(f, garbageDir) } catch (_: Throwable) {}
                                    }
                                    target.deleteRecursively()
                                    scope.launch {
                                        showMessage("Folder \"$name\" deleted")
                                    }

                                } catch (t: Throwable) {
                                    scope.launch {
                                        showMessage("Failed to delete folder")
                                    }
                                }
                                folderToDeleteName = null
                                showDeleteFolderDialog = false
                                rootDirKey = computeDirKey(musicDir)
                                garbageDirKey = computeDirKey(garbageDir)
                                app.syncStorage()
                                currentTab = "home"
                            }) { Text("Delete", fontWeight = FontWeight.W600, fontSize = 16.sp) }
                        },
                        dismissButton = {
                            TextButton(
                                shapes = ButtonDefaults.shapes(),
                                onClick = { showDeleteFolderDialog = false; folderToDeleteName = null }) {
                                Text("Cancel", fontWeight = FontWeight.W600, fontSize = 16.sp)
                            }
                        }
                    )
                }
            }
        }
    }
}
