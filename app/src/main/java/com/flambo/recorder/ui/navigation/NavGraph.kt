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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
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
private val expressiveSpringOffset = spring<IntOffset>(
    dampingRatio = 0.9f,
    stiffness = 380f
)

private val fastFadeSpring = spring<Float>(dampingRatio = 1f, stiffness = 600f)

private fun AnimatedContentTransitionScope<NavBackStackEntry>.expressiveEnter() =
    slideInHorizontally(
        initialOffsetX = { it / 5 },
        animationSpec = expressiveSpringOffset
    ) + fadeIn(animationSpec = spring(dampingRatio = 0.8f)) + scaleIn(
        initialScale = 0.96f,
        animationSpec = expressiveSpring
    )

private fun AnimatedContentTransitionScope<NavBackStackEntry>.expressiveExit() =
    slideOutHorizontally(
        targetOffsetX = { -it / 6 },
        animationSpec = expressiveSpringOffset
    ) + fadeOut(animationSpec = fastFadeSpring) + scaleOut(
        targetScale = 0.98f,
        animationSpec = fastFadeSpring
    )

private fun AnimatedContentTransitionScope<NavBackStackEntry>.expressivePopEnter() =
    slideInHorizontally(
        initialOffsetX = { -it / 5 },
        animationSpec = expressiveSpringOffset
    ) + fadeIn(animationSpec = fastFadeSpring) + scaleIn(
        initialScale = 0.98f,
        animationSpec = fastFadeSpring
    )

