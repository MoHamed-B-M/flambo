package com.flambo.recorder.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
fun WaveformVisualizer(
    amplitudes: List<Float>,
    currentAmplitude: Float,
    isPaused: Boolean,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.primary,
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh
) {
    val animatedAmp by animateFloatAsState(
        targetValue = if (isPaused) 0.08f else currentAmplitude.coerceIn(0f, 1f),
        label = "amp"
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(88.dp)
    ) {
        val width = size.width
        val height = size.height
        val centerY = height / 2f

        // background pill
        drawRoundRect(
            color = backgroundColor,
            cornerRadius = CornerRadius(24.dp.toPx()),
            size = Size(width, height)
        )

        if (amplitudes.isEmpty()) {
            // idle pulse
            val pulseH = (height * 0.18f) + (animatedAmp * height * 0.45f)
            drawRoundRect(
                color = barColor.copy(alpha = 0.95f),
                topLeft = Offset(width / 2f - 14.dp.toPx(), centerY - pulseH / 2),
                size = Size(28.dp.toPx(), pulseH),
                cornerRadius = CornerRadius(999f)
            )
            return@Canvas
        }

        val count = amplitudes.size.coerceAtMost(48)
        val recent = amplitudes.takeLast(count)
        val barW = (width - 32.dp.toPx()) / count
        val gap = 3.dp.toPx()
        val startX = 16.dp.toPx()

        recent.forEachIndexed { idx, amp ->
            val hNorm = (amp * 0.9f + 0.08f).coerceIn(0.08f, 1f)
            val barH = height * (0.12f + hNorm * 0.78f)
            // give latest bar a tiny boost from live amplitude
            val boost = if (idx == recent.lastIndex) animatedAmp * 0.12f * height else 0f
            val finalH = (barH + boost).coerceAtMost(height * 0.92f)
            val x = startX + idx * barW
            val y = centerY - finalH / 2
            drawRoundRect(
                color = if (idx == recent.lastIndex) barColor else barColor.copy(alpha = 0.72f + hNorm * 0.26f),
                topLeft = Offset(x + gap / 2, y),
                size = Size(barW - gap, finalH),
                cornerRadius = CornerRadius(999f)
            )
        }
    }
}

@Composable
fun StaticWaveform(
    peaks: List<Float>,
    progress: Float, // 0..1
    modifier: Modifier = Modifier,
    playedColor: Color = MaterialTheme.colorScheme.primary,
    remainingColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest
) {
    Canvas(modifier = modifier.fillMaxWidth().height(48.dp)) {
        if (peaks.isEmpty()) {
            drawRoundRect(
                color = remainingColor,
                size = Size(size.width, 6.dp.toPx()),
                topLeft = Offset(0f, size.height / 2 - 3.dp.toPx()),
                cornerRadius = CornerRadius(999f)
            )
            return@Canvas
        }
        val count = peaks.size.coerceAtMost(80)
        val data = peaks.take(count)
        val barW = size.width / count
        val gap = 2.dp.toPx()
        val centerY = size.height / 2
        data.forEachIndexed { i, amp ->
            val isPlayed = i.toFloat() / count <= progress
            val h = size.height * (0.18f + amp.coerceIn(0f, 1f) * 0.82f)
            drawRoundRect(
                color = if (isPlayed) playedColor else remainingColor,
                topLeft = Offset(i * barW + gap / 2, centerY - h / 2),
                size = Size(barW - gap, h),
                cornerRadius = CornerRadius(999f)
            )
        }
    }
}
