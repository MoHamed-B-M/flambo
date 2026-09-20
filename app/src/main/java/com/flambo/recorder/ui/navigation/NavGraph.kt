package com.flambo.recorder.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
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
import com.flambo.recorder.ui.settings.SettingsScreen
import com.flambo.recorder.update.UpdateDownloadState

sealed class Dest(val route: String) {
    data object Home : Dest("home")
    data object Settings : Dest("settings")
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
            kotlinx.coroutines.delay(450)
            isNavigating.value = false
        }
    }
    fun debouncedPop(): Boolean {
        if (isNavigating.value) return false
        isNavigating.value = true
        val popped = navController.popBackStack()
        scope.launch {
            kotlinx.coroutines.delay(450)
            isNavigating.value = false
        }
        return popped
    }

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
            enterTransition = { expressivePopEnter() },
            exitTransition = { expressiveExit() }
        ) {
            val factory = remember {
                object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        return HomeViewModel(app.repository, app.recorder) as T
                    }
                }
            }
            val vm: HomeViewModel = viewModel(factory = factory)
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
                onRequestMicPermission = onRequestMicPermission
            )
        }

        composable(
            route = Dest.Detail.route,
            arguments = listOf(navArgument("id") { type = NavType.LongType }),
            enterTransition = { expressiveEnter() },
            exitTransition = { expressiveExit() },
            popEnterTransition = { expressivePopEnter() },
            popExitTransition = { expressivePopExit() }
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getLong("id") ?: return@composable
            val factory = remember(id) {
                object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        return DetailViewModel(app.repository, id, app.transcription, app.prefs) as T
                    }
                }
            }
            val vm: DetailViewModel = viewModel(factory = factory)
            DetailScreen(
                viewModel = vm,
                playback = playback,
                onBack = { debouncedPop() },
                onDeleted = { debouncedPop() }
            )
        }

        composable(
            route = Dest.Settings.route,

            enterTransition = {
                slideInHorizontally(initialOffsetX = { it / 3 }, animationSpec = expressiveSpringOffset) +
                    fadeIn(spring(dampingRatio = 0.8f)) + scaleIn(initialScale = 0.97f, animationSpec = expressiveSpring)
            },
            exitTransition = {
                slideOutHorizontally(targetOffsetX = { it / 4 }, animationSpec = expressiveSpringOffset) +
                    fadeOut(spring(dampingRatio = 0.9f))
            },
            popEnterTransition = { expressivePopEnter() },
            popExitTransition = {
                slideOutHorizontally(targetOffsetX = { it / 3 }, animationSpec = expressiveSpringOffset) +
                    fadeOut(spring(dampingRatio = 0.9f)) + scaleOut(targetScale = 0.97f)
            }
        ) {
            SettingsScreen(
                prefs = app.prefs,
                scope = scope,
                transcription = app.transcription,
                updateDownload = updateDownload,
                onBack = { debouncedPop() },
                onRerunOnboarding = onRerunOnboarding,
                onRequestSystemCapture = onRequestSystemCapture
            )
        }
    }
}
