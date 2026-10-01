package com.flambo.recorder.ui.haptics

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.flambo.recorder.FlamboApp

/**
 * Lightweight press haptics. Intensity follows the Appearance slider
 * (0-100, 0 is off) and is deliberately capped well below max amplitude
 * so taps stay subtle. Level is mirrored on [FlamboApp] to avoid a
 * DataStore read on every tap.
 */
object AppHaptics {

    const val DEFAULT_LEVEL = 50

    /** Hard ceiling: firm but never full-strength. */
    private const val MAX_AMPLITUDE = 180
    private const val TAP_MS = 25L

    fun levelOf(context: Context): Int =
        (context.applicationContext as? FlamboApp)?.hapticLevel ?: DEFAULT_LEVEL

    fun labelFor(level: Int): String = when {
        level <= 0 -> "Off"
        level <= 33 -> "Light"
        level <= 66 -> "Medium"
        else -> "Strong"
    }

    fun tap(context: Context) {
        val level = levelOf(context)
        if (level <= 0) return
        val amplitude = ((level / 100f) * MAX_AMPLITUDE).toInt().coerceIn(1, MAX_AMPLITUDE)
        runCatching {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            if (!vibrator.hasVibrator()) return
            vibrator.vibrate(VibrationEffect.createOneShot(TAP_MS, amplitude))
        }
    }
}
