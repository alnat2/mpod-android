package com.example.mpod.ui.screens.subscriptions

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.mpod.ui.components.EpisodeRowAction
import com.example.mpod.ui.components.EpisodeRow
import com.example.mpod.ui.components.DownloadFailureBanner
import com.example.mpod.ui.components.UnsubscribeUndoBanner
import com.example.mpod.ui.components.MarkAllListenedHeader
import com.example.mpod.ui.components.ModalScreenMobile
import com.example.mpod.ui.components.MpodBottomNav
import com.example.mpod.ui.components.MpodButton
import com.example.mpod.ui.components.PageHeader
import com.example.mpod.ui.components.PodcastCard
import com.example.mpod.ui.components.ShowNotesMobile
import com.example.mpod.ui.components.figmaDropShadow
import com.example.mpod.ui.navigation.Screen
import com.example.mpod.ui.theme.MpodTheme
import com.example.mpod.ui.util.formatEpisodeDuration
import com.example.mpod.ui.util.formatPublishedDate

@Composable
fun SubscriptionsRoute(
    refreshKey: Int = 0,
    viewModel: SubscriptionsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(refreshKey) {
        if (refreshKey > 0) viewModel.refresh()
    }
    SubscriptionsScreen(
        state = state,
        onRefreshAll = viewModel::refreshAll,
        onRefreshPodcast = viewModel::refreshPodcast,
        onUnsubscribePodcast = viewModel::schedulePodcastUnsubscribe,
        onUndoPodcastUnsubscribe = viewModel::undoPodcastUnsubscribe,
        onMarkAllListened = viewModel::markAllListened,
        onAddEpisodeToPlaylist = viewModel::addEpisodeToPlaylist,
        onRemoveEpisodeFromPlaylist = viewModel::removeEpisodeFromPlaylist,
        onSetEpisodeListened = viewModel::setEpisodeListened,
        onDownloadEpisode = viewModel::downloadEpisode,
        onDismissDownloadFailure = viewModel::dismissDownloadFailure
    )
}

