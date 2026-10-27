package com.pranshulgg.recordmaster.core.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.pranshulgg.recordmaster.core.data.AudioFileStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

data class PlaybackState(
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val speed: Float = 1f,
    val isPrepared: Boolean = false,
    val currentPath: String? = null
)

/**
 * Single playback pipeline for 2.0. Home rows and the playback screen will
 * observe this state in Phase 4, so only one track can play at a time and
 * playback survives backgrounding through [PlaybackService].
 */
class PlaybackController(private val context: Context) {

    private var player: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var pendingPath: String? = null
    private var pendingTitle: String? = null
    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state

    private var ticker: Job? = null
    private var currentPath: String? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun clearError() {
        _error.value = null
    }

    /**
     * Connects to [PlaybackService]. Safe to call again while a connection is
     * live or pending.
     */
    fun connect(onReady: () -> Unit = {}) {
        if (controllerFuture != null) return
        val sessionToken = SessionToken(
            context,
            ComponentName(context, PlaybackService::class.java)
        )
        controllerFuture = MediaController.Builder(context, sessionToken)
            .buildAsync()
            .also { future ->
                future.addListener({
                    val controller = runCatching { future.get() }.getOrNull()
                    if (controller == null) {
                        controllerFuture = null
                        return@addListener
                    }
                    player = controller
                    controller.addListener(controllerListener)
                    controller.playbackParameters = controller.playbackParameters
                        .withSpeed(_state.value.speed)
                    pendingPath?.let { path ->
                        pendingPath = null
                        val title = pendingTitle
                        pendingTitle = null
                        play(path, title)
                    }
                    onReady()
                }, ContextCompat.getMainExecutor(context))
            }
    }

    private val controllerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.value = _state.value.copy(
                isPlaying = isPlaying,
                isPrepared = true,
                currentPath = currentPath
            )
            if (isPlaying) startTicker() else ticker?.cancel()
        }

        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_ENDED) {
                _state.value = _state.value.copy(
                    isPlaying = false,
                    positionMs = _state.value.durationMs,
                    currentPath = currentPath
                )
                ticker?.cancel()
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            _error.value = "Couldn't play this recording — the audio file may be missing or damaged."
            ticker?.cancel()
            _state.value = _state.value.copy(isPlaying = false)
        }
    }

    /**
     * [title] is shown in the media notification. When null it is derived from
     * the file name (works for both file paths and content URIs).
     */
    fun play(path: String, title: String? = null) {
        if (path.isBlank()) return
        if (!AudioFileStore.exists(context, path)) {
            _error.value = "Audio file not found — it may have been deleted outside the app."
            return
        }
        clearError()

        if (currentPath == path && player != null) {
            val controller = player!!
            val ended = controller.playbackState == Player.STATE_ENDED
            val atEnd = _state.value.durationMs > 0 &&
                _state.value.positionMs >= _state.value.durationMs - 300
            if (ended || atEnd) {
                controller.seekTo(0)
                controller.playWhenReady = true
                controller.play()
                _state.value = _state.value.copy(
                    positionMs = 0,
                    isPlaying = true,
                    isPrepared = true
                )
                startTicker()
                return
            }
            controller.play()
            return
        }

        val controller = player
        if (controller == null) {
            pendingPath = path
            pendingTitle = title
            connect()
            return
        }

        dropTrack()
        currentPath = path
        _state.value = PlaybackState(speed = _state.value.speed, currentPath = path)
        val displayTitle = title?.takeIf { it.isNotBlank() }
            ?: AudioFileStore.displayName(context, path)
                .substringBeforeLast('.')
                .ifBlank { "Recording" }
        val uri = if (AudioFileStore.isContentUri(path)) {
            Uri.parse(path)
        } else {
            Uri.fromFile(File(path))
        }
        controller.setMediaItem(
            MediaItem.Builder()
                .setUri(uri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(displayTitle)
                        .setArtist("Flambo")
                        .build()
                )
                .build()
        )
        controller.prepare()
        controller.playWhenReady = true
        controller.playbackParameters = controller.playbackParameters
            .withSpeed(_state.value.speed)
        startTicker()
    }

    fun pause() {
        player?.pause()
    }

    fun resume() {
        val controller = player ?: return
        if (controller.playbackState == Player.STATE_ENDED) {
            controller.seekTo(0)
            _state.value = _state.value.copy(positionMs = 0)
        }
        controller.play()
    }

    fun toggle() {
        if (_state.value.isPlaying) {
            pause()
        } else {
            val controller = player
            if (controller != null &&
                (controller.playbackState == Player.STATE_ENDED ||
                    (_state.value.durationMs > 0 &&
                        _state.value.positionMs >= _state.value.durationMs - 300))
            ) {
                controller.seekTo(0)
                _state.value = _state.value.copy(positionMs = 0)
            }
            resume()
        }
    }

    fun seekTo(ms: Long) {
        // Duration is 0 until the player is prepared; do not clamp a seek to
        // the start in that window (the old +10 s button did exactly that).
        val max = _state.value.durationMs.takeIf { it > 0 } ?: Long.MAX_VALUE
        val target = ms.coerceIn(0L, max)
        player?.seekTo(target)
        _state.value = _state.value.copy(positionMs = target)
    }

    fun skip(deltaMs: Long) {
        seekTo(_state.value.positionMs + deltaMs)
    }

    fun setSpeed(speed: Float) {
        _state.value = _state.value.copy(speed = speed)
        val controller = player ?: return
        controller.playbackParameters = controller.playbackParameters.withSpeed(speed)
    }

    fun stop() {
        ticker?.cancel()
        player?.stop()
        _state.value = PlaybackState(speed = _state.value.speed, currentPath = null)
        currentPath = null
    }

    /** Stops playback when the loaded track is one of [paths]. */
    fun stopIfCurrent(vararg paths: String) {
        if (paths.any { it.isNotBlank() && it == currentPath }) stop()
    }

    fun release() {
        ticker?.cancel()
        dropTrack()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        player = null
        pendingPath = null
        pendingTitle = null
    }

    /** Drops the current track but keeps the session connection alive. */
    private fun dropTrack() {
        ticker?.cancel()
        player?.stop()
        currentPath = null
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            var ticks = 0
            while (isActive) {
                val controller = player
                if (controller != null) {
                    val next = _state.value.copy(
                        positionMs = controller.currentPosition.coerceAtLeast(0),
                        durationMs = controller.duration.coerceAtLeast(0)
                            .takeIf { it != C.TIME_UNSET }
                            ?: _state.value.durationMs,
                        isPlaying = controller.isPlaying,
                        currentPath = currentPath
                    )
                    if (next != _state.value) _state.value = next

                    // Guard an externally deleted file every ~2 seconds.
                    ticks++
                    if (ticks % 16 == 0) {
                        val path = currentPath
                        if (path != null && !AudioFileStore.exists(context, path)) {
                            _error.value = "Audio file was deleted outside the app — playback stopped."
                            dropTrack()
                            _state.value = PlaybackState(
                                speed = _state.value.speed,
                                currentPath = null
                            )
                            return@launch
                        }
                    }
                }
                delay(120)
            }
        }
    }
}
