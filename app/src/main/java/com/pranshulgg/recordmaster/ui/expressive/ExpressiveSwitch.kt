package com.pranshulgg.recordmaster.ui.expressive

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

// Adapted from material-app/core/designsystem/ExpressiveSwitch.kt.
// A 52x32dp track with a 24dp thumb whose check mark morphs into a cross when
// off, replacing the stock Switch + thumbContent icon pair.

private val TrackWidth = 52.dp
private val TrackHeight = 32.dp
private val ThumbSize = 24.dp

@Composable
fun ExpressiveSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }

    val trackColor by animateColorAsState(
        targetValue = if (checked) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        animationSpec = softSpring(),
        label = "switchTrackColor",
    )

    val borderColor by animateColorAsState(
        targetValue = when {
            checked -> Color.Transparent
            !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
            else -> MaterialTheme.colorScheme.outline
        },
        animationSpec = softSpring(),
        label = "switchBorderColor",
    )

    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 24.dp else 4.dp,
        animationSpec = pressSpring(),
        label = "switchThumbOffset",
    )

    val checkedFraction by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = pressSpring(),
        label = "switchCheckedFraction",
    )

    val thumbColor by animateColorAsState(
        targetValue = when {
            !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            checked -> MaterialTheme.colorScheme.onPrimary
            else -> MaterialTheme.colorScheme.outline
        },
        animationSpec = softSpring(),
        label = "switchThumbColor",
    )

    val checkColor = if (enabled) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val closeColor = MaterialTheme.colorScheme.surface

    val toggleableModifier = if (onCheckedChange != null) {
        Modifier.toggleable(
            value = checked,
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            role = Role.Switch,
            onValueChange = onCheckedChange,
        )
    } else {
        Modifier
    }

    // The outer box keeps a 48dp tall touch target while the track stays 32dp.
    Box(
        modifier = modifier
            .size(TrackWidth, 48.dp)
            .then(toggleableModifier),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(TrackWidth, TrackHeight)
                .clip(RoundedCornerShape(50))
                .background(trackColor)
                .border(
                    width = if (checked) 0.dp else 2.dp,
                    color = borderColor,
                    shape = RoundedCornerShape(50),
                ),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                modifier = Modifier
                    .offset { IntOffset(thumbOffset.roundToPx(), 0) }
                    .size(ThumbSize),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(modifier = Modifier.size(ThumbSize)) {
                    drawCircle(
                        color = thumbColor,
                        radius = size.minDimension / 2f,
                    )

                    val w = size.width
                    val h = size.height

                    if (checkedFraction > 0.05f) {
                        val strokeWidth = 2.dp.toPx()
                        val checkPath = Path().apply {
                            moveTo(w * 0.28f, h * 0.52f)
                            lineTo(w * 0.44f, h * 0.68f)
                            lineTo(w * 0.72f, h * 0.36f)
                        }
                        drawPath(
                            path = checkPath,
                            color = checkColor.copy(alpha = checkedFraction),
                            style = Stroke(
                                width = strokeWidth,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round,
                            ),
                        )
                    }

                    if (checkedFraction < 0.95f) {
                        val strokeWidth = 2.dp.toPx()
                        val crossPath = Path().apply {
                            moveTo(w * 0.34f, h * 0.34f)
                            lineTo(w * 0.66f, h * 0.66f)
                            moveTo(w * 0.66f, h * 0.34f)
                            lineTo(w * 0.34f, h * 0.66f)
                        }
                        drawPath(
                            path = crossPath,
                            color = closeColor.copy(alpha = 1f - checkedFraction),
                            style = Stroke(
                                width = strokeWidth,
                                cap = StrokeCap.Round,
                            ),
                        )
                    }
                }
            }
        }
    }
}
