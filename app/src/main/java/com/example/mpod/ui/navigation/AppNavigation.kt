package com.example.mpod.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.dialog
import androidx.navigation.compose.rememberNavController
import androidx.compose.ui.window.DialogProperties
import com.example.mpod.ui.components.MpodBottomNav
import com.example.mpod.ui.screens.home.HomeScreen
import com.example.mpod.ui.screens.settings.SettingsScreen
import com.example.mpod.ui.screens.subscriptions.SubscriptionsScreen

@Composable
fun AppNavigation(
    launchViewModel: AppLaunchViewModel = hiltViewModel()
) {
    val launchState by launchViewModel.state.collectAsState()
    val authUiState by launchViewModel.authUiState.collectAsState()
    val startDestination = when (launchState) {
        AppLaunchState.Loading -> null
        AppLaunchState.SetupRequired -> Screen.Setup.route
        AppLaunchState.Unauthenticated -> Screen.Login.route
        AppLaunchState.Authenticated -> Screen.Home.route
    }

    if (startDestination == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        )
        return
    }

    key(startDestination) {
        val navController = rememberNavController()
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route
        val showBottomNav = currentRoute in setOf(Screen.Home.route, Screen.Subscriptions.route, Screen.Settings.route)

        Column(modifier = Modifier.fillMaxSize()) {
            NavHost(
                navController = navController,
                startDestination = startDestination,
                modifier = Modifier.weight(1f)
            ) {
                composable(Screen.Setup.route) {
                    com.example.mpod.ui.screens.auth.SetupScreen(
                        isSubmitting = authUiState.isSubmitting,
                        errorMessage = authUiState.errorMessage,
                        onSubmit = launchViewModel::register
                    )
                }
                composable(Screen.Login.route) {
                    com.example.mpod.ui.screens.auth.LoginScreen(
                        isSubmitting = authUiState.isSubmitting,
                        errorMessage = authUiState.errorMessage,
                        onSubmit = launchViewModel::login
                    )
                }
                composable(Screen.Home.route) { HomeScreen() }
                composable(Screen.Subscriptions.route) { SubscriptionsScreen() }
                composable(Screen.Settings.route) { SettingsScreen() }
                dialog(
                    route = Screen.AddPodcast.route,
                    dialogProperties = DialogProperties(usePlatformDefaultWidth = false)
                ) {
                    com.example.mpod.ui.components.AddPodcastModal(
                        onDismiss = { navController.popBackStack() },
                        onAddUrl = { /* TODO */ navController.popBackStack() },
                        onImportOpml = { /* TODO */ navController.popBackStack() }
                    )
                }
            }
            if (showBottomNav) {
                MpodBottomNav(
                    currentRoute = currentRoute,
                    onNavigate = { screen ->
                        if (screen == Screen.AddPodcast) {
                            navController.navigate(screen.route)
                        } else {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    },
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp)
                )
            }
        }
    }
}
