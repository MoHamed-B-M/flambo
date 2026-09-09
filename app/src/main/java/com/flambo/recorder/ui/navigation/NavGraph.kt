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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
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
import com.flambo.recorder.ui.detail.DetailScreen
import com.flambo.recorder.ui.detail.DetailViewModel
import com.flambo.recorder.ui.home.HomeScreen
import com.flambo.recorder.ui.home.HomeViewModel
import com.flambo.recorder.ui.settings.SettingsScreen

sealed class Dest(val route: String) {
    data object Home : Dest("home")
    data object Settings : Dest("settings")
    data object Detail : Dest("detail/{id}") {
        fun create(id: Long) = "detail/$id"
    }
}

// Expressive bouncy spring — low damping for playful overshoot, medium stiffness
private val expressiveSpring = spring<Float>(
    dampingRatio = Spring.DampingRatioMediumBouncy, // 0.7f bouncy
    stiffness = Spring.StiffnessMediumLow // ~300f
)
private val expressiveSpringOffset = spring<IntOffset>(
    dampingRatio = Spring.DampingRatioMediumBouncy,
    stiffness = Spring.StiffnessMediumLow
)

// Slide + scale + fade — feels alive, not just sliding
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
    ) + fadeOut(animationSpec = spring(dampingRatio = 0.9f)) + scaleOut(
        targetScale = 0.98f,
        animationSpec = spring(dampingRatio = 0.9f)
    )

private fun AnimatedContentTransitionScope<NavBackStackEntry>.expressivePopEnter() =
    slideInHorizontally(
        initialOffsetX = { -it / 5 },
        animationSpec = expressiveSpringOffset
    ) + fadeIn(animationSpec = spring(dampingRatio = 0.8f)) + scaleIn(
        initialScale = 0.98f,
        animationSpec = expressiveSpring
    )

private fun AnimatedContentTransitionScope<NavBackStackEntry>.expressivePopExit() =
    slideOutHorizontally(
        targetOffsetX = { it / 4 },
        animationSpec = expressiveSpringOffset
    ) + fadeOut(animationSpec = spring(dampingRatio = 0.9f)) + scaleOut(
        targetScale = 0.96f,
        animationSpec = expressiveSpring
    )

@Composable
fun FlamboNavGraph() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val app = context.applicationContext as FlamboApp
    val scope = rememberCoroutineScope()

    val playback = remember { PlaybackController(context) }

    NavHost(
        navController = navController,
        startDestination = Dest.Home.route,
        // Default bouncy transitions for all destinations unless overridden per-composable
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
                onOpenDetail = { id -> navController.navigate(Dest.Detail.create(id)) },
                onOpenSettings = { navController.navigate(Dest.Settings.route) }
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
                        return DetailViewModel(app.repository, id) as T
                    }
                }
            }
            val vm: DetailViewModel = viewModel(factory = factory)
            DetailScreen(
                viewModel = vm,
                playback = playback,
                onBack = { navController.popBackStack() },
                onDeleted = { navController.popBackStack() }
            )
        }

        composable(
            route = Dest.Settings.route,
            // Settings slides up like a modal sheet — vertical expressive motion
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
                onBack = { navController.popBackStack() }
            )
        }
    }
}
