package com.pranshulgg.recordmaster.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.pranshulgg.recordmaster.R
import com.pranshulgg.recordmaster.RecordMasterApp
import com.pranshulgg.recordmaster.ui.components.ConfirmDialog
import com.pranshulgg.recordmaster.ui.components.Symbol
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


var mainTitle = "Record"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RecordingScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val app = remember(context) { context.applicationContext as RecordMasterApp }
    val controller = app.recorder
    val recorderState by controller.state.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    val isRecording = recorderState.isRecording
    val isPaused = recorderState.isPaused
    val elapsedSeconds = recorderState.elapsedMs / 1000L

    var screenTitle by remember { mutableStateOf(mainTitle) }
    var isStarting by remember { mutableStateOf(false) }
    var showConfirmDeleteDialog by remember { mutableStateOf(false) }

    val maxSamples = 40
    val amplitudes = remember(recorderState.peaks, recorderState.isRecording) {
        if (!recorderState.isRecording) {
            emptyList()
        } else {
            val recent = recorderState.peaks.takeLast(maxSamples)
            List(maxSamples - recent.size) { 0f } + recent
        }
    }

    fun startNow() {
        if (isStarting) return
        isStarting = true
        coroutineScope.launch {
            try {
                val started = controller.startHeadless()
                if (started) {
                    val title = runCatching { controller.peekNextTitle() }
                        .getOrDefault(mainTitle)
                    screenTitle = title
                    mainTitle = title
                } else {
                    snackbarHostState.showSnackbar("Could not start recorder")
                }
            } finally {
                isStarting = false
            }
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { startNow() }
    )

    fun startAfterNotificationPermission() {
        val needsNotificationPermission =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
        if (needsNotificationPermission) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startNow()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            if (granted) {
                startAfterNotificationPermission()
            } else {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Mic permission is required to record")
                }
            }
        }
    )

    // Re-entering the screen while a background recording is active keeps the
    // same title and live state the user left behind.
    LaunchedEffect(recorderState.isRecording) {
        if (recorderState.isRecording) {
            val title = runCatching { controller.peekNextTitle() }
                .getOrDefault(screenTitle)
            screenTitle = title
            mainTitle = title
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {Text(screenTitle)},
                subtitle = {Text(SimpleDateFormat("MMM dd 'at' h:mm a", Locale.getDefault()).format(Date()))},
                navigationIcon = {
                    IconButton(
                        onClick = {onDone()}
                    ) {
                        Symbol(R.drawable.arrow_back_24px, color = MaterialTheme.colorScheme.onSurface)
                    }
                },
                actions = {
                    IconButton(shapes = IconButtonDefaults.shapes(), onClick = {
                        showConfirmDeleteDialog = true
                    }) {
                        Symbol(R.drawable.delete_forever_24px)
                    }

                    if (showConfirmDeleteDialog) {
                        ConfirmDialog(
                            title = "Delete recording",
                            message = "This recording will be permanently deleted. This cannot be undone",
                            confirmText = "Delete",
                            cancelText = "Cancel",
                            onConfirm = {
                                controller.cancel()
                                showConfirmDeleteDialog = false
                                onDone()
                            },
                            onDismiss = { showConfirmDeleteDialog = false }
                        )
                    }
                }
            )
        }


    ) { padding ->
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {



                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(fraction = 0.7f)
                        .clip(MaterialTheme.shapes.extraLarge)
                        .background(MaterialTheme.colorScheme.surfaceContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Waveform(amplitudes = amplitudes)
                }


                Spacer(modifier = Modifier.height(15.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(
                                if (isRecording && !isPaused) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = formatElapsed(elapsedSeconds), fontSize = 40.sp, color = MaterialTheme.colorScheme.secondary)
                }
                Spacer(modifier = Modifier.height(24.dp))


                Row(
                    modifier = Modifier.fillMaxWidth().animateContentSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = if (!isRecording) Arrangement.Center else Arrangement.spacedBy(5.dp)
                ) {


                    val buttonLabel = when {
                        !isRecording -> "Record"
                        isRecording && !isPaused -> "Pause"
                        else -> "Resume"
                    }


                    Box(
                       modifier = if (!isRecording) Modifier.fillMaxWidth() else Modifier.weight(1f),
                        contentAlignment = Alignment.Center

                    ) {
                                ToggleButton(
                                    checked = isRecording && !isPaused,
                                    colors = ToggleButtonDefaults.toggleButtonColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        checkedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                    ),
                                    onCheckedChange = {
                                        when {
                                            !isRecording -> {
                                                val permState = ContextCompat.checkSelfPermission(
                                                    context,
                                                    Manifest.permission.RECORD_AUDIO
                                                )
                                                if (permState == PackageManager.PERMISSION_GRANTED) {
                                                    startAfterNotificationPermission()
                                                } else {
                                                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                                }
                                            }

                                            isRecording && !isPaused -> {
                                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                                    controller.pause()
                                                    coroutineScope.launch {
                                                        snackbarHostState.showSnackbar(
                                                            if (controller.state.value.isPaused) "Paused"
                                                            else "Pause failed on this device"
                                                        )
                                                    }
                                                } else {
                                                    coroutineScope.launch {
                                                        snackbarHostState.showSnackbar(
                                                            "Pause not supported on this device"
                                                        )
                                                    }
                                                }
                                            }

                                            isRecording && isPaused -> {
                                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                                    controller.resume()
                                                    coroutineScope.launch {
                                                        snackbarHostState.showSnackbar(
                                                            if (!controller.state.value.isPaused) "Resumed"
                                                            else "Resume failed on this device"
                                                        )
                                                    }
                                                } else {
                                                    coroutineScope.launch {
                                                        snackbarHostState.showSnackbar(
                                                            "Resume not supported on this device"
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    },
                                    modifier = Modifier.heightIn(ButtonDefaults.LargeContainerHeight).fillMaxWidth(if (!isRecording) 0.6f else 1f),
                                    contentPadding = if (!isRecording)  ButtonDefaults.contentPaddingFor(ButtonDefaults.LargeContainerHeight) else PaddingValues(0.dp),

                                    shapes = ToggleButtonDefaults.shapes(),
                                ) {
                                    val iconRes = when {
                                        !isRecording -> R.drawable.fiber_manual_record_24px
                                        isPaused -> R.drawable.play_arrow_24px
                                        else -> R.drawable.pause_24px
                                    }

                                    val contentColor = if(isRecording && !isPaused) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimaryContainer

                                    Symbol(iconRes, size = 28.dp, color = contentColor,)
                                    Spacer(Modifier.width(5.dp))
                                    Text(buttonLabel, color = contentColor, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                                }
                    }

                    val stopVisible = isRecording

                    val stopAlpha by animateFloatAsState(if (stopVisible) 1f else 0f)
                    val stopWeight by animateFloatAsState(if (stopVisible) 1f else 0.001f)

                    Box(
                        modifier = Modifier
                            .weight(stopWeight)
                            .alpha(stopAlpha)
                    ) {
                                Button(

                                    onClick = {
                                        controller.stop { recording ->
                                            coroutineScope.launch {
                                                screenTitle = recording.title
                                                mainTitle = recording.title
                                                snackbarHostState.showSnackbar("Saved: ${recording.filePath}")
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().heightIn(ButtonDefaults.LargeContainerHeight),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer
                                    )

                                ) {
                                    Symbol(R.drawable.stop_24px, size = 28.dp, color = MaterialTheme.colorScheme.onErrorContainer)
                                    Spacer(Modifier.width(5.dp))
                                    Text("Stop", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                                }
                    }

                }

            }
                    Spacer(modifier = Modifier.height(16.dp))
                }
}
}

@Composable
private fun Waveform(amplitudes: List<Float>) {
    val display = remember { mutableStateListOf<Float>() }
    val colorBar = MaterialTheme.colorScheme.primary

    LaunchedEffect(amplitudes.size) {
        if (display.size < amplitudes.size) {
            repeat(amplitudes.size - display.size) { display.add(0f) }
        } else if (display.size > amplitudes.size) {
            for (i in display.size - 1 downTo amplitudes.size) display.removeAt(i)
        }
    }

    LaunchedEffect(amplitudes) {
        val frameDelay = 16L // 60 fps
        val followFactor = 0.15f

        while (isActive) {
            val target = amplitudes
            if (display.size < target.size) {
                repeat(target.size - display.size) { display.add(0f) }
            }

            for (i in target.indices) {
                val curr = display.getOrNull(i) ?: 0f
                val next = curr * (1 - followFactor) + target[i] * followFactor
                if (i < display.size) display[i] = next else display.add(next)
            }

            delay(frameDelay)
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val count = display.size.coerceAtLeast(1)
        val barWidth = (w / count).coerceAtLeast(2f)
        val gap = (barWidth * 0.2f).coerceAtMost(6f)
        val actualBar = barWidth - gap

        for (i in 0 until count) {
            val amp = display.getOrNull(i) ?: 0f
            // Peaks are already normalized to 0..1, so no extra gain is needed.
            // Keep some headroom so loud input does not saturate every bar.
            val scale = 0.72f
            val barHeight = (amp * scale).coerceIn(0f, 1f) * h

            val left = i * barWidth + gap / 2f
            val top = (h - barHeight) / 2f

            drawRoundRect(
                color = colorBar,
                topLeft = Offset(left, top),
                size = Size(actualBar, barHeight),
                cornerRadius = CornerRadius(x = actualBar / 2f, y = actualBar / 2f)
            )
        }
    }
}

private fun formatElapsed(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) String.format("%02d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
}

