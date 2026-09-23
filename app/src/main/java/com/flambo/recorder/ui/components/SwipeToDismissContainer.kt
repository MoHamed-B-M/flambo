package com.flambo.recorder.ui.components

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.math.max

@Composable
fun SwipeToDismissContainer(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    dismissThreshold: androidx.compose.ui.unit.Dp = 100.dp,
    velocityThreshold: androidx.compose.ui.unit.Dp = 400.dp,
    background: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    // Clean native: no manual parent stacking — NavHost handles parent via predictive back
    if (!enabled) {
        Box(modifier = modifier.fillMaxSize()) { content() }
        return
    }

    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    val scale = remember { Animatable(1f) }
    val velocityTracker = remember { VelocityTracker() }

    PredictiveBackHandler(enabled = enabled) { progress ->
        try {
            progress.collect { event ->
                val p = event.progress
                scale.snapTo((1f - p * 0.05f).coerceIn(0.95f, 1f))
                offsetX.snapTo(p * with(density) { 1080.dp.toPx() } * 0.3f)
            }
            onDismiss()
        } catch (e: CancellationException) {
            scope.launch {
                offsetX.animateTo(0f, spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow))
                scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioLowBouncy))
            }
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val screenWidthPx = with(density) { maxWidth.toPx() }
        val dismissDistancePx = with(density) { dismissThreshold.toPx() }
        val velocityThresholdPx = with(density) { velocityThreshold.toPx() }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = offsetX.value
                    scaleX = scale.value
                    scaleY = scale.value
                    clip = false
                }
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragStart = { velocityTracker.resetTracking() },
                        onDragEnd = {
                            val offset = offsetX.value
                            val velocity = runCatching { velocityTracker.calculateVelocity().x }.getOrDefault(0f)
                            val shouldDismiss = offset > dismissDistancePx || velocity > velocityThresholdPx
                            if (shouldDismiss) {
                                scope.launch { offsetX.animateTo(screenWidthPx, spring(stiffness = Spring.StiffnessMedium)) }
                                scope.launch {
                                    kotlinx.coroutines.delay(160)
                                    if (offsetX.value >= screenWidthPx * 0.95f) onDismiss()
                                }
                            } else {
                                scope.launch {
                                    offsetX.animateTo(0f, spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow))
                                    scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioLowBouncy))
                                }
                            }
                        },
                        onDragCancel = {
                            scope.launch {
                                offsetX.animateTo(0f, spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow))
                                scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioLowBouncy))
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            velocityTracker.addPosition(change.uptimeMillis, change.position)
                            val newOffset = max(0f, offsetX.value + dragAmount)
                            scope.launch {
                                offsetX.snapTo(newOffset)
                                val progress = (newOffset / screenWidthPx).coerceIn(0f, 1f)
                                scale.snapTo((1f - progress * 0.05f).coerceIn(0.95f, 1f))
                            }
                        }
                    )
                }
        ) {
            content()
        }
    }
}

@Composable
fun SwipeToDismissContainer(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    SwipeToDismissContainer(
        onDismiss = onDismiss,
        modifier = modifier,
        enabled = enabled,
        background = null,
        content = content
    )
}
