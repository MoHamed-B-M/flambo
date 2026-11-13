package com.pranshulgg.recordmaster.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.pranshulgg.recordmaster.R
import com.pranshulgg.recordmaster.RecordMasterApp
import com.pranshulgg.recordmaster.core.stt.ModelImportState
import com.pranshulgg.recordmaster.core.stt.SpeechModel
import com.pranshulgg.recordmaster.core.stt.SpeechModelCatalog
import com.pranshulgg.recordmaster.utils.formatBytes
import com.pranshulgg.recordmaster.utils.topPadding
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeechModelsScreen(
    snackbarHostState: SnackbarHostState,
    navController: NavController
) {
    val context = LocalContext.current
    val app = remember(context) { context.applicationContext as RecordMasterApp }
    val scope = rememberCoroutineScope()

    val models by app.speechModels.models.collectAsState(initial = SpeechModelCatalog.BUILT_INS)
    val selectedId by app.speechModels.selectedModelId
        .collectAsState(initial = SpeechModelCatalog.DEFAULT.id)
    val importState by app.transcription.modelManager.importState
        .collectAsState(initial = ModelImportState.Idle)
    val downloadProgress by app.transcription.modelManager.progress
        .collectAsState(initial = emptyMap())

    var showImportSheet by remember { mutableStateOf(false) }
    var showUrlDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<SpeechModel?>(null) }
    var deleteTarget by remember { mutableStateOf<SpeechModel?>(null) }
    var busyModelId by remember { mutableStateOf<String?>(null) }
    var importJob by remember { mutableStateOf<Job?>(null) }
    var refreshTick by remember { mutableStateOf(0) }

    val installed = remember(models, refreshTick) {
        models.associate { it.id to app.transcription.modelManager.isInstalled(it) }
    }

    fun message(text: String) {
        scope.launch { snackbarHostState.showSnackbar(text) }
    }

    suspend fun handleImport(result: Result<SpeechModel>) {
        result.onSuccess { model ->
            app.speechModels.add(model)
            app.speechModels.select(model.id)
            app.transcription.release()
            message("Model imported")
        }.onFailure { error ->
            message(error.message ?: "Import failed.")
        }
    }

    fun useModel(model: SpeechModel) {
        if (busyModelId != null || importState !is ModelImportState.Idle) return
        scope.launch {
            if (!app.transcription.modelManager.isInstalled(model)) {
                if (!model.isBuiltIn) {
                    message("This model isn't on this device. Import it again.")
                    return@launch
                }
                busyModelId = model.id
                val download = app.transcription.modelManager.ensureModel(model)
                busyModelId = null
                refreshTick++
                if (download.isFailure) {
                    message(download.exceptionOrNull()?.message ?: "Download failed.")
                    return@launch
                }
            }
            app.speechModels.select(model.id)
            app.transcription.release()
            message("Using ${model.displayName}")
        }
    }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            importJob = scope.launch {
                handleImport(app.transcription.modelManager.importFromUri(uri))
            }
        }
    }

    val installedCount = models.count { installed[it.id] == true }
    val usedBytes = models
        .filter { installed[it.id] == true }
        .sumOf { it.sizeBytes }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = 4.dp,
                            end = 16.dp,
                            top = topPadding() + 8.dp,
                            bottom = 12.dp
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            painter = painterResource(R.drawable.arrow_back_24px),
                            contentDescription = "Back"
                        )
                    }
                    Text(
                        text = "Speech models",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.W600
                    )
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding()),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("On-device only", fontWeight = FontWeight.W600)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Models run locally. Audio never leaves your device.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                Text(
                    text = "Speech models",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.W700,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 6.dp, start = 3.dp)
                )
            }

            items(models, key = { it.id }) { model ->
                ModelRow(
                    model = model,
                    active = model.id == selectedId,
                    installed = installed[model.id] == true,
                    downloadProgress = downloadProgress[model.id],
                    enabled = busyModelId == null && importState is ModelImportState.Idle,
                    onUse = { useModel(model) },
                    onRename = { renameTarget = model },
                    onDelete = { deleteTarget = model }
                )
            }

            val working = importState as? ModelImportState.Working
            if (working != null) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Importing ${working.label}",
                                fontWeight = FontWeight.W600,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(10.dp))
                            val progress = working.progress
                            if (progress == null) {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            } else {
                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (progress == null) "Checking model\u2026"
                                    else "${(progress * 100).toInt()}%",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                TextButton(onClick = { importJob?.cancel() }) {
                                    Text("Cancel")
                                }
                            }
                        }
                    }
                }
            }

            item {
                FilledTonalButton(
                    onClick = { showImportSheet = true },
                    enabled = importState is ModelImportState.Idle,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Text("Import model")
                }
            }

            item {
                Text(
                    text = if (installedCount == 0) "No model downloaded yet"
                    else "${formatBytes(usedBytes)} used by speech models",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 3.dp)
                )
            }
        }
    }

    if (showImportSheet) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { showImportSheet = false },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, bottom = 32.dp)
            ) {
                Text("Import model", fontSize = 22.sp, fontWeight = FontWeight.W600)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "GGML .bin files only \u00B7 multilingual models recommended",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                ListItem(
                    headlineContent = { Text("Choose a file") },
                    supportingContent = { Text("Pick a model already on this device") },
                    leadingContent = {
                        Icon(
                            painter = painterResource(R.drawable.folder_24px),
                            contentDescription = null
                        )
                    },
                    modifier = Modifier.clickable {
                        showImportSheet = false
                        filePicker.launch(arrayOf("*/*"))
                    }
                )
                ListItem(
                    headlineContent = { Text("Paste a link") },
                    supportingContent = { Text("Download from a URL") },
                    leadingContent = {
                        Icon(
                            painter = painterResource(R.drawable.notes_24px),
                            contentDescription = null
                        )
                    },
                    modifier = Modifier.clickable {
                        showImportSheet = false
                        showUrlDialog = true
                    }
                )
            }
        }
    }

    if (showUrlDialog) {
        var url by remember { mutableStateOf("") }
        val valid = url.trim().startsWith("https://", ignoreCase = true)
        AlertDialog(
            onDismissRequest = { showUrlDialog = false },
            title = { Text("Paste a link") },
            text = {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    placeholder = { Text("https://huggingface.co/.../ggml-small-q8_0.bin") },
                    minLines = 2,
                    isError = url.isNotBlank() && !valid,
                    supportingText = {
                        if (url.isNotBlank() && !valid) Text("Enter an https:// link")
                    }
                )
            },
            confirmButton = {
                TextButton(
                    enabled = valid,
                    onClick = {
                        showUrlDialog = false
                        importJob = scope.launch {
                            handleImport(
                                app.transcription.modelManager.importFromUrl(url.trim())
                            )
                        }
                    }
                ) { Text("Import") }
            },
            dismissButton = {
                TextButton(onClick = { showUrlDialog = false }) { Text("Cancel") }
            }
        )
    }

    renameTarget?.let { target ->
        var name by remember(target.id) { mutableStateOf(target.displayName) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename model") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            app.speechModels.rename(target.id, name)
                            renameTarget = null
                        }
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("Cancel") }
            }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete model?") },
            text = { Text("This removes ${target.displayName} from this device.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            val wasActive = target.id == selectedId
                            app.transcription.modelManager.delete(target)
                            app.speechModels.remove(target.id)
                            if (wasActive) app.transcription.release()
                            deleteTarget = null
                            message("Model deleted")
                        }
                    }
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ModelRow(
    model: SpeechModel,
    active: Boolean,
    installed: Boolean,
    downloadProgress: Float?,
    enabled: Boolean,
    onUse: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }

    Surface(
        color = if (active) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onUse)
    ) {
        Row(
            modifier = Modifier.padding(start = 8.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = active, onClick = null, enabled = enabled)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = model.displayName,
                    fontWeight = FontWeight.W600,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = metaText(model, installed),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (downloadProgress != null) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { downloadProgress },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            Box {
                IconButton(onClick = { menuOpen = true }, enabled = enabled) {
                    Icon(
                        painter = painterResource(R.drawable.more_vert_24px),
                        contentDescription = "More options"
                    )
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Use") },
                        onClick = {
                            menuOpen = false
                            onUse()
                        }
                    )
                    if (!model.isBuiltIn) {
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            onClick = {
                                menuOpen = false
                                onRename()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            onClick = {
                                menuOpen = false
                                onDelete()
                            }
                        )
                    }
                }
            }
        }
    }
}

private fun metaText(model: SpeechModel, installed: Boolean): String = buildString {
    append(if (model.isBuiltIn) "Built-in" else "Imported")
    append(" \u00B7 ")
    append(if (model.multilingual) "multilingual" else "English only")
    append(" \u00B7 ")
    if (installed) append(formatBytes(model.sizeBytes)) else append("not downloaded")
}