private fun AnimatedContentTransitionScope<NavBackStackEntry>.expressivePopExit() =
    slideOutHorizontally(
        targetOffsetX = { it / 4 },
        animationSpec = expressiveSpringOffset
    ) + fadeOut(animationSpec = fastFadeSpring) + scaleOut(
        targetScale = 0.96f,
        animationSpec = fastFadeSpring
    )

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

    SharedTransitionLayout {
        // The layout's scope, handed down explicitly (no CompositionLocal for
        // it on all supported library versions).
        val sharedScope = this
        NavHost(
            navController = navController,
            startDestination = Dest.Home.route,

            enterTransition = { expressiveEnter() },
            exitTransition = { expressiveExit() },
            popEnterTransition = { expressivePopEnter() },
            popExitTransition = { expressivePopExit() }
        ) {
        composable(
            route = Dest.Home.route,
            // Returning from detail under the morph: a spring fade/scale keeps
            // the content transition alive so the minimizing card can ride it
            // back into place. None freezes progress and the morph snaps.
            enterTransition = {
                if (app.cardExpandAnim && initialState.destination.route == Dest.Detail.route) fadeIn(animationSpec = fastFadeSpring) + scaleIn(initialScale = 0.98f, animationSpec = fastFadeSpring)
                else expressivePopEnter()
            },
            // When the card morph is active it rides the route transition: home
            // gently fades and settles on a spring while the card takes over.
            // None would freeze transition progress and snap the morph.
            exitTransition = {
                if (app.cardExpandAnim && targetState.destination.route == Dest.Detail.route) fadeOut(animationSpec = fastFadeSpring)
                else expressiveExit()
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
            enterTransition = { if (app.cardExpandAnim) fadeIn(animationSpec = fastFadeSpring) + scaleIn(initialScale = 0.94f, animationSpec = expressiveSpring) else slideInHorizontally(initialOffsetX = { it }) + fadeIn() },
            exitTransition = { if (app.cardExpandAnim) fadeOut(animationSpec = fastFadeSpring) + scaleOut(targetScale = 0.96f, animationSpec = fastFadeSpring) else slideOutHorizontally(targetOffsetX = { -it / 3 }) + fadeOut() },
            popEnterTransition = { if (app.cardExpandAnim) fadeIn(animationSpec = fastFadeSpring) + scaleIn(initialScale = 0.94f, animationSpec = expressiveSpring) else slideInHorizontally(initialOffsetX = { -it / 3 }) + fadeIn() },
            popExitTransition = { if (app.cardExpandAnim) fadeOut(animationSpec = fastFadeSpring) + scaleOut(targetScale = 0.96f, animationSpec = fastFadeSpring) else slideOutHorizontally(targetOffsetX = { it }) + fadeOut() }
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
            route = Dest.Settings.route,
            enterTransition = { slideInHorizontally(initialOffsetX = { it }) + fadeIn() },
            exitTransition = { slideOutHorizontally(targetOffsetX = { -it / 3 }) + fadeOut() },
            popEnterTransition = { slideInHorizontally(initialOffsetX = { -it / 3 }) + fadeIn() },
            popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut() }
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
            enterTransition = { slideInHorizontally(initialOffsetX = { it }) + fadeIn() },
            exitTransition = { slideOutHorizontally(targetOffsetX = { -it / 3 }) + fadeOut() },
            popEnterTransition = { slideInHorizontally(initialOffsetX = { -it / 3 }) + fadeIn() },
            popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut() }
        ) {
            RecordingSettingsScreen(prefs = app.prefs, scope = scope, onBack = { debouncedPop() })
        }

        composable(
            route = Dest.SettingsAppearance.route,
            enterTransition = { slideInHorizontally(initialOffsetX = { it }) + fadeIn() },
            exitTransition = { slideOutHorizontally(targetOffsetX = { -it / 3 }) + fadeOut() },
            popEnterTransition = { slideInHorizontally(initialOffsetX = { -it / 3 }) + fadeIn() },
            popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut() }
        ) {
            AppearanceSettingsScreen(prefs = app.prefs, scope = scope, onBack = { debouncedPop() })
        }

        composable(
            route = Dest.SettingsStt.route,
            enterTransition = { slideInHorizontally(initialOffsetX = { it }) + fadeIn() },
            exitTransition = { slideOutHorizontally(targetOffsetX = { -it / 3 }) + fadeOut() },
            popEnterTransition = { slideInHorizontally(initialOffsetX = { -it / 3 }) + fadeIn() },
            popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut() }
        ) {
            SttSettingsScreen(prefs = app.prefs, scope = scope, transcription = app.transcription, onBack = { debouncedPop() })
        }

        composable(
            route = Dest.SettingsStorage.route,
            enterTransition = { slideInHorizontally(initialOffsetX = { it }) + fadeIn() },
            exitTransition = { slideOutHorizontally(targetOffsetX = { -it / 3 }) + fadeOut() },
            popEnterTransition = { slideInHorizontally(initialOffsetX = { -it / 3 }) + fadeIn() },
            popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut() }
        ) {
            StorageSettingsScreen(prefs = app.prefs, scope = scope, onBack = { debouncedPop() })
        }

        composable(
            route = Dest.SettingsUpdates.route,
            enterTransition = { slideInHorizontally(initialOffsetX = { it }) + fadeIn() },
            exitTransition = { slideOutHorizontally(targetOffsetX = { -it / 3 }) + fadeOut() },
            popEnterTransition = { slideInHorizontally(initialOffsetX = { -it / 3 }) + fadeIn() },
            popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut() }
        ) {
            UpdatesSettingsScreen(prefs = app.prefs, scope = scope, updateDownload = updateDownload, onBack = { debouncedPop() })
        }

        composable(
            route = Dest.SettingsAbout.route,
            enterTransition = { slideInHorizontally(initialOffsetX = { it }) + fadeIn() },
            exitTransition = { slideOutHorizontally(targetOffsetX = { -it / 3 }) + fadeOut() },
            popEnterTransition = { slideInHorizontally(initialOffsetX = { -it / 3 }) + fadeIn() },
            popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut() }
        ) {
            AboutSettingsScreen(prefs = app.prefs, onBack = { debouncedPop() }, onRerunOnboarding = onRerunOnboarding)
        }
        }
    }
}
