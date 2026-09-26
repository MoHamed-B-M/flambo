package com.flambo.recorder.playback

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.flambo.recorder.data.AudioFileStore
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

    private var player: ExoPlayer? = null
    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state

    private var ticker: Job? = null
    private var currentPath: String? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun clearError() { _error.value = null }

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
        release()
        currentPath = path
        _state.value = PlaybackState(speed = _state.value.speed, currentPath = path)
        val exo = ExoPlayer.Builder(context).build().also { player = it }
        exo.setMediaItem(MediaItem.fromUri(path))
        exo.prepare()
        exo.playWhenReady = true
        exo.playbackParameters = exo.playbackParameters.withSpeed(_state.value.speed)
        exo.addListener(object : Player.Listener {
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
        })
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
        ticker?.cancel()
        player?.release()
        player = null
        currentPath = null
    }

    fun isPlayingPath(path: String): Boolean = currentPath == path && _state.value.isPlaying
    fun isCurrentPath(path: String): Boolean = currentPath == path

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
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
                }
                delay(120)
            }
        }
    }
}
