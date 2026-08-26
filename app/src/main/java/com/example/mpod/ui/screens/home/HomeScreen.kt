package com.example.mpod.ui.screens.home

import android.content.ComponentName
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.mpod.R
import com.example.mpod.playback.PlaybackService
import com.example.mpod.ui.components.ModalScreenMobile
import com.example.mpod.ui.components.MpodButton
import com.example.mpod.ui.components.PageHeader
import com.example.mpod.ui.components.PlayerPlaylistItem
import com.example.mpod.ui.components.PlayerView
import com.example.mpod.ui.components.ShowNotesMobile
import com.example.mpod.ui.components.figmaDropShadow
import com.example.mpod.ui.util.formatEpisodeDuration
import com.example.mpod.ui.util.formatProgressTime
import com.example.mpod.ui.util.formatRemainingTime
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
    var playbackSummary by remember { mutableStateOf(HomePlaybackSummaryUiState()) }
    val playbackStateProvider = remember { { playbackState } }
    val latestActiveEpisodeId by rememberUpdatedState(state.activeEpisodeId)
    val latestQueue by rememberUpdatedState(state.queue)
    val controllerFuture = remember(context) {
        MediaController.Builder(
            context,
            SessionToken(context, ComponentName(context, PlaybackService::class.java))
        ).buildAsync()
    }

    fun updatePlaybackSnapshot() {
        val nextState = controller.toHomePlaybackUiState(
            activeEpisodeId = latestActiveEpisodeId,
            queue = latestQueue
        )
        playbackState = nextState

        val nextSummary = nextState.toSummary()
        if (playbackSummary != nextSummary) {
            playbackSummary = nextSummary
        }
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
        updatePlaybackSnapshot()
    }

    DisposableEffect(controller) {
        val player = controller
        if (player == null) {
            onDispose {}
        } else {
            val listener = object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) {
                    updatePlaybackSnapshot()
                }
            }
            player.addListener(listener)
            onDispose { player.removeListener(listener) }
        }
    }

    LaunchedEffect(controller) {
        while (true) {
            updatePlaybackSnapshot()
            val player = controller
            delay(
                if (player?.isPlaying == true) {
                    PLAYING_PLAYBACK_SNAPSHOT_INTERVAL_MS
                } else {
                    IDLE_PLAYBACK_SNAPSHOT_INTERVAL_MS
                }
            )
        }
    }

    LaunchedEffect(playbackSummary.currentEpisodeId) {
        val episodeId = playbackSummary.currentEpisodeId ?: return@LaunchedEffect
        if (episodeId != state.activeEpisodeId) {
            delay(PLAYBACK_ROUTE_REFRESH_DELAY_MS)
            viewModel.refresh(invalidatePlaybackQueue = false)
        }
    }

    HomeScreen(
        state = state,
        playbackSummary = playbackSummary,
        playbackStateProvider = playbackStateProvider,
        onPlayToggle = {
            controller?.let { player ->
                if (playbackIntentActive(player.playWhenReady)) {
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
        onSeekTo = { progress ->
            controller?.let { player ->
                val durationMs = player.duration.takeIf { it > 0 }
                    ?: playbackStateProvider().durationSeconds.takeIf { it > 0 }?.times(1_000L)
                if (durationMs != null) {
                    player.seekTo((durationMs * progress.coerceIn(0f, 1f)).toLong())
                }
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
        onRetryLoad = viewModel::refresh,
        onMoveEpisode = viewModel::moveEpisode,
        onRemoveEpisodeFromPlaylist = viewModel::removeEpisodeFromPlaylist
    )
}

@Composable
fun HomeScreen(
    state: HomeUiState = HomeUiState(),
    playbackSummary: HomePlaybackSummaryUiState = HomePlaybackSummaryUiState(),
    playbackStateProvider: () -> HomePlaybackUiState = { HomePlaybackUiState() },
    onPlayToggle: () -> Unit = {},
    onSeekBy: (Int) -> Unit = {},
    onSeekTo: (Float) -> Unit = {},
    onSpeedChange: (String) -> Unit = {},
    onPlayEpisode: (Long) -> Unit = {},
    onAddRssFeed: () -> Unit = {},
    onImportOpml: () -> Unit = {},
    onRetryLoad: () -> Unit = {},
    onMoveEpisode: (episodeId: Long, offset: Int) -> Unit = { _, _ -> },
    onRemoveEpisodeFromPlaylist: (Long) -> Unit = {}
) {
    var showNotesEpisode by remember { mutableStateOf<HomeEpisodeUi?>(null) }
    var draggedEpisodeId by remember { mutableStateOf<Long?>(null) }
    var dragAccumulatorPx by remember { mutableStateOf(0f) }
    val reorderStepPx = with(LocalDensity.current) { 80.dp.toPx() }
    val currentEpisode = state.queue.firstOrNull { it.id == playbackSummary.currentEpisodeId }
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
                    item {
                        StatusCard(
                            message = state.errorMessage,
                            actionLabel = "Retry",
                            onAction = onRetryLoad
                        )
                    }
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
                    item { PlaylistEmptyState() }
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

                    playbackSummary.errorMessage?.let { message ->
                        item {
                            StatusCard(
                                message = message,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )
                        }
                    }

                    item {
                        val latestPlayerEpisode = rememberUpdatedState(currentEpisode)
                        val playerNotesClick = remember {
                            { showNotesEpisode = latestPlayerEpisode.value }
                        }
                        HomePlayerCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            episode = currentEpisode,
                            playbackStateProvider = playbackStateProvider,
                            onSpeedChange = onSpeedChange,
                            onPlayClick = onPlayToggle,
                            onSeekBackward = { onSeekBy(-15) },
                            onSeekForward = { onSeekBy(30) },
                            onSeekTo = onSeekTo,
                            onNotesClick = playerNotesClick
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
                        key = { _, episode -> episode.id },
                        contentType = { _, _ -> "episode_row" }
                    ) { index, episode ->
                        val isDragging = draggedEpisodeId == episode.id
                        val latestCurrentEpisodeId = rememberUpdatedState(currentEpisode.id)
                        val latestOnPlayToggle = rememberUpdatedState(onPlayToggle)
                        val latestOnPlayEpisode = rememberUpdatedState(onPlayEpisode)
                        val latestOnRemoveEpisodeFromPlaylist = rememberUpdatedState(onRemoveEpisodeFromPlaylist)
                        val latestOnMoveEpisode = rememberUpdatedState(onMoveEpisode)
                        val latestReorderStepPx by rememberUpdatedState(reorderStepPx)
                        val rowClick = remember(episode.id) {
                            { latestOnPlayEpisode.value(episode.id) }
                        }
                        val playToggleClick = remember(episode.id) {
                            {
                                if (episode.id == latestCurrentEpisodeId.value) {
                                    latestOnPlayToggle.value()
                                } else {
                                    latestOnPlayEpisode.value(episode.id)
                                }
                            }
                        }
                        val removeClick = remember(episode.id) {
                            { latestOnRemoveEpisodeFromPlaylist.value(episode.id) }
                        }
                        PlayerPlaylistItem(
                            title = episode.title,
                            podcastName = episode.podcastTitle,
                            duration = formatEpisodeDuration(episode.durationSeconds),
                            isCurrent = episode.id == currentEpisode.id,
                            isPlaying = episode.id == currentEpisode.id && playbackSummary.isPlaying,
                            downloaded = episode.downloaded,
                            actionsEnabled = episode.id !in state.busyEpisodeIds,
                            modifier = Modifier
                                .padding(bottom = 4.dp)
                                .alpha(if (isDragging) 0.82f else 1f)
                                .then(
                                    if (episode.id in state.busyEpisodeIds || state.queue.size < 2) {
                                        Modifier
                                    } else {
                                        Modifier.pointerInput(episode.id) {
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
                                                    if (abs(dragAccumulatorPx) >= latestReorderStepPx) {
                                                        val offset = if (dragAccumulatorPx < 0f) -1 else 1
                                                        latestOnMoveEpisode.value(episode.id, offset)
                                                        dragAccumulatorPx -= latestReorderStepPx * offset
                                                    }
                                                }
                                            )
                                        }
                                    }
                                ),
                            onClick = rowClick,
                            onRemoveFromPlaylist = removeClick,
                            onPlayToggle = playToggleClick
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
    }
}

@Composable
private fun QueueSummaryCard(
    text: String,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
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

@Composable
private fun HomePlayerCard(
    episode: HomeEpisodeUi,
    playbackStateProvider: () -> HomePlaybackUiState,
    onSpeedChange: (String) -> Unit,
    onPlayClick: () -> Unit,
    onSeekBackward: () -> Unit,
    onSeekForward: () -> Unit,
    onSeekTo: (Float) -> Unit,
    onNotesClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val playbackState = playbackStateProvider()

    PlayerView(
        modifier = modifier,
        title = episode.title,
        podcastTitle = episode.podcastTitle,
        elapsedLabel = formatProgressTime(playbackState.positionSeconds),
        durationLabel = formatRemainingTime(
            durationSeconds = playbackState.durationSeconds,
            positionSeconds = playbackState.positionSeconds
        ),
        progress = playbackState.progress,
        isPlaying = playbackState.isPlaying,
        speedLabel = playbackState.speedLabel,
        onSpeedChange = onSpeedChange,
        onPlayClick = onPlayClick,
        onSeekBackward = onSeekBackward,
        onSeekForward = onSeekForward,
        onSeekTo = onSeekTo,
        onNotesClick = onNotesClick
    )
}

@Composable
private fun PlaylistEmptyState(modifier: Modifier = Modifier) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 228.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 228.dp)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Playlist is empty",
                fontSize = 18.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "Add episodes from Subscriptions to start listening.",
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun StatusCard(
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
            actionLabel?.let { label ->
                MpodButton(
                    text = label,
                    height = 32.dp,
                    radius = 6.dp,
                    onClick = onAction
                )
            }
        }
    }
}

private fun queueSummary(episodes: List<HomeEpisodeUi>): String {
    val totalSeconds = episodes.sumOf { it.durationSeconds ?: 0 }
    val episodeLabel = if (episodes.size == 1) "episode" else "episodes"
    return "${episodes.size} $episodeLabel · ${formatTotalDuration(totalSeconds)}"
}

data class HomePlaybackSummaryUiState(
    val currentEpisodeId: Long? = null,
    val isPlaying: Boolean = false,
    val errorMessage: String? = null
)

data class HomePlaybackUiState(
    val currentEpisodeId: Long? = null,
    val positionSeconds: Int = 0,
    val durationSeconds: Int = 0,
    val isPlaying: Boolean = false,
    val speedLabel: String = "1.0",
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

private fun HomePlaybackUiState.toSummary(): HomePlaybackSummaryUiState {
    return HomePlaybackSummaryUiState(
        currentEpisodeId = currentEpisodeId,
        isPlaying = isPlaying,
        errorMessage = errorMessage
    )
}

private fun Player?.toHomePlaybackUiState(
    activeEpisodeId: Long?,
    queue: List<HomeEpisodeUi>
): HomePlaybackUiState {
    val episodeId = this?.currentMediaItem?.mediaId?.toLongOrNull()
        ?: activeEpisodeId
        ?: queue.firstOrNull()?.id
    val episode = queue.firstOrNull { it.id == episodeId }
    val durationMs = this?.duration?.takeIf { it > 0 }
        ?: ((episode?.durationSeconds ?: 0) * 1_000L)
    val positionMs = this?.currentPosition
        ?: (episode?.playbackPositionSeconds?.toLong()?.times(1_000L) ?: 0L)

    return HomePlaybackUiState(
        currentEpisodeId = episodeId,
        positionSeconds = (positionMs / 1_000L).toInt().coerceAtLeast(0),
        durationSeconds = (durationMs / 1_000L).toInt().coerceAtLeast(0),
        isPlaying = playbackIntentActive(this?.playWhenReady == true),
        speedLabel = this?.playbackParameters?.speed.toSpeedLabel(),
        errorMessage = this?.playerError?.let {
            "Could not play this episode. Check its audio source and try again."
        }
    )
}

internal fun playbackIntentActive(playWhenReady: Boolean): Boolean = playWhenReady

private fun Float?.toSpeedLabel(): String = when (this) {
    0.5f -> "0.5"
    0.75f -> "0.75"
    1f -> "1.0"
    1.3f -> "1.3"
    1.5f -> "1.5"
    2f -> "2.0"
    else -> "1.0"
}

private const val PLAYING_PLAYBACK_SNAPSHOT_INTERVAL_MS = 500L
private const val IDLE_PLAYBACK_SNAPSHOT_INTERVAL_MS = 1_500L
private const val PLAYBACK_ROUTE_REFRESH_DELAY_MS = 500L

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
                        painter = painterResource(id = R.drawable.ic_huge_podcast),
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
