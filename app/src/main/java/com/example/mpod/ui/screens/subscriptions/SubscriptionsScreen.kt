package com.example.mpod.ui.screens.subscriptions

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.mpod.R
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
    onAddRssFeed: () -> Unit = {},
    onImportOpml: () -> Unit = {},
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
        onDismissDownloadFailure = viewModel::dismissDownloadFailure,
        onRetryLoad = viewModel::refresh,
        onRetryRefresh = viewModel::retryLastAction,
        onAddRssFeed = onAddRssFeed,
        onImportOpml = onImportOpml
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
    onRetryLoad: () -> Unit = {},
    onAddRssFeed: () -> Unit = {},
    onImportOpml: () -> Unit = {},
    onRetryRefresh: () -> Unit = onRefreshAll
) {
    var visibility by remember { mutableStateOf(SubscriptionVisibility.Unlistened) }
    val podcasts = remember(state.podcasts, visibility) {
        state.podcasts.visibleFor(visibility)
    }
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
                state.isLoading || (!state.hasLoadedOnce && state.errorMessage == null) -> {
                    PageHeader(title = "Subscriptions")
                    SubscriptionsStatusCard(message = "Loading subscriptions")
                }

                state.errorMessage != null -> {
                    PageHeader(title = "Subscriptions")
                    SubscriptionsStatusCard(
                        message = state.errorMessage,
                        actionLabel = "Try again",
                        onAction = onRetryLoad
                    )
                }

                state.podcasts.isEmpty() -> {
                    PageHeader(
                        title = "No podcasts",
                        subtitle = "Start with one RSS feed or import subscriptions from another app."
                    )
                    SubscriptionsEmptyState(
                        title = "No podcasts yet",
                        description = "Add one RSS feed or bring subscriptions from another podcast app with OPML.",
                        onAddRssFeed = onAddRssFeed,
                        onImportOpml = onImportOpml
                    )
                }

                podcasts.isEmpty() -> {
                    PageHeader(
                        title = "Subscriptions",
                        subtitle = subscriptionsHeaderSubtitle(state.podcasts),
                        showActions = true,
                        onRefreshClick = if (state.isRefreshingAll) null else onRefreshAll,
                        isRefreshing = state.isRefreshingAll,
                        viewActionDescription = "Show all",
                        viewIconRes = visibilityIconRes(SubscriptionVisibility.Unlistened),
                        onViewClick = toggleVisibility
                    )
                    AllCaughtUpState(
                        onShowAll = toggleVisibility,
                        onAddRssFeed = onAddRssFeed,
                        onImportOpml = onImportOpml
                    )
                }

                else -> {
                    PageHeader(
                        title = "Subscriptions",
                        subtitle = if (hasRefreshError) {
                            "Last refresh · today 3:04"
                        } else {
                            subscriptionsHeaderSubtitle(state.podcasts)
                        },
                        showActions = true,
                        onRefreshClick = if (state.isRefreshingAll) null else onRefreshAll,
                        isRefreshing = state.isRefreshingAll,
                        viewActionDescription = if (visibility == SubscriptionVisibility.All) {
                            "Show unlistened"
                        } else {
                            "Show all"
                        },
                        viewIconRes = visibilityIconRes(visibility),
                        onViewClick = toggleVisibility
                    )

                    val loopsContinuously = podcasts.size > 1
                    val carouselPodcastIds = remember(podcasts) { podcasts.map { it.id } }
                    val pagerState = key(loopsContinuously, carouselPodcastIds) {
                        rememberPagerState(
                            initialPage = 0,
                            pageCount = { if (loopsContinuously) podcasts.size + 2 else 1 }
                        )
                    }
                    LaunchedEffect(pagerState, carouselPodcastIds) {
                        if (loopsContinuously) {
                            pagerState.scrollToPage(1)
                            snapshotFlow { pagerState.settledPage }
                                .collect { settledPage ->
                                    when (settledPage) {
                                        0 -> pagerState.scrollToPage(podcasts.size)
                                        podcasts.size + 1 -> pagerState.scrollToPage(1)
                                    }
                                }
                        }
                    }
                    val selectedPodcast = podcasts[
                        podcastIndexForCarouselPage(pagerState.currentPage, podcasts.size)
                    ]
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                    ) {
                        val carouselWidth = maxWidth + 40.dp
                        HorizontalPager(
                            state = pagerState,
                            key = { page -> page },
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            pageSize = PageSize.Fixed(maxWidth),
                            pageSpacing = 12.dp,
                            flingBehavior = PagerDefaults.flingBehavior(
                                state = pagerState,
                                pagerSnapDistance = PagerSnapDistance.atMost(1)
                            ),
                            modifier = Modifier
                                .requiredWidth(carouselWidth)
                                .height(160.dp)
                                .testTag("subscriptions_podcast_pager")
                        ) {
                            val podcast = podcasts[podcastIndexForCarouselPage(it, podcasts.size)]
                            val isSelected = it == pagerState.currentPage
                            val cardPosition = when (it) {
                                pagerState.currentPage -> "selected"
                                pagerState.currentPage - 1 -> "previous"
                                pagerState.currentPage + 1 -> "next"
                                else -> "page_$it"
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("subscription_podcast_card_$cardPosition")
                            ) {
                                PodcastCard(
                                    title = podcast.title,
                                    description = podcast.description,
                                    imageUrl = podcast.imageUrl,
                                    selected = isSelected,
                                    onUnsubscribe = { onUnsubscribePodcast(podcast.id) },
                                    isRefreshing = state.isRefreshingAll || podcast.id in state.refreshingPodcastIds,
                                    isUnsubscribing = podcast.id in state.unsubscribingPodcastIds,
                                    isUnsubscribePending = state.pendingUnsubscribe?.podcastId == podcast.id,
                                    unsubscribeEnabled = state.pendingUnsubscribe == null,
                                    errorMessage = podcast.errorMessage,
                                    onRefresh = { onRefreshPodcast(podcast.id) },
                                    modifier = if (isSelected) {
                                        Modifier
                                    } else {
                                        Modifier.clearAndSetSemantics { }
                                    }
                                )
                            }
                        }
                    }

                    if (selectedPodcast.episodesUnavailable) {
                        SubscriptionsStatusCard(
                            message = "Episodes could not be loaded. Use Refresh on the podcast card to try again.",
                            modifier = Modifier.padding(horizontal = 2.dp)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .testTag("subscriptions_episode_list"),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            item {
                                val isMarkingAll = selectedPodcast.id in state.markingAllListenedPodcastIds
                                MarkAllListenedHeader(
                                    summary = podcastEpisodeSummary(selectedPodcast),
                                    enabled = selectedPodcast.unlistenedEpisodeCount > 0 && !isMarkingAll,
                                    isLoading = isMarkingAll,
                                    onMarkAllListened = { onMarkAllListened(selectedPodcast.id) },
                                    modifier = Modifier.testTag("subscriptions_episode_header")
                                )
                            }
                            items(
                                items = selectedPodcast.episodes,
                                key = { episode -> episode.id },
                                contentType = { "episode_row" }
                            ) { episode ->
                                val latestSelectedPodcast = rememberUpdatedState(selectedPodcast)
                                val latestEpisode = rememberUpdatedState(episode)
                                val latestOnAddEpisodeToPlaylist = rememberUpdatedState(onAddEpisodeToPlaylist)
                                val latestOnRemoveEpisodeFromPlaylist = rememberUpdatedState(onRemoveEpisodeFromPlaylist)
                                val latestOnSetEpisodeListened = rememberUpdatedState(onSetEpisodeListened)
                                val latestOnDownloadEpisode = rememberUpdatedState(onDownloadEpisode)
                                val rowAction = remember(episode.id) {
                                    { action: EpisodeRowAction ->
                                        when (action) {
                                            EpisodeRowAction.Play -> Unit
                                            EpisodeRowAction.AddToPlaylist -> {
                                                latestOnAddEpisodeToPlaylist.value(episode.id)
                                            }
                                            EpisodeRowAction.RemoveFromPlaylist -> {
                                                latestOnRemoveEpisodeFromPlaylist.value(episode.id)
                                            }
                                            EpisodeRowAction.ShowNotes -> {
                                                showNotesEpisode = latestSelectedPodcast.value to latestEpisode.value
                                            }
                                            EpisodeRowAction.Download -> latestOnDownloadEpisode.value(episode.id)
                                            EpisodeRowAction.MarkListened -> {
                                                latestOnSetEpisodeListened.value(episode.id, true)
                                            }
                                            EpisodeRowAction.MarkUnlistened -> {
                                                latestOnSetEpisodeListened.value(episode.id, false)
                                            }
                                            EpisodeRowAction.MoveUp -> Unit
                                            EpisodeRowAction.MoveDown -> Unit
                                        }
                                    }
                                }
                                EpisodeRow(
                                    title = episode.title,
                                    podcastName = selectedPodcast.title,
                                    duration = formatEpisodeDuration(episode.durationSeconds),
                                    date = formatPublishedDate(episode.publishedAt),
                                    inPlaylist = episode.inPlaylist,
                                    isListened = episode.isListened,
                                    downloaded = episode.downloaded,
                                    isDownloading = episode.id in state.downloadingEpisodeIds,
                                    actionsEnabled = episode.id !in state.busyEpisodeIds,
                                    showDragHandle = false,
                                    modifier = Modifier.testTag("subscription_episode_row_${episode.id}"),
                                    onAction = rowAction
                                )
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

internal fun podcastIndexForCarouselPage(page: Int, podcastCount: Int): Int {
    if (podcastCount <= 1) return 0
    return when {
        page <= 0 -> podcastCount - 1
        page >= podcastCount + 1 -> 0
        else -> (page - 1).coerceIn(0, podcastCount - 1)
    }
}

@Composable
private fun SubscriptionsStatusCard(
    message: String,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
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
                text = message,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (actionLabel != null) {
                MpodButton(text = actionLabel, onClick = onAction)
            }
        }
    }
}

@Composable
private fun SubscriptionsEmptyState(
    title: String,
    description: String,
    onAddRssFeed: () -> Unit,
    onImportOpml: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_huge_podcast),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            MpodButton(
                text = "Add RSS feed",
                modifier = Modifier.weight(1f),
                onClick = onAddRssFeed
            )
            MpodButton(
                text = "Import OPML",
                primary = false,
                outlined = true,
                modifier = Modifier.weight(1f),
                onClick = onImportOpml
            )
        }
    }
}

