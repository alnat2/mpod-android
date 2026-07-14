package com.example.mpod.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import android.content.ComponentName
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.mpod.R
import com.example.mpod.ui.components.EpisodeRow
import com.example.mpod.ui.components.EpisodeRowAction
import com.example.mpod.ui.components.DownloadFailureBanner
import com.example.mpod.ui.components.ModalScreenMobile
import com.example.mpod.ui.components.MpodButton
import com.example.mpod.ui.components.MpodBottomNav
import com.example.mpod.ui.components.PageHeader
import com.example.mpod.ui.components.PlayerView
import com.example.mpod.ui.components.ShowNotesMobile
import com.example.mpod.ui.components.figmaDropShadow
import com.example.mpod.ui.navigation.Screen
import com.example.mpod.playback.PlaybackService
import com.example.mpod.ui.theme.MpodTheme
import com.example.mpod.ui.util.formatEpisodeDuration
import com.example.mpod.ui.util.formatProgressTime
import com.example.mpod.ui.util.formatTotalDuration
import kotlinx.coroutines.delay
import kotlin.math.abs

@Composable
fun HomeRoute(
    refreshKey: Int = 0,
    onAddRssFeed: () -> Unit = {},
    onImportOpml: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var controller by remember { mutableStateOf<MediaController?>(null) }
    var playbackState by remember { mutableStateOf(HomePlaybackUiState()) }
    val controllerFuture = remember(context) {
        MediaController.Builder(
            context,
            SessionToken(context, ComponentName(context, PlaybackService::class.java))
        ).buildAsync()
    }

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(controllerFuture) {
        controllerFuture.addListener(
            { runCatching { controllerFuture.get() }.onSuccess { controller = it } },
            ContextCompat.getMainExecutor(context)
        )
        onDispose {
            controller = null
            if (controllerFuture.isDone) {
                runCatching { controllerFuture.get().release() }
            } else {
                controllerFuture.cancel(true)
            }
        }
    }

    LaunchedEffect(refreshKey) {
        if (refreshKey > 0) viewModel.refresh()
    }

    LaunchedEffect(controller, state.activeEpisodeId, state.queue) {
        while (true) {
            val player = controller
            val episodeId = player?.currentMediaItem?.mediaId?.toIntOrNull()
                ?: state.activeEpisodeId
                ?: state.queue.firstOrNull()?.id
            val episode = state.queue.firstOrNull { it.id == episodeId }
            val durationMs = player?.duration?.takeIf { it > 0 }
                ?: ((episode?.durationSeconds ?: 0) * 1_000L)
            playbackState = HomePlaybackUiState(
                currentEpisodeId = episodeId,
                positionSeconds = ((player?.currentPosition ?: episode?.playbackPositionSeconds?.times(1_000L) ?: 0L) / 1_000L)
                    .toInt().coerceAtLeast(0),
                durationSeconds = (durationMs / 1_000L).toInt().coerceAtLeast(0),
                isPlaying = player?.isPlaying == true,
                speedLabel = player?.playbackParameters?.speed.toSpeedLabel(),
                errorMessage = player?.playerError?.let {
                    "Could not play this episode. Check its audio source and try again."
                }
            )
            delay(if (player?.isPlaying == true) 500 else 1_500)
        }
    }

    LaunchedEffect(playbackState.currentEpisodeId) {
        val episodeId = playbackState.currentEpisodeId ?: return@LaunchedEffect
        if (episodeId != state.activeEpisodeId) {
            delay(500)
            viewModel.refresh()
        }
    }

    HomeScreen(
        state = state,
        playbackState = playbackState,
        onPlayToggle = {
            controller?.let { player ->
                if (player.isPlaying) {
                    player.pause()
                } else {
                    if (player.playerError != null) player.prepare()
                    player.play()
                }
            }
        },
        onSeekBy = { seconds ->
            controller?.let { player ->
                val durationMs = player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
                val nextPosition = (player.currentPosition + seconds * 1_000L)
                    .coerceIn(0L, durationMs)
                player.seekTo(nextPosition)
            }
        },
        onSpeedChange = { speed ->
            speed.toFloatOrNull()?.let { speedValue ->
                controller?.setPlaybackSpeed(speedValue)
            }
        },
        onPlayEpisode = { episodeId ->
            controller?.let { player ->
                val index = (0 until player.mediaItemCount)
                    .firstOrNull { player.getMediaItemAt(it).mediaId == episodeId.toString() }
                if (index != null) {
                    player.seekToDefaultPosition(index)
                    player.play()
                }
            }
        },
        onAddRssFeed = onAddRssFeed,
        onImportOpml = onImportOpml,
        onMoveEpisode = viewModel::moveEpisode,
        onRemoveEpisodeFromPlaylist = viewModel::removeEpisodeFromPlaylist,
        onSetEpisodeListened = viewModel::setEpisodeListened,
        onDownloadEpisode = viewModel::downloadEpisode,
        onDismissDownloadFailure = viewModel::dismissDownloadFailure
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
    onPlayEpisode: (Int) -> Unit = {},
    onAddRssFeed: () -> Unit = {},
    onImportOpml: () -> Unit = {},
    onMoveEpisode: (episodeId: Int, offset: Int) -> Unit = { _, _ -> },
    onRemoveEpisodeFromPlaylist: (Int) -> Unit = {},
    onSetEpisodeListened: (episodeId: Int, isListened: Boolean) -> Unit = { _, _ -> },
    onDownloadEpisode: (Int) -> Unit = {},
    onDismissDownloadFailure: () -> Unit = {}
) {
    var showNotesEpisode by remember { mutableStateOf<HomeEpisodeUi?>(null) }
    var draggedEpisodeId by remember { mutableStateOf<Int?>(null) }
    var dragAccumulatorPx by remember { mutableStateOf(0f) }
    val reorderStepPx = with(LocalDensity.current) { 80.dp.toPx() }
    val currentEpisode = state.queue.firstOrNull { it.id == playbackState.currentEpisodeId }
        ?: state.queue.firstOrNull()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            when {
                state.isLoading -> {
                    item {
                        PageHeader(
                            title = "Now playing",
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                    }
                    item { StatusCard(message = "Loading playlist") }
                }

                state.errorMessage != null -> {
                    item {
                        PageHeader(
                            title = "Now playing",
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                    }
                    item { StatusCard(message = state.errorMessage) }
                }

                !state.hasPodcasts -> {
                    item {
                        PageHeader(
                            title = "No podcasts",
                            subtitle = "Start with one RSS feed or import subscriptions from another app.",
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                    }
                    item {
                        NoPodcastsEmptyState(
                            onAddRssFeed = onAddRssFeed,
                            onImportOpml = onImportOpml,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                currentEpisode == null -> {
                    item {
                        PageHeader(
                            title = "Now playing",
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                    }
                    item { StatusCard(message = "Playlist is empty") }
                }

                else -> {
                    item {
                        PageHeader(
                            title = "Now playing",
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                    }

                    state.actionErrorMessage?.let { message ->
                        item {
                            StatusCard(
                                message = message,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )
                        }
                    }

                    playbackState.errorMessage?.let { message ->
                        item {
                            StatusCard(
                                message = message,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )
                        }
                    }

                    item {
                        PlayerView(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
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
                    }

                    item {
                        QueueSummaryCard(
                            text = queueSummary(state.queue),
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }

                    itemsIndexed(
                        items = state.queue,
                        key = { _, episode -> episode.id }
                    ) { index, episode ->
                        val isDragging = draggedEpisodeId == episode.id
                        EpisodeRow(
                            title = episode.title,
                            podcastName = episode.podcastTitle,
                            duration = formatEpisodeDuration(episode.durationSeconds),
                            isPlaying = episode.id == currentEpisode.id,
                            inPlaylist = true,
                            isListened = episode.isListened,
                            downloaded = episode.downloaded,
                            isDownloading = episode.id in state.downloadingEpisodeIds,
                            actionsEnabled = episode.id !in state.busyEpisodeIds,
                            canMoveUp = index > 0,
                            canMoveDown = index < state.queue.lastIndex,
                            showDragHandle = true,
                            compactPlaybackMenu = true,
                            compactPlaybackActionLabel = if (
                                episode.id == currentEpisode.id && playbackState.isPlaying
                            ) {
                                "Pause"
                            } else {
                                "Play"
                            },
                            statusTextOverride = if (episode.id == currentEpisode.id) {
                                "${episode.podcastTitle} · now playing"
                            } else {
                                episode.podcastTitle
                            },
                            modifier = Modifier
                                .padding(bottom = 4.dp)
                                .alpha(if (isDragging) 0.82f else 1f)
                                .then(
                                    if (episode.id in state.busyEpisodeIds || state.queue.size < 2) {
                                        Modifier
                                    } else {
                                        Modifier.pointerInput(episode.id, index, state.queue.size) {
                                            detectDragGesturesAfterLongPress(
                                                onDragStart = {
                                                    draggedEpisodeId = episode.id
                                                    dragAccumulatorPx = 0f
                                                },
                                                onDragCancel = {
                                                    draggedEpisodeId = null
                                                    dragAccumulatorPx = 0f
                                                },
                                                onDragEnd = {
                                                    draggedEpisodeId = null
                                                    dragAccumulatorPx = 0f
                                                },
                                                onDrag = { change, dragAmount ->
                                                    change.consume()
                                                    dragAccumulatorPx += dragAmount.y
                                                    if (abs(dragAccumulatorPx) >= reorderStepPx) {
                                                        val offset = if (dragAccumulatorPx < 0f) -1 else 1
                                                        onMoveEpisode(episode.id, offset)
                                                        dragAccumulatorPx -= reorderStepPx * offset
                                                    }
                                                }
                                            )
                                        }
                                    }
                                ),
                            onClick = { onPlayEpisode(episode.id) },
                            onAction = { action ->
                                when (action) {
                                    EpisodeRowAction.Play -> {
                                        if (episode.id == currentEpisode.id) onPlayToggle()
                                        else onPlayEpisode(episode.id)
                                    }
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

        showNotesEpisode?.let { episode ->
            ModalScreenMobile {
                ShowNotesMobile(
                    podcastTitle = "${episode.podcastTitle} - ${episode.title}",
                    notes = episode.summary?.takeIf { it.isNotBlank() } ?: "No show notes for this episode.",
                    onClose = { showNotesEpisode = null }
                )
            }
        }

        state.downloadFailure?.let { failure ->
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
    }
}

@Composable
private fun QueueSummaryCard(
    text: String,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier
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
                text = text,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
    val currentEpisodeId: Int? = null,
    val positionSeconds: Int = 0,
    val durationSeconds: Int = 0,
    val isPlaying: Boolean = false,
    val speedLabel: String = "1.3",
    val errorMessage: String? = null
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

private fun Float?.toSpeedLabel(): String = when (this) {
    0.5f -> "0.5"
    0.75f -> "0.75"
    1f -> "1.0"
    1.3f -> "1.3"
    1.5f -> "1.5"
    2f -> "2.0"
    else -> "1.3"
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
                durationSeconds = 54 * 60,
                playbackPositionSeconds = 0,
                isListened = false,
                downloaded = false,
                summary = "A story about loyalty cards, UX traps, and the tiny design decisions that become habits."
            ),
            HomeEpisodeUi(
                id = 2,
                title = "How public transit maps teach invisible habits",
                podcastTitle = "Decoder Ring",
                durationSeconds = 36 * 60,
                playbackPositionSeconds = 0,
                isListened = false,
                downloaded = false,
                summary = "Transit maps look simple, but the choices behind them shape how people move through cities."
            ),
            HomeEpisodeUi(
                id = 3,
                title = "The app menu nobody understands but everyone...",
                podcastTitle = "Decoder Ring",
                durationSeconds = 43 * 60,
                playbackPositionSeconds = 0,
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

@Preview(
    name = "Home loading / 360",
    widthDp = 360,
    heightDp = 800,
    showBackground = true
)
@Composable
private fun HomeLoadingPreview() {
    MpodTheme {
        HomePreviewShell {
            HomeScreen(state = HomeUiState(isLoading = true))
        }
    }
}

@Preview(
    name = "Home load error / 360",
    widthDp = 360,
    heightDp = 800,
    showBackground = true
)
@Composable
private fun HomeLoadErrorPreview() {
    MpodTheme {
        HomePreviewShell {
            HomeScreen(state = HomeUiState(errorMessage = "Could not load playlist."))
        }
    }
}

@Preview(
    name = "Home empty playlist / 360",
    widthDp = 360,
    heightDp = 800,
    showBackground = true
)
@Composable
private fun HomeEmptyPlaylistPreview() {
    MpodTheme {
        HomePreviewShell {
            HomeScreen(
                state = HomeUiState(
                    hasPodcasts = true,
                    queue = emptyList()
                )
            )
        }
    }
}

@Composable
private fun HomePreviewShell(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Box(modifier = Modifier.weight(1f)) {
            content()
        }
        MpodBottomNav(
            currentRoute = Screen.Home.route,
            onNavigate = {},
            modifier = Modifier.padding(horizontal = 20.dp)
        )
    }
}
