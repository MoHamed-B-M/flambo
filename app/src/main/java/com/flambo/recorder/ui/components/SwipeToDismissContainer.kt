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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
    background: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    if (!enabled) {
        Box(modifier = modifier.fillMaxSize()) { content() }
        return
    }

    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    val backdropAlpha = remember { Animatable(0.5f) }
    val velocityTracker = remember { VelocityTracker() }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val screenWidthPx = with(density) { maxWidth.toPx() }
        val dismissDistancePx = with(density) { dismissThreshold.toPx() }
        val velocityThresholdPx = with(density) { velocityThreshold.toPx() }

        // Parent render tree — kept alive underneath child, visible during drag (no black flash)
        if (background != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        // Keep parent fully rendered, no scale needed, just ensure layer caching
                        clip = false
                    }
            ) {
                background()
            }
        }

        // Dynamic dark backdrop over parent — hardware layer alpha 0.5 -> 0.0 tied to offsetX
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .graphicsLayer {
                    alpha = backdropAlpha.value
                    clip = false
                }
        )

        // Child sheet — GPU translation, zero recomposition
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = offsetX.value
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
                                scope.launch {
                                    offsetX.animateTo(screenWidthPx, spring(stiffness = Spring.StiffnessMedium))
                                }
                                scope.launch {
                                    backdropAlpha.animateTo(0f, spring(stiffness = Spring.StiffnessMedium))
                                }.invokeOnCompletion {
                                    scope.launch { onDismiss() }
                                }
                            } else {
                                scope.launch {
                                    offsetX.animateTo(0f, spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow))
                                }
                                scope.launch {
                                    backdropAlpha.animateTo(0.5f, spring(dampingRatio = Spring.DampingRatioLowBouncy))
                                }
                            }
                        },
                        onDragCancel = {
                            scope.launch {
                                offsetX.animateTo(0f, spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow))
                                backdropAlpha.animateTo(0.5f, spring(dampingRatio = Spring.DampingRatioLowBouncy))
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            velocityTracker.addPosition(change.uptimeMillis, change.position)
                            val newOffset = max(0f, offsetX.value + dragAmount)
                            scope.launch {
                                offsetX.snapTo(newOffset)
                                val progress = (newOffset / screenWidthPx).coerceIn(0f, 1f)
                                // Backdrop 0.5 -> 0.0 linear with offset
                                backdropAlpha.snapTo((0.5f * (1f - progress)).coerceIn(0f, 0.5f))
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
