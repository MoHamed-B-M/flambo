package com.flambo.recorder.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
    var isDismissing by remember { mutableStateOf(false) }

    val maxDistancePx = with(density) { 600.dp.toPx() }
    val dismissDistancePx = with(density) { 180.dp.toPx() }
    val velocityThresholdPx = with(density) { 900.dp.toPx() }
    val minDragPx = with(density) { 10.dp.toPx() }

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
                    awaitEachGesture {
                        if (isDismissing) return@awaitEachGesture
                        val down = awaitFirstDown(requireUnconsumed = false)
                        velocityTracker.resetTracking()
                        velocityTracker.addPosition(down.uptimeMillis, down.position)
                        var pastSlop = false

                        val drag = awaitTouchSlopOrCancellation(down.id) { change, over ->
                            val d = hypot(over.x.toDouble(), over.y.toDouble()).toFloat()
                            if (d > minDragPx) pastSlop = true
                            change.consume()
                            pastSlop
                        }
                        if (drag == null || !pastSlop) return@awaitEachGesture

                        var pointerId = drag.id
                        drag(drag.id) { change ->
                            if (isDismissing) return@drag
                            change.consume()
                            velocityTracker.addPosition(change.uptimeMillis, change.position)
                            val newX = offsetX.value + change.position.x - change.previousPosition.x
                            val newY = offsetY.value + change.position.y - change.previousPosition.y
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
                            if (change.pressed.not()) return@drag
                        }

                        val distance = hypot(offsetX.value.toDouble(), offsetY.value.toDouble()).toFloat()
                        if (distance < minDragPx) {
                            scope.launch {
                                launch { offsetX.animateTo(0f, spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)) }
                                launch { offsetY.animateTo(0f, spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)) }
                                launch { scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioLowBouncy)) }
                                launch { corner.animateTo(0f, spring(dampingRatio = Spring.DampingRatioLowBouncy)) }
                                launch { backdropAlpha.animateTo(0.6f) }
                            }
                            return@awaitEachGesture
                        }
                        val vel = runCatching { velocityTracker.calculateVelocity() }.getOrNull()
                        val vx = vel?.x ?: 0f
                        val vy = vel?.y ?: 0f
                        val speed = hypot(vx.toDouble(), vy.toDouble()).toFloat()
                        val shouldDismiss = distance > dismissDistancePx || speed > velocityThresholdPx
                        if (shouldDismiss && !isDismissing) {
                            isDismissing = true
                            val targetX = offsetX.value * 3f
                            val targetY = offsetY.value * 3f + with(density) { 800.dp.toPx() } * if (offsetY.value >= 0) 1 else -1
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
                    }
                }
        ) {
            content()
        }
    }
}