@Composable
private fun AllCaughtUpState(
    onShowAll: () -> Unit,
    onAddRssFeed: () -> Unit,
    onImportOpml: () -> Unit,
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
                modifier = Modifier.fillMaxWidth(),
                onClick = onShowAll
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                MpodButton(
                    text = "Add RSS feed",
                    primary = false,
                    outlined = true,
                    modifier = Modifier.weight(1f),
                    onClick = onAddRssFeed
                )
                MpodButton(
                    text = "Import OPML",
                    primary = false,
                    outlined = true,
                    modifier = Modifier.weight(1f),
                    onClick = onImportOpml
                )
            }
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

internal fun subscriptionsHeaderSubtitle(podcasts: List<SubscriptionPodcastUi>): String {
    val podcastsWithUnlistened = podcasts.count { it.unlistenedEpisodeCount > 0 }
    return "${podcastCountLabel(podcasts.size)} · $podcastsWithUnlistened unlistened"
}

private fun podcastEpisodeSummary(podcast: SubscriptionPodcastUi): String {
    return "${podcast.totalEpisodeCount} / ${podcast.unlistenedEpisodeCount} episodes"
}

internal enum class SubscriptionVisibility {
    Unlistened,
    All
}

internal fun visibilityIconRes(visibility: SubscriptionVisibility): Int {
    return if (visibility == SubscriptionVisibility.All) {
        R.drawable.ic_huge_view_off
    } else {
        R.drawable.ic_huge_view
    }
}

internal fun List<SubscriptionPodcastUi>.visibleFor(
    visibility: SubscriptionVisibility
): List<SubscriptionPodcastUi> {
    if (visibility == SubscriptionVisibility.All) return this

    return filter { podcast -> podcast.unlistenedEpisodeCount > 0 || podcast.errorMessage != null }
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
