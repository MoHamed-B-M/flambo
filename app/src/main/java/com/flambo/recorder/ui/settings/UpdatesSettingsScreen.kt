package com.flambo.recorder.ui.settings

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import com.flambo.recorder.data.PreferencesManager
import com.flambo.recorder.update.ApkInstaller
import com.flambo.recorder.update.UpdateCheck
import com.flambo.recorder.update.UpdateChecker
import com.flambo.recorder.update.UpdateDownloadState
import com.flambo.recorder.ui.settings.components.SectionHeader
import com.flambo.recorder.ui.theme.ShapeLargeIncreased
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdatesSettingsScreen(
    prefs: PreferencesManager,
    scope: CoroutineScope,
    updateDownload: UpdateDownloadState,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val updateChannel by prefs.updateChannelFlow.collectAsState(initial = UpdateChecker.CHANNEL_BETA)
    val autoUpdateCheck by prefs.autoUpdateCheckFlow.collectAsState(initial = false)
    var checkingUpdate by remember { mutableStateOf(false) }
    var updateResult by remember { mutableStateOf<UpdateCheck?>(null) }
    var downloadError by remember { mutableStateOf<String?>(null) }
    var needsUnknownSources by remember { mutableStateOf(false) }
    var installedLabel by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        val (version, code) = UpdateChecker.installed(context.applicationContext)
        installedLabel = "v$version • build ${(code - 1).coerceAtLeast(0)}"
    }
    LaunchedEffect(Unit) {
        if (updateDownload.downloadedFile == null && updateDownload.progress == null) {
            val pending = runCatching { prefs.pendingApkDelete() }.getOrNull()
            if (!pending.isNullOrBlank()) {
                val f = File(ApkInstaller.updatesDir(context.applicationContext), pending)
                if (f.exists()) {
                    updateDownload.downloadedFile = f
                    updateDownload.downloadedAssetName = f.name
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Updates", style = MaterialTheme.typography.titleLarge) },
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
                SectionHeader(title = "Release Channel")
                Surface(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("Release channel", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    if (installedLabel.isBlank()) "Checking installed version…" else "Installed $installedLabel",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween), modifier = Modifier.fillMaxWidth()) {
                            listOf(UpdateChecker.CHANNEL_BETA to "Beta", UpdateChecker.CHANNEL_STABLE to "Stable").forEachIndexed { index, (value, label) ->
                                ToggleButton(
                                    checked = updateChannel == value,
                                    onCheckedChange = {
                                        scope.launch { prefs.setUpdateChannel(value) }
                                        updateResult = null
                                        updateDownload.downloadedFile?.let { runCatching { it.delete() } }
                                        updateDownload.clear()
                                        scope.launch { prefs.clearPendingApkDelete() }
                                        downloadError = null
                                        needsUnknownSources = false
                                    },
                                    shapes = when (index) {
                                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                        else -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                    },
                                    modifier = Modifier.weight(1f)
                                ) { Text(label) }
                            }
                        }
                        Text(
                            if (updateChannel == UpdateChecker.CHANNEL_BETA) "Beta follows the rolling preview by build number — newest first." else "Stable follows versioned releases only.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Check on launch", style = MaterialTheme.typography.titleSmall)
                                Text("Show a snackbar when an update is ready", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = autoUpdateCheck, onCheckedChange = { scope.launch { prefs.setAutoUpdateCheck(it) } })
                        }
                        when {
                            checkingUpdate -> LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
                            updateResult?.error != null -> Text(updateResult?.error ?: "", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                            updateResult != null -> {
                                val result = updateResult!!
                                if (result.available && result.release != null) {
                                    Surface(shape = ShapeLargeIncreased, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
                                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text("Update ready: v${result.release.version}" + if (result.release.buildNumber > 0) " • build ${result.release.buildNumber}" else "", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                            Text(result.release.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f))
                                            val asset = result.release.bestApk()
                                            val downloading = updateDownload.progress != null
                                            val savedFile = updateDownload.fileFor(asset?.name)
                                            if (downloading) {
                                                LinearWavyProgressIndicator(progress = { updateDownload.progress ?: 0f }, modifier = Modifier.fillMaxWidth())
                                                Text("Downloading ${asset?.name ?: "update"}… ${((updateDownload.progress ?: 0f) * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f))
                                            }
                                            if (downloadError != null) Text(downloadError ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                            if (needsUnknownSources) Text("Allow “Install unknown apps” for Flambo, then tap again.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                            if (savedFile != null && !downloading) {
                                                Text("Downloaded ${savedFile.name} — kept until you install.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f))
                                                FilledTonalButton(
                                                    onClick = {
                                                        if (!ApkInstaller.canInstall(context)) {
                                                            needsUnknownSources = true
                                                            context.startActivity(ApkInstaller.unknownSourcesIntent(context))
                                                            return@FilledTonalButton
                                                        }
                                                        needsUnknownSources = false
                                                        context.startActivity(ApkInstaller.installIntent(context, savedFile))
                                                    },
                                                    shapes = ButtonDefaults.shapes(),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                                    Spacer(Modifier.width(8.dp))
                                                    Text("Install update")
                                                }
                                                TextButton(onClick = {
                                                    runCatching { savedFile.delete() }
                                                    updateDownload.downloadedFile = null
                                                    updateDownload.downloadedAssetName = null
                                                    scope.launch { prefs.clearPendingApkDelete() }
                                                }, shapes = ButtonDefaults.shapes()) { Text("Delete file") }
                                            } else {
                                                FilledTonalButton(
                                                    onClick = {
                                                        if (asset == null) {
                                                            downloadError = "No APK attached to this release yet."
                                                            return@FilledTonalButton
                                                        }
                                                        if (!ApkInstaller.canInstall(context)) {
                                                            needsUnknownSources = true
                                                            context.startActivity(ApkInstaller.unknownSourcesIntent(context))
                                                            return@FilledTonalButton
                                                        }
                                                        needsUnknownSources = false
                                                        downloadError = null
                                                        updateDownload.downloadedFile?.takeIf { it.name != asset.name }?.let { runCatching { it.delete() } }
                                                        updateDownload.progress = 0f
                                                        updateDownload.job = scope.launch {
                                                            val res = ApkInstaller.download(context.applicationContext, asset.url, asset.name) { updateDownload.progress = it }
                                                            updateDownload.progress = null
                                                            updateDownload.job = null
                                                            res.onSuccess { file ->
                                                                updateDownload.downloadedFile = file
                                                                updateDownload.downloadedAssetName = file.name
                                                                prefs.setPendingApkDelete(file.name)
                                                            }.onFailure { downloadError = it.message ?: "Download failed." }
                                                        }
                                                    },
                                                    enabled = !downloading,
                                                    shapes = ButtonDefaults.shapes(),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                                    Spacer(Modifier.width(8.dp))
                                                    Text(if (downloading) "Downloading…" else "Download update")
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    Text("You're up to date on ${if (updateChannel == UpdateChecker.CHANNEL_BETA) "beta" else "stable"}.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        FilledTonalButton(
                            onClick = {
                                checkingUpdate = true
                                updateResult = null
                                updateDownload.job?.cancel()
                                updateDownload.job = null
                                updateDownload.progress = null
                                downloadError = null
                                needsUnknownSources = false
                                scope.launch {
                                    updateResult = UpdateChecker.check(context.applicationContext, updateChannel)
                                    checkingUpdate = false
                                }
                            },
                            enabled = !checkingUpdate && updateDownload.progress == null,
                            shapes = ButtonDefaults.shapes(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(if (checkingUpdate) "Checking…" else "Check for updates")
                        }
                    }
                }
            }
        }
    }
}

