package com.flambo.recorder.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.flambo.recorder.domain.RecordingQuality
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.flambo.recorder.FlamboApp
import com.flambo.recorder.playback.PlaybackController
import com.flambo.recorder.record.AudioSource
import com.flambo.recorder.ui.detail.DetailScreen
import com.flambo.recorder.ui.detail.DetailViewModel
import com.flambo.recorder.ui.home.HomeScreen
import com.flambo.recorder.ui.home.HomeViewModel
import com.flambo.recorder.ui.settings.AboutSettingsScreen
import com.flambo.recorder.ui.settings.AppearanceSettingsScreen
import com.flambo.recorder.ui.settings.RecordingSettingsScreen
import com.flambo.recorder.ui.settings.SettingsScreen
import com.flambo.recorder.ui.settings.StorageSettingsScreen
import com.flambo.recorder.ui.settings.SttSettingsScreen
import com.flambo.recorder.ui.settings.UpdatesSettingsScreen
import com.flambo.recorder.update.UpdateDownloadState

sealed class Dest(val route: String) {
    data object Home : Dest("home")
    data object Settings : Dest("settings")
    data object SettingsRecording : Dest("settings/recording")
    data object SettingsAppearance : Dest("settings/appearance")
    data object SettingsStt : Dest("settings/stt")
    data object SettingsStorage : Dest("settings/storage")
    data object SettingsUpdates : Dest("settings/updates")
    data object SettingsAbout : Dest("settings/about")
    data object Detail : Dest("detail/{id}") {
        fun create(id: Long) = "detail/$id"
    }
}

private val expressiveSpring = spring<Float>(
    dampingRatio = 0.85f,
    stiffness = 400f
)
private val fastFadeSpring = spring<Float>(dampingRatio = 1f, stiffness = 600f)

// Standard screen transitions (Material predictive-back style): partial
// slides with 300ms fades, no springs. Home/Detail keep their morph-aware
// overrides below; everything else inherits these from the NavHost.
private fun AnimatedContentTransitionScope<NavBackStackEntry>.screenEnter() =
    slideInHorizontally(
        initialOffsetX = { it / 4 },
        animationSpec = tween(300)
    )

private fun AnimatedContentTransitionScope<NavBackStackEntry>.screenExit() =
    slideOutHorizontally(
        targetOffsetX = { -it / 4 },
        animationSpec = tween(300)
    )

private fun AnimatedContentTransitionScope<NavBackStackEntry>.screenPopEnter() =
    slideInHorizontally(
        initialOffsetX = { -it / 4 },
        animationSpec = tween(300)
    )

private fun AnimatedContentTransitionScope<NavBackStackEntry>.screenPopExit() =
    slideOutHorizontally(
        targetOffsetX = { it / 4 },
        animationSpec = tween(300)
    )

// Sub-settings zoom: the standard slide/fade plus a subtle 0.95 scale
// morph, matching Android 14/15 back motion.
private fun AnimatedContentTransitionScope<NavBackStackEntry>.subSettingsEnter() =
    slideInHorizontally(
        initialOffsetX = { it / 4 },
        animationSpec = tween(300)
    ) + scaleIn(
        initialScale = 0.95f,
        animationSpec = tween(300)
    )

private fun AnimatedContentTransitionScope<NavBackStackEntry>.subSettingsExit() =
    slideOutHorizontally(
        targetOffsetX = { -it / 4 },
        animationSpec = tween(300)
    ) + scaleOut(
        targetScale = 0.95f,
        animationSpec = tween(300)
    )

private fun AnimatedContentTransitionScope<NavBackStackEntry>.subSettingsPopEnter() =
    slideInHorizontally(
        initialOffsetX = { -it / 4 },
        animationSpec = tween(300)
    ) + scaleIn(
        initialScale = 0.95f,
        animationSpec = tween(300)
    )

private fun AnimatedContentTransitionScope<NavBackStackEntry>.subSettingsPopExit() =
    slideOutHorizontally(
        targetOffsetX = { it / 4 },
        animationSpec = tween(300)
    ) + scaleOut(
        targetScale = 0.95f,
        animationSpec = tween(300)
    )

