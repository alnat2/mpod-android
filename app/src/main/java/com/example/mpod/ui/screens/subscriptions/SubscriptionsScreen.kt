package com.example.mpod.ui.screens.subscriptions

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.mpod.ui.components.EpisodeRow
import com.example.mpod.ui.components.MarkAllListenedHeader
import com.example.mpod.ui.components.MpodBottomNav
import com.example.mpod.ui.components.MpodButton
import com.example.mpod.ui.components.PageHeader
import com.example.mpod.ui.components.PodcastCard
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
    LaunchedEffect(refreshKey) {
        if (refreshKey > 0) viewModel.refresh()
    }
    SubscriptionsScreen(
        state = state,
        onRefreshAll = viewModel::refreshAll,
        onRefreshPodcast = viewModel::refreshPodcast
    )
}

@Composable
fun SubscriptionsScreen(
    hasRefreshError: Boolean = false,
    state: SubscriptionsUiState = remember(hasRefreshError) { previewSubscriptionsState() },
    onRefreshAll: () -> Unit = {},
    onRefreshPodcast: (Int) -> Unit = {}
) {
    val podcasts = state.podcasts
    val refreshErrorMessage = state.actionErrorMessage

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
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

                podcasts.isEmpty() -> {
                    PageHeader(
                        title = "No podcasts",
                        subtitle = "Start with one RSS feed or import subscriptions from another app."
                    )
                    SubscriptionsStatusCard(message = "No podcasts yet")
                }

                else -> {
                    PageHeader(
                        title = "Subscriptions",
                        subtitle = if (hasRefreshError) "Last refresh · today 3:04" else podcastCountLabel(podcasts.size),
                        showActions = true,
                        onRefreshClick = if (state.isRefreshingAll) null else onRefreshAll
                    )

                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val itemWidth = maxWidth
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            itemsIndexed(podcasts, key = { _, podcast -> podcast.id }) { index, podcast ->
                                Column(
                                    modifier = Modifier.width(itemWidth),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    PodcastCard(
                                        title = podcast.title,
                                        description = podcast.description,
                                        selected = index == 0,
                                        onUnsubscribe = { /* TODO */ },
                                        isRefreshing = podcast.id in state.refreshingPodcastIds,
                                        onRefresh = { onRefreshPodcast(podcast.id) }
                                    )
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 210.dp)
                                            .padding(horizontal = 2.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        MarkAllListenedHeader(summary = podcastEpisodeSummary(podcast))
                                        podcast.episodes.take(20).forEach { episode ->
                                            EpisodeRow(
                                                title = episode.title,
                                                podcastName = podcast.title,
                                                duration = formatEpisodeDuration(episode.durationSeconds),
                                                date = formatPublishedDate(episode.publishedAt),
                                                inPlaylist = episode.inPlaylist,
                                                showDragHandle = index != 0
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

        if (hasRefreshError || refreshErrorMessage != null) {
            RefreshErrorBanner(
                message = refreshErrorMessage ?: "Refresh failed for \"The Watch\" podcast",
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(top = 10.dp)
                    .align(Alignment.TopCenter)
            )
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
private fun RefreshErrorBanner(
    message: String,
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
            modifier = Modifier.width(86.dp)
        )
    }
}

private fun podcastCountLabel(count: Int): String {
    return "$count ${if (count == 1) "podcast" else "podcasts"}"
}

private fun podcastEpisodeSummary(podcast: SubscriptionPodcastUi): String {
    val unlistened = podcast.episodes.count { !it.isListened }
    return "${podcast.episodes.size} / $unlistened episodes"
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
            inPlaylist = true
        ),
        SubscriptionEpisodeUi(
            id = 2,
            title = "How public transit maps teach invisible habits",
            durationSeconds = 36 * 60,
            publishedAt = "2026-03-31T00:00:00Z",
            isListened = false,
            downloaded = false,
            inPlaylist = false
        )
    )

    return SubscriptionsUiState(
        podcasts = listOf(
            SubscriptionPodcastUi(
                id = 1,
                title = "Decoder Ring",
                description = "Culture stories behind everyday design",
                episodes = episodes
            ),
            SubscriptionPodcastUi(
                id = 2,
                title = "Rude Emails",
                description = "Workplace stories and tiny disasters",
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
