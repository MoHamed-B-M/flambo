package com.pranshulgg.recordmaster.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.media.MediaMetadataRetriever
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.pranshulgg.recordmaster.R
import com.pranshulgg.recordmaster.RecordMasterApp
import com.pranshulgg.recordmaster.core.audio.AudioEnhancer
import com.pranshulgg.recordmaster.core.audio.EnhanceStrength
import com.pranshulgg.recordmaster.core.data.AudioFileStore
import com.pranshulgg.recordmaster.core.data.Recording
import com.pranshulgg.recordmaster.core.stt.FileResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DropdownMenu(
    navController: NavController,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    file: File,
    recording: Recording? = null,
    playbackSpeed: Float = 1f,
    onSpeedSelected: (Float) -> Unit = {},
    onMetadataChanged: () -> Unit = {},
    onShowMessage: (String) -> Unit = {},
    onCleanReplaced: () -> Unit = {}
) {
    val context = LocalContext.current
    val app = remember(context) { context.applicationContext as RecordMasterApp }
    val scope = rememberCoroutineScope()

    var expanded by remember { mutableStateOf(false) }
    var showAboutBottomSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )

    var showCurrentRecordDeleteDialog by remember { mutableStateOf(false) }
    var showTagsDialog by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showTranscriptDialog by remember { mutableStateOf(false) }
    var transcriptText by remember { mutableStateOf("") }
    var transcribing by remember { mutableStateOf(false) }
    var transcribeProgress by remember { mutableStateOf(0f) }
    var cleaning by remember { mutableStateOf(false) }

    val info = remember(file) { getAudioInfo(file) }

    fun hideAboutSheet() {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            if (!sheetState.isVisible) {
                showAboutBottomSheet = false
            }
        }
    }

    suspend fun latestRecording(): Recording? {
        val direct = app.repository.getByPath(file.absolutePath)
        if (direct != null) return direct
        app.repository.syncExternalFiles()
        return app.repository.getByPath(file.absolutePath)
    }

    Tooltip("More options", preferredPosition = TooltipAnchorPosition.Below, spacing = 10.dp) {
        IconButton(
            onClick = { expanded = !expanded }, shapes = IconButtonDefaults.shapes()
        ) {
            Symbol(
                R.drawable.more_vert_24px,
                desc = "More options",
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
    androidx.compose.material3.DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false },
        shape = RoundedCornerShape(16.dp),
        offset = DpOffset(x = (-10).dp, y = (-48).dp)
    ) {
        DropdownMenuItem(
            text = { DropDownMenuText("Delete") },
            onClick = {
                showCurrentRecordDeleteDialog = true
                expanded = false
            },
            leadingIcon = { DropDownMenuIcon(R.drawable.delete_forever_24px) }
        )
        DropdownMenuItem(
            text = { DropDownMenuText("Share") },
            onClick = {
                onShare()
                expanded = false
            },
            leadingIcon = { DropDownMenuIcon(R.drawable.share_24px) }
        )
        DropdownMenuItem(
            text = {
                DropDownMenuText(
                    if (recording?.isFavorite == true) "Remove from favorites"
                    else "Add to favorites"
                )
            },
            onClick = {
                expanded = false
                scope.launch {
                    val rec = latestRecording()
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
            },
            leadingIcon = {
                DropDownMenuIcon(
                    if (recording?.isFavorite == true) R.drawable.favorite_24px
                    else R.drawable.favorite_border_24px
                )
            }
        )
        DropdownMenuItem(
            text = { DropDownMenuText("Edit tags") },
            onClick = {
                showTagsDialog = true
                expanded = false
            },
            leadingIcon = { DropDownMenuIcon(R.drawable.label_24px) }
        )
        DropdownMenuItem(
            text = {
                DropDownMenuText(
                    if (recording?.transcriptText.isNullOrBlank()) "Transcribe"
                    else "View transcript"
                )
            },
            onClick = {
                expanded = false
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
                                val rec = latestRecording()
                                if (rec != null) {
                                    app.repository.saveTranscript(rec.id, result.text)
                                    onMetadataChanged()
                                }
                                showTranscriptDialog = true
                            }

                            is FileResult.Failed -> onShowMessage(result.message)
                        }
                    }
                }
            },
            leadingIcon = { DropDownMenuIcon(R.drawable.notes_24px) }
        )
        DropdownMenuItem(
            text = { DropDownMenuText("Clean audio") },
            onClick = {
                expanded = false
                scope.launch {
                    cleaning = true
                    app.playback.stopIfCurrent(file.absolutePath)
                    val input = AudioFileStore.ensureLocalFile(context, file.absolutePath)
                    if (input == null) {
                        cleaning = false
                        onShowMessage("Audio file is missing")
                    } else {
                        val strength = EnhanceStrength.fromPref(
                            app.prefs.enhanceStrengthFlow.first()
                        )
                        val result = AudioEnhancer.enhance(input, strength)
                        result.onSuccess { enhanced ->
                            val rec = latestRecording()
                            if (rec == null) {
                                onShowMessage("Cleaned copy created, but it could not be indexed")
                            } else {
                                app.repository.saveEnhanced(rec.id, enhanced.file.absolutePath)
                                val keepOriginal = app.prefs.keepOriginalFlow.first()
                                val replaced = if (!keepOriginal) {
                                    app.repository.replaceWithEnhanced(rec.id)
                                } else {
                                    false
                                }
                                onMetadataChanged()
                                if (!keepOriginal && replaced) {
                                    onShowMessage("Cleaned audio saved")
                                    onCleanReplaced()
                                } else {
                                    onShowMessage("Cleaned copy saved")
                                }
                            }
                        }.onFailure {
                            onShowMessage(it.message ?: "Cleanup failed")
                        }
                        cleaning = false
                    }
                }
            },
            leadingIcon = { DropDownMenuIcon(R.drawable.auto_fix_24px) }
        )
        DropdownMenuItem(
            text = { DropDownMenuText("Playback speed") },
            onClick = {
                showSpeedDialog = true
                expanded = false
            },
            leadingIcon = { DropDownMenuIcon(R.drawable.routine_24px) }
        )
        DropdownMenuItem(
            text = { DropDownMenuText("About file") },
            onClick = {
                showAboutBottomSheet = true
                expanded = false
            },
            leadingIcon = { DropDownMenuIcon(R.drawable.info_24px) }
        )
    }

    if (showAboutBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = {
                showAboutBottomSheet = false
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
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(12.dp))
                SettingSection(
                    tiles = buildList {
                        add(
                            SettingTile.TextTile(
                                leading = { SettingsTileIcon(R.drawable.graphic_eq_24px) },
                                title = "Encoding",
                                description = info.encoding
                            )
                        )
                        add(
                            SettingTile.TextTile(
                                leading = { SettingsTileIcon(R.drawable.clock_loader_60_24px) },
                                title = "Audio duration",
                                description = formatDuration(info.duration)
                            )
                        )
                        add(
                            SettingTile.TextTile(
                                leading = { SettingsTileIcon(R.drawable.storage_24px) },
                                title = "File size",
                                description = formatFileSize(info.size)
                            )
                        )
                        add(
                            SettingTile.TextTile(
                                leading = { SettingsTileIcon(R.drawable.folder_24px) },
                                title = "File path",
                                description = info.path
                            )
                        )
                        if (!recording?.transcriptText.isNullOrBlank()) {
                            add(
                                SettingTile.TextTile(
                                    leading = { SettingsTileIcon(R.drawable.notes_24px) },
                                    title = "Transcript",
                                    description = recording?.transcriptText
                                )
                            )
                        }
                    }
                )

                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { hideAboutSheet() },
                    modifier = Modifier.fillMaxWidth(fraction = 0.9f)
                ) {
                    Text("Close")
                }

                Spacer(Modifier.height(10.dp))

            }

        }
    }


    if (showCurrentRecordDeleteDialog) {
        ConfirmDialog(
            title = "Delete recording",
            message = "The recording will be permanently deleted and cannot be undone",
            confirmText = "Delete",
            cancelText = "Cancel",
            onConfirm = {
                onDelete()
                showCurrentRecordDeleteDialog = false
            },
            onDismiss = { showCurrentRecordDeleteDialog = false }
        )
    }

    if (showTagsDialog) {
        TagsDialog(
            initialTags = recording?.tagList.orEmpty(),
            onDismiss = { showTagsDialog = false },
            onSave = { tags ->
                showTagsDialog = false
                scope.launch {
                    val rec = latestRecording()
                    if (rec == null) {
                        onShowMessage("Could not index this recording")
                    } else {
                        app.repository.updateTags(rec.id, tags)
                        onMetadataChanged()
                        onShowMessage(if (tags.isEmpty()) "Tags cleared" else "Tags updated")
                    }
                }
            }
        )
    }

    if (showSpeedDialog) {
        val speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
        AlertDialog(
            onDismissRequest = { showSpeedDialog = false },
            title = { Text("Playback speed") },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    speeds.forEach { speed ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSpeedSelected(speed)
                                    showSpeedDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (speed == 1f) "Normal (1x)"
                                else String.format(Locale.getDefault(), "%.2fx", speed),
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            if (kotlin.math.abs(playbackSpeed - speed) < 0.01f) {
                                Symbol(
                                    R.drawable.check,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSpeedDialog = false }) { Text("Close") }
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
                ) { Text("Copy") }
            },
            dismissButton = {
                TextButton(onClick = { showTranscriptDialog = false }) { Text("Close") }
            }
        )
    }

}


