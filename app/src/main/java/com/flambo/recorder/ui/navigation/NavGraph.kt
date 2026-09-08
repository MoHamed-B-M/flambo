package com.flambo.recorder.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.viewmodel.compose.viewModel
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
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

sealed class Dest(val route: String) {
    data object Home : Dest("home")
    data object Settings : Dest("settings")
    data object Detail : Dest("detail/{id}") {
        fun create(id: Long) = "detail/$id"
    }
}

@Composable
fun FlamboNavGraph() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val app = context.applicationContext as FlamboApp
    val scope = rememberCoroutineScope()

    // singletons — keep one playback controller for mini-player continuity
    val playback = remember { PlaybackController(context) }

    NavHost(navController = navController, startDestination = Dest.Home.route) {
        composable(Dest.Home.route) {
            // create HomeViewModel with factory so it gets repo + controller
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
            arguments = listOf(navArgument("id") { type = NavType.LongType })
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

        composable(Dest.Settings.route) {
            SettingsScreen(
                prefs = app.prefs,
                scope = scope,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
