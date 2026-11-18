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
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import kotlin.math.roundToInt

// Adapted from material-app/core/designsystem/ExpressiveMotion.kt so Flambo can
// share one set of spring tokens and honour the system reduced-motion setting.

/**
 * True when the user has asked Android to remove animations ("Remove animations"
 * in accessibility settings, or animator duration scale set to 0), or when the
 * in-app Accessibility switch asks for reduced motion.
 */
val LocalReducedMotion = staticCompositionLocalOf { false }

/**
 * Multiplier for every app animation. 1 is Flambo's authored timing, values
 * above 1 finish sooner, values below 1 give the motion more room.
 */
val LocalMotionSpeed = staticCompositionLocalOf { 1f }

object ExpressiveMotion {
    const val PressedScale = 0.94f
    const val RestingScale = 1f
    const val HoldDurationMillis = 1_500L
    const val LoadingDurationMillis = 1_200L
    const val TapDurationMillis = 100L

    const val MinSpeed = 0.5f
    const val MaxSpeed = 2f

    /**
     * Material's softest shipped stiffness. Anything below it stops reading as
     * a spring and starts reading as jelly, so the speed multiplier is not
     * allowed to go lower.
     */
    const val MinSpringStiffness = Spring.StiffnessLow

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

    /**
     * Spring duration scales with 1/sqrt(stiffness), so squaring the speed
     * multiplier keeps the curve's character while changing how long it takes.
     * Floored at [MinSpringStiffness]: 0.5x on an already-soft spring would
     * otherwise turn a quick press into a slow wobble rather than a slower one.
     */
    fun scaledStiffness(stiffness: Float, speed: Float): Float =
        (stiffness * speed * speed).coerceAtLeast(MinSpringStiffness)

    fun scaledDuration(durationMillis: Long, speed: Float): Long =
        (durationMillis / speed.coerceAtLeast(0.01f)).toLong().coerceAtLeast(1L)

    fun speed(value: Float): Float = value.coerceIn(MinSpeed, MaxSpeed)

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    fun motionScheme(reducedMotion: Boolean, speed: Float = 1f): MotionScheme =
        when {
            reducedMotion -> ReducedMotionScheme
            speed == 1f -> MotionScheme.expressive()
            else -> ScaledMotionScheme(MotionScheme.expressive(), speed)
        }
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

/**
 * Wraps the Material Expressive scheme so the in-app speed choice reaches
 * component transitions as well as Flambo's own springs.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private class ScaledMotionScheme(
    private val base: MotionScheme,
    private val speed: Float,
) : MotionScheme {
    override fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> =
        base.defaultSpatialSpec<T>().scaledBy(speed)

    override fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> =
        base.fastSpatialSpec<T>().scaledBy(speed)

    override fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> =
        base.slowSpatialSpec<T>().scaledBy(speed)

    override fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> =
        base.defaultEffectsSpec<T>().scaledBy(speed)

    override fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> =
        base.fastEffectsSpec<T>().scaledBy(speed)

    override fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> =
        base.slowEffectsSpec<T>().scaledBy(speed)
}

private fun <T> FiniteAnimationSpec<T>.scaledBy(speed: Float): FiniteAnimationSpec<T> =
    when (this) {
        is SpringSpec<T> -> spring(
            dampingRatio = dampingRatio,
            stiffness = ExpressiveMotion.scaledStiffness(stiffness, speed),
            visibilityThreshold = visibilityThreshold,
        )

        is TweenSpec<T> -> tween(
            durationMillis = (durationMillis / speed).roundToInt().coerceAtLeast(1),
            delayMillis = delay,
            easing = easing,
        )

        else -> this
    }

@Composable
fun <T> pressSpring(): FiniteAnimationSpec<T> =
    if (LocalReducedMotion.current) snap() else spring(
        dampingRatio = ExpressiveMotion.PressDampingRatio,
        stiffness = ExpressiveMotion.scaledStiffness(
            ExpressiveMotion.PressStiffness,
            LocalMotionSpeed.current,
        ),
    )

@Composable
fun <T> expressiveSpring(): FiniteAnimationSpec<T> =
    if (LocalReducedMotion.current) snap() else spring(
        dampingRatio = ExpressiveMotion.ExpressiveDampingRatio,
        stiffness = ExpressiveMotion.scaledStiffness(
            ExpressiveMotion.ExpressiveStiffness,
            LocalMotionSpeed.current,
        ),
    )

@Composable
fun <T> standardSpring(): FiniteAnimationSpec<T> =
    if (LocalReducedMotion.current) snap() else spring(
        dampingRatio = ExpressiveMotion.StandardDampingRatio,
        stiffness = ExpressiveMotion.scaledStiffness(
            ExpressiveMotion.StandardStiffness,
            LocalMotionSpeed.current,
        ),
    )

@Composable
fun <T> softSpring(): FiniteAnimationSpec<T> =
    if (LocalReducedMotion.current) snap() else spring(
        dampingRatio = ExpressiveMotion.StandardDampingRatio,
        stiffness = ExpressiveMotion.scaledStiffness(
            ExpressiveMotion.ExpressiveStiffness,
            LocalMotionSpeed.current,
        ),
    )

@Composable
fun <T> progressSpring(): FiniteAnimationSpec<T> =
    if (LocalReducedMotion.current) snap() else spring(
        dampingRatio = ExpressiveMotion.StandardDampingRatio,
        stiffness = ExpressiveMotion.scaledStiffness(
            ExpressiveMotion.ProgressStiffness,
            LocalMotionSpeed.current,
        ),
    )

@Composable
fun <T> shakeSpring(): FiniteAnimationSpec<T> =
    if (LocalReducedMotion.current) snap() else spring(
        stiffness = ExpressiveMotion.scaledStiffness(
            ExpressiveMotion.ShakeStiffness,
            LocalMotionSpeed.current,
        ),
    )

/** How long a tap's press-and-release feedback stays on screen. */
@Composable
fun tapFeedbackDurationMillis(): Long =
    ExpressiveMotion.scaledDuration(
        ExpressiveMotion.TapDurationMillis,
        LocalMotionSpeed.current,
    )

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
