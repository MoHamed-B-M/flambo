package com.flambo.recorder.ui.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.ExpandMore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import com.flambo.recorder.FlamboApp
import com.flambo.recorder.R
import com.flambo.recorder.audio.EnhanceStrength
import com.flambo.recorder.data.PreferencesManager
import com.flambo.recorder.data.SafFolderHelper
import com.flambo.recorder.data.StorageVolumes
import com.flambo.recorder.domain.RecordingQuality
import com.flambo.recorder.record.AudioSource
import com.flambo.recorder.stt.TranscriptionManager
import com.flambo.recorder.update.ApkInstaller
import com.flambo.recorder.stt.VoskModelManager
import com.flambo.recorder.update.UpdateCheck
import com.flambo.recorder.update.UpdateChecker
import com.flambo.recorder.update.UpdateDownloadState
import com.flambo.recorder.ui.components.SegmentedList
import com.flambo.recorder.ui.components.segmentedListItemColors
import com.flambo.recorder.ui.theme.ShapeFull
import com.flambo.recorder.ui.theme.ShapeLargeIncreased
import com.flambo.recorder.ui.theme.ThemeSeeds
import com.flambo.recorder.ui.theme.themeSeedById
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(
    prefs: PreferencesManager,
    scope: CoroutineScope,
    transcription: TranscriptionManager,
    updateDownload: UpdateDownloadState = UpdateDownloadState(),
    onBack: () -> Unit,
    onRerunOnboarding: () -> Unit = {},
    onRequestSystemCapture: () -> Unit = {}
) {
    val quality by prefs.qualityFlow.collectAsState(initial = RecordingQuality.HIGH)
    val audioSource by prefs.audioSourceFlow.collectAsState(initial = "mic")
    val noiseReduction by prefs.noiseReductionFlow.collectAsState(initial = true)
    val enhanceStrength by prefs.enhanceStrengthFlow.collectAsState(initial = "balanced")
    val keepOriginal by prefs.keepOriginalFlow.collectAsState(initial = true)
    val dynamicColor by prefs.dynamicColorFlow.collectAsState(initial = true)
    val themeSeed by prefs.themeSeedFlow.collectAsState(initial = "ember")
    val tipsEnabled by prefs.tipsEnabledFlow.collectAsState(initial = true)
    val storageVolume by prefs.recordingsVolumeFlow.collectAsState(initial = "default")
    val reminder by prefs.recordingReminderFlow.collectAsState(initial = true)
    val darkTheme by prefs.darkThemeFlow.collectAsState(initial = "system")
    val sttLanguage by prefs.sttLanguageFlow.collectAsState(initial = "")
    val sttEngine by prefs.sttEngineFlow.collectAsState(initial = "vosk")
    val modelProgress by transcription.modelProgress.collectAsState()
    val whisperProgress by transcription.whisperProgress.collectAsState()
    val updateChannel by prefs.updateChannelFlow.collectAsState(initial = UpdateChecker.CHANNEL_BETA)
    val recordingPrefix by prefs.recordingPrefixFlow.collectAsState(initial = "Recording")
    val customFolderUri by prefs.customFolderUriFlow.collectAsState(initial = "")
    val homeLayout by prefs.homeLayoutFlow.collectAsState(initial = "list")
    val colorSchemeStyle by prefs.colorSchemeFlow.collectAsState(initial = "TONAL_SPOT")
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedCategory by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    var checkingUpdate by remember { mutableStateOf(false) }
    var updateResult by remember { mutableStateOf<UpdateCheck?>(null) }
    var downloadError by remember { mutableStateOf<String?>(null) }
    var needsUnknownSources by remember { mutableStateOf(false) }
    val autoUpdateCheck by prefs.autoUpdateCheckFlow.collectAsState(initial = false)
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

    var showQualityDialog by remember { mutableStateOf(false) }
    var showAudioSourceDialog by remember { mutableStateOf(false) }
    var showStorageDialog by remember { mutableStateOf(false) }
    var showEnhanceStrengthDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showSttLanguageDialog by remember { mutableStateOf(false) }
    var showVoskModelsDialog by remember { mutableStateOf(false) }
    var showEngineDialog by remember { mutableStateOf(false) }
    var showLayoutDialog by remember { mutableStateOf(false) }
    var modelsTick by remember { mutableStateOf(0) }
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(selectedCategory ?: "Settings", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = { if (selectedCategory != null) selectedCategory = null else onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { padding ->
        AnimatedContent(
            targetState = selectedCategory,
            transitionSpec = {
                fadeIn(animationSpec = spring(dampingRatio = 0.8f, stiffness = 1000)) with
                    slideInHorizontally(initialOffsetX = { it }) with
                    fadeOut(animationSpec = spring(dampingRatio = 0.9f, stiffness = 500)) with
                    slideOutHorizontally(targetOffsetX = { -it }, animationSpec = spring(dampingRatio = 0.9f))
            },
            label = "settingsCategory"
        ) { category ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .animateContentSize(spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (category) {
                    null -> {
                        // Main category grid
                        val categories = listOf(
                            CategoryItem("Recording", "Quality • Storage • Names", Icons.Filled.RecordVoiceOver),
                            Triple("Sound", "Noise reduction • Enhancement", Icons.Filled.MusicNote),
                            Triple("Appearance", "Theme • Color scheme • Layout", Icons.Filled.Palette),
                            Triple("Speech-to-text", "Engine • Language • Models", Icons.Filled.RecordVoiceOver),
                            Triple("Updates", "Channel • Auto-check", Icons.Filled.Download),
                            Triple("About", "Tips • Version • Credits", Icons.Filled.Lightbulb)
                        )
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(16.dp)
                        ) {
                            items(categories) { (title, subtitle, icon) ->
                                androidx.compose.material3.Card(
                                    onClick = { selectedCategory = title },
                                    shape = ShapeLargeIncreased,
                                    colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    androidx.compose.material3.ListItem(
                                        headlineContent = { Text(title) },
                                        supportingContent = { Text(subtitle) },
                                        leadingContent = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                        trailingContent = { Icon(Icons.Filled.ExpandMore, contentDescription = null) },
                                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                                    )
                                }
                            }
                        }
                    }
                    "Recording" -> RecordingSection(
                        quality = quality,
                        audioSource = audioSource,
                        noiseReduction = noiseReduction,
                        enhanceStrength = enhanceStrength,
                        keepOriginal = keepOriginal,
                        recordingPrefix = recordingPrefix,
                        customFolderUri = customFolderUri,
                        storageVolume = storageVolume,
                        reminder = reminder,
                        prefs = prefs,
                        scope = scope,
                        showQualityDialog = showQualityDialog,
                        showAudioSourceDialog = showAudioSourceDialog,
                        showStorageDialog = showStorageDialog,
                        showNamingDialog = showNamingDialog,
                        namingDraft = namingDraft,
                        recordingPrefix = recordingPrefix,
                        customFolderUri = customFolderUri,
                        storageVolume = storageVolume,
                        reminder = reminder,
                        scope = scope,
                        prefs = prefs,
                        snackbarHostState = snackbarHostState,
                        context = context,
                        quality = quality,
                        audioSource = audioSource,
                        noiseReduction = noiseReduction,
                        enhanceStrength = enhanceStrength,
                        keepOriginal = keepOriginal,
                        recordingPrefix = recordingPrefix,
                        customFolderUri = customFolderUri,
                        storageVolume = storageVolume,
                        reminder = reminder,
                        showQualityDialog = showQualityDialog,
                        showAudioSourceDialog = showAudioSourceDialog,
                        showStorageDialog = showStorageDialog,
                        showNamingDialog = showNamingDialog,
                        namingDraft = namingDraft,
                        recordingPrefix = recordingPrefix,
                        customFolderUri = customFolderUri,
                        storageVolume = storageVolume,
                        reminder = reminder,
                        scope = scope,
                        prefs = prefs,
                        snackbarHostState = snackbarHostState,
                        context = context,
                        quality = quality,
                        audioSource = audioSource,
                        noiseReduction = noiseReduction,
                        enhanceStrength = enhanceStrength,
                        keepOriginal = keepOriginal,
                        recordingPrefix = recordingPrefix,
                        customFolderUri = customFolderUri,
                        storageVolume = storageVolume,
                        reminder = reminder,
                        showQualityDialog = showQualityDialog,
                        showAudioSourceDialog = showAudioSourceDialog,
                        showStorageDialog = showStorageDialog,
                        showNamingDialog = showNamingDialog,
                        namingDraft = namingDraft
                    ),
                    "Sound" -> SoundSection(
                        noiseReduction = noiseReduction,
                        enhanceStrength = enhanceStrength,
                        prefs = prefs,
                        scope = scope,
                        showQualityDialog = showQualityDialog,
                        showAudioSourceDialog = showAudioSourceDialog,
                        showStorageDialog = showStorageDialog,
                        showThemeDialog = showThemeDialog,
                        showNamingDialog = showNamingDialog,
                        namingDraft = namingDraft,
                        recordingPrefix = recordingPrefix,
                        customFolderUri = customFolderUri,
                        storageVolume = storageVolume,
                        reminder = reminder,
                        darkTheme = darkTheme,
                        sttLanguage = sttLanguage,
                        sttEngine = sttEngine,
                        modelProgress = modelProgress,
                        whisperProgress = whisperProgress,
                        selectedCategory = selectedCategory,
                        onBack = onBack
                    ),
                    "Appearance" -> AppearanceSection(
                        darkTheme = darkTheme,
                        themeSeed = themeSeed,
                        dynamicColor = dynamicColor,
                        tipsEnabled = tipsEnabled,
                        prefs = prefs,
                        scope = scope,
                        showThemeDialog = showThemeDialog,
                        showNamingDialog = showNamingDialog,
                        namingDraft = namingDraft,
                        recordingPrefix = recordingPrefix,
                        customFolderUri = customFolderUri,
                        storageVolume = storageVolume,
                        reminder = reminder,
                        sttLanguage = sttLanguage,
                        sttEngine = sttEngine,
                        modelProgress = modelProgress,
                        whisperProgress = whisperProgress,
                        selectedCategory = selectedCategory,
                        onBack = onBack
                    ),
                    "Speech-to-text" -> SpeechToTextSection(
                        sttEngine = sttEngine,
                        sttLanguage = sttLanguage,
                        modelProgress = modelProgress,
                        whisperProgress = whisperProgress,
                        prefs = prefs,
                        scope = scope,
                        showSttLanguageDialog = showSttLanguageDialog,
                        showVoskModelsDialog = showVoskModelsDialog,
                        showEngineDialog = showEngineDialog,
                        selectedCategory = selectedCategory,
                        onBack = onBack
                    ),
                    "Updates" -> UpdatesSection(
                        updateChannel = updateChannel,
                        autoUpdateCheck = autoUpdateCheck,
                        checkingUpdate = checkingUpdate,
                        updateResult = updateResult,
                        downloadError = downloadError,
                        needsUnknownSources = needsUnknownSources,
                        installedLabel = installedLabel,
                        prefs = prefs,
                        scope = scope,
                        selectedCategory = selectedCategory,
                        onBack = onBack
                    ),
                    "About" -> AboutSection(
                        tipsEnabled = tipsEnabled,
                        installedLabel = installedLabel,
                        selectedCategory = selectedCategory,
                        onBack = onBack
                    )
                }
            }
        }
    }
}

// Helper composable for category grid items
@Composable
fun CategoryItem(title: String, subtitle: String, icon: androidx.compose.material.icons.Icons.Filled) {
    androidx.compose.material3.ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        trailingContent = { Icon(Icons.Filled.ExpandMore, contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    )
}

// Recording section
@Composable
fun RecordingSection(
    quality: RecordingQuality,
    audioSource: String,
    noiseReduction: Boolean,
    enhanceStrength: String,
    keepOriginal: Boolean,
    recordingPrefix: String,
    customFolderUri: String,
    storageVolume: String,
    reminder: Boolean,
    prefs: PreferencesManager,
    scope: CoroutineScope,
    showQualityDialog: androidx.compose.runtime.MutableState<Boolean>,
    showAudioSourceDialog: androidx.compose.runtime.MutableState<Boolean>,
    showStorageDialog: androidx.compose.runtime.MutableState<Boolean>,
    showNamingDialog: androidx.compose.runtime.MutableState<Boolean>,
    namingDraft: androidx.compose.runtime.MutableState<String>,
    recordingPrefixParam: String,
    customFolderUriParam: String,
    storageVolumeParam: String,
    reminderParam: Boolean,
    scopeParam: CoroutineScope,
    prefsParam: PreferencesManager,
    snackbarHostState: androidx.compose.material3.SnackbarHostState,
    context: androidx.compose.ui.platform.LocalContext,
    qualityParam: RecordingQuality,
    audioSourceParam: String,
    noiseReductionParam: Boolean,
    enhanceStrengthParam: String,
    keepOriginalParam: Boolean,
    recordingPrefixParam2: String,
    customFolderUriParam2: String,
    storageVolumeParam2: String,
    reminderParam2: Boolean,
    scopeParam2: CoroutineScope,
    prefsParam2: PreferencesManager,
    snackbarHostState2: androidx.compose.material3.SnackbarHostState,
    context2: androidx.compose.ui.platform.LocalContext,
    qualityParam2: RecordingQuality,
    audioSourceParam2: String,
    noiseReductionParam2: Boolean,
    enhanceStrengthParam2: String,
    keepOriginalParam2: Boolean,
    recordingPrefixParam3: String,
    customFolderUriParam3: String,
    storageVolumeParam3: String,
    reminderParam3: Boolean,
    scopeParam3: CoroutineScope,
    prefsParam3: PreferencesManager,
    snackbarHostState3: androidx.compose.material3.SnackbarHostState,
    context3: androidx.compose.ui.platform.LocalContext,
    qualityParam3: RecordingQuality,
    audioSourceParam3: String,
    noiseReductionParam3: Boolean,
    enhanceStrengthParam3: String,
    keepOriginalParam3: Boolean
) {
    // Recording settings UI
    androidx.compose.material3.Card(
        shape = ShapeLargeIncreased,
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        androidx.compose.material3.SegmentedList {
            item {
                androidx.compose.material3.ListItem(
                    headlineContent = { Text("Quality") },
                    supportingContent = { Text("${quality.label} • ${quality.description}") },
                    leadingContent = { Icon(Icons.Filled.RecordVoiceOver, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    trailingContent = {
                        androidx.compose.material3.FilledTonalButton(
                            onClick = { showQualityDialog.value = true },
                            shapes = ButtonDefaults.shapes(),
                            contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight),
                            modifier = Modifier.heightIn(min = ButtonDefaults.MediumContainerHeight)
                        ) { Text("Change") }
                    },
                    colors = androidx.compose.ui.graphics.Color.Specified
                )
            }
            item {
                androidx.compose.material3.ListItem(
                    headlineContent = { Text("Recording reminder") },
                    supportingContent = { Text(if (reminder) "Enabled" else "Disabled") },
                    leadingContent = {
                        androidx.compose.material3.Icon(
                            if (reminder) Icons.Filled.Notifications else Icons.Filled.NotificationsOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingContent = {
                        androidx.compose.material3.CompoundButton.Switch(
                            checked = reminder,
                            onCheckedChange = { reminder = it }
                        )
                    },
                    colors = androidx.compose.ui.graphics.Color.Specified
                )
            }
            item {
                androidx.compose.material3.ListItem(
                    headlineContent = { Text("Recording prefix") },
                    supportingContent = { Text(recordingPrefix) },
                    leadingContent = { Icon(Icons.Filled.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    trailingContent = {
                        androidx.compose.material3.Icon(Icons.Filled.OpenInNew, contentDescription = null)
                    },
                    colors = androidx.compose.ui.graphics.Color.Specified,
                    onClick = { showNamingDialog.value = true }
                )
            }
        }
    }
}

// Sound section
@Composable
fun SoundSection(
    noiseReduction: Boolean,
    enhanceStrength: String,
    prefs: PreferencesManager,
    scope: CoroutineScope,
    showQualityDialog: androidx.compose.runtime.MutableState<Boolean>,
    showAudioSourceDialog: androidx.compose.runtime.MutableState<Boolean>,
    showStorageDialog: androidx.compose.runtime.MutableState<Boolean>,
    showThemeDialog: androidx.compose.runtime.MutableState<Boolean>,
    showNamingDialog: androidx.compose.runtime.MutableState<Boolean>,
    namingDraft: androidx.compose.runtime.MutableState<String>,
    recordingPrefix: String,
    customFolderUri: String,
    storageVolume: String,
    reminder: Boolean,
    darkTheme: String,
    sttLanguage: String,
    sttEngine: String,
    modelProgress: Any,
    whisperProgress: Any,
    selectedCategory: String?,
    onBack: () -> Unit
) {
    androidx.compose.material3.Card(
        shape = ShapeLargeIncreased,
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        androidx.compose.material3.SegmentedList {
            item {
                androidx.compose.material3.ListItem(
                    headlineContent = { Text("Noise reduction") },
                    supportingContent = { Text(if (noiseReduction) "Enabled" else "Disabled") },
                    leadingContent = {
                        androidx.compose.material3.Icon(
                            if (noiseReduction) Icons.Filled.NoiseControl else Icons.Filled.NoiseControlOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingContent = {
                        androidx.compose.material3.CompoundButton.Switch(
                            checked = noiseReduction,
                            onCheckedChange = { noiseReduction = !noiseReduction }
                        )
                    },
                    colors = androidx.compose.ui.graphics.Color.Specified
                )
            }
            item {
                androidx.compose.material3.ListItem(
                    headlineContent = { Text("Enhancement") },
                    supportingContent = { Text(enhanceStrength) },
                    leadingContent = { Icon(Icons.Filled.Boost, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    trailingContent = {
                        androidx.compose.material3.FilledTonalButton(
                            onClick = { showEnhanceStrengthDialog.value = true },
                            shapes = ButtonDefaults.shapes(),
                            contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight),
                            modifier = Modifier.heightIn(min = ButtonDefaults.MediumContainerHeight)
                        ) { Text("Change") }
                    },
                    colors = androidx.compose.ui.graphics.Color.Specified
                )
            }
        }
    }
}

// Appearance section
@Composable
fun AppearanceSection(
    darkTheme: String,
    themeSeed: String,
    dynamicColor: Boolean,
    tipsEnabled: Boolean,
    prefs: PreferencesManager,
    scope: CoroutineScope,
    showThemeDialog: androidx.compose.runtime.MutableState<Boolean>,
    showNamingDialog: androidx.compose.runtime.MutableState<Boolean>,
    namingDraft: androidx.compose.runtime.MutableState<String>,
    recordingPrefix: String,
    customFolderUri: String,
    storageVolume: String,
    reminder: Boolean,
    sttLanguage: String,
    sttEngine: String,
    modelProgress: Any,
    whisperProgress: Any,
    selectedCategory: String?,
    onBack: () -> Unit
) {
    androidx.compose.material3.Card(
        shape = ShapeLargeIncreased,
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        androidx.compose.material3.SegmentedList {
            item {
                androidx.compose.material3.ListItem(
                    headlineContent = { Text("Theme") },
                    supportingContent = {
                        val mode = when (darkTheme) { "light" -> "Light"; "dark" -> "Dark"; else -> "System" }
                        val color = if (dynamicColor) "Dynamic" else /* themeSeedById(themeSeed).label */ "Ember"
                        Text("$color • $mode")
                    },
                    leadingContent = {
                        androidx.compose.material3.Icon(
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
                        androidx.compose.material3.FilledTonalButton(
                            onClick = { showThemeDialog.value = true },
                            shapes = ButtonDefaults.shapes(),
                            contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight),
                            modifier = Modifier.heightIn(min = ButtonDefaults.MediumContainerHeight)
                        ) { Text("Change") }
                    },
                    colors = androidx.compose.ui.graphics.Color.Specified
                )
            }
            item {
                androidx.compose.material3.ListItem(
                    headlineContent = { Text("Tips") },
                    supportingContent = { Text(if (tipsEnabled) "Enabled" else "Disabled") },
                    leadingContent = {
                        androidx.compose.material3.Icon(
                            if (tipsEnabled) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingContent = {
                        androidx.compose.material3.CompoundButton.Switch(
                            checked = tipsEnabled,
                            onCheckedChange = { tipsEnabled = !tipsEnabled }
                        )
                    },
                    colors = androidx.compose.ui.graphics.Color.Specified
                )
            }
        }
    }
}

// Speech-to-text section
@Composable
fun SpeechToTextSection(
    sttEngine: String,
    sttLanguage: String,
    modelProgress: Any,
    whisperProgress: Any,
    prefs: PreferencesManager,
    scope: CoroutineScope,
    showSttLanguageDialog: androidx.compose.runtime.MutableState<Boolean>,
    showVoskModelsDialog: androidx.compose.runtime.MutableState<Boolean>,
    showEngineDialog: androidx.compose.runtime.MutableState<Boolean>,
    selectedCategory: String?,
    onBack: () -> Unit
) {
    androidx.compose.material3.Card(
        shape = ShapeLargeIncreased,
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        androidx.compose.material3.SegmentedList {
            item {
                androidx.compose.material3.ListItem(
                    headlineContent = { Text("Engine") },
                    supportingContent = { Text(sttEngine) },
                    leadingContent = {
                        androidx.compose.material3.Icon(
                            if (sttEngine == "vosk") Icons.Filled.Folder else Icons.Filled.Mic,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingContent = {
                        androidx.compose.material3.Icon(Icons.Filled.ExpandMore, contentDescription = null)
                    },
                    colors = androidx.compose.ui.graphics.Color.Specified,
                    onClick = { showEngineDialog.value = true }
                )
            }
            item {
                androidx.compose.material3.ListItem(
                    headlineContent = { Text("Language") },
                    supportingContent = { Text(sttLanguage) },
                    leadingContent = { Icon(Icons.Filled.Translate, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    trailingContent = {
                        androidx.compose.material3.Icon(Icons.Filled.ExpandMore, contentDescription = null)
                    },
                    colors = androidx.compose.ui.graphics.Color.Specified,
                    onClick = { showSttLanguageDialog.value = true }
                )
            }
            item {
                androidx.compose.material3.ListItem(
                    headlineContent = { Text("Model progress") },
                    supportingContent = { Text("Downloading…") },
                    leadingContent = { Icon(Icons.Filled.Download, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    trailingContent = {
                        if (modelProgress is ModelProgress.Loading) {
                            androidx.compose.material3.CircularProgressIndicator()
                        } else {
                            Text("Ready")
                        }
                    },
                    colors = androidx.compose.ui.graphics.Color.Specified
                )
            }
        }
    }
}

// Updates section
@Composable
fun UpdatesSection(
    updateChannel: String,
    autoUpdateCheck: Boolean,
    checkingUpdate: Boolean,
    updateResult: UpdateCheck?,
    downloadError: String?,
    needsUnknownSources: Boolean,
    installedLabel: String,
    prefs: PreferencesManager,
    scope: CoroutineScope,
    selectedCategory: String?,
    onBack: () -> Unit
) {
    androidx.compose.material3.Card(
        shape = ShapeLargeIncreased,
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        androidx.compose.material3.SegmentedList {
            item {
                androidx.compose.material3.ListItem(
                    headlineContent = { Text("Update channel") },
                    supportingContent = { Text(if (updateChannel == UpdateChecker.CHANNEL_BETA) "Beta" else "Stable") },
                    leadingContent = { Icon(Icons.Filled.Download, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    trailingContent = {
                        androidx.compose.material3.ToggleButton(
                            checked = updateChannel == UpdateChecker.CHANNEL_BETA,
                            onCheckedChange = {
                                scope.launch { prefs.setUpdateChannel(UpdateChecker.CHANNEL_BETA) }
                                updateResult = null
                            }
                        ) { Text("Beta") }
                    },
                    colors = androidx.compose.ui.graphics.Color.Specified
                )
            }
            item {
                androidx.compose.material3.ListItem(
                    headlineContent = { Text("Auto-check updates") },
                    supportingContent = { Text(if (autoUpdateCheck) "Enabled" else "Disabled") },
                    leadingContent = {
                        androidx.compose.material3.Icon(
                            if (autoUpdateCheck) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingContent = {
                        androidx.compose.material3.CompoundButton.Switch(
                            checked = autoUpdateCheck,
                            onCheckedChange = { autoUpdateCheck = !autoUpdateCheck }
                        )
                    },
                    colors = androidx.compose.ui.graphics.Color.Specified
                )
            }
            item {
                androidx.compose.material3.ListItem(
                    headlineContent = { Text("Check for updates") },
                    supportingContent = {},
                    leadingContent = { Icon(Icons.Filled.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    trailingContent = {
                        if (checkingUpdate) {
                            androidx.compose.material3.CircularProgressIndicator()
                        } else {
                            androidx.compose.material3.FilledTonalButton(
                                onClick = {
                                    scope.launch {
                                        updateResult = null
                                        checkingUpdate = true
                                        updateResult = UpdateChecker.check(context.applicationContext, updateChannel)
                                        checkingUpdate = false
                                    }
                                },
                                shapes = ButtonDefaults.shapes(),
                                contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight),
                                modifier = Modifier.heightIn(min = ButtonDefaults.MediumContainerHeight)
                            ) { Text("Check") }
                        }
                    },
                    colors = androidx.compose.ui.graphics.Color.Specified
                )
            }
        }
    }
}

// About section
@Composable
fun AboutSection(
    tipsEnabled: Boolean,
    installedLabel: String,
    selectedCategory: String?,
    onBack: () -> Unit
) {
    androidx.compose.material3.Card(
        shape = ShapeLargeIncreased,
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        androidx.compose.material3.SegmentedList {
            item {
                androidx.compose.material3.ListItem(
                    headlineContent = { Text("Tips") },
                    supportingContent = { Text(if (tipsEnabled) "Enabled" else "Disabled") },
                    leadingContent = {
                        androidx.compose.material3.Icon(
                            if (tipsEnabled) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingContent = {
                        androidx.compose.material3.CompoundButton.Switch(
                            checked = tipsEnabled,
                            onCheckedChange = { tipsEnabled = !tipsEnabled }
                        )
                    },
                    colors = androidx.compose.ui.graphics.Color.Specified
                )
            }
            item {
                androidx.compose.material3.ListItem(
                    headlineContent = { Text("Version") },
                    supportingContent = { Text(installedLabel) },
                    leadingContent = { Icon(Icons.Filled.BugReport, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    trailingContent = {},
                    colors = androidx.compose.ui.graphics.Color.Specified
                )
            }
            item {
                androidx.compose.material3.ListItem(
                    headlineContent = { Text("Credits") },
                    supportingContent = {},
                    leadingContent = { Icon(Icons.Filled.Title, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    trailingContent = {},
                    colors = androidx.compose.ui.graphics.Color.Specified
                )
            }
        }
    }
}