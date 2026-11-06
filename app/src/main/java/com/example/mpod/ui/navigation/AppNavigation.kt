package com.example.mpod.ui.navigation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.dialog
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.example.mpod.ui.components.AddPodcastModal
import com.example.mpod.ui.components.AddPodcastMode
import com.example.mpod.ui.components.AddPodcastViewModel
import com.example.mpod.ui.components.MpodBottomNav
import com.example.mpod.ui.components.MpodStatusBanner
import com.example.mpod.ui.components.StatusBannerType
import com.example.mpod.ui.screens.home.HomeRoute
import com.example.mpod.ui.screens.settings.AddProxyScreen
import com.example.mpod.ui.screens.settings.EditProxyScreen
import com.example.mpod.ui.screens.settings.SettingsRoute
import com.example.mpod.ui.screens.settings.SettingsViewModel
import com.example.mpod.ui.screens.subscriptions.SubscriptionsRoute
import com.example.mpod.ui.theme.ThemeMode
import com.example.mpod.data.local.entity.ShareImportStatus
import com.example.mpod.share.ShareImportManager
import com.example.mpod.share.selectVisibleShareBatchId
import com.example.mpod.util.AudioSourceValidator
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun AppNavigation(
    themeMode: ThemeMode = ThemeMode.System,
    onThemeModeChange: (ThemeMode) -> Unit = {},
    initialDestination: String = Screen.Home.route,
    shareImportManager: ShareImportManager
) {
    val navController = rememberNavController()
    var libraryRefreshKey by remember { mutableIntStateOf(0) }
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
    val addPodcastViewModel: AddPodcastViewModel = hiltViewModel()
    val addPodcastState by addPodcastViewModel.state.collectAsStateWithLifecycle()
    val bannerState by addPodcastViewModel.bannerState.collectAsStateWithLifecycle()
    val recentImports by shareImportManager.recentImports.collectAsStateWithLifecycle(emptyList())
    val unsupportedShareMessage by shareImportManager.unsupportedShareMessage.collectAsStateWithLifecycle()
    val activeSubmissionCount by shareImportManager.activeSubmissionCount.collectAsStateWithLifecycle()
    var handledBatchIds by remember { mutableStateOf(emptySet<String>()) }
    var dismissedShareBatchIds by remember { mutableStateOf(emptySet<String>()) }
    var unsupportedShareDismissed by remember { mutableStateOf(false) }
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showBottomNav = currentRoute in setOf(Screen.Home.route, Screen.Subscriptions.route, Screen.Settings.route)

    val latestBatchId = selectVisibleShareBatchId(recentImports)
    val latestBatch = remember(recentImports, latestBatchId) {
        latestBatchId?.let { batchId -> recentImports.filter { it.batchId == batchId } }.orEmpty()
    }
    val pendingImports = latestBatch.filter {
        it.status in setOf(ShareImportStatus.ENQUEUED, ShareImportStatus.COPYING, ShareImportStatus.IMPORTING)
    }
    val successfulImports = latestBatch.count { it.status == ShareImportStatus.DONE }
    val failedImports = latestBatch.filter { it.status == ShareImportStatus.FAILED }
    val latestCopy = pendingImports.firstOrNull { it.status == ShareImportStatus.COPYING }
    val shareBannerType = when {
        pendingImports.isNotEmpty() || activeSubmissionCount > 0 -> StatusBannerType.Loading
        failedImports.isNotEmpty() -> StatusBannerType.Error
        successfulImports > 0 -> StatusBannerType.Info
        else -> null
    }
    val shareBannerMessage = when {
        activeSubmissionCount > 0 && pendingImports.isEmpty() -> "Adding shared audio…"
        pendingImports.isNotEmpty() && latestCopy?.totalBytes?.let { it > 0 } == true -> {
            val percent = (latestCopy.progressBytes * 100 / latestCopy.totalBytes!!).coerceIn(0, 100)
            "Copying shared audio… $percent%"
        }
        pendingImports.isNotEmpty() -> "Adding shared audio…"
        failedImports.isNotEmpty() && successfulImports > 0 ->
            "Added $successfulImports audio; could not add ${failedImports.size}."
        failedImports.size == 1 -> failedImports.single().error ?: "Could not import shared audio."
        failedImports.isNotEmpty() -> "Could not add ${failedImports.size} audio tracks."
        successfulImports > 0 -> "Added $successfulImports audio ${if (successfulImports == 1) "track" else "tracks"}."
        else -> null
    }
    val shareBatchClosed = latestBatch.isNotEmpty() && pendingImports.isEmpty()

    androidx.compose.runtime.LaunchedEffect(recentImports) {
        recentImports.groupBy { it.batchId }.forEach { (batchId, events) ->
            val unacknowledged = events.filterNot { it.completionAcknowledged }
            val hasRemaining = unacknowledged.any {
                it.status in setOf(ShareImportStatus.ENQUEUED, ShareImportStatus.COPYING, ShareImportStatus.IMPORTING)
            }
            val hasSuccess = unacknowledged.any { it.status == ShareImportStatus.DONE }
            if (!hasRemaining && hasSuccess && batchId !in handledBatchIds) {
                handledBatchIds = handledBatchIds + batchId
                coroutineScope.launch { shareImportManager.acknowledgeBatch(batchId) }
                libraryRefreshKey += 1
                navController.navigate(Screen.Home.route) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                }
            }
        }
    }
    androidx.compose.runtime.LaunchedEffect(latestBatchId, shareBannerType, shareBatchClosed) {
        val batchId = latestBatchId ?: return@LaunchedEffect
        if (shareBatchClosed && failedImports.isEmpty() && successfulImports > 0) {
            delay(3000L)
            dismissedShareBatchIds = dismissedShareBatchIds + batchId
        }
    }
    androidx.compose.runtime.LaunchedEffect(unsupportedShareMessage) {
        if (unsupportedShareMessage != null) unsupportedShareDismissed = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
        NavHost(
            navController = navController,
            startDestination = initialDestination,
            modifier = Modifier
                .weight(1f)
                .background(MaterialTheme.colorScheme.background),
            enterTransition = {
                fadeIn(animationSpec = tween(220, easing = LinearOutSlowInEasing)) +
                    scaleIn(initialScale = 0.92f, animationSpec = tween(220, easing = FastOutSlowInEasing))
            },
            exitTransition = {
                fadeOut(animationSpec = tween(180, easing = FastOutLinearInEasing)) +
                    scaleOut(targetScale = 1.05f, animationSpec = tween(180, easing = FastOutSlowInEasing))
            },
            popEnterTransition = {
                fadeIn(animationSpec = tween(220, easing = LinearOutSlowInEasing)) +
                    scaleIn(initialScale = 1.05f, animationSpec = tween(220, easing = FastOutSlowInEasing))
            },
            popExitTransition = {
                fadeOut(animationSpec = tween(180, easing = FastOutLinearInEasing)) +
                    scaleOut(targetScale = 0.92f, animationSpec = tween(180, easing = FastOutSlowInEasing))
            }
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
                    onThemeModeChange = onThemeModeChange,
                    onNavigateToAddProxy = { navController.navigate(Screen.AddProxy.route) },
                    onNavigateToEditProxy = { serverId -> navController.navigate(Screen.EditProxy.createRoute(serverId)) },
                    viewModel = settingsViewModel
                )
            }
            composable(Screen.AddProxy.route) {
                AddProxyScreen(
                    onBack = { navController.popBackStack() },
                    onSaveServer = { name, host, port, username, password ->
                        settingsViewModel.addProxyServer(name, host, port, username, password)
                    },
                    onSaveSuccess = { navController.popBackStack() }
                )
            }
            composable(
                route = Screen.EditProxy.route,
                arguments = listOf(navArgument("serverId") { type = NavType.StringType })
            ) { backStackEntry ->
                val serverId = backStackEntry.arguments?.getString("serverId") ?: ""
                val settingsState by settingsViewModel.state.collectAsStateWithLifecycle()
                val server = settingsState.proxyServers.firstOrNull { it.id == serverId }
                EditProxyScreen(
                    server = server,
                    onBack = { navController.popBackStack() },
                    onUpdateServer = { id, name, host, port, username, password ->
                        settingsViewModel.updateProxyServer(id, name, host, port, username, password)
                    },
                    onDeleteServer = { id ->
                        settingsViewModel.deleteProxyServer(id)
                    },
                    onSaveSuccess = { navController.popBackStack() },
                    onDeleteSuccess = { navController.popBackStack() }
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
                        navController.popBackStack()
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
                        val trimmed = url.trim()
                        val isAudio = addPodcastState.mode == AddPodcastMode.AudioUrl
                        val isValid = if (isAudio) {
                            AudioSourceValidator.isSupportedAudioSource(trimmed)
                        } else {
                            AudioSourceValidator.isHttpUrl(trimmed)
                        }
                        if (trimmed.isBlank() || !isValid) {
                            if (isAudio) {
                                addPodcastViewModel.addAudioUrl(url) {}
                            } else {
                                addPodcastViewModel.addRssFeed(url) {}
                            }
                        } else {
                            navController.popBackStack()
                            if (isAudio) {
                                shareImportManager.submitFromActivity(
                                    payload = trimmed,
                                    title = null,
                                    sourceUri = null,
                                    fingerprint = shareImportManager.fingerprint(trimmed)
                                )
                            } else {
                                addPodcastViewModel.addRssFeed(trimmed) {
                                    libraryRefreshKey += 1
                                }
                            }
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

    val showUnsupportedShare = unsupportedShareMessage != null && !unsupportedShareDismissed
    val showShareBanner = shareBannerType != null && (
        activeSubmissionCount > 0 ||
            (latestBatchId != null && latestBatchId !in dismissedShareBatchIds)
        )
    if (showUnsupportedShare || showShareBanner) {
        val isUnsupported = showUnsupportedShare
        MpodStatusBanner(
            message = if (isUnsupported) unsupportedShareMessage.orEmpty() else shareBannerMessage.orEmpty(),
            type = if (isUnsupported) StatusBannerType.Error else shareBannerType!!,
            actionLabel = if (!isUnsupported && failedImports.isNotEmpty()) "Try again" else null,
            onAction = if (!isUnsupported && failedImports.isNotEmpty() && latestBatchId != null) {
                { coroutineScope.launch { shareImportManager.retryFailed(latestBatchId!!) } }
            } else null,
            onDismiss = {
                if (isUnsupported) {
                    unsupportedShareDismissed = true
                    shareImportManager.dismissUnsupportedShare()
                } else {
                    latestBatchId?.let { dismissedShareBatchIds = dismissedShareBatchIds + it }
                }
            },
            modifier = Modifier
                .statusBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(top = 10.dp)
                .align(Alignment.TopCenter)
        )
    } else {
        bannerState?.let { banner ->
            MpodStatusBanner(
                message = banner.message,
                type = banner.type,
                actionLabel = banner.actionLabel,
                onAction = if (banner.actionLabel != null) {
                    {
                        if (banner.actionLabel == "Details") {
                            navController.navigate(Screen.AddPodcast.route)
                        } else {
                            addPodcastViewModel.retryLastAction(
                                onRssSuccess = { libraryRefreshKey += 1 },
                                onAudioSuccess = {
                                    libraryRefreshKey += 1
                                    navController.navigate(Screen.Home.route) {
                                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                    }
                                },
                                onOpmlSuccess = { libraryRefreshKey += 1 }
                            )
                        }
                    }
                } else null,
                onDismiss = { addPodcastViewModel.dismissBanner() },
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(top = 10.dp)
                    .align(Alignment.TopCenter)
            )
        }
    }
}
}
