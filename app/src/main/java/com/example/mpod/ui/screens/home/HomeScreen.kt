package com.example.mpod.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.mpod.R
import com.example.mpod.ui.components.EpisodeRow
import com.example.mpod.ui.components.ModalScreenMobile
import com.example.mpod.ui.components.MpodButton
import com.example.mpod.ui.components.MpodBottomNav
import com.example.mpod.ui.components.PageHeader
import com.example.mpod.ui.components.PlayerView
import com.example.mpod.ui.components.ShowNotesMobile
import com.example.mpod.ui.components.figmaDropShadow
import com.example.mpod.ui.navigation.Screen
import com.example.mpod.ui.theme.MpodTheme
import com.example.mpod.ui.util.formatEpisodeDuration
import com.example.mpod.ui.util.formatProgressTime
import com.example.mpod.ui.util.formatTotalDuration

@Composable
fun HomeRoute(
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    HomeScreen(state = state)
}

@Composable
fun HomeScreen(
    hasPodcasts: Boolean = true,
    state: HomeUiState = remember(hasPodcasts) { previewHomeState(hasPodcasts) }
) {
    var showNotes by remember { mutableStateOf(false) }
    val currentEpisode = state.queue.firstOrNull()

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
                    PageHeader(title = "Now playing")
                    StatusCard(message = "Loading playlist")
                }

                state.errorMessage != null -> {
                    PageHeader(title = "Now playing")
                    StatusCard(message = state.errorMessage)
                }

                !state.hasPodcasts -> {
                    PageHeader(
                        title = "No podcasts",
                        subtitle = "Start with one RSS feed or import subscriptions from another app."
                    )

                    NoPodcastsEmptyState(
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                currentEpisode == null -> {
                    PageHeader(
                        title = "Now playing",
                        showActions = true
                    )
                    StatusCard(message = "Playlist is empty")
                }

                else -> {
                PageHeader(
                    title = "Now playing",
                    showActions = true
                )

                PlayerView(
                    modifier = Modifier.fillMaxWidth(),
                    title = currentEpisode.title,
                    podcastTitle = currentEpisode.podcastTitle,
                    elapsedLabel = "0:00",
                    durationLabel = formatProgressTime(currentEpisode.durationSeconds ?: 0),
                    progress = 0f,
                    onNotesClick = { showNotes = true }
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Card(
                        shape = RoundedCornerShape(4.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .figmaDropShadow(radius = 4.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.CenterStart,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp)
                        ) {
                            Text(
                                text = queueSummary(state.queue),
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                                fontWeight = FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    state.queue.forEachIndexed { index, episode ->
                        EpisodeRow(
                            title = episode.title,
                            podcastName = episode.podcastTitle,
                            duration = formatEpisodeDuration(episode.durationSeconds),
                            isPlaying = index == 0,
                            showDragHandle = true
                        )
                    }
                }
                }
            }
        }

        if (showNotes) {
            ModalScreenMobile {
                ShowNotesMobile(
                    onClose = { showNotes = false }
                )
            }
        }
    }
}

@Composable
private fun StatusCard(
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

private fun queueSummary(episodes: List<HomeEpisodeUi>): String {
    val totalSeconds = episodes.sumOf { it.durationSeconds ?: 0 }
    val episodeLabel = if (episodes.size == 1) "episode" else "episodes"
    return "${episodes.size} $episodeLabel · ${formatTotalDuration(totalSeconds)}"
}

private fun previewHomeState(hasPodcasts: Boolean): HomeUiState {
    if (!hasPodcasts) return HomeUiState(hasPodcasts = false)

    return HomeUiState(
        hasPodcasts = true,
        queue = listOf(
            HomeEpisodeUi(
                id = 1,
                title = "Why store loyalty cards became a UX minefield",
                podcastTitle = "Decoder Ring",
                durationSeconds = 54 * 60
            ),
            HomeEpisodeUi(
                id = 2,
                title = "How public transit maps teach invisible habits",
                podcastTitle = "Decoder Ring",
                durationSeconds = 36 * 60
            ),
            HomeEpisodeUi(
                id = 3,
                title = "The app menu nobody understands but everyone...",
                podcastTitle = "Decoder Ring",
                durationSeconds = 43 * 60
            )
        )
    )
}

@Composable
private fun NoPodcastsEmptyState(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
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
                        painter = painterResource(id = R.drawable.ic_podcast_empty),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = "No podcasts yet",
                    fontSize = 18.sp,
                    lineHeight = 28.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Add one RSS feed or bring subscriptions from another podcast app with OPML.",
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MpodButton(
                    text = "Add RSS feed",
                    height = 32.dp,
                    radius = 6.dp,
                    modifier = Modifier.weight(1f)
                )
                MpodButton(
                    text = "Import OPML",
                    primary = false,
                    outlined = true,
                    height = 32.dp,
                    radius = 6.dp,
                    containerColor = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Preview(
    name = "No podcasts screen / 360",
    widthDp = 360,
    heightDp = 800,
    showBackground = true
)
@Composable
private fun NoPodcastsScreenPreview() {
    MpodTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            Box(modifier = Modifier.weight(1f)) {
                HomeScreen(hasPodcasts = false)
            }
            MpodBottomNav(
                currentRoute = Screen.Home.route,
                onNavigate = {},
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }
    }
}
