package com.flambo.recorder.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.hypot

@Composable
fun TelegramOverlayCard(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }
    val scale = remember { Animatable(1f) }
    val backdropAlpha = remember { Animatable(0.6f) }
    val corner = remember { Animatable(0f) }

    val maxDistancePx = with(density) { 600.dp.toPx() }
    val dismissDistancePx = with(density) { 150.dp.toPx() }
    val velocityThresholdPx = with(density) { 1000.dp.toPx() }

    val velocityTracker = remember { VelocityTracker() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = backdropAlpha.value))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                    translationX = offsetX.value
                    translationY = offsetY.value
                    shape = RoundedCornerShape(corner.value.dp)
                    clip = corner.value > 0f
                }
                .clip(RoundedCornerShape(corner.value.dp))
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = {
                            velocityTracker.resetTracking()
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            velocityTracker.addPosition(change.uptimeMillis, change.position)
                            val newX = offsetX.value + dragAmount.x
                            val newY = offsetY.value + dragAmount.y
                            scope.launch {
                                offsetX.snapTo(newX)
                                offsetY.snapTo(newY)
                            }
                            val distance = hypot(newX.toDouble(), newY.toDouble()).toFloat()
                            val progress = (distance / maxDistancePx).coerceIn(0f, 1f)
                            scope.launch {
                                scale.snapTo((1f - progress * 0.15f).coerceIn(0.85f, 1f))
                                corner.snapTo(progress * 24f)
                                backdropAlpha.snapTo((0.6f * (1f - progress)).coerceIn(0f, 0.6f))
                            }
                        },
                        onDragEnd = {
                            val velocity = velocityTracker.calculateVelocity()
                            val vel = hypot(velocity.x.toDouble(), velocity.y.toDouble()).toFloat()
                            val distance = hypot(offsetX.value.toDouble(), offsetY.value.toDouble()).toFloat()
                            val shouldDismiss = distance > dismissDistancePx || vel > velocityThresholdPx
                            if (shouldDismiss) {
                                val targetX = offsetX.value * 3
                                val targetY = offsetY.value * 3 + with(density) { 800.dp.toPx() } * if (offsetY.value >= 0) 1 else -1
                                scope.launch {
                                    launch { offsetX.animateTo(targetX, spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)) }
                                    launch { offsetY.animateTo(targetY, spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)) }
                                    launch { scale.animateTo(0.85f, spring(dampingRatio = Spring.DampingRatioLowBouncy)) }
                                    launch { backdropAlpha.animateTo(0f) }
                                }.invokeOnCompletion { onDismiss() }
                            } else {
                                scope.launch {
                                    launch { offsetX.animateTo(0f, spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)) }
                                    launch { offsetY.animateTo(0f, spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)) }
                                    launch { scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioLowBouncy)) }
                                    launch { corner.animateTo(0f, spring(dampingRatio = Spring.DampingRatioLowBouncy)) }
                                    launch { backdropAlpha.animateTo(0.6f) }
                                }
                            }
                        },
                        onDragCancel = {
                            scope.launch {
                                launch { offsetX.animateTo(0f, spring(dampingRatio = Spring.DampingRatioLowBouncy)) }
                                launch { offsetY.animateTo(0f, spring(dampingRatio = Spring.DampingRatioLowBouncy)) }
                                launch { scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioLowBouncy)) }
                                launch { corner.animateTo(0f, spring(dampingRatio = Spring.DampingRatioLowBouncy)) }
                                launch { backdropAlpha.animateTo(0.6f) }
                            }
                        }
                    )
                }
        ) {
            content()
        }
    }
}
