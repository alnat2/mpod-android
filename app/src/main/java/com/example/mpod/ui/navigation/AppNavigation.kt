package com.example.mpod.ui.navigation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.Alignment
import com.example.mpod.ui.components.AddPodcastModal
import com.example.mpod.ui.components.AddPodcastMode
import com.example.mpod.ui.components.AddPodcastViewModel
import com.example.mpod.ui.components.MpodBottomNav
import com.example.mpod.ui.components.MpodLogo
import com.example.mpod.ui.screens.home.HomeRoute
import com.example.mpod.ui.screens.auth.BackendUnavailableScreen
import com.example.mpod.ui.screens.settings.SettingsRoute
import com.example.mpod.ui.screens.subscriptions.SubscriptionsRoute
import com.example.mpod.ui.theme.ThemeMode

@Composable
fun AppNavigation(
    themeMode: ThemeMode = ThemeMode.System,
    onThemeModeChange: (ThemeMode) -> Unit = {},
    launchViewModel: AppLaunchViewModel = hiltViewModel()
) {
    val launchState by launchViewModel.state.collectAsState()
    val authUiState by launchViewModel.authUiState.collectAsState()
    val startDestination = when (launchState) {
        AppLaunchState.Loading -> null
        AppLaunchState.BackendUnavailable -> null
        AppLaunchState.SetupRequired -> Screen.Setup.route
        AppLaunchState.Unauthenticated -> Screen.Login.route
        AppLaunchState.Authenticated -> Screen.Subscriptions.route
    }

    if (launchState == AppLaunchState.BackendUnavailable) {
        BackendUnavailableScreen(onRetry = launchViewModel::refreshSession)
        return
    }

    if (startDestination == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                MpodLogo()
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 2.dp
                )
            }
        }
        return
    }

    key(startDestination) {
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
                        onThemeModeChange = onThemeModeChange,
                        onLogout = launchViewModel::logout
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
}
