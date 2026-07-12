package com.example.mpod.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.*
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.mpod.R
import com.example.mpod.ui.components.EpisodeRow
import com.example.mpod.ui.components.EpisodeRowAction
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
import kotlinx.coroutines.delay

@Composable
fun HomeRoute(
    refreshKey: Int = 0,
    onAddRssFeed: () -> Unit = {},
    onImportOpml: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val currentEpisode = state.queue.firstOrNull()
    var playbackState by remember { mutableStateOf(HomePlaybackUiState()) }
    val player = remember(state.audioRequestHeaders) {
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(state.audioRequestHeaders)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playbackState = playbackState.copy(isPlaying = isPlaying)
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(refreshKey) {
        if (refreshKey > 0) viewModel.refresh()
    }

    LaunchedEffect(player, currentEpisode?.id) {
        if (currentEpisode == null) {
            player.stop()
            playbackState = HomePlaybackUiState()
        } else {
            player.setMediaItem(MediaItem.fromUri(currentEpisode.audioUrl))
            player.prepare()
            playbackState = HomePlaybackUiState(
                durationSeconds = currentEpisode.durationSeconds ?: 0,
                speedLabel = playbackState.speedLabel
            )
        }
    }

    LaunchedEffect(player, currentEpisode?.id) {
        while (true) {
            val durationMs = player.duration.takeIf { it > 0 } ?: ((currentEpisode?.durationSeconds ?: 0) * 1000L)
            playbackState = playbackState.copy(
                positionSeconds = (player.currentPosition / 1000).toInt().coerceAtLeast(0),
                durationSeconds = (durationMs / 1000).toInt().coerceAtLeast(0),
                isPlaying = player.isPlaying
            )
            delay(500)
        }
    }

    LaunchedEffect(player, currentEpisode?.id, playbackState.isPlaying) {
        while (currentEpisode != null && playbackState.isPlaying) {
            delay(15_000)
            viewModel.updatePlayback(
                episodeId = currentEpisode.id,
                positionSeconds = (player.currentPosition / 1000).toInt(),
                durationSeconds = ((player.duration.takeIf { it > 0 } ?: 0L) / 1000).toInt()
            )
        }
    }

    HomeScreen(
        state = state,
        playbackState = playbackState,
        onPlayToggle = {
            if (player.isPlaying) {
                currentEpisode?.let { episode ->
                    viewModel.updatePlayback(
                        episodeId = episode.id,
                        positionSeconds = (player.currentPosition / 1000).toInt(),
                        durationSeconds = ((player.duration.takeIf { it > 0 } ?: 0L) / 1000).toInt()
                    )
                }
                player.pause()
            } else {
                player.play()
            }
        },
        onSeekBy = { seconds ->
            currentEpisode?.let { episode ->
                val durationMs = player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
                val nextPosition = (player.currentPosition + seconds * 1000L)
                    .coerceIn(0L, durationMs)
                player.seekTo(nextPosition)
                viewModel.updatePlayback(
                    episodeId = episode.id,
                    positionSeconds = (nextPosition / 1000).toInt(),
                    durationSeconds = ((player.duration.takeIf { it > 0 } ?: 0L) / 1000).toInt(),
                    didSeek = true
                )
            }
        },
        onSpeedChange = { speed ->
            speed.toFloatOrNull()?.let { speedValue ->
                player.playbackParameters = PlaybackParameters(speedValue)
                playbackState = playbackState.copy(speedLabel = speed)
            }
        },
        onAddRssFeed = onAddRssFeed,
        onImportOpml = onImportOpml,
        onMoveEpisode = viewModel::moveEpisode,
        onRemoveEpisodeFromPlaylist = viewModel::removeEpisodeFromPlaylist,
        onSetEpisodeListened = viewModel::setEpisodeListened,
        onDownloadEpisode = viewModel::downloadEpisode
    )
}

@Composable
fun HomeScreen(
    hasPodcasts: Boolean = true,
    state: HomeUiState = remember(hasPodcasts) { previewHomeState(hasPodcasts) },
    playbackState: HomePlaybackUiState = HomePlaybackUiState(
        positionSeconds = 23 * 60 + 14,
        durationSeconds = 37 * 60 + 17,
        speedLabel = "1.5"
    ),
    onPlayToggle: () -> Unit = {},
    onSeekBy: (Int) -> Unit = {},
    onSpeedChange: (String) -> Unit = {},
    onAddRssFeed: () -> Unit = {},
    onImportOpml: () -> Unit = {},
    onMoveEpisode: (episodeId: Int, offset: Int) -> Unit = { _, _ -> },
    onRemoveEpisodeFromPlaylist: (Int) -> Unit = {},
    onSetEpisodeListened: (episodeId: Int, isListened: Boolean) -> Unit = { _, _ -> },
    onDownloadEpisode: (Int) -> Unit = {}
) {
    var showNotesEpisode by remember { mutableStateOf<HomeEpisodeUi?>(null) }
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
                        onAddRssFeed = onAddRssFeed,
                        onImportOpml = onImportOpml,
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

                state.actionErrorMessage?.let { message ->
                    StatusCard(message = message)
                }

                PlayerView(
                    modifier = Modifier.fillMaxWidth(),
                    title = currentEpisode.title,
                    podcastTitle = currentEpisode.podcastTitle,
                    elapsedLabel = formatProgressTime(playbackState.positionSeconds),
                    durationLabel = formatProgressTime(playbackState.remainingSeconds),
                    progress = playbackState.progress,
                    isPlaying = playbackState.isPlaying,
                    speedLabel = playbackState.speedLabel,
                    onSpeedChange = onSpeedChange,
                    onPlayClick = onPlayToggle,
                    onSeekBackward = { onSeekBy(-10) },
                    onSeekForward = { onSeekBy(15) },
                    onNotesClick = { showNotesEpisode = currentEpisode }
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
                            inPlaylist = true,
                            isListened = episode.isListened,
                            downloaded = episode.downloaded,
                            actionsEnabled = episode.id !in state.busyEpisodeIds,
                            canMoveUp = index > 0,
                            canMoveDown = index < state.queue.lastIndex,
                            showDragHandle = true,
                            statusTextOverride = if (index == 0) {
                                "${episode.podcastTitle} · now playing"
                            } else {
                                episode.podcastTitle
                            },
                            onAction = { action ->
                                when (action) {
                                    EpisodeRowAction.AddToPlaylist -> Unit
                                    EpisodeRowAction.RemoveFromPlaylist -> onRemoveEpisodeFromPlaylist(episode.id)
                                    EpisodeRowAction.ShowNotes -> showNotesEpisode = episode
                                    EpisodeRowAction.Download -> onDownloadEpisode(episode.id)
                                    EpisodeRowAction.MarkListened -> onSetEpisodeListened(episode.id, true)
                                    EpisodeRowAction.MarkUnlistened -> onSetEpisodeListened(episode.id, false)
                                    EpisodeRowAction.MoveUp -> onMoveEpisode(episode.id, -1)
                                    EpisodeRowAction.MoveDown -> onMoveEpisode(episode.id, 1)
                                }
                            }
                        )
                    }
                }
                }
            }
        }

        showNotesEpisode?.let { episode ->
            ModalScreenMobile {
                ShowNotesMobile(
                    podcastTitle = "${episode.podcastTitle} - ${episode.title}",
                    notes = episode.summary?.takeIf { it.isNotBlank() } ?: "No show notes for this episode.",
                    onClose = { showNotesEpisode = null }
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

data class HomePlaybackUiState(
    val positionSeconds: Int = 0,
    val durationSeconds: Int = 0,
    val isPlaying: Boolean = false,
    val speedLabel: String = "1.5"
) {
    val remainingSeconds: Int
        get() = (durationSeconds - positionSeconds).coerceAtLeast(0)

    val progress: Float
        get() = if (durationSeconds > 0) {
            (positionSeconds.toFloat() / durationSeconds.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
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
                audioUrl = "http://192.168.0.222:5051/api/episodes/1/audio",
                durationSeconds = 54 * 60,
                isListened = false,
                downloaded = false,
                summary = "A story about loyalty cards, UX traps, and the tiny design decisions that become habits."
            ),
            HomeEpisodeUi(
                id = 2,
                title = "How public transit maps teach invisible habits",
                podcastTitle = "Decoder Ring",
                audioUrl = "http://192.168.0.222:5051/api/episodes/2/audio",
                durationSeconds = 36 * 60,
                isListened = false,
                downloaded = false,
                summary = "Transit maps look simple, but the choices behind them shape how people move through cities."
            ),
            HomeEpisodeUi(
                id = 3,
                title = "The app menu nobody understands but everyone...",
                podcastTitle = "Decoder Ring",
                audioUrl = "http://192.168.0.222:5051/api/episodes/3/audio",
                durationSeconds = 43 * 60,
                isListened = false,
                downloaded = false,
                summary = "A short note about menu design and why obvious labels are sometimes the hardest thing to ship."
            )
        )
    )
}

@Composable
private fun NoPodcastsEmptyState(
    onAddRssFeed: () -> Unit = {},
    onImportOpml: () -> Unit = {},
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
                    modifier = Modifier.weight(1f),
                    onClick = onAddRssFeed
                )
                MpodButton(
                    text = "Import OPML",
                    primary = false,
                    outlined = true,
                    height = 32.dp,
                    radius = 6.dp,
                    containerColor = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    onClick = onImportOpml
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
