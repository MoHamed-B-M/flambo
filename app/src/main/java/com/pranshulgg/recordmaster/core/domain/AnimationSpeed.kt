package com.pranshulgg.recordmaster.core.domain

/**
 * User-facing speed for Flambo's motion. A multiplier above 1 makes every
 * spring and transition finish sooner; below 1 gives it more room.
 *
 * The labels match the playback-speed voice already used in Settings.
 */
enum class AnimationSpeed(
    val label: String,
    val multiplier: Float,
) {
    SLOWER("0.5x", 0.5f),
    RELAXED("0.75x", 0.75f),
    NORMAL("1x", 1f),
    QUICK("1.25x", 1.25f),
    FAST("1.5x", 1.5f),
    FASTEST("2x", 2f);

    companion object {
        val Default: AnimationSpeed = NORMAL

        fun fromMultiplier(value: Float): AnimationSpeed =
            entries.minByOrNull { kotlin.math.abs(it.multiplier - value) } ?: Default
    }
}
