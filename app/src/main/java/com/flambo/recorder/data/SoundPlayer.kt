package com.flambo.recorder.data

import android.content.Context
import android.media.MediaPlayer
import com.flambo.recorder.R

/**
 * Short sound effects — update pop and 3s intro.
 * Uses res/raw assets so no storage permission or file path is needed.
 */
object SoundPlayer {

    @Volatile
    private var player: MediaPlayer? = null

    /** Play the subtle pop when a new version is available. */
    fun playPop(context: Context) {
        play(context.applicationContext, R.raw.pop)
    }

    /** Play the 3 s intro sound together with the intro animation. */
    fun playIntro(context: Context, onDone: (() -> Unit)? = null) {
        play(context.applicationContext, R.raw.intro_sound, onDone)
    }

    private fun play(app: Context, resId: Int, onDone: (() -> Unit)? = null) {
        try {
            player?.release()
            player = MediaPlayer.create(app, resId)?.apply {
                setOnCompletionListener {
                    it.release()
                    if (player === it) player = null
                    onDone?.invoke()
                }
                setOnErrorListener { mp, _, _ ->
                    mp.release()
                    if (player === mp) player = null
                    onDone?.invoke()
                    true
                }
                start()
            } ?: run { onDone?.invoke() }
        } catch (_: Exception) {
            onDone?.invoke()
        }
    }

    fun stop() {
        try { player?.stop() } catch (_: Exception) {}
        try { player?.release() } catch (_: Exception) {}
        player = null
    }
}
