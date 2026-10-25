package com.pranshulgg.recordmaster.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.runtime.*
import androidx.compose.material3.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.io.File
import android.net.Uri
import androidx.core.content.FileProvider
import android.media.MediaMetadataRetriever
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import com.pranshulgg.recordmaster.RecordMasterApp
import com.pranshulgg.recordmaster.core.audio.AudioEnhancer
import com.pranshulgg.recordmaster.core.audio.EnhanceStrength
import com.pranshulgg.recordmaster.core.data.AudioFileStore
import com.pranshulgg.recordmaster.core.data.Recording
import com.pranshulgg.recordmaster.core.stt.FileResult
import com.pranshulgg.recordmaster.ui.components.Symbol
import com.pranshulgg.recordmaster.utils.formatMillis
import com.pranshulgg.recordmaster.utils.moveFileToDir
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import com.pranshulgg.recordmaster.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun RecordingRow(
    file: File,
    isThisPlaying: Boolean,
    progress: Float,
    currentPositionMillis: Int,
    currentDurationMillis: Int,
    onPlayPause: () -> Unit,
    onItemClick: () -> Unit,
    onDelete: () -> Unit,
    isGarbage: Boolean,
    musicDir: File,
    onRestore: () -> Unit,
    onMoved: () -> Unit,
    onRenamed: () -> Unit,
    onShowMessage: (message: String) -> Unit,
    currentTab: String,
    onLongClick: () -> Unit,
    isSelected: Boolean = false,
    recording: Recording? = null,
    onMetadataChanged: () -> Unit = {}
) {
    val context = LocalContext.current
    val app = remember(context) { context.applicationContext as RecordMasterApp }
    val scope = rememberCoroutineScope()
    var showBottomSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )
    val sdfDate = SimpleDateFormat("MMM dd", Locale.getDefault())
    val sdfTime = SimpleDateFormat("hh:mm a", Locale.getDefault())
    val date = sdfDate.format(Date(file.lastModified()))
    val time = sdfTime.format(Date(file.lastModified()))

    var showTagsDialog by remember { mutableStateOf(false) }
    var showTranscriptDialog by remember { mutableStateOf(false) }
    var transcriptText by remember { mutableStateOf("") }
    var transcribing by remember { mutableStateOf(false) }
    var transcribeProgress by remember { mutableStateOf(0f) }
    var cleaning by remember { mutableStateOf(false) }

    fun hideSheet() {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            if (!sheetState.isVisible) {
                showBottomSheet = false
            }
        }
    }

    /**
     * Rows may appear before the background sync has indexed them. Look up the
     * Room row lazily and force one scan before giving up.
     */
    suspend fun rowRecording(): Recording? {
        val direct = app.repository.getByPath(file.absolutePath)
        if (direct != null) return direct
        app.repository.syncExternalFiles()
        return app.repository.getByPath(file.absolutePath)
    }

    var fileDurationMillis by remember { mutableStateOf(0) }
    LaunchedEffect(file.absolutePath) {
        fileDurationMillis = withContext(Dispatchers.IO) {
            try {
                val mmr = MediaMetadataRetriever()
                mmr.setDataSource(file.absolutePath)
                val dur = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toIntOrNull() ?: 0
                mmr.release()
                dur
            } catch (_: Exception) {
                0
            }
        }
    }

    var lastPosition by remember { mutableStateOf(0) }

    val displayCurrent = if (isThisPlaying) {
        lastPosition = currentPositionMillis
        currentPositionMillis
    } else {
        lastPosition
    }
    val displayTotal = if (isThisPlaying && currentDurationMillis > 0) currentDurationMillis else fileDurationMillis

    var menuExpanded by remember { mutableStateOf(false) }

    var showRenameDialog by remember { mutableStateOf(false) }
    var renameText by rememberSaveable { mutableStateOf(file.name) }

    var showMoveDialog by remember { mutableStateOf(false) }
    var availableFolders by remember { mutableStateOf(listOf<File>()) }

    val folderName = file.parentFile?.name ?: "Unknown"

    val tonedDown = Color(
        ColorUtils.blendARGB(
            MaterialTheme.colorScheme.surfaceContainerHigh.toArgb(),
            MaterialTheme.colorScheme.surface.toArgb(),
            0.6f
        )
    )

    Box (
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(shape = RoundedCornerShape(20.dp))
            .combinedClickable(
                onClick = { onItemClick() },
                onLongClick = { onLongClick() }
            )
            .background(if(isSelected) MaterialTheme.colorScheme.surfaceContainerLowest else tonedDown)

    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 14.dp, bottom = 16.dp, end = 5.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        file.nameWithoutExtension,
                        fontSize = 26.sp,
                        maxLines = 1,
                        color = MaterialTheme.colorScheme.onSurface,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(0.8f)
                    )

                    Text(
                        text = "$date at $time",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                Row(
                ) {
                    Text(formatMillis(displayCurrent) + " • ",fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), lineHeight = 1.sp )
                    Text(formatMillis(displayTotal),fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), lineHeight = 1.sp )

                }

                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.padding(end = 5.dp)
                ) {

                    if(!isSelected){
                    IconToggleButton(onCheckedChange = { onPlayPause() }, checked = isThisPlaying,
                        modifier = Modifier.height(34.dp,),
                        shapes = IconButtonDefaults.toggleableShapes(),
                        colors = IconButtonDefaults.iconToggleButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                        )
                        ) {
                        if (isThisPlaying) Symbol(R.drawable.pause_24px, color = MaterialTheme.colorScheme.primary) else Symbol(
                            R.drawable.play_arrow_24px, color = MaterialTheme.colorScheme.onSurface)
                    }
                IconButton(onClick = {showBottomSheet = true}, shapes = IconButtonDefaults.shapes(), Modifier.height(34.dp), colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                )) {
                    Symbol(R.drawable.more_vert_24px, color = MaterialTheme.colorScheme.onSurface)
                }

            } else{
                Checkbox(checked = true, onCheckedChange = { })

            }
        }
    }

            Spacer(Modifier.height(3.dp))

            val animatedProgress by
            animateFloatAsState(
                targetValue = progress.coerceIn(0f, 1f),
                animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,

                )

            Column(modifier = Modifier.padding(end = 16.dp)) {
                LinearWavyProgressIndicator(
                    progress = { animatedProgress  },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .height(5.dp),
                    wavelength = 20.dp,
                    // Flat while paused; wavy only while this row is actually playing.
                    amplitude = { _ -> if (isThisPlaying) 1f else 0f }

                    )
            }

            if (showBottomSheet) {
                ModalBottomSheet(
                    onDismissRequest = {
                        showBottomSheet = false
                    },
                    sheetState = sheetState,
                    dragHandle = {
                        Box(
                            modifier = Modifier.padding(top = 22.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(32.dp)
                                    .height(4.dp)
                                    .background(
                                        MaterialTheme.colorScheme.onSurfaceVariant,
                                        RoundedCornerShape(50.dp)
                                    )
                            )
                        }
                    }
                ) {

                    Column(
                        Modifier.padding(bottom = 16.dp)
                    ) {
                        Spacer(Modifier.height(12.dp))


                        SettingSection(
                            tiles =  if(isGarbage) {
                                listOf<SettingTile>(
                                    SettingTile.ActionTile(
                                        leading = {SettingsTileIcon(R.drawable.restore_from_trash_24px)},
                                        title = "Restore",
                                        onClick = {
                                            onRestore()
                                            hideSheet()
                                        }
                                    ),
                                    SettingTile.ActionTile (
                                        leading = {SettingsTileIcon(R.drawable.delete_forever_24px, dangerColor = true)},
                                        title = "Delete forever",
                                        danger = true,
                                        onClick = {
                                        onDelete()
                                        hideSheet()
                                    }
                                )
                            )
                        } else{
                            buildList {
                                add(
                                    SettingTile.ActionTile(
                                        leading = {SettingsTileIcon(R.drawable.delete_24px)},
                                        title = "Delete",
                                        onClick = {
                                            onDelete()
                                            hideSheet()
                                        }
                                    )
                                )
                                add(
                                    SettingTile.ActionTile(
                                        leading = {SettingsTileIcon(R.drawable.edit_24px)},
                                        title = "Rename",
                                        onClick = {
                                            renameText = file.name
                                            showRenameDialog = true
                                            hideSheet()
                                        }
                                    )
                                )

                                if(currentTab != "home") {
                                    add(
                                        SettingTile.ActionTile(
                                            leading = { SettingsTileIcon(R.drawable.drive_file_move_24px) },
                                            title = "Remove from folder",
                                            onClick = {
                                                scope.launch {
                                                    val rec = rowRecording()
                                                    val moved = try {
                                                        if (rec != null) {
                                                            app.repository.moveToDirectory(listOf(rec.id), musicDir) == 1
                                                        } else {
                                                            moveFileToDir(file, musicDir)
                                                            true
                                                        }
                                                    } catch (t: Throwable) {
                                                        false
                                                    }
                                                    if (moved) {
                                                        onShowMessage("Removed from folder")
                                                        onMoved()
                                                    } else {
                                                        onShowMessage("Move failed")
                                                    }
                                                }
                                                hideSheet()
                                            }
                                        )
                                    )
                                } else {
                                    add(
                                        SettingTile.ActionTile(
                                            leading = {SettingsTileIcon(R.drawable.folder_24px)},
                                            title = "Move to folder",
                                            onClick = {
                                                val dirs = (musicDir.listFiles() ?: emptyArray())
                                                    .filter { it.isDirectory && it.name != "garbage" }
                                                availableFolders = dirs
                                                showMoveDialog = true
                                                hideSheet()
                                            }
                                        )
                                    )
                                }

                                add(
                                    SettingTile.ActionTile(
                                        leading = {
                                            SettingsTileIcon(
                                                if (recording?.isFavorite == true)
                                                    R.drawable.favorite_24px
                                                else
                                                    R.drawable.favorite_border_24px
                                            )
                                        },
                                        title = if (recording?.isFavorite == true)
                                            "Remove from favorites" else "Add to favorites",
                                        onClick = {
                                            scope.launch {
                                                val rec = rowRecording()
                                                if (rec == null) {
                                                    onShowMessage("Could not index this recording")
                                                } else {
                                                    app.repository.toggleFavorite(rec.id)
                                                    onMetadataChanged()
                                                    onShowMessage(
                                                        if (rec.isFavorite) "Removed from favorites"
                                                        else "Added to favorites"
                                                    )
                                                }
                                            }
                                            hideSheet()
                                        }
                                    )
                                )
                                add(
                                    SettingTile.ActionTile(
                                        leading = {SettingsTileIcon(R.drawable.label_24px)},
                                        title = "Edit tags",
                                        description = recording?.tagList
                                            ?.takeIf { it.isNotEmpty() }
                                            ?.joinToString(", "),
                                        onClick = {
                                            showTagsDialog = true
                                            hideSheet()
                                        }
                                    )
                                )
                                add(
                                    SettingTile.ActionTile(
                                        leading = {SettingsTileIcon(R.drawable.notes_24px)},
                                        title = if (recording?.transcriptText.isNullOrBlank())
                                            "Transcribe" else "View transcript",
                                        onClick = {
                                            hideSheet()
                                            if (!recording?.transcriptText.isNullOrBlank()) {
                                                transcriptText = recording?.transcriptText.orEmpty()
                                                showTranscriptDialog = true
                                            } else {
                                                scope.launch {
                                                    transcribing = true
                                                    transcribeProgress = 0f
                                                    val result = app.transcription
                                                        .transcribeFileWithProgress(file.absolutePath) {
                                                            transcribeProgress = it
                                                        }
                                                    transcribing = false
                                                    when (result) {
                                                        is FileResult.Done -> {
                                                            transcriptText = result.text
                                                            val rec = rowRecording()
                                                            if (rec != null) {
                                                                app.repository.saveTranscript(
                                                                    rec.id,
                                                                    result.text
                                                                )
                                                                onMetadataChanged()
                                                            }
                                                            showTranscriptDialog = true
                                                        }

                                                        is FileResult.Failed -> onShowMessage(result.message)
                                                    }
                                                }
                                            }
                                        }
                                    )
                                )
                                add(
                                    SettingTile.ActionTile(
                                        leading = {SettingsTileIcon(R.drawable.auto_fix_24px)},
                                        title = "Clean audio",
                                        description = "Reduce noise and level the take",
                                        onClick = {
                                            hideSheet()
                                            scope.launch {
                                                cleaning = true
                                                app.playback.stopIfCurrent(file.absolutePath)
                                                val input = AudioFileStore.ensureLocalFile(
                                                    context,
                                                    file.absolutePath
                                                )
                                                if (input == null) {
                                                    cleaning = false
                                                    onShowMessage("Audio file is missing")
                                                } else {
                                                    val strength = EnhanceStrength.fromPref(
                                                        app.prefs.enhanceStrengthFlow.first()
                                                    )
                                                    val result = AudioEnhancer.enhance(input, strength)
                                                    result.onSuccess { enhanced ->
                                                        val rec = rowRecording()
                                                        if (rec == null) {
                                                            onShowMessage(
                                                                "Cleaned copy created, but it could not be indexed"
                                                            )
                                                        } else {
                                                            app.repository.saveEnhanced(
                                                                rec.id,
                                                                enhanced.file.absolutePath
                                                            )
                                                            val keepOriginal =
                                                                app.prefs.keepOriginalFlow.first()
                                                            val replaced = if (!keepOriginal) {
                                                                app.repository.replaceWithEnhanced(rec.id)
                                                            } else {
                                                                false
                                                            }
                                                            onMetadataChanged()
                                                            onShowMessage(
                                                                if (!keepOriginal && replaced)
                                                                    "Cleaned audio saved"
                                                                else
                                                                    "Cleaned copy saved"
                                                            )
                                                        }
                                                    }.onFailure {
                                                        onShowMessage(it.message ?: "Cleanup failed")
                                                    }
                                                    cleaning = false
                                                }
                                            }
                                        }
                                    )
                                )
                                if (!recording?.enhancedPath.isNullOrBlank()) {
                                    add(
                                        SettingTile.ActionTile(
                                            leading = {SettingsTileIcon(R.drawable.delete_24px)},
                                            title = "Remove cleaned copy",
                                            onClick = {
                                                hideSheet()
                                                scope.launch {
                                                    val rec = rowRecording()
                                                    if (rec != null) {
                                                        app.repository.clearEnhanced(rec.id)
                                                        onMetadataChanged()
                                                        onShowMessage("Cleaned copy removed")
                                                    }
                                                }
                                            }
                                        )
                                    )
                                }

                                add(
                                    SettingTile.ActionTile(
                                        leading = {SettingsTileIcon(R.drawable.share_24px)},
                                        title = "Share",
                                        onClick = {
                                            try {
                                                val uri: Uri = try {
                                                    FileProvider.getUriForFile(
                                                        context,
                                                        "${context.packageName}.provider",
                                                        file
                                                    )
                                                } catch (_: Exception) {
                                                    Uri.fromFile(file)
                                                }
                                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                    type = "audio/*"
                                                    putExtra(Intent.EXTRA_STREAM, uri)
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                }
                                                val chooser = Intent.createChooser(shareIntent, "Share audio")
                                                context.startActivity(chooser)
                                            } catch (e: Exception) {
                                                onShowMessage("Failed to share file")

                                            }
                                            hideSheet()
                                        }
                                    )
                                )
                            }
                        }
                    )
                }
            }
        }

            if (showMoveDialog) {
                AlertDialog(
                    onDismissRequest = { showMoveDialog = false },
                    title = { Text("Move to folder") },
                    text = {
                        Column {
                            if (availableFolders.isEmpty()) {
                                Text("No folders found. Create a folder from the sidebar.")
                            } else {
                                Column {
                                    availableFolders.forEach { dir ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    scope.launch {
                                                        val rec = rowRecording()
                                                        val moved = try {
                                                            if (rec != null) {
                                                                app.repository.moveToDirectory(
                                                                    listOf(rec.id),
                                                                    dir
                                                                ) == 1
                                                            } else {
                                                                moveFileToDir(file, dir)
                                                                true
                                                            }
                                                        } catch (t: Throwable) {
                                                            false
                                                        }
                                                        showMoveDialog = false
                                                        if (moved) {
                                                            Toast.makeText(
                                                                context,
                                                                "Moved to ${dir.name}",
                                                                Toast.LENGTH_SHORT
                                                            ).show()
                                                            onMoved()
                                                        } else {
                                                            Toast.makeText(
                                                                context,
                                                                "Move failed",
                                                                Toast.LENGTH_SHORT
                                                            ).show()
                                                        }
                                                    }
                                                }
                                                .padding(vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(dir.name)
                                        }

                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showMoveDialog = false }, shapes = ButtonDefaults.shapes()) { Text("Cancel", fontWeight = FontWeight.W600, fontSize = 16.sp) }
                    }
                )
            }

            if (showRenameDialog) {
                AlertDialog(
                    onDismissRequest = { showRenameDialog = false },
                    title = { Text("Rename") },
                    text = {
                        Column {
                            OutlinedTextField(
                                value = renameText,
                                onValueChange = { renameText = it },
                                singleLine = true,
                                placeholder = { "New name" },
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(
                            shapes = ButtonDefaults.shapes(),
                            onClick = {
                            val newNameTrimmed = renameText.trim()
                            if (newNameTrimmed.isNotEmpty()) {
                                scope.launch {
                                    val rec = rowRecording()
                                    val success = if (rec != null) {
                                        app.repository.rename(rec.id, newNameTrimmed)
                                    } else {
                                        val ext = file.extension
                                        val base = if (ext.isNotBlank() &&
                                            newNameTrimmed.endsWith(".$ext", ignoreCase = true)
                                        ) {
                                            newNameTrimmed.dropLast(ext.length + 1)
                                        } else {
                                            newNameTrimmed
                                        }
                                        try {
                                            file.renameTo(
                                                File(file.parentFile, "$base.$ext")
                                            )
                                        } catch (t: Throwable) {
                                            false
                                        }
                                    }
                                    showRenameDialog = false
                                    if (success) {
                                        onShowMessage("Renamed to $newNameTrimmed")
                                        onRenamed()
                                    } else {
                                        onShowMessage("Rename failed")
                                    }
                                }
                            } else {
                                onShowMessage("Invalid name")
                            }
                        }) { Text("Rename", fontWeight = FontWeight.W600, fontSize = 16.sp) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showRenameDialog = false }, shapes = ButtonDefaults.shapes()) { Text("Cancel", fontWeight = FontWeight.W600, fontSize = 16.sp) }
                    }
                )
            }

            if (showTagsDialog) {
                TagsDialog(
                    initialTags = recording?.tagList.orEmpty(),
                    onDismiss = { showTagsDialog = false },
                    onSave = { tags ->
                        showTagsDialog = false
                        scope.launch {
                            val rec = rowRecording()
                            if (rec == null) {
                                onShowMessage("Could not index this recording")
                            } else {
                                app.repository.updateTags(rec.id, tags)
                                onMetadataChanged()
                                onShowMessage(
                                    if (tags.isEmpty()) "Tags cleared" else "Tags updated"
                                )
                            }
                        }
                    }
                )
            }

            if (transcribing) {
                AlertDialog(
                    onDismissRequest = { },
                    title = { Text("Transcribing") },
                    text = {
                        Column {
                            Text("Working offline. This can take a moment.")
                            Spacer(Modifier.height(12.dp))
                            LinearProgressIndicator(
                                progress = { transcribeProgress.coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    },
                    confirmButton = { }
                )
            }

            if (cleaning) {
                AlertDialog(
                    onDismissRequest = { },
                    title = { Text("Cleaning audio") },
                    text = { Text("Reducing noise and leveling the recording…") },
                    confirmButton = { }
                )
            }

            if (showTranscriptDialog) {
                AlertDialog(
                    onDismissRequest = { showTranscriptDialog = false },
                    title = { Text("Transcript") },
                    text = {
                        Column(
                            modifier = Modifier
                                .heightIn(max = 360.dp)
                                .clipToBounds()
                        ) {
                            Text(
                                transcriptText.ifBlank { "No speech was detected." },
                                fontSize = 14.sp
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(
                            shapes = ButtonDefaults.shapes(),
                            onClick = {
                                val clipboard = context.getSystemService(
                                    Context.CLIPBOARD_SERVICE
                                ) as ClipboardManager
                                clipboard.setPrimaryClip(
                                    ClipData.newPlainText("Transcript", transcriptText)
                                )
                                showTranscriptDialog = false
                                onShowMessage("Transcript copied")
                            }
                        ) { Text("Copy", fontWeight = FontWeight.W600, fontSize = 16.sp) }
                    },
                    dismissButton = {
                        TextButton(
                            shapes = ButtonDefaults.shapes(),
                            onClick = { showTranscriptDialog = false }
                        ) { Text("Close", fontWeight = FontWeight.W600, fontSize = 16.sp) }
                    }
                )
            }
        }
    }
}

