package com.pranshulgg.recordmaster.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pranshulgg.recordmaster.ui.expressive.ExpressiveMotion
import com.pranshulgg.recordmaster.ui.expressive.LocalReducedMotion
import com.pranshulgg.recordmaster.ui.expressive.expressiveSpring
import com.pranshulgg.recordmaster.ui.expressive.standardSpring
import kotlinx.coroutines.delay

// Adapted from material-app/samples/actions/ActionSamples.kt FluidToggleButtonGroup.
// A connected group whose segments grow while pressed and whose outer corners
// round off independently, so the row reads as one fluid control.

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T> FluidSegmentedButtonGroup(
    options: List<T>,
    selected: T,
    onSelected: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    height: Dp = 44.dp,
) {
    val reducedMotion = LocalReducedMotion.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { index, option ->
            val interactionSource = remember(option) { MutableInteractionSource() }
            val pressed by interactionSource.collectIsPressedAsState()
            var tapGeneration by remember(option) { mutableIntStateOf(0) }
            var tapped by remember(option) { mutableStateOf(false) }
            LaunchedEffect(option, tapGeneration) {
                if (tapGeneration > 0 && !reducedMotion) {
                    tapped = true
                    delay(ExpressiveMotion.TapDurationMillis)
                    tapped = false
                }
            }
            val visualPressed = pressed || tapped
            val isSelected = option == selected

            val animatedWeight by animateFloatAsState(
                targetValue = when {
                    visualPressed -> 1.4f
                    isSelected -> 1.2f
                    else -> 1f
                },
                animationSpec = expressiveSpring(),
                label = "segment weight",
            )

            val outerRadius = height / 2
            val innerRadius = 8.dp
            val pressedRadius = 12.dp
            val startRadius by animateDpAsState(
                targetValue = when {
                    visualPressed -> pressedRadius
                    isSelected || index == 0 -> outerRadius
                    else -> innerRadius
                },
                animationSpec = standardSpring(),
                label = "segment start radius",
            )
            val endRadius by animateDpAsState(
                targetValue = when {
                    visualPressed -> pressedRadius
                    isSelected || index == options.lastIndex -> outerRadius
                    else -> innerRadius
                },
                animationSpec = standardSpring(),
                label = "segment end radius",
            )
            val containerColor by animateColorAsState(
                targetValue = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLow
                },
                animationSpec = standardSpring(),
                label = "segment container color",
            )
            val contentColor by animateColorAsState(
                targetValue = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                animationSpec = standardSpring(),
                label = "segment content color",
            )

            Surface(
                modifier = Modifier
                    .weight(animatedWeight)
                    .fillMaxHeight()
                    .selectable(
                        selected = isSelected,
                        onClick = {
                            tapGeneration++
                            onSelected(option)
                        },
                        role = Role.RadioButton,
                        interactionSource = interactionSource,
                    ),
                shape = RoundedCornerShape(
                    topStart = startRadius,
                    bottomStart = startRadius,
                    topEnd = endRadius,
                    bottomEnd = endRadius,
                ),
                color = containerColor,
                contentColor = contentColor,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = label(option),
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
