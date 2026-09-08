package com.flambo.recorder.playback

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
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
    val isPrepared: Boolean = false
)

/**
 * Very small wrapper around ExoPlayer for single-file playback.
 * Keeps one global player so mini-player can survive navigation.
 */
class PlaybackController(private val context: Context) {

    private var player: ExoPlayer? = null
    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state

    private var ticker: Job? = null
    private var currentPath: String? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    fun play(path: String) {
        if (currentPath == path && player != null) {
            player?.play()
            return
        }
        release()
        currentPath = path
        val exo = ExoPlayer.Builder(context).build().also { player = it }
        exo.setMediaItem(MediaItem.fromUri(path))
        exo.prepare()
        exo.playWhenReady = true
        exo.playbackParameters = exo.playbackParameters.withSpeed(_state.value.speed)
        exo.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.value = _state.value.copy(isPlaying = isPlaying, isPrepared = true)
                if (isPlaying) startTicker() else ticker?.cancel()
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) {
                    _state.value = _state.value.copy(isPlaying = false, positionMs = _state.value.durationMs)
                    ticker?.cancel()
                }
            }
        })
        startTicker()
    }

    fun pause() { player?.pause() }
    fun resume() { player?.play() }
    fun toggle() { if (_state.value.isPlaying) pause() else resume() }

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
        _state.value = PlaybackState(speed = _state.value.speed)
    }

    fun release() {
        ticker?.cancel()
        player?.release()
        player = null
        currentPath = null
    }

    fun isPlayingPath(path: String): Boolean = currentPath == path && _state.value.isPlaying

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                val p = player
                if (p != null) {
                    _state.value = _state.value.copy(
                        positionMs = p.currentPosition.coerceAtLeast(0),
                        durationMs = p.duration.coerceAtLeast(0).takeIf { it != androidx.media3.common.C.TIME_UNSET } ?: _state.value.durationMs,
                        isPlaying = p.isPlaying
                    )
                }
                delay(120)
            }
        }
    }
}
