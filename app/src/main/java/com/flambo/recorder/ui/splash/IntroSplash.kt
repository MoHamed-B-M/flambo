package com.flambo.recorder.ui.splash

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

private const val INTRO_TOTAL_MS = 3100

private const val TOP_PATH =
    "M400,500 C477.32,500 540,437.32 540,360 L540,220 C540,142.68 477.32,80 400,80 " +
        "C322.68,80 260,142.68 260,220 L260,360 C260,437.32 322.68,500 400,500 Z " +
        "M310,220 C310,170.3 350.3,130 400,130 C449.7,130 490,170.3 490,220 L490,360 " +
        "C490,409.7 449.7,450 400,450 C350.3,450 310,409.7 310,360 L310,220 Z"

private const val BOTTOM_PATH =
    "M600,330 C586.19,330 575,341.19 575,355 C575,451.65 496.65,530 400,530 " +
        "C303.35,530 225,451.65 225,355 C225,341.19 213.81,330 200,330 " +
        "C186.19,330 175,341.19 175,355 C175,466.86 256.76,559.81 362.5,577.16 " +
        "L362.5,670 L290,670 C276.19,670 265,681.19 265,695 C265,708.81 276.19,720 290,720 " +
        "L510,720 C523.81,720 535,708.81 535,695 C535,681.19 523.81,670 510,670 " +
        "L437.5,670 L437.5,577.16 C543.24,559.81 625,466.86 625,355 " +
        "C625,341.19 613.81,330 600,330 Z"

private val DrawEase = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

private fun phase(tMs: Float, startMs: Int, durationMs: Int): Float =
    ((tMs - startMs) / durationMs).coerceIn(0f, 1f)

/**
 * Launch intro, drawn natively: mic capsule draws itself, fills with the
 * brand gradient plus a halo glow, then the FLAMBO wordmark rises in.
 * Same staging as the brand SVG (top 0.2-1.7s, cradle 1.2-2.7s,
 * text 2.3-3.1s). Tap or back skips.
 */
@Composable
fun IntroSplash(onDone: () -> Unit, modifier: Modifier = Modifier) {
    BackHandler(onBack = onDone)
    val clock = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        clock.animateTo(1f, tween(INTRO_TOTAL_MS, easing = LinearEasing))
        onDone()
    }

    val topPath = remember { PathParser().parsePathString(TOP_PATH).toPath() }
    val bottomPath = remember { PathParser().parsePathString(BOTTOM_PATH).toPath() }
    val measure = remember { PathMeasure() }
    val segment = remember { Path() }
    val textMeasurer = rememberTextMeasurer()
    val textStyle = TextStyle(
        fontSize = 40.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = 0.35.em,
        brush = Brush.linearGradient(
            listOf(Color(0xFFFF416C), Color(0xFFFF4B2B), Color(0xFF8A2387)),
            start = Offset(200f, 810f),
            end = Offset(600f, 810f)
        )
    )
    val textLayout = remember { textMeasurer.measure("FLAMBO", textStyle) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF090A0F))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDone
            )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val tMs = clock.value * INTRO_TOTAL_MS
            val s = minOf(size.width / 800f, size.height / 900f)
            val tx = (size.width - 800f * s) / 2f
            val ty = (size.height - 900f * s) / 2f - 20f * s
            val brush = Brush.linearGradient(
                listOf(Color(0xFFFF416C), Color(0xFFFF4B2B), Color(0xFF8A2387)),
                start = Offset(0f, 0f),
                end = Offset(800f, 900f)
            )
            withTransform({
                translate(tx, ty)
                scale(s, s, pivot = Offset.Zero)
            }) {
                drawMorphPath(
                    path = topPath,
                    brush = brush,
                    measure = measure,
                    segment = segment,
                    drawFrac = DrawEase.transform(phase(tMs, 200, 1100)),
                    fillFrac = LinearOutSlowInEasing.transform(phase(tMs, 1200, 500))
                )
                drawMorphPath(
                    path = bottomPath,
                    brush = brush,
                    measure = measure,
                    segment = segment,
                    drawFrac = DrawEase.transform(phase(tMs, 1200, 1100)),
                    fillFrac = LinearOutSlowInEasing.transform(phase(tMs, 2100, 600))
                )
                val textFrac = LinearOutSlowInEasing.transform(phase(tMs, 2300, 800))
                if (textFrac > 0f) {
                    drawText(
                        textMeasurer = textMeasurer,
                        text = "FLAMBO",
                        topLeft = Offset(
                            400f - textLayout.size.width / 2f,
                            810f + (1f - textFrac) * 18f
                        ),
                        style = textStyle,
                        alpha = textFrac
                    )
                }
            }
        }
    }
}

/** Progressive stroke draw, then gradient fill with a halo glow. */
private fun DrawScope.drawMorphPath(
    path: Path,
    brush: Brush,
    measure: PathMeasure,
    segment: Path,
    drawFrac: Float,
    fillFrac: Float
) {
    if (drawFrac > 0f) {
        measure.setPath(path, false)
        segment.reset()
        measure.getSegment(0f, measure.length * drawFrac, segment, true)
        drawPath(
            path = segment,
            brush = brush,
            alpha = (drawFrac / 0.2f).coerceIn(0f, 1f) * (1f - fillFrac),
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = 10f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
    if (fillFrac > 0f) {
        // Cheap halo: wide soft stroke behind the fill, no blur layer needed.
        drawPath(
            path = path,
            brush = brush,
            alpha = 0.3f * fillFrac,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = 30f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
        drawPath(path = path, brush = brush, alpha = fillFrac)
    }
}
