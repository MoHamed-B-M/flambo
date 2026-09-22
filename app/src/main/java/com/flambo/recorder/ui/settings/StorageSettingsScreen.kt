package com.flambo.recorder.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Title
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.flambo.recorder.FlamboApp
import com.flambo.recorder.data.PreferencesManager
import com.flambo.recorder.data.SafFolderHelper
import com.flambo.recorder.data.StorageVolumes
import com.flambo.recorder.ui.settings.components.PreferenceValueItem
import com.flambo.recorder.ui.settings.components.SectionHeader
import com.flambo.recorder.ui.settings.components.SegmentedPreferenceGroup
import com.flambo.recorder.ui.theme.ShapeLargeIncreased
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageSettingsScreen(
    prefs: PreferencesManager,
    scope: CoroutineScope,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val storageVolume by prefs.recordingsVolumeFlow.collectAsState(initial = "default")
    val recordingPrefix by prefs.recordingPrefixFlow.collectAsState(initial = "Recording")
    val customFolderUri by prefs.customFolderUriFlow.collectAsState(initial = "")

    var showStorageDialog by remember { mutableStateOf(false) }
    var showNamingDialog by remember { mutableStateOf(false) }
    var namingDraft by remember { mutableStateOf(recordingPrefix) }
    LaunchedEffect(recordingPrefix) { if (!showNamingDialog) namingDraft = recordingPrefix }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, flags)
            } catch (_: Exception) {}
            SafFolderHelper.takePersistablePermission(context.applicationContext, uri)
            scope.launch {
                prefs.setCustomFolderUri(uri.toString())
                (context.applicationContext as? FlamboApp)?.let { it.customFolderUri = uri.toString() }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Storage & Data", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                .verticalScroll(rememberSaveable(saver = ScrollState.Saver) { ScrollState(0) })
                .padding(horizontal = 16.dp)
                .animateContentSize(spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader(title = "Storage Location")
                SegmentedPreferenceGroup {
                    item {
                        val volumes = remember { StorageVolumes.list(context.applicationContext) }
                        val current = volumes.firstOrNull { it.id == storageVolume } ?: volumes.firstOrNull()
                        PreferenceValueItem(
                            icon = Icons.Filled.Folder,
                            title = "Storage folder",
                            value = current?.label ?: "Phone storage",
                            subtitle = current?.let { StorageVolumes.formatBytes(it.freeBytes) + " free" },
                            onClick = { showStorageDialog = true }
                        )
                    }
                    item {
                        val customLabel = if (customFolderUri.isBlank()) "Not set — uses Storage folder above" else SafFolderHelper.displayName(context, customFolderUri)
                        PreferenceValueItem(
                            icon = Icons.Filled.FolderOpen,
                            title = "Custom folder",
                            value = customLabel,
                            subtitle = "System picker — SAF persistent permission",
                            onClick = { folderPicker.launch(null) }
                        )
                    }
                }
                if (customFolderUri.isNotBlank()) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = {
                            scope.launch {
                                prefs.clearCustomFolderUri()
                                (context.applicationContext as? FlamboApp)?.let { it.customFolderUri = "" }
                            }
                        }, shapes = ButtonDefaults.shapes()) { Text("Clear custom folder") }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader(title = "Naming")
                SegmentedPreferenceGroup {
                    item {
                        PreferenceValueItem(
                            icon = Icons.Filled.Title,
                            title = "Recording name",
                            value = "$recordingPrefix 1 • $recordingPrefix 2",
                            subtitle = "e.g. \"$recordingPrefix 1\" — numbering follows highest existing",
                            onClick = { namingDraft = recordingPrefix; showNamingDialog = true }
                        )
                    }
                }
            }
        }
    }

    if (showStorageDialog) {
        val volumes = remember { StorageVolumes.list(context.applicationContext) }
        AlertDialog(
            onDismissRequest = { showStorageDialog = false },
            title = { Text("Storage folder", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("New recordings go here. Existing ones stay where they are.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    volumes.forEachIndexed { index, volume ->
                        ToggleButton(
                            checked = storageVolume == volume.id,
                            onCheckedChange = {
                                scope.launch {
                                    prefs.setRecordingsVolume(volume.id)
                                    (context.applicationContext as FlamboApp).storageVolumeId = volume.id
                                }
                                showStorageDialog = false
                            },
                            shapes = when (index) {
                                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                volumes.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
                            Column(modifier = Modifier.weight(1f).padding(start = 8.dp, top = 4.dp, bottom = 4.dp)) {
                                Text(volume.label, style = MaterialTheme.typography.titleMedium)
                                Text(StorageVolumes.formatBytes(volume.freeBytes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showStorageDialog = false }, shapes = ButtonDefaults.shapes()) { Text("Close") } },
            shape = ShapeLargeIncreased
        )
    }

    if (showNamingDialog) {
        AlertDialog(
            onDismissRequest = { showNamingDialog = false },
            title = { Text("Recording name", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Choose the prefix for new recordings. Numbering follows the highest existing number and restarts at 1 when the library is empty.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = namingDraft,
                        onValueChange = { namingDraft = it },
                        label = { Text("Prefix") },
                        placeholder = { Text("e.g. Sound, Voice, MyRec") },
                        singleLine = true,
                        shape = ShapeLargeIncreased,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Preview: \"${namingDraft.ifBlank { "Recording" }} 1\" • \"${namingDraft.ifBlank { "Recording" }} 2\"", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            prefs.setRecordingPrefix(namingDraft)
                            (context.applicationContext as? FlamboApp)?.let { it.recordingPrefix = namingDraft.ifBlank { "Recording" } }
                        }
                        showNamingDialog = false
                    }, shapes = ButtonDefaults.shapes()
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showNamingDialog = false }, shapes = ButtonDefaults.shapes()) { Text("Cancel") } },
            shape = ShapeLargeIncreased
        )
    }
}

