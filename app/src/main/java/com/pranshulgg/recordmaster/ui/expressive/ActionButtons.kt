package com.pranshulgg.recordmaster.ui.expressive

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

// Adapted from material-app/samples/actions/ActionSamples.kt.
// ExpressiveActionButton: spring press, corner-radius morph and inline loading.
// HoldToConfirmButton: press-and-hold destructive action with a progress fill.

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveActionButton(
    onClick: () -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    height: Dp = 56.dp,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    var tapGeneration by remember { mutableIntStateOf(0) }
    var tapped by remember { mutableStateOf(false) }
    val reducedMotion = LocalReducedMotion.current
    LaunchedEffect(tapGeneration) {
        if (tapGeneration > 0 && !reducedMotion) {
            tapped = true
            delay(ExpressiveMotion.TapDurationMillis)
            tapped = false
        }
    }
    val visualPressed = pressed || tapped
    val loadingEnter = if (reducedMotion) {
        EnterTransition.None
    } else {
        fadeIn(animationSpec = softSpring())
    }
    val loadingExit = if (reducedMotion) {
        ExitTransition.None
    } else {
        fadeOut(animationSpec = softSpring())
    }
    val restCorner = height / 2
    val pressedCorner = (height / 2 - 8.dp).coerceAtLeast(0.dp)
    val cornerRadius by animateDpAsState(
        targetValue = if (visualPressed && enabled) pressedCorner else restCorner,
        animationSpec = standardSpring(),
        label = "expressive button corner radius",
    )
    val contentScale by animateFloatAsState(
        targetValue = ExpressiveMotion.pressScale(visualPressed, enabled),
        animationSpec = pressSpring(),
        label = "expressive button content scale",
    )
    val extraPadding by animateDpAsState(
        targetValue = if (visualPressed) 16.dp else 0.dp,
        animationSpec = standardSpring(),
        label = "expressive button horizontal padding",
    )
    val containerColor by animateColorAsState(
        targetValue = if (loading) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.primary
        },
        animationSpec = standardSpring(),
        label = "expressive button container color",
    )
    val contentColor by animateColorAsState(
        targetValue = if (loading) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onPrimary
        },
        animationSpec = standardSpring(),
        label = "expressive button content color",
    )

    Button(
        onClick = {
            tapGeneration++
            onClick()
        },
        enabled = enabled && !loading,
        shape = RoundedCornerShape(cornerRadius),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
        interactionSource = interactionSource,
        contentPadding = PaddingValues(horizontal = 24.dp + extraPadding),
        modifier = modifier
            .height(height)
            .semantics { if (loading) stateDescription = "Loading" },
    ) {
        AnimatedContent(
            targetState = loading,
            transitionSpec = {
                (loadingEnter togetherWith loadingExit).using(
                    if (reducedMotion) {
                        SizeTransform(clip = false) { _, _ -> snap() }
                    } else {
                        SizeTransform()
                    }
                )
            },
            label = "button loading content",
        ) { isLoading ->
            if (isLoading) {
                LoadingIndicator(modifier = Modifier.size(height * 0.5f))
            } else {
                Text(
                    text,
                    modifier = Modifier.graphicsLayer {
                        scaleX = contentScale
                        scaleY = contentScale
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HoldToConfirmButton(
    text: String,
    onHoldComplete: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    holdDurationMillis: Long = ExpressiveMotion.HoldDurationMillis,
    height: Dp = 56.dp,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val stillPressed by rememberUpdatedState(pressed)
    var rawProgress by remember { mutableFloatStateOf(0f) }
    val reducedMotion = LocalReducedMotion.current
    val haptics = LocalHapticFeedback.current
    val currentOnHoldComplete by rememberUpdatedState(onHoldComplete)
    val animatedProgress by animateFloatAsState(
        targetValue = rawProgress,
        animationSpec = if (rawProgress < 0.05f) softSpring() else progressSpring(),
        label = "hold progress",
    )
    val restCorner = height / 2
    val pressedCorner = (height / 2 - 8.dp).coerceAtLeast(0.dp)
    val cornerRadius by animateDpAsState(
        targetValue = if (pressed && enabled) pressedCorner else restCorner,
        animationSpec = standardSpring(),
        label = "hold button corner radius",
    )
    val contentScale by animateFloatAsState(
        targetValue = ExpressiveMotion.pressScale(pressed, enabled),
        animationSpec = pressSpring(),
        label = "hold button content scale",
    )
    val extraPadding by animateDpAsState(
        targetValue = if (pressed && enabled) 16.dp else 0.dp,
        animationSpec = standardSpring(),
        label = "hold button horizontal padding",
    )
    val safeDuration = holdDurationMillis.coerceAtLeast(1L)

    LaunchedEffect(pressed, enabled, holdDurationMillis) {
        try {
            rawProgress = 0f
            if (pressed && enabled) {
                runCatching { haptics.performHapticFeedback(HapticFeedbackType.LongPress) }
                if (reducedMotion) {
                    delay(safeDuration)
                } else {
                    val startedAt = withFrameMillis { it }
                    while (stillPressed && rawProgress < 1f) {
                        val now = withFrameMillis { it }
                        rawProgress = ExpressiveMotion.holdProgress(now - startedAt, safeDuration)
                    }
                }
                if (stillPressed) {
                    runCatching { haptics.performHapticFeedback(HapticFeedbackType.LongPress) }
                    currentOnHoldComplete()
                }
            }
        } finally {
            rawProgress = 0f
        }
    }

    Surface(
        onClick = {},
        color = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(cornerRadius),
        enabled = enabled,
        interactionSource = interactionSource,
        modifier = modifier
            .height(height)
            .semantics {
                stateDescription = if (rawProgress > 0f) {
                    "Holding ${(rawProgress * 100).toInt()} percent"
                } else {
                    "Hold to confirm"
                }
                onLongClick(label = "Confirm") {
                    if (enabled) {
                        currentOnHoldComplete()
                        true
                    } else {
                        false
                    }
                }
            },
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (!reducedMotion && animatedProgress > 0f) {
                val progressBrush = Brush.horizontalGradient(
                    listOf(
                        contentColor.copy(alpha = 0.12f),
                        contentColor.copy(alpha = 0.24f),
                    ),
                )
                Box(
                    Modifier
                        .matchParentSize()
                        .drawBehind {
                            drawRect(
                                brush = progressBrush,
                                size = size.copy(width = size.width * animatedProgress.coerceIn(0f, 1f)),
                            )
                        },
                )
            }
            Row(
                modifier = Modifier
                    .padding(horizontal = 24.dp + extraPadding)
                    .graphicsLayer {
                        scaleX = contentScale
                        scaleY = contentScale
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) { Text(text) }
        }
    }
}
