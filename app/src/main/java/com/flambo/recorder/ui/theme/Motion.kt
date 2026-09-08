package com.flambo.recorder.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween

// MD3 motion tokens — legacy easing for transitions, spring for expressive morphs.
// Compose expressive spring is supported via MotionScheme in newer BOMs, but we expose
// these easing tokens for consistent transitions across components.

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
}
