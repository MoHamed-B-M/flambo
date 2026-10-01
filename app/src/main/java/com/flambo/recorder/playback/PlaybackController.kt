package com.flambo.recorder.playback

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.flambo.recorder.data.AudioFileStore
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PlaybackState(
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val speed: Float = 1f,
    val isPrepared: Boolean = false,
    val currentPath: String? = null
)

class PlaybackController(private val context: Context) {

    // MediaController implements Player, so everything below (play, pause,
    // seekTo, ticker reads) works unchanged — only the session behind it
    // moved into PlaybackService for background survival.
    private var player: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    /** play() requested before the controller connected; flushed on ready. */
    private var pendingPath: String? = null
    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state

    private var ticker: Job? = null
    private var currentPath: String? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun clearError() { _error.value = null }

    /**
     * Connects to PlaybackService. Call once where the controller is created;
     * safe to call again (no-op while a connection is live or pending).
     */
    fun connect(onReady: () -> Unit = {}) {
        if (controllerFuture != null) return
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync().also { future ->
            future.addListener({
                val controller = runCatching { future.get() }.getOrNull()
                if (controller == null) {
                    // Session didn't come up: allow the next tap to retry.
                    controllerFuture = null
                    return@addListener
                }
                player = controller
                controller.addListener(controllerListener)
                controller.playbackParameters = controller.playbackParameters.withSpeed(_state.value.speed)
                pendingPath?.let { path ->
                    pendingPath = null
                    play(path)
                }
                onReady()
            }, ContextCompat.getMainExecutor(context))
        }
    }

    private val controllerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.value = _state.value.copy(isPlaying = isPlaying, isPrepared = true, currentPath = currentPath)
            if (isPlaying) startTicker() else ticker?.cancel()
        }

        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_ENDED) {
                _state.value = _state.value.copy(isPlaying = false, positionMs = _state.value.durationMs, currentPath = currentPath)
                ticker?.cancel()
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            _error.value = "Couldn't play this recording — the audio file may be missing or damaged."
            ticker?.cancel()
            _state.value = _state.value.copy(isPlaying = false)
        }
    }

    fun play(path: String) {
        if (path.isBlank()) return
        if (!AudioFileStore.exists(context, path)) {
            _error.value = "Audio file not found — it may have been deleted outside the app."
            return
        }
        clearError()

        if (currentPath == path && player != null) {
            val p = player!!
            val ended = p.playbackState == Player.STATE_ENDED
            val atEnd = _state.value.durationMs > 0 && _state.value.positionMs >= _state.value.durationMs - 300
            if (ended || atEnd) {
                p.seekTo(0)
                p.playWhenReady = true
                p.play()
                _state.value = _state.value.copy(positionMs = 0, isPlaying = true, isPrepared = true)
                startTicker()
                return
            }
            p.play()
            return
        }
        val controller = player
        if (controller == null) {
            // Tapped before the session connected: stash and flush on ready.
            pendingPath = path
            connect()
            return
        }
        dropTrack()
        currentPath = path
        _state.value = PlaybackState(speed = _state.value.speed, currentPath = path)
        controller.setMediaItem(MediaItem.fromUri(path))
        controller.prepare()
        controller.playWhenReady = true
        controller.playbackParameters = controller.playbackParameters.withSpeed(_state.value.speed)
        startTicker()
    }

    fun pause() { player?.pause() }
    fun resume() {
        val p = player ?: return
        if (p.playbackState == Player.STATE_ENDED) {
            p.seekTo(0)
            _state.value = _state.value.copy(positionMs = 0)
        }
        p.play()
    }
    fun toggle() {
        if (_state.value.isPlaying) pause()
        else {

            val p = player
            if (p != null && (p.playbackState == Player.STATE_ENDED || (_state.value.durationMs > 0 && _state.value.positionMs >= _state.value.durationMs - 300))) {
                p.seekTo(0)
                _state.value = _state.value.copy(positionMs = 0)
            }
            resume()
        }
    }

    fun seekTo(ms: Long) {

        player?.seekTo(ms.coerceIn(0, _state.value.durationMs))
        _state.value = _state.value.copy(positionMs = ms.coerceIn(0, _state.value.durationMs))
    }

    fun skip(deltaMs: Long) { seekTo(_state.value.positionMs + deltaMs) }

    fun setSpeed(speed: Float) {
        _state.value = _state.value.copy(speed = speed)
        player?.playbackParameters = player?.playbackParameters?.withSpeed(speed) ?: return
    }

    fun stop() {
        ticker?.cancel()
        player?.stop()
        _state.value = PlaybackState(speed = _state.value.speed, currentPath = null)
        currentPath = null
    }

    /**
     * Stops playback if the loaded track is one of [paths] (e.g. it was just
     * deleted or trashed). Blank paths are ignored. Safe to call unconditionally.
     */
    fun stopIfCurrent(vararg paths: String) {
        if (paths.any { it.isNotBlank() && it == currentPath }) stop()
    }

    fun release() {
        dropTrack()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        player = null
        pendingPath = null
    }

    /** Drops the current track but keeps the session connection alive. */
    private fun dropTrack() {
        ticker?.cancel()
        player?.stop()
        currentPath = null
    }

    fun isPlayingPath(path: String): Boolean = currentPath == path && _state.value.isPlaying
    fun isCurrentPath(path: String): Boolean = currentPath == path

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            var ticks = 0
            while (isActive) {
                val p = player
                if (p != null) {
                    val next = _state.value.copy(
                        positionMs = p.currentPosition.coerceAtLeast(0),
                        durationMs = p.duration.coerceAtLeast(0).takeIf { it != androidx.media3.common.C.TIME_UNSET } ?: _state.value.durationMs,
                        isPlaying = p.isPlaying,
                        currentPath = currentPath
                    )
                    // Emit only on change: a paused player otherwise recomposes every tick.
                    if (next != _state.value) _state.value = next
                    // Guard the playing file every ~2s: an externally deleted
                    // file must stop playback and release the player now,
                    // not keep a dead handle.
                    ticks++
                    if (ticks % 16 == 0) {
                        val path = currentPath
                        if (path != null && !AudioFileStore.exists(context, path)) {
                            _error.value = "Audio file was deleted outside the app — playback stopped."
                            dropTrack()
                            _state.value = PlaybackState(speed = _state.value.speed, currentPath = null)
                            return@launch
                        }
                    }
                }
                delay(120)
            }
        }
    }
}