@Composable
fun DropDownMenuText(text: String) =
    Text(
        text,
        fontSize = 16.sp,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(end = 10.dp)
    )

@Composable
fun DropDownMenuIcon(icon: Int) =
    Symbol(icon, color = MaterialTheme.colorScheme.onSurface, size = 22.dp, paddingStart = 3.dp)



fun getAudioInfo(file: File): AudioInfo {
    return try {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(file.absolutePath)

        val mime = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
        val durationMs =
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                ?: 0L

        retriever.release()

        AudioInfo(
            encoding = mime ?: file.extension,
            duration = durationMs,
            size = file.length(),
            path = file.absolutePath
        )
    } catch (e: Exception) {
        AudioInfo(
            encoding = file.extension,
            duration = 0L,
            size = file.length(),
            path = file.absolutePath
        )
    }
}

data class AudioInfo(
    val encoding: String,
    val duration: Long,
    val size: Long,
    val path: String
)

fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return if (hours > 0)
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    else
        String.format("%02d:%02d", minutes, seconds)
}

fun formatFileSize(bytes: Long): String {
    val kb = 1024.0
    val mb = kb * 1024
    val gb = mb * 1024

    return when {
        bytes >= gb -> String.format("%.2f GB", bytes / gb)
        bytes >= mb -> String.format("%.2f MB", bytes / mb)
        bytes >= kb -> String.format("%.2f KB", bytes / kb)
        else -> "$bytes B"
    }
}

