package com.flambo.recorder.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/** Playback progress-bar styles selectable in Appearance settings. */
object ProgressBarStyle {
    const val SLIDER = "slider"
    const val LINEAR = "linear"
    const val WAVY = "wavy"

    fun isValid(id: String): Boolean = id == SLIDER || id == LINEAR || id == WAVY

    fun label(id: String): String = when (id) {
        LINEAR -> "Linear"
        WAVY -> "Wavy"
        else -> "Slider"
    }
}

/**
 * Playback progress with a user-selectable style.
 *
 * - [ProgressBarStyle.SLIDER]: the default draggable Material slider.
 * - [ProgressBarStyle.LINEAR] / [ProgressBarStyle.WAVY]: bars that seek on tap.
 */
@Composable
fun PlaybackProgressBar(
    progress: Float,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: String = ProgressBarStyle.SLIDER
) {
    when (style) {
        ProgressBarStyle.LINEAR -> LinearProgressIndicator(
            progress = { progress },
            modifier = modifier
                .fillMaxWidth()
                .graphicsLayer {
                    clip = true
                    shape = RoundedCornerShape(12.dp)
                }
                .pointerInput(enabled, onSeek) {
                    if (!enabled) return@pointerInput
                    detectTapGestures { offset ->
                        onSeek((offset.x / size.width).coerceIn(0f, 1f))
                    }
                }
        )
        ProgressBarStyle.WAVY -> LinearWavyProgressIndicator(
            progress = { progress },
            modifier = modifier
                .fillMaxWidth()
                .pointerInput(enabled, onSeek) {
                    if (!enabled) return@pointerInput
                    detectTapGestures { offset ->
                        onSeek((offset.x / size.width).coerceIn(0f, 1f))
                    }
                }
        )
        else -> Slider(
            value = progress,
            enabled = enabled,
            onValueChange = onSeek,
            modifier = modifier
                .fillMaxWidth()
                .graphicsLayer {
                    clip = true
                    shape = RoundedCornerShape(12.dp)
                }
        )
    }
}
