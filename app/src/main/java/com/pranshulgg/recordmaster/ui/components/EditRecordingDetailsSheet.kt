package com.pranshulgg.recordmaster.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pranshulgg.recordmaster.R
import com.pranshulgg.recordmaster.core.data.Recording
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EditRecordingDetailsSheet(
    recording: Recording,
    info: AudioInfo,
    onDismiss: () -> Unit,
    onSave: (customName: String, authorName: String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var customName by rememberSaveable(recording.id, recording.customName) {
        mutableStateOf(recording.customName.ifBlank { recording.title })
    }
    var authorName by rememberSaveable(recording.id, recording.authorName) {
        mutableStateOf(recording.authorName)
    }

    fun dismiss() {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            if (!sheetState.isVisible) onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = {
            Box(modifier = Modifier.padding(top = 22.dp)) {
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
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Edit details",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                OutlinedTextField(
                    value = customName,
                    onValueChange = { customName = it },
                    label = { Text("Custom name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = authorName,
                    onValueChange = { authorName = it },
                    label = { Text("Author") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = "Properties",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp)
            )

            SettingSection(
                tiles = listOf(
                    SettingTile.TextTile(
                        leading = { SettingsTileIcon(R.drawable.graphic_eq_24px) },
                        title = "Format",
                        description = info.encoding.ifBlank { "Unknown" }
                    ),
                    SettingTile.TextTile(
                        leading = { SettingsTileIcon(R.drawable.storage_24px) },
                        title = "Size",
                        description = formatFileSize(
                            if (info.size > 0) info.size else recording.fileSize
                        )
                    ),
                    SettingTile.TextTile(
                        leading = { SettingsTileIcon(R.drawable.clock_loader_60_24px) },
                        title = "Duration",
                        description = formatDuration(
                            if (info.duration > 0) info.duration else recording.durationMs
                        )
                    ),
                    SettingTile.TextTile(
                        leading = { SettingsTileIcon(R.drawable.info_24px) },
                        title = "Sample rate",
                        description = info.sampleRate
                    )
                )
            )

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    val trimmedCustom = customName.trim()
                    val trimmedAuthor = authorName.trim()
                    onSave(trimmedCustom, trimmedAuthor)
                    dismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Text("Save")
            }
        }
    }
}
