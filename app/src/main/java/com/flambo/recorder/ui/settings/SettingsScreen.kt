package com.flambo.recorder.ui.settings

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.flambo.recorder.R
import android.os.Build
import com.flambo.recorder.data.PreferencesManager
import com.flambo.recorder.domain.RecordingQuality
import com.flambo.recorder.record.AudioSource
import com.flambo.recorder.record.MediaProjectionHolder
import com.flambo.recorder.stt.TranscriptionManager
import com.flambo.recorder.update.ApkInstaller
import com.flambo.recorder.stt.VoskModelManager
import com.flambo.recorder.update.UpdateCheck
import com.flambo.recorder.update.UpdateChecker
import com.flambo.recorder.ui.components.SegmentedList
import com.flambo.recorder.ui.components.segmentedListItemColors
import com.flambo.recorder.ui.theme.ShapeFull
import com.flambo.recorder.ui.theme.ShapeLargeIncreased
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(
    prefs: PreferencesManager,
    scope: CoroutineScope,
    transcription: TranscriptionManager,
    onBack: () -> Unit,
    onRerunIntro: () -> Unit = {},
    onRequestSystemCapture: () -> Unit = {}
) {
    val quality by prefs.qualityFlow.collectAsState(initial = RecordingQuality.HIGH)
    val audioSource by prefs.audioSourceFlow.collectAsState(initial = "mic")
    val dynamicColor by prefs.dynamicColorFlow.collectAsState(initial = true)
    val reminder by prefs.recordingReminderFlow.collectAsState(initial = true)
    val darkTheme by prefs.darkThemeFlow.collectAsState(initial = "system")
    val sttLanguage by prefs.sttLanguageFlow.collectAsState(initial = "")
    val modelProgress by transcription.modelProgress.collectAsState()
    val updateChannel by prefs.updateChannelFlow.collectAsState(initial = UpdateChecker.CHANNEL_BETA)

    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    var checkingUpdate by remember { mutableStateOf(false) }
    var updateResult by remember { mutableStateOf<UpdateCheck?>(null) }
    var downloadProgress by remember { mutableStateOf<Float?>(null) }
    var downloadError by remember { mutableStateOf<String?>(null) }
    var needsUnknownSources by remember { mutableStateOf(false) }
    val autoUpdateCheck by prefs.autoUpdateCheckFlow.collectAsState(initial = false)
    var installedLabel by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        val (version, code) = UpdateChecker.installed(context.applicationContext)
        installedLabel = "v$version • build ${(code - 1).coerceAtLeast(0)}"
    }

    var showQualityDialog by remember { mutableStateOf(false) }
    var showAudioSourceDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showSttLanguageDialog by remember { mutableStateOf(false) }
    var showVoskModelsDialog by remember { mutableStateOf(false) }
    var modelsTick by remember { mutableStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
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
                .padding(horizontal = 16.dp)
                .animateContentSize(spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("Recording", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))

            // Recording group — connected segmented rows, no shadows
            SegmentedList {
                item {
                    ListItem(
                        headlineContent = { Text("Quality") },
                        supportingContent = { Text("${quality.label} • ${quality.description}") },
                        leadingContent = { Icon(Icons.Filled.RecordVoiceOver, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        trailingContent = {
                            TextButton(
                                onClick = { showQualityDialog = true },
                                shapes = ButtonDefaults.shapes()
                            ) { Text("Change") }
                        },
                        colors = segmentedListItemColors()
                    )
                }
                item {
                    ListItem(
                        headlineContent = { Text("Recording reminder") },
                        supportingContent = { Text("Show a gentle reminder before long recordings") },
                        trailingContent = {
                            Switch(
                                checked = reminder,
                                onCheckedChange = { scope.launch { prefs.setRecordingReminder(it) } },
                                thumbContent = if (reminder) {
                                    { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(SwitchDefaults.IconSize)) }
                                } else null
                            )
                        },
                        colors = segmentedListItemColors()
                    )
                }
                item {
                    ListItem(
                        headlineContent = { Text("Audio source") },
                        supportingContent = { Text(AudioSource.fromPref(audioSource).label) },
                        leadingContent = {
                            Icon(
                                if (audioSource == "system") Icons.Filled.MusicNote else Icons.Filled.Mic,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingContent = {
                            TextButton(
                                onClick = { showAudioSourceDialog = true },
                                shapes = ButtonDefaults.shapes()
                            ) { Text("Change") }
                        },
                        colors = segmentedListItemColors()
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)

            Text("Appearance", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)

            SegmentedList {
                item {
                    ListItem(
                        headlineContent = { Text("Dynamic color") },
                        supportingContent = { Text("Use Material You colors from your wallpaper (Android 12+)") },
                        leadingContent = { Icon(Icons.Filled.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary) },
                        trailingContent = {
                            Switch(
                                checked = dynamicColor,
                                onCheckedChange = { scope.launch { prefs.setDynamicColor(it) } },
                                thumbContent = if (dynamicColor) {
                                    { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(SwitchDefaults.IconSize)) }
                                } else null
                            )
                        },
                        colors = segmentedListItemColors()
                    )
                }
                item {
                    ListItem(
                        headlineContent = { Text("Theme") },
                        supportingContent = { Text(when (darkTheme) { "light" -> "Light"; "dark" -> "Dark"; else -> "System default" }) },
                        leadingContent = {
                            Icon(
                                when (darkTheme) {
                                    "light" -> Icons.Filled.LightMode
                                    "dark" -> Icons.Filled.DarkMode
                                    else -> Icons.Filled.SettingsBrightness
                                },
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingContent = {
                            TextButton(
                                onClick = { showThemeDialog = true },
                                shapes = ButtonDefaults.shapes()
                            ) { Text("Change") }
                        },
                        colors = segmentedListItemColors()
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)

            Text("Speech-to-text", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)

            SegmentedList {
                item {
                    // Offline transcription runs fully on-device after a recording.
                    ListItem(
                        headlineContent = { Text("Offline transcription") },
                        supportingContent = { Text("Vosk turns recordings into text — no cloud, no account") },
                        leadingContent = { Icon(Icons.Filled.RecordVoiceOver, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        colors = segmentedListItemColors()
                    )
                }
                item {
                    ListItem(
                        headlineContent = { Text("Transcription language") },
                        supportingContent = {
                            Text(
                                VoskModelManager.forTag(sttLanguage.ifBlank { "en" })?.label
                                    ?: if (sttLanguage.isBlank()) "Best installed model" else sttLanguage
                            )
                        },
                        trailingContent = {
                            TextButton(
                                onClick = { showSttLanguageDialog = true },
                                shapes = ButtonDefaults.shapes()
                            ) { Text("Change") }
                        },
                        colors = segmentedListItemColors()
                    )
                }
                item {
                    val installedCount = remember(modelsTick) { transcription.vosk.models.installedCodes().size }
                    ListItem(
                        headlineContent = { Text("Vosk offline models") },
                        supportingContent = {
                            Text(
                                if (installedCount == 0) "None yet — download one to transcribe offline"
                                else "$installedCount language${if (installedCount > 1) "s" else ""} ready offline"
                            )
                        },
                        trailingContent = {
                            TextButton(
                                onClick = { showVoskModelsDialog = true },
                                shapes = ButtonDefaults.shapes()
                            ) { Text("Manage") }
                        },
                        colors = segmentedListItemColors()
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)

            Text("Updates", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)

            Surface(
                shape = ShapeLargeIncreased,
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Release channel", style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (installedLabel.isBlank()) "Checking installed version…"
                                else "Installed $installedLabel",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf(UpdateChecker.CHANNEL_BETA to "Beta", UpdateChecker.CHANNEL_STABLE to "Stable").forEachIndexed { index, (value, label) ->
                            ToggleButton(
                                checked = updateChannel == value,
                                onCheckedChange = {
                                    scope.launch { prefs.setUpdateChannel(value) }
                                    updateResult = null
                                    downloadProgress = null
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
                        if (updateChannel == UpdateChecker.CHANNEL_BETA) "Beta follows the rolling preview by build number — newest first."
                        else "Stable follows versioned releases only.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Check on launch", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Show a snackbar when an update is ready",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = autoUpdateCheck,
                            onCheckedChange = { scope.launch { prefs.setAutoUpdateCheck(it) } }
                        )
                    }
                    when {
                        checkingUpdate -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        updateResult?.error != null -> Text(
                            updateResult?.error ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        updateResult != null -> {
                            val result = updateResult!!
                            if (result.available && result.release != null) {
                                Surface(
                                    shape = ShapeLargeIncreased,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                            "Update ready: v${result.release.version}" + if (result.release.buildNumber > 0) " • build ${result.release.buildNumber}" else "",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        Text(
                                            result.release.name,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                                        )
                                        val asset = result.release.bestApk()
                                        if (downloadProgress != null) {
                                            LinearProgressIndicator(
                                                progress = { downloadProgress ?: 0f },
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                            Text(
                                                "Downloading ${asset?.name ?: "update"}… ${((downloadProgress ?: 0f) * 100).toInt()}%",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                                            )
                                        }
                                        if (downloadError != null) {
                                            Text(
                                                downloadError ?: "",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                        if (needsUnknownSources) {
                                            Text(
                                                "Allow “Install unknown apps” for Flambo, then tap again.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
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
                                                downloadProgress = 0f
                                                scope.launch {
                                                    val res = ApkInstaller.download(
                                                        context.applicationContext,
                                                        asset.url,
                                                        asset.name
                                                    ) { downloadProgress = it }
                                                    downloadProgress = null
                                                    res.onSuccess { file ->
                                                        prefs.setPendingApkDelete(file.name)
                                                        context.startActivity(ApkInstaller.installIntent(context, file))
                                                    }.onFailure {
                                                        downloadError = it.message ?: "Download failed."
                                                    }
                                                }
                                            },
                                            enabled = downloadProgress == null,
                                            shapes = ButtonDefaults.shapes(),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text(if (downloadProgress != null) "Downloading…" else "Download & install")
                                        }
                                    }
                                }
                            } else {
                                Text(
                                    "You're up to date on ${if (updateChannel == UpdateChecker.CHANNEL_BETA) "beta" else "stable"}.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    FilledTonalButton(
                        onClick = {
                            checkingUpdate = true
                            updateResult = null
                            downloadProgress = null
                            downloadError = null
                            needsUnknownSources = false
                            scope.launch {
                                updateResult = UpdateChecker.check(context.applicationContext, updateChannel)
                                checkingUpdate = false
                            }
                        },
                        enabled = !checkingUpdate,
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (checkingUpdate) "Checking…" else "Check for updates")
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)

            Text("About", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)

            // Replay the first-launch tour
            Surface(
                shape = ShapeLargeIncreased,
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                ListItem(
                    headlineContent = { Text("Replay introduction") },
                    supportingContent = { Text("Take the quick tour again") },
                    leadingContent = { Icon(Icons.Filled.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    trailingContent = {
                        TextButton(
                            onClick = onRerunIntro,
                            shapes = ButtonDefaults.shapes()
                        ) { Text("Replay") }
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                )
            }

            // App card — flat tonal, follows dynamic color, no shadow
            androidx.compose.material3.Card(
                shape = ShapeLargeIncreased,
                colors = androidx.compose.material3.CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ),
                elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(20.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = ShapeFull,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(56.dp)
                    ) {
                        androidx.compose.foundation.layout.Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Icon(
                                Icons.Filled.Mic,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "Flambo",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            AssistChip(onClick = {}, label = { Text("v1.0") })
                        }
                        Text(
                            "A calm, expressive voice recorder. Transcripts and recordings stay on your device.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            // Developer card — flat tonal, follows dynamic color, no shadow
            androidx.compose.material3.Card(
                shape = ShapeLargeIncreased,
                colors = androidx.compose.material3.CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                ),
                elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.github_avatar),
                            contentDescription = "MoHamed-B-M profile picture",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .border(2.dp, MaterialTheme.colorScheme.primaryContainer, CircleShape)
                        )
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                "Hamma",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Text(
                                "@MoHamed-B-M",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Text(
                                "Indie Android dev • voice nerd",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        androidx.compose.material3.FilledTonalButton(
                            onClick = { uriHandler.openUri("https://github.com/MoHamed-B-M") },
                            shapes = ButtonDefaults.shapes(),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("GitHub")
                        }
                        OutlinedButton(
                            onClick = { uriHandler.openUri("https://github.com/MoHamed-B-M/flambo/issues/new") },
                            shape = ShapeFull,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.BugReport, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Report issue")
                        }
                    }
                }
            }
            Spacer(Modifier.size(24.dp))
        }
    }

    if (showQualityDialog) {
        AlertDialog(
            onDismissRequest = { showQualityDialog = false },
            title = { Text("Recording quality", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Expressive ToggleButton group for quality
                    RecordingQuality.entries.forEachIndexed { index, q ->
                        val selected = q == quality
                        ToggleButton(
                            checked = selected,
                            onCheckedChange = {
                                scope.launch { prefs.setQuality(q) }
                                showQualityDialog = false
                            },
                            shapes = when (index) {
                                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                RecordingQuality.entries.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(vertical = 4.dp)) {
                                Text(q.label, style = MaterialTheme.typography.titleMedium)
                                Text(q.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showQualityDialog = false },
                    shapes = ButtonDefaults.shapes()
                ) { Text("Close") }
            },
            shape = ShapeLargeIncreased
        )
    }

    if (showAudioSourceDialog) {
        val systemSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        AlertDialog(
            onDismissRequest = { showAudioSourceDialog = false },
            title = { Text("Audio source", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "System sound captures music, videos and anything else playing on this phone. Android will show a screen-recording prompt first — Flambo only records sound, never your screen.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    listOf(
                        Triple("mic", "Microphone", "Your voice and the room around you"),
                        Triple(
                            "system",
                            "System sound" + if (systemSupported) "" else " (needs Android 10+)",
                            "Music, videos and app audio playing on this device"
                        )
                    ).forEachIndexed { index, (value, label, description) ->
                        val enabled = value == "mic" || systemSupported
                        ToggleButton(
                            checked = audioSource == value,
                            onCheckedChange = {
                                if (value == "system") {
                                    // Reuse the live grant when possible; otherwise ask the
                                    // system, which flips the pref itself on approval.
                                    if (MediaProjectionHolder.hasGrant()) {
                                        scope.launch { prefs.setAudioSource(value) }
                                    } else {
                                        onRequestSystemCapture()
                                    }
                                } else {
                                    scope.launch { prefs.setAudioSource(value) }
                                }
                                showAudioSourceDialog = false
                            },
                            enabled = enabled,
                            shapes = when (index) {
                                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                else -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                if (value == "mic") Icons.Filled.Mic else Icons.Filled.MusicNote,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Column(modifier = Modifier.weight(1f).padding(start = 8.dp, top = 4.dp, bottom = 4.dp)) {
                                Text(label, style = MaterialTheme.typography.titleMedium)
                                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showAudioSourceDialog = false },
                    shapes = ButtonDefaults.shapes()
                ) { Text("Close") }
            },
            shape = ShapeLargeIncreased
        )
    }

    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("Theme", style = MaterialTheme.typography.titleLarge) },
            text = {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val options = listOf("system" to "System", "light" to "Light", "dark" to "Dark")
                    options.forEachIndexed { index, (value, label) ->
                        ToggleButton(
                            checked = darkTheme == value,
                            onCheckedChange = {
                                scope.launch { prefs.setDarkTheme(value) }
                                showThemeDialog = false
                            },
                            shapes = when (index) {
                                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                when (value) {
                                    "light" -> Icons.Filled.LightMode
                                    "dark" -> Icons.Filled.DarkMode
                                    else -> Icons.Filled.SettingsBrightness
                                },
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(label, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showThemeDialog = false },
                    shapes = ButtonDefaults.shapes()
                ) { Text("Close") }
            },
            shape = ShapeLargeIncreased
        )
    }

    if (showSttLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showSttLanguageDialog = false },
            title = { Text("Transcription language", style = MaterialTheme.typography.titleLarge) },
            text = {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    item {
                        ToggleButton(
                            checked = sttLanguage.isBlank(),
                            onCheckedChange = {
                                scope.launch { prefs.setSttLanguage("") }
                                showSttLanguageDialog = false
                            },
                            shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Best installed model") }
                    }
                    items(VoskModelManager.CATALOG, key = { it.code }) { model ->
                        ToggleButton(
                            checked = sttLanguage == model.code,
                            onCheckedChange = {
                                scope.launch { prefs.setSttLanguage(model.code) }
                                showSttLanguageDialog = false
                            },
                            shapes = ButtonGroupDefaults.connectedMiddleButtonShapes(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(model.label, modifier = Modifier.weight(1f))
                            Text(
                                if (transcription.vosk.models.isInstalled(model.code)) "ready"
                                else "~${model.sizeMb} MB",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showSttLanguageDialog = false },
                    shapes = ButtonDefaults.shapes()
                ) { Text("Close") }
            },
            shape = ShapeLargeIncreased
        )
    }

    if (showVoskModelsDialog) {
        AlertDialog(
            onDismissRequest = { showVoskModelsDialog = false },
            title = { Text("Offline models", style = MaterialTheme.typography.titleLarge) },
            text = {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(VoskModelManager.CATALOG, key = { it.code }) { model ->
                        val installed = remember(modelsTick, modelProgress) {
                            transcription.vosk.models.isInstalled(model.code)
                        }
                        val prog = modelProgress[model.code]
                        Surface(
                            shape = ShapeLargeIncreased,
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(model.label, style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            if (installed) "Ready offline"
                                            else "~${model.sizeMb} MB one-time download" + if (model.large) " • large" else "",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    when {
                                        prog != null -> Text(
                                            "${(prog * 100).toInt()}%",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        installed -> IconButton(onClick = {
                                            scope.launch {
                                                transcription.vosk.models.delete(model.code)
                                                modelsTick++
                                            }
                                        }) {
                                            Icon(Icons.Filled.Delete, contentDescription = "Delete ${model.label} model", tint = MaterialTheme.colorScheme.error)
                                        }
                                        else -> FilledTonalButton(
                                            onClick = {
                                                scope.launch {
                                                    transcription.vosk.models.download(model.code)
                                                    modelsTick++
                                                }
                                            },
                                            shapes = ButtonDefaults.shapes()
                                        ) {
                                            Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text("Get")
                                        }
                                    }
                                }
                                if (prog != null) {
                                    LinearProgressIndicator(progress = { prog }, modifier = Modifier.fillMaxWidth())
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showVoskModelsDialog = false },
                    shapes = ButtonDefaults.shapes()
                ) { Text("Done") }
            },
            shape = ShapeLargeIncreased
        )
    }
}
