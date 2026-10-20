package com.flambo.recorder.ui.splash

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private const val INTRO_TOTAL_MS = 3200

/** Warm-up beat: lets cold-start class loading settle before frame one. */
private const val INTRO_WARMUP_MS = 180

/** Returning launch: the same staging compressed to a quick flash. */
private const val INTRO_FAST_TOTAL_MS = 400
private const val INTRO_FAST_WARMUP_MS = 30

/** Path from the animated-logo.svg (24x24 viewBox, centered). */
private const val LOGO_PATH =
    "M20 12V13C20 17.4183 16.4183 21 12 21C7.58172 21 4 17.4183 4 13V12" +
    "M12 17C9.79086 17 8 15.2091 8 13V7C8 4.79086 9.79086 3 12 3" +
    "C14.2091 3 16 4.79086 16 7V13C16 15.2091 14.2091 17 12 17Z"

private val DrawEase = CubicBezierEasing(0.68f, -0.55f, 0.265f, 1.55f)

private fun phase(tMs: Float, startMs: Int, durationMs: Int): Float =
    ((tMs - startMs) / durationMs).coerceIn(0f, 1f)

/**
 * Launch intro using the animated-logo.svg path: the mic capsule draws
 * itself with a bouncy stroke animation, then fills with the brand gradient.
 * Tap or back skips.
 */
@Composable
fun IntroSplash(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    /** First launch plays the full sequence; returns get a ~400ms flash. */
    fast: Boolean = false
) {
    BackHandler(onBack = onDone)
    val totalMs = if (fast) INTRO_FAST_TOTAL_MS else INTRO_TOTAL_MS
    val sc: (Int) -> Int = { ms -> (ms * totalMs / INTRO_TOTAL_MS).coerceAtLeast(1) }
    val clock = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay((if (fast) INTRO_FAST_WARMUP_MS else INTRO_WARMUP_MS).toLong())
        clock.animateTo(1f, tween(totalMs, easing = LinearEasing))
        onDone()
    }

    val logoPath = remember { PathParser().parsePathString(LOGO_PATH).toPath() }
    val measure = remember { PathMeasure() }
    val segment = remember { Path() }

    // Brand gradient matching the app theme
    val morphBrush = remember {
        Brush.linearGradient(
            listOf(Color(0xFFFF416C), Color(0xFFFF4B2B), Color(0xFF8A2387)),
            start = androidx.compose.ui.geometry.Offset(0f, 0f),
            end = androidx.compose.ui.geometry.Offset(800f, 900f)
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF5E8D6)) // Cream background from SVG
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDone
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val tMs = clock.value * totalMs
            // Scale to fit within 80% of the smaller screen dimension
            val targetSize = minOf(size.width, size.height) * 0.8f
            val pathBounds = logoPath.getBounds()
            val pathWidth = pathBounds.width
            val pathHeight = pathBounds.height
            val scale = targetSize / maxOf(pathWidth, pathHeight)
            val tx = (size.width - pathWidth * scale) / 2f
            val ty = (size.height - pathHeight * scale) / 2f

            withTransform({
                translate(tx, ty)
                scale(scale, scale, pivot = androidx.compose.ui.geometry.Offset.Zero)
            }) {
                // Stroke draw animation (0-2.2s)
                val drawFrac = DrawEase.transform(phase(tMs, 0, sc(2200)))
                // Fill fade-in (1.8s-3.2s)
                val fillFrac = phase(tMs, sc(1800), sc(1400))

                // Draw stroke
                if (drawFrac > 0f) {
                    measure.setPath(logoPath, false)
                    segment.reset()
                    measure.getSegment(0f, measure.length * drawFrac, segment, true)
                    drawPath(
                        path = segment,
                        brush = morphBrush,
                        alpha = (drawFrac / 0.1f).coerceIn(0f, 1f) * (1f - fillFrac),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = 4f,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }
                // Fill with gradient
                if (fillFrac > 0f) {
                    drawPath(path = logoPath, brush = morphBrush, alpha = fillFrac)
                }
            }
        }
    }
}