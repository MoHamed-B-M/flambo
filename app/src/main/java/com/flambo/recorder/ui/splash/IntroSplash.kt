package com.flambo.recorder.ui.splash

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.animation.graphics.vector.rememberAnimatedVectorPainter
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private const val INTRO_TOTAL_MS = 2000

/** Warm-up beat: lets cold-start class loading settle before frame one. */
private const val INTRO_WARMUP_MS = 180

/** Returning launch: compressed flash. */
private const val INTRO_FAST_TOTAL_MS = 400
private const val INTRO_FAST_WARMUP_MS = 30

/**
 * Launch intro using the AnimatedVectorDrawable (res/drawable/animated_logo_anim.xml)
 * which encodes the exact stroke-draw + fill animation from the brand SVG.
 * Background uses the app's theme color. Tap or back skips.
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
    val clock = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay((if (fast) INTRO_FAST_WARMUP_MS else INTRO_WARMUP_MS).toLong())
        clock.animateTo(1f, tween(totalMs, easing = LinearEasing))
        onDone()
    }

    // Load the AnimatedVectorDrawable from resources
    val animatedVector = AnimatedImageVector.animatedVectorResource(
        id = com.flambo.recorder.R.drawable.animated_logo_anim
    )
    val painter = rememberAnimatedVectorPainter(animatedVector)
    var atEnd by remember { mutableStateOf(false) }

    // App theme background color
    val backgroundColor = MaterialTheme.colorScheme.background

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDone
            ),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.Image(
            painter = painter,
            contentDescription = null,
            modifier = Modifier.size(200.dp)
        )
    }
}