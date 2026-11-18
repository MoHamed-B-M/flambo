package com.pranshulgg.recordmaster.core.playback

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
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

/**
 * Short-lived ExoPlayer used only by the paused "Scrub & Review" bar.
 *
 * It deliberately does not use the shared [PlaybackController] or a media
 * session: review audio must never appear in the system media notification or
 * compete with the library's single playback pipeline. The controller owns the
 * player and the screen just observes [state].
 */
@androidx.annotation.OptIn(UnstableApi::class)
class ReviewController(private val context: Context) {

    data class ReviewState(
        val isReady: Boolean = false,
        val isPlaying: Boolean = false,
        val positionMs: Long = 0L,
        val durationMs: Long = 0L,
        val path: String? = null
    )

    private val _state = MutableStateFlow(ReviewState())
    val state: StateFlow<ReviewState> = _state

    private var player: ExoPlayer? = null
    private var ticker: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** Loads (or reloads) the paused chunk for scrubbing. */
    fun prepare(path: String) {
        if (path.isBlank()) return
        releasePlayer()
        _state.value = ReviewState(path = path)

        val exoPlayer = ExoPlayer.Builder(context).build()
        player = exoPlayer
        exoPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    _state.value = _state.value.copy(
                        isReady = true,
                        durationMs = exoPlayer.duration
                            .takeIf { it != C.TIME_UNSET }
                            ?: _state.value.durationMs
                    )
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.value = _state.value.copy(isPlaying = isPlaying)
                if (isPlaying) startTicker() else ticker?.cancel()
            }
        })
        exoPlayer.setMediaItem(MediaItem.fromUri(Uri.fromFile(File(path))))
        exoPlayer.prepare()
    }

    fun play() {
        val exoPlayer = player ?: return
        if (exoPlayer.playbackState == Player.STATE_ENDED) {
            exoPlayer.seekTo(0)
            _state.value = _state.value.copy(positionMs = 0L)
        }
        exoPlayer.playWhenReady = true
        exoPlayer.play()
    }

    fun pause() {
        player?.pause()
    }

    fun toggle() {
        if (_state.value.isPlaying) pause() else play()
    }

    fun seekTo(ms: Long) {
        val target = ms.coerceAtLeast(0L)
        player?.seekTo(target)
        _state.value = _state.value.copy(positionMs = target)
    }

    /** Releases the player and clears the state (called on resume/save/cancel). */
    fun stop() {
        releasePlayer()
        _state.value = ReviewState()
    }

    private fun releasePlayer() {
        ticker?.cancel()
        ticker = null
        player?.release()
        player = null
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                val exoPlayer = player ?: break
                _state.value = _state.value.copy(
                    positionMs = exoPlayer.currentPosition.coerceAtLeast(0L),
                    durationMs = exoPlayer.duration
                        .takeIf { it != C.TIME_UNSET }
                        ?: _state.value.durationMs
                )
                delay(120L)
            }
        }
    }
}
