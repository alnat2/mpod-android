package com.example.mpod.ui.navigation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.dialog
import androidx.navigation.compose.rememberNavController
import com.example.mpod.ui.components.AddPodcastModal
import com.example.mpod.ui.components.AddPodcastMode
import com.example.mpod.ui.components.AddPodcastViewModel
import com.example.mpod.ui.components.MpodBottomNav
import com.example.mpod.ui.screens.home.HomeRoute
import com.example.mpod.ui.screens.settings.SettingsRoute
import com.example.mpod.ui.screens.subscriptions.SubscriptionsRoute
import com.example.mpod.ui.theme.ThemeMode

@Composable
fun AppNavigation(
    themeMode: ThemeMode = ThemeMode.System,
    onThemeModeChange: (ThemeMode) -> Unit = {}
) {
    val navController = rememberNavController()
    var libraryRefreshKey by remember { mutableIntStateOf(0) }
    val addPodcastViewModel: AddPodcastViewModel = hiltViewModel()
    val addPodcastState by addPodcastViewModel.state.collectAsState()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showBottomNav = currentRoute in setOf(Screen.Home.route, Screen.Subscriptions.route, Screen.Settings.route)

    Column(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = Screen.Subscriptions.route,
            modifier = Modifier.weight(1f),
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None }
        ) {
            composable(Screen.Home.route) {
                HomeRoute(
                    refreshKey = libraryRefreshKey,
                    onAddRssFeed = {
                        addPodcastViewModel.begin(AddPodcastMode.RssFeedUrl)
                        navController.navigate(Screen.AddPodcast.route)
                    },
                    onImportOpml = {
                        addPodcastViewModel.begin(AddPodcastMode.ImportOpmlFile)
                        navController.navigate(Screen.AddPodcast.route)
                    }
                )
            }
            composable(Screen.Subscriptions.route) {
                SubscriptionsRoute(
                    refreshKey = libraryRefreshKey,
                    onAddRssFeed = {
                        addPodcastViewModel.begin(AddPodcastMode.RssFeedUrl)
                        navController.navigate(Screen.AddPodcast.route)
                    },
                    onImportOpml = {
                        addPodcastViewModel.begin(AddPodcastMode.ImportOpmlFile)
                        navController.navigate(Screen.AddPodcast.route)
                    }
                )
            }
            composable(Screen.Settings.route) {
                SettingsRoute(
                    themeMode = themeMode,
                    onThemeModeChange = onThemeModeChange
                )
            }
            dialog(
                route = Screen.AddPodcast.route,
                dialogProperties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                val opmlPicker = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument()
                ) { uri ->
                    uri?.let { selectedUri ->
                        addPodcastViewModel.importOpml(selectedUri) {
                            libraryRefreshKey += 1
                        }
                    }
                }
                AddPodcastModal(
                    onDismiss = {
                        addPodcastViewModel.reset()
                        navController.popBackStack()
                    },
                    onAddUrl = { url ->
                        addPodcastViewModel.addRssFeed(url) {
                            libraryRefreshKey += 1
                            navController.popBackStack()
                        }
                    },
                    onImportOpml = {
                        opmlPicker.launch(arrayOf("text/xml", "application/xml", "text/x-opml", "*/*"))
                    },
                    initialMode = addPodcastState.mode,
                    isSubmitting = addPodcastState.isSubmitting,
                    errorMessage = addPodcastState.errorMessage,
                    importResult = addPodcastState.importResult,
                    controlledMode = addPodcastState.mode,
                    controlledUrl = addPodcastState.rssUrl,
                    onModeChange = addPodcastViewModel::setMode,
                    onUrlChange = addPodcastViewModel::setRssUrl
                )
            }
        }
        if (showBottomNav) {
            MpodBottomNav(
                currentRoute = currentRoute,
                onNavigate = { screen ->
                    if (screen == Screen.AddPodcast) {
                        addPodcastViewModel.begin(AddPodcastMode.RssFeedUrl)
                        navController.navigate(screen.route)
                    } else {
                        navController.navigate(screen.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
