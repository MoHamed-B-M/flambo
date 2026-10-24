package com.pranshulgg.recordmaster.screens

import android.content.Intent
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.audiofx.Visualizer
import android.net.Uri
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.navigation.NavController
import com.pranshulgg.recordmaster.R
import com.pranshulgg.recordmaster.RecordMasterApp
import com.pranshulgg.recordmaster.core.playback.PlaybackController
import com.pranshulgg.recordmaster.ui.components.DropdownMenu
import com.pranshulgg.recordmaster.ui.components.Symbol
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PlayRecordingScreen(
    filePath: String,
    onDone: () -> Unit,
    navController: NavController,
    playback: PlaybackController
) {
    val context = LocalContext.current
    val app = remember(context) { context.applicationContext as RecordMasterApp }
    val file = remember(filePath) { File(filePath) }

    val recording by app.repository.observeByPath(filePath)
        .collectAsState(initial = null)

    val playbackState by playback.state.collectAsState()
    val playbackError by playback.error.collectAsState()
    val isCurrent = playbackState.currentPath == filePath
    val isPlaying = isCurrent && playbackState.isPlaying

    var lastPosition by remember { mutableStateOf(0) }
    val currentPos = if (isCurrent) {
        playbackState.positionMs.toInt().also { lastPosition = it }
    } else {
        lastPosition
    }

    var storedDuration by remember { mutableStateOf(0) }
    val duration = if (isCurrent && playbackState.durationMs > 0) {
        playbackState.durationMs.toInt()
    } else {
        storedDuration
    }

    val sdfDate = SimpleDateFormat("MMM dd", Locale.getDefault())
    val sdfTime = SimpleDateFormat("hh:mm a", Locale.getDefault())

    val date = sdfDate.format(Date(file.lastModified()))
    val time = sdfTime.format(Date(file.lastModified()))

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    val requestedBarCount = 140
    val maxVisibleBars = 120

    val staticAmpsState = remember { mutableStateOf<FloatArray?>(null) }
    var isComputing by remember { mutableStateOf(false) }
    // Restart only when real stored peaks arrive. A Room row with blank peaks
    // (the normal initial emission) must not cancel an in-flight computation.
    val storedPeaksKey = recording?.amplitudePeaks?.takeIf { it.isNotBlank() }

    LaunchedEffect(filePath) {
        app.syncStorage()
    }

    LaunchedEffect(filePath, storedPeaksKey) {
        val stored = recording?.peakList.orEmpty()
        if (stored.isNotEmpty()) {
            staticAmpsState.value = stored.toFloatArray()
            return@LaunchedEffect
        }
        isComputing = true
        try {
            val amps = withContext(Dispatchers.IO) {
                computePeaksFromAudioFile(file.absolutePath, requestedBarCount)
            }
            staticAmpsState.value = amps
        } catch (e: CancellationException) {
            // The screen left composition or the effect key changed. This is
            // not a waveform failure and must not surface a snackbar.
            throw e
        } catch (e: Exception) {
            staticAmpsState.value = null
            launch {
                snackbarHostState.showSnackbar("Waveform compute failed: ${e.message}")
            }
        } finally {
            isComputing = false
        }
    }

    LaunchedEffect(filePath, recording?.durationMs) {
        val fromDb = recording?.durationMs ?: 0L
        if (fromDb > 0) {
            storedDuration = fromDb.toInt()
            return@LaunchedEffect
        }
        try {
            val durationFromFile = withContext(Dispatchers.IO) {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(file.absolutePath)
                val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                retriever.release()
                durStr?.toIntOrNull()
            }
            if (durationFromFile != null) storedDuration = durationFromFile
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    LaunchedEffect(playbackError) {
        if (playbackError != null) {
            snackbarHostState.showSnackbar(playbackError!!)
            playback.clearError()
        }
    }

    Scaffold(
        topBar = { TopAppBar(
            navigationIcon = {
                IconButton(
                    onClick = {onDone()}
                ) {
                    Symbol(R.drawable.arrow_back_24px, color = MaterialTheme.colorScheme.onSurface)
                }
            },
            title = { Text(file.nameWithoutExtension) },
            subtitle = {Text("$date at $time")},
            actions = {
                DropdownMenu(
                    navController,
                    onDelete = {
                        coroutineScope.launch {
                            try {
                                playback.stopIfCurrent(filePath)
                                val rec = recording ?: app.repository.getByPath(filePath)
                                if (rec != null) {
                                    app.repository.deletePermanently(rec.id)
                                    onDone()
                                } else {
                                    val ok = file.delete()
                                    if (ok) {
                                        onDone()
                                    } else {
                                        snackbarHostState.showSnackbar("Delete failed")
                                    }
                                }
                            } catch (e: Exception) {
                                snackbarHostState.showSnackbar("Delete error: ${e.message}")
                            }
                        }
                    },
                    onShare = {
                        try {
                            val uri: Uri = try {
                                FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
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

                        }
                    },
                    file = file,
                    recording = recording,
                    playbackSpeed = playbackState.speed,
                    onSpeedSelected = { speed ->
                        playback.setSpeed(speed)
                        coroutineScope.launch { app.prefs.setPlaybackSpeed(speed) }
                    },
                    onMetadataChanged = {
                        app.syncStorage()
                    },
                    onShowMessage = { message ->
                        coroutineScope.launch { snackbarHostState.showSnackbar(message) }
                    },
                    onCleanReplaced = {
                        // The cleaned copy became the primary file and the old
                        // path is gone; this screen's nav argument is stale.
                        onDone()
                    }
                )
            }
        )
         },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            val displayBarCount = min(staticAmpsState.value?.size ?: requestedBarCount, maxVisibleBars)

            var uiProgress by remember { mutableStateOf(0f) }
            var isUserSeeking by remember { mutableStateOf(false) }


                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(MaterialTheme.shapes.extraLarge)
                        .background(MaterialTheme.colorScheme.surfaceContainer),
                    contentAlignment = Alignment.Center
                ) {
                WaveformBars(
                    audioSessionId = 0,
                    progress = if (!isUserSeeking && duration > 0) currentPos / duration.toFloat() else uiProgress,
                    onSeek = { frac ->
                        if (duration > 0) {
                            val target = (frac * duration).toInt().coerceIn(0, duration)
                            playback.seekTo(target.toLong())
                            lastPosition = target
                        }
                        uiProgress = frac.coerceIn(0f, 1f)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    barCount = displayBarCount,
                    staticAmps = staticAmpsState.value,
                    barWidth = 8.dp,
                    barSpacing = 5.dp,
                    isUserSeeking = isUserSeeking
                )

                if (isComputing) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.matchParentSize(),
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            LoadingIndicator()
                        }
                    }
                }
            }

            val interactionSource = remember { MutableInteractionSource() }
            Spacer(Modifier.height(24.dp))
            Column(
                Modifier.padding(end = 5.dp, start = 5.dp)
            ) {

            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                val fracFromPlayer = if (duration > 0) currentPos / duration.toFloat() else 0f
                LaunchedEffect(fracFromPlayer, isUserSeeking) {
                    if (!isUserSeeking) uiProgress = fracFromPlayer.coerceIn(0f, 1f)
                }

                Slider(
                    value = uiProgress.coerceIn(0f, 1f),
                    onValueChange = { new ->
                        isUserSeeking = true
                        uiProgress = new.coerceIn(0f, 1f)
                    },
                    track = { sliderState ->
                        SliderDefaults.Track(
                            sliderState = sliderState,
                            modifier = Modifier.height(5.dp)
                        )
                    },
                    thumb = {
                        SliderDefaults.Thumb(
                            interactionSource,
                            modifier = Modifier.width(5.dp).height(18.dp)
                        )

                    },
                    onValueChangeFinished = {
                        isUserSeeking = false
                        if (duration > 0) {
                            val target = (uiProgress * duration).toInt().coerceIn(0, duration)
                            playback.seekTo(target.toLong())
                            lastPosition = target
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(26.dp)
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatMs(currentPos), color = MaterialTheme.colorScheme.onSurface)
                Text(formatMs(duration), color = MaterialTheme.colorScheme.onSurface)
            }
            }
            Spacer(modifier = Modifier.height(18.dp))

            ExpressiveTransportRow(
                isPlaying = isPlaying,
                onRewind = { playback.skip(-5_000L) },
                onPlayPause = {
                    if (isCurrent) {
                        playback.toggle()
                    } else {
                        playback.play(filePath, file.nameWithoutExtension)
                    }
                },
                onForward = { playback.skip(10_000L) }
            )

            Spacer(modifier = Modifier.height(24.dp))

        }
    }
}

