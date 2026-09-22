package com.flambo.recorder.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.max

@Composable
fun SwipeToDismissContainer(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    dismissThreshold: androidx.compose.ui.unit.Dp = 100.dp,
    velocityThreshold: androidx.compose.ui.unit.Dp = 400.dp,
    background: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    if (!enabled) {
        Box(modifier = modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) { background() }
            Box(Modifier.fillMaxSize()) { content() }
        }
        return
    }

    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    val backdropAlpha = remember { Animatable(0.6f) }
    val parentScale = remember { Animatable(0.96f) }
    var isDismissing by remember { mutableStateOf(false) }

    val dismissDistancePx = with(density) { dismissThreshold.toPx() }
    val velocityThresholdPx = with(density) { velocityThreshold.toPx() }
    val velocityTracker = remember { VelocityTracker() }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize()
    ) {
        val screenWidthPx = with(density) { maxWidth.toPx() }

        // Live parent screen behind, with subtle scale
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = parentScale.value
                    scaleY = parentScale.value
                }
        ) {
            background()
        }

        // Dimming overlay between parent and child — hardware layer alpha (no background recomposition)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .graphicsLayer {
                    alpha = backdropAlpha.value
                    clip = false
                }
        )

        // Child overlay with right-swipe only
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = offsetX.value
                }
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragStart = {
                            velocityTracker.resetTracking()
                        },
                        onDragEnd = {
                            val offset = offsetX.value
                            val velocity = runCatching { velocityTracker.calculateVelocity().x }.getOrDefault(0f)
                            val shouldDismiss = offset > dismissDistancePx || velocity > velocityThresholdPx
                            if (shouldDismiss && !isDismissing) {
                                isDismissing = true
                                scope.launch {
                                    launch { offsetX.animateTo(screenWidthPx, spring(stiffness = Spring.StiffnessMedium)) }
                                    launch { backdropAlpha.animateTo(0f, spring(stiffness = Spring.StiffnessMedium)) }
                                    launch { parentScale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioNoBouncy)) }
                                }.invokeOnCompletion {
                                    scope.launch { onDismiss() }
                                }
                            } else {
                                scope.launch {
                                    launch { offsetX.animateTo(0f, spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)) }
                                    launch { backdropAlpha.animateTo(0.6f, spring(dampingRatio = Spring.DampingRatioLowBouncy)) }
                                    launch { parentScale.animateTo(0.96f, spring(dampingRatio = Spring.DampingRatioLowBouncy)) }
                                }
                            }
                        },
                        onDragCancel = {
                            scope.launch {
                                launch { offsetX.animateTo(0f, spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)) }
                                launch { backdropAlpha.animateTo(0.6f, spring(dampingRatio = Spring.DampingRatioLowBouncy)) }
                                launch { parentScale.animateTo(0.96f, spring(dampingRatio = Spring.DampingRatioLowBouncy)) }
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            if (isDismissing) return@detectHorizontalDragGestures
                            change.consume()
                            velocityTracker.addPosition(change.uptimeMillis, change.position)
                            val newOffset = max(0f, offsetX.value + dragAmount)
                            scope.launch {
                                offsetX.snapTo(newOffset)
                                val progress = (newOffset / screenWidthPx).coerceIn(0f, 1f)
                                backdropAlpha.snapTo((0.6f * (1f - progress)).coerceIn(0f, 0.6f))
                                parentScale.snapTo((0.96f + progress * 0.04f).coerceIn(0.96f, 1f))
                            }
                        }
                    )
                }
        ) {
            content()
        }
    }
}
