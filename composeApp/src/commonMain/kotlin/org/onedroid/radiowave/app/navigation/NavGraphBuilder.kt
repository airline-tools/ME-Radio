package org.onedroid.radiowave.app.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import org.onedroid.radiowave.presentation.home.HomeScreen
import org.onedroid.radiowave.presentation.settings.SettingScreen
import org.onedroid.radiowave.presentation.settings.SettingViewModel
import org.onedroid.radiowave.presentation.news.NewsMatchesScreen

fun NavGraphBuilder.navGraphBuilder(
    rootNavController: NavController,
    settingViewModel: SettingViewModel
) {
    composable<Route.Home> {
        HomeScreen(
            rootNavController = rootNavController
        )
    }
    composable<Route.NewsMatches> { backStackEntry ->
        val route = backStackEntry.toRoute<Route.NewsMatches>()
        NewsMatchesScreen(rootNavController = rootNavController, initialTab = route.initialTab)
    }
    composable<Route.Settings> {
        SettingScreen(
            viewModel = settingViewModel,
            rootNavController = rootNavController
        )
    }
}