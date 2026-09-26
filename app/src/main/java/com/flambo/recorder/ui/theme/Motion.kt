package com.flambo.recorder.ui.theme

import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

object FlamboMotion {
    val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    val Standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val StandardDecelerate = CubicBezierEasing(0f, 0f, 0f, 1f)
    val StandardAccelerate = CubicBezierEasing(0.3f, 0f, 1f, 1f)

    const val DurationLong = 500
    const val DurationMedium = 400
    const val DurationShort = 200
    const val DurationQuick = 150

    fun emphasizedTween(duration: Int = DurationMedium) =
        tween<Float>(durationMillis = duration, easing = Emphasized)

    fun emphasizedDecelerateTween(duration: Int = DurationMedium) =
        tween<Float>(durationMillis = duration, easing = EmphasizedDecelerate)

    // Heavy mass: card shells, background surfaces, inline drawers.
    // High momentum, smooth deceleration, low-bounce landing.
    val ContainerSpatialSpringFloat = spring<Float>(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMediumLow
    )
    val ContainerSizeSpring = spring<IntSize>(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMediumLow
    )
    val ContainerOffsetSpring = spring<IntOffset>(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    // Medium mass: titles, metadata, waveforms. Tracks the container
    // without lag or over-oscillation.
    val ContentSpringFloat = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    // Light mass: play buttons, hearts, chips, action pills.
    // Rapid acceleration with elastic recoil on landing.
    val ActionSpringFloat = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium
    )

    // Item placement: list/grid reorder glides. Fades stay disabled at
    // call sites (fadeInSpec = null, fadeOutSpec = null) so cards travel
    // along trajectory vectors instead of crossfading.
    val PlacementSpring = spring<IntOffset>(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    // Shared-element bounds: BoundsTransform animates Rect, so these are
    // Rect springs with matching mass characters.
    val ContainerBoundsTransform = BoundsTransform { _, _ ->
        spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)
    }
    val ActionBoundsTransform = BoundsTransform { _, _ ->
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
    }
    val ContentBoundsTransform = BoundsTransform { _, _ ->
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
    }
}