private enum class TransportAction { REWIND, PLAY_PAUSE, FORWARD }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ExpressiveTransportRow(
    isPlaying: Boolean,
    onRewind: () -> Unit,
    onPlayPause: () -> Unit,
    onForward: () -> Unit,
    expansionWeight: Float = 1.30f,
    compressionWeight: Float = 0.60f
) {
    var lastClicked by remember { mutableStateOf<TransportAction?>(null) }

    fun weightFor(action: TransportAction): Float = when (lastClicked) {
        null -> 1f
        action -> expansionWeight
        else -> compressionWeight
    }

    val rewindWeight by animateFloatAsState(
        targetValue = weightFor(TransportAction.REWIND),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "rewindWeight"
    )
    val playWeight by animateFloatAsState(
        targetValue = weightFor(TransportAction.PLAY_PAUSE),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "playWeight"
    )
    val forwardWeight by animateFloatAsState(
        targetValue = weightFor(TransportAction.FORWARD),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "forwardWeight"
    )

    val playCornerRadius by animateDpAsState(
        targetValue = if (isPlaying) 22.dp else 50.dp,
        animationSpec = MotionScheme.expressive().defaultSpatialSpec(),
        label = "playCornerRadius"
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        TransportButton(
            modifier = Modifier
                .weight(rewindWeight)
                .height(ButtonDefaults.LargeContainerHeight),
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            onClick = {
                lastClicked = TransportAction.REWIND
                onRewind()
            }
        ) {
            Symbol(
                R.drawable.replay_5_24px,
                desc = "Rewind 5 seconds",
                size = 30.dp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        TransportButton(
            modifier = Modifier
                .weight(playWeight)
                .height(ButtonDefaults.LargeContainerHeight),
            shape = RoundedCornerShape(playCornerRadius),
            containerColor = if (isPlaying) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.primaryContainer
            },
            onClick = {
                lastClicked = TransportAction.PLAY_PAUSE
                onPlayPause()
            }
        ) {
            if (isPlaying) {
                Symbol(
                    R.drawable.pause_24px,
                    desc = "Pause",
                    size = 32.dp,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            } else {
                Symbol(
                    R.drawable.play_arrow_24px,
                    desc = "Play",
                    size = 32.dp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        TransportButton(
            modifier = Modifier
                .weight(forwardWeight)
                .height(ButtonDefaults.LargeContainerHeight),
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            onClick = {
                lastClicked = TransportAction.FORWARD
                onForward()
            }
        ) {
            Symbol(
                R.drawable.forward_10_24px,
                desc = "Forward 10 seconds",
                size = 30.dp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun TransportButton(
    modifier: Modifier,
    shape: Shape,
    containerColor: Color,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(containerColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

private fun formatMs(ms: Int): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(ms.toLong())
    val seconds = TimeUnit.MILLISECONDS.toSeconds(ms.toLong()) - TimeUnit.MINUTES.toSeconds(minutes)
    return String.format("%02d:%02d", minutes, seconds)
}

suspend fun computePeaksFromAudioFile(path: String, barCount: Int): FloatArray = withContext(Dispatchers.IO) {
    val extractor = MediaExtractor()
    extractor.setDataSource(path)
    var audioTrackIndex = -1
    var format: MediaFormat? = null
    for (i in 0 until extractor.trackCount) {
        val f = extractor.getTrackFormat(i)
        val mime = f.getString(MediaFormat.KEY_MIME) ?: ""
        if (mime.startsWith("audio/")) {
            audioTrackIndex = i
            format = f
            break
        }
    }
    if (audioTrackIndex < 0 || format == null) {
        extractor.release()
        throw IllegalArgumentException("No audio track found")
    }

    extractor.selectTrack(audioTrackIndex)
    val mime = format.getString(MediaFormat.KEY_MIME)!!

    val decoder = MediaCodec.createDecoderByType(mime)
    decoder.configure(format, null, null, 0)
    decoder.start()

    val sampleList = ArrayList<Float>()

    val inputBufs = decoder.inputBuffers
    val outputBufs = decoder.outputBuffers
    val info = MediaCodec.BufferInfo()

    var sawInputEOS = false
    var sawOutputEOS = false

    while (!sawOutputEOS) {
        if (!sawInputEOS) {
            val inIndex = decoder.dequeueInputBuffer(10_000)
            if (inIndex >= 0) {
                val inputBuf = inputBufs[inIndex]
                val sampleSize = extractor.readSampleData(inputBuf, 0)
                if (sampleSize < 0) {
                    decoder.queueInputBuffer(inIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    sawInputEOS = true
                } else {
                    val presentationTimeUs = extractor.sampleTime
                    decoder.queueInputBuffer(inIndex, 0, sampleSize, presentationTimeUs, 0)
                    extractor.advance()
                }
            }
        }

        val outIndex = decoder.dequeueOutputBuffer(info, 10_000)
        when {
            outIndex >= 0 -> {
                val outBuf = outputBufs[outIndex]
                val chunk = ByteArray(info.size)
                outBuf.get(chunk)
                outBuf.clear()
                var i = 0
                while (i + 1 < chunk.size) {
                    val low = chunk[i].toInt() and 0xFF
                    val high = chunk[i + 1].toInt()
                    val sample = (high shl 8) or low
                    val signed = if (sample >= 32768) sample - 65536 else sample
                    val normalized = signed.toFloat() / Short.MAX_VALUE.toFloat()
                    sampleList.add(normalized)
                    i += 2
                }

                decoder.releaseOutputBuffer(outIndex, false)

                if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    sawOutputEOS = true
                }
            }
            outIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> {
            }
            outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
            }
        }
    }

    decoder.stop()
    decoder.release()
    extractor.release()

    if (sampleList.isEmpty()) {
        return@withContext FloatArray(barCount) { 0.02f + (it % 7) * 0.0045f }
    }

    val totalSamples = sampleList.size
    val samplesPerBar = max(1, totalSamples / barCount)
    val peaks = FloatArray(barCount)
    var sampleIndex = 0
    for (b in 0 until barCount) {
        var sumSq = 0.0
        var count = 0
        var i = 0
        while (i < samplesPerBar && sampleIndex < totalSamples) {
            val s = sampleList[sampleIndex]
            sumSq += (s * s)
            count++
            sampleIndex++
            i++
        }
        if (count > 0) {
            val rms = sqrt(sumSq / count)
            peaks[b] = rms.toFloat()
        } else {
            peaks[b] = 0f
        }
    }

    val maxVal = peaks.maxOrNull()?.coerceAtLeast(1e-6f) ?: 1f
    for (i in peaks.indices) peaks[i] = (peaks[i] / maxVal).coerceIn(0f, 1f)

    return@withContext peaks
}
@Composable
private fun WaveformBars(
    audioSessionId: Int,
    progress: Float,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    barCount: Int = 40,
    staticAmps: FloatArray? = null,
    barWidth: Dp = 8.dp,
    barSpacing: Dp = 4.dp,
    isUserSeeking: Boolean = false
) {
    val waveformRaw = remember { mutableStateOf<ByteArray?>(null) }

    DisposableEffect(audioSessionId, staticAmps) {
        var visualizer: Visualizer? = null
        if (staticAmps == null && audioSessionId > 0) {
            try {
                visualizer = Visualizer(audioSessionId).apply {
                    try {
                        val range = Visualizer.getCaptureSizeRange()
                        setCaptureSize(range[1])
                    } catch (_: Exception) {}
                    var lastCaptureNs = 0L
                    setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                        override fun onWaveFormDataCapture(v: Visualizer?, waveform: ByteArray?, samplingRate: Int) {
                            if (waveform == null) return
                            val now = System.nanoTime()
                            val elapsedNs = now - lastCaptureNs
                            if (elapsedNs < 28_000_000L) return
                            lastCaptureNs = now
                            waveformRaw.value = waveform.copyOf()
                        }
                        override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, samplingRate: Int) {}
                    }, Visualizer.getMaxCaptureRate() / 4, true, false)
                    enabled = true
                }
            } catch (e: Exception) {
                visualizer?.release()
                visualizer = null
                waveformRaw.value = null
            }
        }

        onDispose {
            try { visualizer?.enabled = false } catch (_: Exception) {}
            try { visualizer?.release() } catch (_: Exception) {}
            waveformRaw.value = null
        }
    }

    val targetAmps = remember { mutableStateListOf<Float>().apply { repeat(barCount) { add(0f) } } }
    val displayAmps = remember { mutableStateListOf<Float>().apply { repeat(barCount) { add(0f) } } }

    LaunchedEffect(staticAmps, barCount) {
        if (staticAmps != null && staticAmps.isNotEmpty()) {
            if (staticAmps.size == barCount) {
                for (i in 0 until barCount) targetAmps[i] = staticAmps[i].coerceIn(0f, 1f)
            } else {
                for (i in 0 until barCount) {
                    val start = (i * staticAmps.size) / barCount
                    val end = ((i + 1) * staticAmps.size) / barCount
                    var sum = 0f; var c = 0
                    for (j in start until end.coerceAtLeast(start + 1).coerceAtMost(staticAmps.size)) {
                        sum += staticAmps[j]; c++
                    }
                    targetAmps[i] = if (c > 0) (sum / c) else 0f
                }
            }
        }
    }

    val waveformHistory = remember { ArrayDeque<FloatArray>() }
    LaunchedEffect(waveformRaw.value) {
        val raw = waveformRaw.value
        if (raw == null || raw.isEmpty()) {
            for (i in 0 until barCount) targetAmps[i] = (0.02f + (i % 7) * 0.0045f)
            return@LaunchedEffect
        }

        val samples = raw.size
        val blockSize = max(1, samples / barCount)
        val curTargets = FloatArray(barCount)

        var idx = 0
        for (b in 0 until barCount) {
            var sumSq = 0f
            var count = 0
            var j = 0
            while (j < blockSize && idx < samples) {
                val v = (raw[idx].toInt() and 0xFF) - 128
                sumSq += (v * v).toFloat()
                count++
                idx++
                j++
            }
            val rms = if (count > 0) kotlin.math.sqrt((sumSq / count).toDouble()).toFloat() / 128f else 0f
            val amp = kotlin.math.log10(1f + 9f * rms).coerceIn(0f, 1f)
            curTargets[b] = amp
        }

        waveformHistory.addLast(curTargets)
        if (waveformHistory.size > 8) waveformHistory.removeFirst()

        val averaged = FloatArray(barCount) { 0f }
        waveformHistory.forEach { arr ->
            for (i in 0 until barCount) averaged[i] += arr[i]
        }
        val inv = 1f / waveformHistory.size
        for (i in 0 until barCount) targetAmps[i] = (averaged[i] * inv).coerceIn(0f, 1f)

        if (barCount >= 3) {
            val tmp = FloatArray(barCount) { 0f }
            for (i in 0 until barCount) {
                val prev = if (i - 1 >= 0) targetAmps[i - 1] else targetAmps[i]
                val next = if (i + 1 < barCount) targetAmps[i + 1] else targetAmps[i]
                tmp[i] = (prev + targetAmps[i] + next) / 3f
            }
            for (i in 0 until barCount) targetAmps[i] = tmp[i]
        }
    }

    LaunchedEffect(barCount) {
        val attack = 0.60f
        val decay = 0.12f
        val frameDelay = 33L

        while (true) {
            for (i in 0 until barCount) {
                val cur = displayAmps.getOrNull(i) ?: 0f
                val tgt = targetAmps.getOrNull(i) ?: 0f
                val coeff = if (tgt > cur) attack else decay
                val next = cur + (tgt - cur) * coeff
                displayAmps[i] = if (next < 0.0005f) 0f else next
            }
            delay(frameDelay)
        }
    }

    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val color = MaterialTheme.colorScheme

    var viewportWidthPx by remember { mutableStateOf(0) }

    val barWidthPx = with(density) { barWidth.toPx() }
    val spacingPx = with(density) { barSpacing.toPx() }
    val totalWidthPx = max(1f, (barWidthPx + spacingPx) * barCount - spacingPx)


    val padStartPx = viewportWidthPx / 2f
    val paddedTotalWidthPx = totalWidthPx + viewportWidthPx

    val waveformPointerModifier = Modifier
        .pointerInput(Unit) {
            detectTapGestures { tap ->
                val contentX = (scrollState.value + tap.x - padStartPx).coerceIn(0f, totalWidthPx)
                val frac = (contentX / totalWidthPx).coerceIn(0f, 1f)
                onSeek(frac)
                if (viewportWidthPx > 0) {
                    val targetScroll = (frac * totalWidthPx).coerceIn(0f, totalWidthPx)
                    coroutineScope.launch { scrollState.animateScrollTo(targetScroll.roundToInt()) }
                }
            }
        }
        .pointerInput(Unit) {
            detectDragGestures(
                onDrag = { change, dragAmount ->
                    change.consume()
                    coroutineScope.launch {
                        try {
                            scrollState.scrollBy(-dragAmount.x)
                        } catch (_: Exception) {}
                        val centerContentX =
                            (scrollState.value + (viewportWidthPx / 2f) - padStartPx).coerceIn(0f, totalWidthPx)
                        val frac = (centerContentX / totalWidthPx).coerceIn(0f, 1f)
                        onSeek(frac)
                    }
                },
                onDragEnd = {
                    val centerContentX =
                        (scrollState.value + (viewportWidthPx / 2f) - padStartPx).coerceIn(0f, totalWidthPx)
                    val frac = (centerContentX / totalWidthPx).coerceIn(0f, 1f)
                    onSeek(frac)
                }
            )
        }

    Box(
        modifier = modifier
            .fillMaxHeight(0.7f)
            .padding(vertical = 8.dp, horizontal = 6.dp)
            .onSizeChanged { viewportWidthPx = it.width }
            .horizontalScroll(scrollState),
        contentAlignment = Alignment.CenterStart
    ) {
        LaunchedEffect(progress, totalWidthPx, viewportWidthPx, isUserSeeking) {
            if (viewportWidthPx <= 0) return@LaunchedEffect
            if (isUserSeeking) return@LaunchedEffect
            val clamped = progress.coerceIn(0f, 1f)
            val targetScroll = (clamped * totalWidthPx).coerceIn(0f, totalWidthPx)
            if (!scrollState.isScrollInProgress) {
                try {
                    scrollState.animateScrollTo(targetScroll.roundToInt())
                } catch (_: Exception) {}
            }
        }

        Canvas(
            modifier = Modifier
                .width(with(density) { (paddedTotalWidthPx / density.density).dp })
                .fillMaxHeight()
                .then(waveformPointerModifier)
        ) {
            val contentW = size.width
            val contentH = size.height

            val inactiveColor = color.outlineVariant
            val activeColor = color.primary

            val viewportCenterGlobalX = (scrollState.value + (viewportWidthPx / 2f)).coerceIn(0f, contentW)

            var x = padStartPx
            for (i in 0 until barCount) {
                val amp = displayAmps.getOrNull(i) ?: 0f
                val barH = max(contentH * 0.06f, contentH * amp * 0.92f)
                val top = (contentH - barH) / 2f
                val left = x
                val centerOfBar = left + barWidthPx / 2f

                val rectColor = if (centerOfBar <= viewportCenterGlobalX) activeColor else inactiveColor

                drawRoundRect(
                    color = rectColor,
                    topLeft = Offset(left, top),
                    size = androidx.compose.ui.geometry.Size(barWidthPx, barH),
                    cornerRadius = CornerRadius(barWidthPx / 2f, barWidthPx / 2f)
                )
                x += barWidthPx + spacingPx
            }

            val rectWidth = 10f
            val rectLeft = viewportCenterGlobalX - rectWidth / 2f

            drawRoundRect(
                color = color.secondary,
                topLeft = Offset(rectLeft, 0f),
                size = Size(rectWidth, contentH),
                cornerRadius = CornerRadius(rectWidth / 2f, rectWidth / 2f)
            )
        }

    }
}