// Style-driven variants for the Appearance "Page transitions" choice.
// "slide" funnels back to the standard above; morph overrides elsewhere
// are untouched so the card animation keeps riding its own transition.
private fun AnimatedContentTransitionScope<NavBackStackEntry>.styledEnter(style: String) = when (style) {
    "fade" -> fadeIn(animationSpec = tween(300))
    "zoom-in" -> scaleIn(animationSpec = tween(300), initialScale = 0.92f)
    "zoom-out" -> scaleIn(animationSpec = tween(300), initialScale = 1.08f)
    else -> screenEnter()
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.styledExit(style: String) = when (style) {
    "fade" -> fadeOut(animationSpec = tween(300))
    "zoom-in" -> scaleOut(animationSpec = tween(300), targetScale = 0.92f)
    "zoom-out" -> scaleOut(animationSpec = tween(300), targetScale = 1.08f)
    else -> screenExit()
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.styledPopEnter(style: String) = when (style) {
    "fade" -> fadeIn(animationSpec = tween(300))
    "zoom-in" -> scaleIn(animationSpec = tween(300), initialScale = 0.92f)
    "zoom-out" -> scaleIn(animationSpec = tween(300), initialScale = 1.08f)
    else -> screenPopEnter()
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.styledPopExit(style: String) = when (style) {
    "fade" -> fadeOut(animationSpec = tween(300))
    "zoom-in" -> scaleOut(animationSpec = tween(300), targetScale = 0.92f)
    "zoom-out" -> scaleOut(animationSpec = tween(300), targetScale = 1.08f)
    else -> screenPopExit()
}

@Composable
fun FlamboNavGraph(
    onRerunOnboarding: () -> Unit = {},
    onRequestSystemCapture: () -> Unit = {},
    onEnableSystemSound: () -> Unit = {},
    onRequestMicPermission: (AudioSource) -> Unit = {}
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val app = context.applicationContext as FlamboApp
    val scope = rememberCoroutineScope()

    val playback = remember { PlaybackController(context) }
    // Pause playback when the app backgrounds so audio never keeps playing
    // behind the launcher and no stray gesture scrubs land mid-minimize.
    // Skipped on rotation (config change recreates without stopping).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, playback) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                val activity = context as? android.app.Activity
                if (activity?.isChangingConfigurations != true) {
                    playback.pause()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    // Stop any playing audio the moment a recording starts — on every start
    // path (UI, shortcuts, automation). Posted to Main: start() may run on a
    // background thread, and player calls belong on Main.
    app.recorder.onRecordingStarted = {
        android.os.Handler(android.os.Looper.getMainLooper()).post { playback.stop() }
    }
    val quality by app.prefs.qualityFlow.collectAsState(initial = RecordingQuality.HIGH)
    val audioSource by app.prefs.audioSourceFlow.collectAsState(initial = "mic")
    val noiseReduction by app.prefs.noiseReductionFlow.collectAsState(initial = true)
    val homeLayout by app.prefs.homeLayoutFlow.collectAsState(initial = "list")

    val updateDownload = remember { UpdateDownloadState() }

    val isNavigating = remember { androidx.compose.runtime.mutableStateOf(false) }
    fun debouncedNavigate(route: String) {
        if (isNavigating.value) return
        isNavigating.value = true
        navController.navigate(route) { launchSingleTop = true }
        scope.launch {
            delay(180)
            isNavigating.value = false
        }
    }
    fun debouncedPop(): Boolean {
        if (isNavigating.value) return false
        isNavigating.value = true
        val popped = navController.popBackStack()
        scope.launch {
            delay(180)
            isNavigating.value = false
        }
        return popped
    }

    val cardExpandAnim by app.prefs.cardExpandAnimFlow.collectAsState(initial = true)
    val transitionStyle by app.prefs.transitionStyleFlow.collectAsState(initial = "slide")

    SharedTransitionLayout {
        // The layout's scope, handed down explicitly (no CompositionLocal for
        // it on all supported library versions).
        val sharedScope = this
        NavHost(
            navController = navController,
            startDestination = Dest.Home.route,

            enterTransition = { styledEnter(transitionStyle) },
            exitTransition = { styledExit(transitionStyle) },
            popEnterTransition = { styledPopEnter(transitionStyle) },
            popExitTransition = { styledPopExit(transitionStyle) }
        ) {
        composable(
            route = Dest.Home.route,
            // Returning from detail under the morph: a spring fade/scale keeps
            // the content transition alive so the minimizing card can ride it
            // back into place. None freezes progress and the morph snaps.
            enterTransition = {
                if (app.cardExpandAnim && initialState.destination.route == Dest.Detail.route) fadeIn(animationSpec = fastFadeSpring) + scaleIn(initialScale = 0.98f, animationSpec = fastFadeSpring)
                else styledPopEnter(transitionStyle)
            },
            // When the card morph is active it rides the route transition: home
            // gently fades and settles on a spring while the card takes over.
            // None would freeze transition progress and snap the morph.
            exitTransition = {
                if (app.cardExpandAnim && targetState.destination.route == Dest.Detail.route) fadeOut(animationSpec = fastFadeSpring)
                else styledExit(transitionStyle)
            }
        ) {
            val animScope = this
            val factory = remember {
                object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        return HomeViewModel(app.repository, app.recorder) as T
                    }
                }
            }
            val activity = LocalContext.current as androidx.activity.ComponentActivity
            val vm: HomeViewModel = viewModel(factory = factory, viewModelStoreOwner = activity)
            HomeScreen(
                viewModel = vm,
                recorder = app.recorder,
                playback = playback,
                prefs = app.prefs,
                quality = quality,
                audioSource = audioSource,
                noiseReduction = noiseReduction,
                homeLayout = homeLayout,
                onOpenDetail = { id -> debouncedNavigate(Dest.Detail.create(id)) },
                onOpenSettings = { debouncedNavigate(Dest.Settings.route) },
                onOpenUpdates = { debouncedNavigate(Dest.SettingsUpdates.route) },
                onEnableSystemSound = onEnableSystemSound,
                onRequestMicPermission = onRequestMicPermission,
                sharedTransitionScope = sharedScope,
                animatedVisibilityScope = animScope,
                cardExpandAnimEnabled = cardExpandAnim
            )
        }

        composable(
            route = Dest.Detail.route,
            arguments = listOf(navArgument("id") { type = NavType.LongType }),
            // When the card morph is active it rides these spring transitions:
            // the page calmly fades and settles (transform + alpha only,
            // GPU-cheap) while the slow bounds morph carries the motion.
            // None freezes progress and the card would just snap open.
            // Otherwise classic slides.
            enterTransition = { if (app.cardExpandAnim) fadeIn(animationSpec = fastFadeSpring) + scaleIn(initialScale = 0.94f, animationSpec = expressiveSpring) else styledEnter(transitionStyle) },
            exitTransition = { if (app.cardExpandAnim) fadeOut(animationSpec = fastFadeSpring) + scaleOut(targetScale = 0.96f, animationSpec = fastFadeSpring) else styledExit(transitionStyle) },
            popEnterTransition = { if (app.cardExpandAnim) fadeIn(animationSpec = fastFadeSpring) + scaleIn(initialScale = 0.94f, animationSpec = expressiveSpring) else styledPopEnter(transitionStyle) },
            popExitTransition = { if (app.cardExpandAnim) fadeOut(animationSpec = fastFadeSpring) + scaleOut(targetScale = 0.96f, animationSpec = fastFadeSpring) else styledPopExit(transitionStyle) }
        ) { backStackEntry ->
            val animScope = this
            val id = backStackEntry.arguments?.getLong("id") ?: return@composable
            val factory = remember(id) {
                object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        return DetailViewModel(app.repository, id, app.transcription, app.prefs, app.applicationContext) as T
                    }
                }
            }
            val vm: DetailViewModel = viewModel(factory = factory)
            DetailScreen(
                viewModel = vm,
                playback = playback,
                onBack = { debouncedPop() },
                onDeleted = { debouncedPop() },
                sharedTransitionScope = sharedScope,
                animatedVisibilityScope = animScope,
                cardExpandAnimEnabled = cardExpandAnim
            )
        }

        composable(
            route = Dest.Settings.route
        ) {
            SettingsScreen(
                prefs = app.prefs,
                scope = scope,
                transcription = app.transcription,
                updateDownload = updateDownload,
                onBack = { debouncedPop() },
                onRerunOnboarding = onRerunOnboarding,
                onRequestSystemCapture = onRequestSystemCapture,
                onNavigateRecording = { debouncedNavigate(Dest.SettingsRecording.route) },
                onNavigateAppearance = { debouncedNavigate(Dest.SettingsAppearance.route) },
                onNavigateStt = { debouncedNavigate(Dest.SettingsStt.route) },
                onNavigateStorage = { debouncedNavigate(Dest.SettingsStorage.route) },
                onNavigateUpdates = { debouncedNavigate(Dest.SettingsUpdates.route) },
                onNavigateAbout = { debouncedNavigate(Dest.SettingsAbout.route) }
            )
        }

        composable(
            route = Dest.SettingsRecording.route,
            enterTransition = { if (transitionStyle == "slide") subSettingsEnter() else styledEnter(transitionStyle) },
            exitTransition = { if (transitionStyle == "slide") subSettingsExit() else styledExit(transitionStyle) },
            popEnterTransition = { if (transitionStyle == "slide") subSettingsPopEnter() else styledPopEnter(transitionStyle) },
            popExitTransition = { if (transitionStyle == "slide") subSettingsPopExit() else styledPopExit(transitionStyle) }
        ) {
            RecordingSettingsScreen(prefs = app.prefs, scope = scope, onBack = { debouncedPop() })
        }

        composable(
            route = Dest.SettingsAppearance.route,
            enterTransition = { if (transitionStyle == "slide") subSettingsEnter() else styledEnter(transitionStyle) },
            exitTransition = { if (transitionStyle == "slide") subSettingsExit() else styledExit(transitionStyle) },
            popEnterTransition = { if (transitionStyle == "slide") subSettingsPopEnter() else styledPopEnter(transitionStyle) },
            popExitTransition = { if (transitionStyle == "slide") subSettingsPopExit() else styledPopExit(transitionStyle) }
        ) {
            AppearanceSettingsScreen(prefs = app.prefs, scope = scope, onBack = { debouncedPop() })
        }

        composable(
            route = Dest.SettingsStt.route,
            enterTransition = { if (transitionStyle == "slide") subSettingsEnter() else styledEnter(transitionStyle) },
            exitTransition = { if (transitionStyle == "slide") subSettingsExit() else styledExit(transitionStyle) },
            popEnterTransition = { if (transitionStyle == "slide") subSettingsPopEnter() else styledPopEnter(transitionStyle) },
            popExitTransition = { if (transitionStyle == "slide") subSettingsPopExit() else styledPopExit(transitionStyle) }
        ) {
            SttSettingsScreen(prefs = app.prefs, scope = scope, transcription = app.transcription, onBack = { debouncedPop() })
        }

        composable(
            route = Dest.SettingsStorage.route,
            enterTransition = { if (transitionStyle == "slide") subSettingsEnter() else styledEnter(transitionStyle) },
            exitTransition = { if (transitionStyle == "slide") subSettingsExit() else styledExit(transitionStyle) },
            popEnterTransition = { if (transitionStyle == "slide") subSettingsPopEnter() else styledPopEnter(transitionStyle) },
            popExitTransition = { if (transitionStyle == "slide") subSettingsPopExit() else styledPopExit(transitionStyle) }
        ) {
            StorageSettingsScreen(prefs = app.prefs, scope = scope, onBack = { debouncedPop() })
        }

        composable(
            route = Dest.SettingsUpdates.route,
            enterTransition = { if (transitionStyle == "slide") subSettingsEnter() else styledEnter(transitionStyle) },
            exitTransition = { if (transitionStyle == "slide") subSettingsExit() else styledExit(transitionStyle) },
            popEnterTransition = { if (transitionStyle == "slide") subSettingsPopEnter() else styledPopEnter(transitionStyle) },
            popExitTransition = { if (transitionStyle == "slide") subSettingsPopExit() else styledPopExit(transitionStyle) }
        ) {
            UpdatesSettingsScreen(prefs = app.prefs, scope = scope, updateDownload = updateDownload, onBack = { debouncedPop() })
        }

        composable(
            route = Dest.SettingsAbout.route,
            enterTransition = { if (transitionStyle == "slide") subSettingsEnter() else styledEnter(transitionStyle) },
            exitTransition = { if (transitionStyle == "slide") subSettingsExit() else styledExit(transitionStyle) },
            popEnterTransition = { if (transitionStyle == "slide") subSettingsPopEnter() else styledPopEnter(transitionStyle) },
            popExitTransition = { if (transitionStyle == "slide") subSettingsPopExit() else styledPopExit(transitionStyle) }
        ) {
            AboutSettingsScreen(prefs = app.prefs, onBack = { debouncedPop() }, onRerunOnboarding = onRerunOnboarding)
        }
        }
    }
}