@Composable
fun SubscriptionsScreen(
    hasRefreshError: Boolean = false,
    state: SubscriptionsUiState = remember(hasRefreshError) { previewSubscriptionsState() },
    onRefreshAll: () -> Unit = {},
    onRefreshPodcast: (Int) -> Unit = {},
    onUnsubscribePodcast: (Int) -> Unit = {},
    onUndoPodcastUnsubscribe: (Int) -> Unit = {},
    onMarkAllListened: (Int) -> Unit = {},
    onAddEpisodeToPlaylist: (Int) -> Unit = {},
    onRemoveEpisodeFromPlaylist: (Int) -> Unit = {},
    onSetEpisodeListened: (episodeId: Int, isListened: Boolean) -> Unit = { _, _ -> },
    onDownloadEpisode: (Int) -> Unit = {},
    onDismissDownloadFailure: () -> Unit = {},
    onRetryRefresh: () -> Unit = onRefreshAll
) {
    var visibility by remember { mutableStateOf(SubscriptionVisibility.Unlistened) }
    val podcasts = state.podcasts.visibleFor(visibility)
    val refreshErrorMessage = state.actionErrorMessage
    var showNotesEpisode by remember { mutableStateOf<Pair<SubscriptionPodcastUi, SubscriptionEpisodeUi>?>(null) }
    val toggleVisibility = {
        visibility = if (visibility == SubscriptionVisibility.Unlistened) {
            SubscriptionVisibility.All
        } else {
            SubscriptionVisibility.Unlistened
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when {
                state.isLoading -> {
                    PageHeader(title = "Subscriptions")
                    SubscriptionsStatusCard(message = "Loading subscriptions")
                }

                state.errorMessage != null -> {
                    PageHeader(title = "Subscriptions")
                    SubscriptionsStatusCard(message = state.errorMessage)
                }

                state.podcasts.isEmpty() -> {
                    PageHeader(
                        title = "No podcasts",
                        subtitle = "Start with one RSS feed or import subscriptions from another app."
                    )
                    SubscriptionsStatusCard(message = "No podcasts yet")
                }

                podcasts.isEmpty() -> {
                    PageHeader(
                        title = "Subscriptions",
                        subtitle = podcastCountLabel(state.podcasts.size),
                        showActions = true,
                        onRefreshClick = if (state.isRefreshingAll) null else onRefreshAll,
                        viewActionDescription = "Show all",
                        onViewClick = toggleVisibility
                    )
                    AllCaughtUpState(onShowAll = toggleVisibility)
                }

                else -> {
                    PageHeader(
                        title = "Subscriptions",
                        subtitle = if (hasRefreshError) {
                            "Last refresh · today 3:04"
                        } else {
                            podcastCountLabel(state.podcasts.size)
                        },
                        showActions = true,
                        onRefreshClick = if (state.isRefreshingAll) null else onRefreshAll,
                        viewActionDescription = if (visibility == SubscriptionVisibility.All) {
                            "Show unlistened"
                        } else {
                            "Show all"
                        },
                        onViewClick = toggleVisibility
                    )

                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        val itemWidth = maxWidth
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight()
                        ) {
                            itemsIndexed(podcasts, key = { _, podcast -> podcast.id }) { index, podcast ->
                                Column(
                                    modifier = Modifier
                                        .width(itemWidth)
                                        .fillMaxHeight()
                                        .animateItem(),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    PodcastCard(
                                        title = podcast.title,
                                        description = podcast.description,
                                        imageUrl = podcast.imageUrl,
                                        selected = index == 0,
                                        onUnsubscribe = { onUnsubscribePodcast(podcast.id) },
                                        isRefreshing = podcast.id in state.refreshingPodcastIds,
                                        isUnsubscribing = podcast.id in state.unsubscribingPodcastIds,
                                        isUnsubscribePending = state.pendingUnsubscribe?.podcastId == podcast.id,
                                        unsubscribeEnabled = state.pendingUnsubscribe == null,
                                        onRefresh = { onRefreshPodcast(podcast.id) }
                                    )
                                    LazyColumn(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f)
                                            .padding(horizontal = 2.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        item {
                                            val isMarkingAll = podcast.id in state.markingAllListenedPodcastIds
                                            MarkAllListenedHeader(
                                                summary = podcastEpisodeSummary(podcast),
                                                enabled = podcast.unlistenedEpisodeCount > 0 && !isMarkingAll,
                                                isLoading = isMarkingAll,
                                                onMarkAllListened = { onMarkAllListened(podcast.id) }
                                            )
                                        }
                                        items(podcast.episodes, key = { episode -> episode.id }) { episode ->
                                            EpisodeRow(
                                                title = episode.title,
                                                podcastName = podcast.title,
                                                duration = formatEpisodeDuration(episode.durationSeconds),
                                                date = formatPublishedDate(episode.publishedAt),
                                                inPlaylist = episode.inPlaylist,
                                                isListened = episode.isListened,
                                                downloaded = episode.downloaded,
                                                isDownloading = episode.id in state.downloadingEpisodeIds,
                                                actionsEnabled = episode.id !in state.busyEpisodeIds,
                                                showDragHandle = index != 0,
                                                onAction = { action ->
                                                    when (action) {
                                                        EpisodeRowAction.AddToPlaylist -> onAddEpisodeToPlaylist(episode.id)
                                                        EpisodeRowAction.RemoveFromPlaylist -> onRemoveEpisodeFromPlaylist(episode.id)
                                                        EpisodeRowAction.ShowNotes -> showNotesEpisode = podcast to episode
                                                        EpisodeRowAction.Download -> onDownloadEpisode(episode.id)
                                                        EpisodeRowAction.MarkListened -> onSetEpisodeListened(episode.id, true)
                                                        EpisodeRowAction.MarkUnlistened -> onSetEpisodeListened(episode.id, false)
                                                        EpisodeRowAction.MoveUp -> Unit
                                                        EpisodeRowAction.MoveDown -> Unit
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        state.pendingUnsubscribe?.let { pending ->
            UnsubscribeUndoBanner(
                podcastTitle = pending.podcastTitle,
                secondsRemaining = pending.secondsRemaining,
                onUndo = { onUndoPodcastUnsubscribe(pending.podcastId) },
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(top = 10.dp)
                    .align(Alignment.TopCenter)
            )
        }

        if (state.pendingUnsubscribe == null) state.downloadFailure?.let { failure ->
            DownloadFailureBanner(
                message = failure.message,
                onDismiss = onDismissDownloadFailure,
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(top = 10.dp)
                    .align(Alignment.TopCenter)
            )
        }

        if (
            state.pendingUnsubscribe == null &&
            state.downloadFailure == null &&
            (hasRefreshError || refreshErrorMessage != null)
        ) {
            RefreshErrorBanner(
                message = refreshErrorMessage ?: "Refresh failed for \"The Watch\" podcast",
                onRetry = onRetryRefresh,
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(top = 10.dp)
                    .align(Alignment.TopCenter)
            )
        }

        showNotesEpisode?.let { (podcast, episode) ->
            ModalScreenMobile {
                ShowNotesMobile(
                    podcastTitle = "${podcast.title} - ${episode.title}",
                    notes = episode.summary?.takeIf { it.isNotBlank() } ?: "No show notes for this episode.",
                    onClose = { showNotesEpisode = null }
                )
            }
        }
    }
}

@Composable
private fun SubscriptionsStatusCard(
    message: String,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier
            .fillMaxWidth()
            .figmaDropShadow(radius = 8.dp)
    ) {
        Text(
            text = message,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
private fun AllCaughtUpState(
    onShowAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier
            .fillMaxWidth()
            .figmaDropShadow(radius = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "All caught up",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "There are no unlistened episodes. Show all to browse your listened podcasts.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            MpodButton(
                text = "Show all",
                onClick = onShowAll
            )
        }
    }
}

@Composable
private fun RefreshErrorBanner(
    message: String,
    onRetry: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val destructive = Color(0xFFE7000B)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .border(1.dp, destructive, RoundedCornerShape(8.dp))
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = message,
            fontSize = MaterialTheme.typography.bodySmall.fontSize,
            lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
            fontWeight = FontWeight.Medium,
            color = destructive,
            modifier = Modifier.weight(1f)
        )
        MpodButton(
            text = "Try again",
            primary = false,
            outlined = true,
            height = 32.dp,
            radius = 6.dp,
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.width(86.dp),
            onClick = onRetry
        )
    }
}

private fun podcastCountLabel(count: Int): String {
    return "$count ${if (count == 1) "podcast" else "podcasts"}"
}

private fun podcastEpisodeSummary(podcast: SubscriptionPodcastUi): String {
    return "${podcast.totalEpisodeCount} / ${podcast.unlistenedEpisodeCount} episodes"
}

internal enum class SubscriptionVisibility {
    Unlistened,
    All
}

internal fun List<SubscriptionPodcastUi>.visibleFor(
    visibility: SubscriptionVisibility
): List<SubscriptionPodcastUi> {
    if (visibility == SubscriptionVisibility.All) return this

    return filter { podcast -> podcast.unlistenedEpisodeCount > 0 }
        .map { podcast ->
            podcast.copy(episodes = podcast.episodes.filterNot { it.isListened })
        }
}

private fun previewSubscriptionsState(): SubscriptionsUiState {
    val episodes = listOf(
        SubscriptionEpisodeUi(
            id = 1,
            title = "Why store loyalty cards became a UX minefield",
            durationSeconds = 54 * 60,
            publishedAt = "2026-03-31T00:00:00Z",
            isListened = false,
            downloaded = false,
            summary = "A story about loyalty cards, UX traps, and the tiny design decisions that become habits.",
            inPlaylist = true
        ),
        SubscriptionEpisodeUi(
            id = 2,
            title = "How public transit maps teach invisible habits",
            durationSeconds = 36 * 60,
            publishedAt = "2026-03-31T00:00:00Z",
            isListened = false,
            downloaded = false,
            summary = "Transit maps look simple, but the choices behind them shape how people move through cities.",
            inPlaylist = false
        )
    )

    return SubscriptionsUiState(
        podcasts = listOf(
            SubscriptionPodcastUi(
                id = 1,
                title = "Decoder Ring",
                description = "Culture stories behind everyday design",
                imageUrl = null,
                totalEpisodeCount = episodes.size,
                unlistenedEpisodeCount = episodes.count { !it.isListened },
                episodes = episodes
            ),
            SubscriptionPodcastUi(
                id = 2,
                title = "Rude Emails",
                description = "Workplace stories and tiny disasters",
                imageUrl = null,
                totalEpisodeCount = episodes.size,
                unlistenedEpisodeCount = episodes.count { !it.isListened },
                episodes = episodes
            )
        )
    )
}

@Preview(
    name = "Subscriptions error / 360",
    widthDp = 360,
    heightDp = 800,
    showBackground = true
)
@Composable
private fun SubscriptionsErrorScreenPreview() {
    MpodTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            Box(modifier = Modifier.weight(1f)) {
                SubscriptionsScreen(hasRefreshError = true)
            }
            MpodBottomNav(
                currentRoute = Screen.Subscriptions.route,
                onNavigate = {},
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }
    }
}

@Preview(
    name = "Subscriptions loading / 360",
    widthDp = 360,
    heightDp = 800,
    showBackground = true
)
@Composable
private fun SubscriptionsLoadingPreview() {
    MpodTheme {
        SubscriptionsPreviewShell {
            SubscriptionsScreen(state = SubscriptionsUiState(isLoading = true))
        }
    }
}

@Preview(
    name = "Subscriptions load error / 360",
    widthDp = 360,
    heightDp = 800,
    showBackground = true
)
@Composable
private fun SubscriptionsLoadErrorPreview() {
    MpodTheme {
        SubscriptionsPreviewShell {
            SubscriptionsScreen(state = SubscriptionsUiState(errorMessage = "Could not load subscriptions."))
        }
    }
}

@Preview(
    name = "Subscriptions empty / 360",
    widthDp = 360,
    heightDp = 800,
    showBackground = true
)
@Composable
private fun SubscriptionsEmptyPreview() {
    MpodTheme {
        SubscriptionsPreviewShell {
            SubscriptionsScreen(state = SubscriptionsUiState(podcasts = emptyList()))
        }
    }
}

@Composable
private fun SubscriptionsPreviewShell(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Box(modifier = Modifier.weight(1f)) {
            content()
        }
        MpodBottomNav(
            currentRoute = Screen.Subscriptions.route,
            onNavigate = {},
            modifier = Modifier.padding(horizontal = 20.dp)
        )
    }
}
