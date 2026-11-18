package com.pranshulgg.recordmaster.ui.expressive

import android.animation.ValueAnimator
import android.content.Context
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.findViewTreeLifecycleOwner

// Adapted from material-app/core/designsystem/ExpressiveMotion.kt so Flambo can
// share one set of spring tokens and honour the system reduced-motion setting.

/**
 * True when the user has asked Android to remove animations ("Remove animations"
 * in accessibility settings, or animator duration scale set to 0).
 */
val LocalReducedMotion = staticCompositionLocalOf { false }

object ExpressiveMotion {
    const val PressedScale = 0.94f
    const val RestingScale = 1f
    const val HoldDurationMillis = 1_500L
    const val LoadingDurationMillis = 1_200L
    const val TapDurationMillis = 100L

    const val PressDampingRatio = Spring.DampingRatioMediumBouncy
    const val PressStiffness = Spring.StiffnessLow
    const val ExpressiveDampingRatio = Spring.DampingRatioLowBouncy
    const val ExpressiveStiffness = Spring.StiffnessLow
    const val StandardDampingRatio = Spring.DampingRatioNoBouncy
    const val StandardStiffness = Spring.StiffnessMedium
    const val ProgressStiffness = Spring.StiffnessHigh
    const val ShakeStiffness = 10_000f

    fun pressScale(pressed: Boolean, enabled: Boolean): Float =
        if (pressed && enabled) PressedScale else RestingScale

    fun holdProgress(elapsedMillis: Long, durationMillis: Long): Float =
        (elapsedMillis.coerceAtLeast(0L).toFloat() / durationMillis.coerceAtLeast(1L))
            .coerceIn(0f, 1f)

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    fun motionScheme(reducedMotion: Boolean): MotionScheme =
        if (reducedMotion) ReducedMotionScheme else MotionScheme.expressive()
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private object ReducedMotionScheme : MotionScheme {
    override fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> = snap()
}

@Composable
fun <T> pressSpring(): FiniteAnimationSpec<T> =
    if (LocalReducedMotion.current) snap() else spring(
        dampingRatio = ExpressiveMotion.PressDampingRatio,
        stiffness = ExpressiveMotion.PressStiffness,
    )

@Composable
fun <T> expressiveSpring(): FiniteAnimationSpec<T> =
    if (LocalReducedMotion.current) snap() else spring(
        dampingRatio = ExpressiveMotion.ExpressiveDampingRatio,
        stiffness = ExpressiveMotion.ExpressiveStiffness,
    )

@Composable
fun <T> standardSpring(): FiniteAnimationSpec<T> =
    if (LocalReducedMotion.current) snap() else spring(
        dampingRatio = ExpressiveMotion.StandardDampingRatio,
        stiffness = ExpressiveMotion.StandardStiffness,
    )

@Composable
fun <T> softSpring(): FiniteAnimationSpec<T> =
    if (LocalReducedMotion.current) snap() else spring(
        dampingRatio = ExpressiveMotion.StandardDampingRatio,
        stiffness = ExpressiveMotion.ExpressiveStiffness,
    )

@Composable
fun <T> progressSpring(): FiniteAnimationSpec<T> =
    if (LocalReducedMotion.current) snap() else spring(
        dampingRatio = ExpressiveMotion.StandardDampingRatio,
        stiffness = ExpressiveMotion.ProgressStiffness,
    )

@Composable
fun <T> shakeSpring(): FiniteAnimationSpec<T> =
    if (LocalReducedMotion.current) snap() else spring(stiffness = ExpressiveMotion.ShakeStiffness)

/**
 * Reads the system animator scale and keeps it live while the app is in the
 * foreground, so toggling "Remove animations" takes effect without a restart.
 * The scale is read directly because [ValueAnimator.areAnimatorsEnabled] can
 * lag behind the settings change on some OEM builds.
 */
@Composable
fun rememberSystemReducedMotion(): Boolean {
    val context = LocalContext.current
    val view = LocalView.current
    var reducedMotion by remember(context) { mutableStateOf(readReducedMotion(context)) }

    DisposableEffect(view) {
        val owner = view.findViewTreeLifecycleOwner()
        if (owner == null) {
            onDispose { }
        } else {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    reducedMotion = readReducedMotion(context)
                }
            }
            owner.lifecycle.addObserver(observer)
            onDispose { owner.lifecycle.removeObserver(observer) }
        }
    }

    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reducedMotion = readReducedMotion(context)
            }
        }
        runCatching {
            context.contentResolver.registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
                false,
                observer,
            )
        }
        onDispose {
            runCatching { context.contentResolver.unregisterContentObserver(observer) }
        }
    }

    return reducedMotion
}

private fun readReducedMotion(context: Context): Boolean {
    val scale = runCatching {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        )
    }.getOrDefault(1f)
    if (scale == 0f) return true
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        !ValueAnimator.areAnimatorsEnabled()
    } else {
        false
    }
}
