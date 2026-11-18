package com.pranshulgg.recordmaster.ui.expressive

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

// Adapted from material-app/samples/actions/ActionSamples.kt ExpressiveActionButton.
// The record FAB keeps its circular rest shape but rounds down while pressed and
// scales its content, so the whole button reads as a physical press.

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RecordButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    containerColor: Color = MaterialTheme.colorScheme.errorContainer,
    contentColor: Color = MaterialTheme.colorScheme.onErrorContainer,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val reducedMotion = LocalReducedMotion.current
    val tapFeedbackMillis = tapFeedbackDurationMillis()
    var tapGeneration by remember { mutableIntStateOf(0) }
    var tapped by remember { mutableStateOf(false) }
    LaunchedEffect(tapGeneration) {
        if (tapGeneration > 0 && !reducedMotion) {
            tapped = true
            delay(tapFeedbackMillis)
            tapped = false
        }
    }
    val visualPressed = pressed || tapped
    val restCorner = size / 2
    val pressedCorner = (size / 2 - 8.dp).coerceAtLeast(0.dp)
    val cornerRadius by animateDpAsState(
        targetValue = if (visualPressed) pressedCorner else restCorner,
        animationSpec = standardSpring(),
        label = "record button corner radius",
    )
    val contentScale by animateFloatAsState(
        targetValue = ExpressiveMotion.pressScale(visualPressed, enabled = true),
        animationSpec = pressSpring(),
        label = "record button content scale",
    )

    FloatingActionButton(
        onClick = {
            tapGeneration++
            onClick()
        },
        modifier = modifier.size(size),
        shape = RoundedCornerShape(cornerRadius),
        containerColor = containerColor,
        contentColor = contentColor,
        interactionSource = interactionSource,
    ) {
        Box(
            modifier = Modifier.graphicsLayer {
                scaleX = contentScale
                scaleY = contentScale
            }
        ) {
            content()
        }
    }
}
