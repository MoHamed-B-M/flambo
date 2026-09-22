package com.flambo.recorder.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.max

@Composable
fun TelegramOverlayCard(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    backgroundContent: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    if (!enabled) {
        Box(modifier = modifier.fillMaxSize()) { content() }
        return
    }

    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    val backdropAlpha = remember { Animatable(0.6f) }
    var isDismissing by remember { mutableStateOf(false) }

    val dismissDistancePx = with(density) { 100.dp.toPx() }
    val velocityThresholdPx = with(density) { 400.dp.toPx() }
    val velocityTracker = remember { VelocityTracker() }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        val screenWidthPx = with(density) { maxWidth.toPx() }

        // Background home preview behind sheet
        Box(modifier = Modifier.fillMaxSize()) {
            if (backgroundContent != null) {
                backgroundContent()
            } else {
                HomePreviewPlaceholder()
            }
        }

        // Dimming layer tied to swipe progress
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = backdropAlpha.value))
        )

        // Foreground sheet with right-swipe only gesture
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = offsetX.value
                    shape = RoundedCornerShape(0.dp)
                    clip = false
                }
                .pointerInput(Unit) {
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
                                    launch { offsetX.animateTo(screenWidthPx, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)) }
                                    launch { backdropAlpha.animateTo(0f, spring(stiffness = Spring.StiffnessMedium)) }
                                }.invokeOnCompletion {
                                    scope.launch { onDismiss() }
                                }
                            } else {
                                scope.launch {
                                    launch { offsetX.animateTo(0f, spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)) }
                                    launch { backdropAlpha.animateTo(0.6f, spring(dampingRatio = Spring.DampingRatioLowBouncy)) }
                                }
                            }
                        },
                        onDragCancel = {
                            scope.launch {
                                launch { offsetX.animateTo(0f, spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)) }
                                launch { backdropAlpha.animateTo(0.6f, spring(dampingRatio = Spring.DampingRatioLowBouncy)) }
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
private fun HomePreviewPlaceholder() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth().height(72.dp)
        ) {
            Box(modifier = Modifier.padding(16.dp), contentAlignment = Alignment.CenterStart) {
                Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)) {
                    Box(modifier = Modifier.fillMaxWidth(0.55f).height(14.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)))
                    Box(modifier = Modifier.fillMaxWidth(0.35f).height(10.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)))
                }
            }
        }
        repeat(5) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth().height(84.dp)
            ) {
                Box(modifier = Modifier.padding(16.dp)) {
                    Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Box(modifier = Modifier.fillMaxWidth(0.7f).height(12.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)))
                        Box(modifier = Modifier.fillMaxWidth(0.45f).height(8.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)))
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(80.dp))
        Text(
            "Home preview • dimmed behind sheet",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}
