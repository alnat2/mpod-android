package com.example.mpod.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import com.example.mpod.data.network.BackendConfig
import com.example.mpod.data.network.MpodApi
import com.example.mpod.data.network.PersistentCookieJar
import com.example.mpod.data.network.model.PlaybackQueueEpisodeDto
import com.example.mpod.data.network.model.PlaybackUpdateRequest
import com.example.mpod.data.network.model.PlaybackUpdateResponse
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl.Companion.toHttpUrl
import com.google.common.collect.ImmutableList
import java.time.Instant
import javax.inject.Inject
import kotlin.math.roundToInt

private const val NETWORK_TIMEOUT_MS = 30_000
private const val PLAYBACK_SYNC_INTERVAL_MS = 15_000L

@AndroidEntryPoint
class PlaybackService : MediaSessionService() {
    @Inject lateinit var api: MpodApi
    @Inject lateinit var backendConfig: BackendConfig
    @Inject lateinit var cookieJar: PersistentCookieJar
    @Inject lateinit var queueInvalidator: PlaybackQueueInvalidator

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var player: ExoPlayer
    private var mediaSession: MediaSession? = null
    private var syncJob: Job? = null
    private var previousEpisodeId: Int? = null
    private var lastCompletedEpisodeId: Int? = null
    private var applyingServerSpeed = false
    private val queueReconciliationMutex = Mutex()
    private lateinit var playbackSyncManager: PlaybackSyncManager

    @UnstableApi
    override fun onCreate() {
        super.onCreate()
        setMediaNotificationProvider(PlayPauseOnlyMediaNotificationProvider(this))
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(NETWORK_TIMEOUT_MS)
            .setReadTimeoutMs(NETWORK_TIMEOUT_MS)
            .setDefaultRequestProperties(audioRequestHeaders())

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
            .build()
        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
            .also { it.addListener(playerListener) }
        mediaSession = MediaSession.Builder(this, player)
            .setCallback(mediaSessionCallback)
            .build()

        playbackSyncManager = PlaybackSyncManager(
            transport = ApiPlaybackSyncTransport(api),
            store = SharedPreferencesPendingPlaybackSyncStore(this),
            scope = serviceScope,
            onRetriedCompletion = ::handleRetriedCompletion
        )
        val pendingSpeed = playbackSyncManager.pendingSnapshot().speedLabel
        pendingSpeed.toPlaybackSpeedOrNull()?.let { speed ->
            applyingServerSpeed = true
            player.playbackParameters = PlaybackParameters(speed)
            applyingServerSpeed = false
        }
        serviceScope.launch {
            playbackSyncManager.flushPendingOnce()
            if (pendingSpeed == null) loadAuthoritativeSpeed()
            loadInitialQueue()
            playbackSyncManager.start()
        }
        serviceScope.launch {
            queueInvalidator.events.collectLatest {
                reconcileSharedStateWithBackend()
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    @UnstableApi
    private val mediaSessionCallback = object : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val result = MediaSession.ConnectionResult.AcceptedResultBuilder(session)
            if (session.isMediaNotificationController(controller)) {
                result.setAvailablePlayerCommands(mediaNotificationPlayerCommands())
            }
            return result.build()
        }
    }

    override fun onDestroy() {
        syncJob?.cancel()
        serviceScope.cancel()
        mediaSession?.release()
        mediaSession = null
        player.removeListener(playerListener)
        player.release()
        super.onDestroy()
    }

    private suspend fun loadAuthoritativeSpeed() {
        val pendingSpeed = playbackSyncManager.pendingSnapshot().speedLabel
        val response = runCatching { api.getSettings() }.getOrNull() ?: return
        val speed = resolvePlaybackSpeedForReconciliation(
            backendSpeedLabel = response.body()?.settings?.playbackSpeed,
            pendingSpeedLabel = pendingSpeed
        ) ?: return
        applyingServerSpeed = true
        player.playbackParameters = PlaybackParameters(speed)
        applyingServerSpeed = false
    }

    private suspend fun reconcileSharedStateWithBackend() {
        loadAuthoritativeSpeed()
        reconcileQueueWithBackend()
    }

    private suspend fun loadInitialQueue() {
        reconcileQueueWithBackend()
    }

    private suspend fun reconcileQueueWithBackend(
        preferredEpisodeId: Int? = null,
        preferFirstEpisode: Boolean = false,
        forcePlayPreferred: Boolean = false
    ): Unit {
        queueReconciliationMutex.withLock {
            val response = runCatching { api.getPlaybackQueue() }.getOrNull()
            val payload = response?.takeIf { it.isSuccessful }?.body() ?: return@withLock
            val backendQueue = payload.queue ?: return@withLock
            val pending = playbackSyncManager.pendingSnapshot()
            val pendingByEpisode = pending.playbackUpdates.associateBy { it.episodeId }
            val queue = backendQueue.filterNot { pendingByEpisode[it.id]?.completed == true }
            val currentEpisodeId = currentEpisodeId()
            val target = resolveQueuePlaybackTarget(
                queue = queue.map {
                    QueueEpisodeState(
                        episodeId = it.id,
                        savedPositionMs = (
                            pendingByEpisode[it.id]?.positionSeconds
                                ?: it.playback?.positionSeconds
                                ?: 0
                            ) * 1_000L
                    )
                },
                backendActiveEpisodeId = pending.activeEpisodeId
                    ?: payload.activePlayback?.episodeId,
                currentEpisodeId = currentEpisodeId,
                currentPositionMs = player.currentPosition,
                currentPlayWhenReady = player.playWhenReady,
                preferredEpisodeId = preferredEpisodeId,
                preferFirstEpisode = preferFirstEpisode,
                forcePlayPreferred = forcePlayPreferred
            )

            if (target == null) {
                if (player.mediaItemCount == 0) return@withLock
                syncJob?.cancel()
                syncJob = null
                player.removeListener(playerListener)
                player.stop()
                player.clearMediaItems()
                previousEpisodeId = null
                player.addListener(playerListener)
                return@withLock
            }

            val backendQueueEpisodeIds = queue.map { it.id }
            val currentQueueEpisodeIds = (0 until player.mediaItemCount).mapNotNull { index ->
                player.getMediaItemAt(index).mediaId.toIntOrNull()
            }
            val requiresRebuild = requiresPlayerQueueRebuild(
                currentQueueEpisodeIds = currentQueueEpisodeIds,
                backendQueueEpisodeIds = backendQueueEpisodeIds,
                currentEpisodeId = currentEpisodeId,
                targetEpisodeId = target.episodeId,
                preferredEpisodeId = preferredEpisodeId
            )
            if (!requiresRebuild) {
                if (target.playWhenReady) {
                    startPeriodicSync()
                } else {
                    syncJob?.cancel()
                    syncJob = null
                }
                return@withLock
            }

            if (
                shouldSyncCurrentBeforeQueueReconciliation(
                    currentEpisodeId = currentEpisodeId,
                    queuedEpisodeIds = backendQueueEpisodeIds.toSet(),
                    isPlaying = player.isPlaying
                )
            ) {
                syncCurrentPlayback()
            }

            val targetIndex = queue.indexOfFirst { it.id == target.episodeId }
            player.removeListener(playerListener)
            player.setMediaItems(queue.map { it.toMediaItem() }, targetIndex, target.positionMs)
            previousEpisodeId = target.episodeId
            lastCompletedEpisodeId = null
            player.prepare()
            player.playWhenReady = target.playWhenReady
            player.addListener(playerListener)

            if (target.playWhenReady) {
                startPeriodicSync()
                if (
                    forcePlayPreferred &&
                    (preferredEpisodeId == target.episodeId || preferFirstEpisode)
                ) {
                    playbackSyncManager.submitActive(target.episodeId)
                }
            } else {
                syncJob?.cancel()
                syncJob = null
            }
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                currentEpisodeId()?.let { episodeId ->
                    serviceScope.launch {
                        playbackSyncManager.submitActive(episodeId)
                    }
                }
                startPeriodicSync()
            } else {
                syncJob?.cancel()
                syncJob = null
                if (player.playbackState != Player.STATE_ENDED) {
                    serviceScope.launch { syncCurrentPlayback() }
                }
            }
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                if (oldPosition.mediaItemIndex == newPosition.mediaItemIndex) {
                    serviceScope.launch { syncCurrentPlayback(didSeek = true) }
                } else {
                    val oldEpisodeId = oldPosition.mediaItemIndex
                        .takeIf { it in 0 until player.mediaItemCount }
                        ?.let(player::getMediaItemAt)
                        ?.mediaId
                        ?.toIntOrNull()
                    if (oldEpisodeId != null) {
                        serviceScope.launch {
                            syncPlayback(
                                episodeId = oldEpisodeId,
                                positionSeconds = (oldPosition.positionMs / 1_000L).toInt(),
                                durationSeconds = durationForEpisode(oldEpisodeId)
                            )
                        }
                    }
                }
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val nextEpisodeId = mediaItem?.mediaId?.toIntOrNull()
            val finishedEpisodeId = previousEpisodeId
            previousEpisodeId = nextEpisodeId
            lastCompletedEpisodeId = null

            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && finishedEpisodeId != null) {
                serviceScope.launch {
                    val completion = completeEpisode(finishedEpisodeId)
                    if (completion != null) {
                        reconcileQueueWithBackend(
                            preferFirstEpisode = true,
                            forcePlayPreferred = true
                        )
                        queueInvalidator.refreshHome()
                    }
                }
            } else if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK && nextEpisodeId != null) {
                serviceScope.launch {
                    playbackSyncManager.submitActive(nextEpisodeId)
                }
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState != Player.STATE_ENDED) return
            val episodeId = currentEpisodeId() ?: return
            if (lastCompletedEpisodeId == episodeId) return
            lastCompletedEpisodeId = episodeId
            serviceScope.launch {
                val completion = completeEpisode(episodeId) ?: return@launch
                reconcileQueueWithBackend(
                    preferFirstEpisode = true,
                    forcePlayPreferred = true
                )
                queueInvalidator.refreshHome()
            }
        }

        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
            if (applyingServerSpeed) return
            val label = playbackParameters.speed.toPlaybackSpeedLabel() ?: return
            serviceScope.launch {
                playbackSyncManager.submitSpeed(label)
            }
        }
    }

    private fun startPeriodicSync() {
        syncJob?.cancel()
        syncJob = serviceScope.launch {
            while (isActive) {
                delay(PLAYBACK_SYNC_INTERVAL_MS)
                syncCurrentPlayback()
            }
        }
    }

    private suspend fun syncCurrentPlayback(didSeek: Boolean = false) {
        val episodeId = currentEpisodeId() ?: return
        val durationSeconds = currentDurationSeconds()
        val positionSeconds = (player.currentPosition / 1_000L).toInt().coerceAtLeast(0)
        syncPlayback(episodeId, positionSeconds, durationSeconds, didSeek)
    }

    private suspend fun syncPlayback(
        episodeId: Int,
        positionSeconds: Int,
        durationSeconds: Int,
        didSeek: Boolean = false
    ) {
        playbackSyncManager.submitPlayback(
            playbackRequest(episodeId, positionSeconds, durationSeconds, didSeek = didSeek)
        )
    }

    private suspend fun completeEpisode(episodeId: Int): PlaybackUpdateResponse? {
        val durationSeconds = durationForEpisode(episodeId)
        return playbackSyncManager.submitPlayback(
            playbackRequest(
                episodeId = episodeId,
                positionSeconds = durationSeconds,
                durationSeconds = durationSeconds,
                completed = true
            )
        )
    }

    private suspend fun handleRetriedCompletion(
        request: PlaybackUpdateRequest,
        response: PlaybackUpdateResponse
    ) {
        val playbackEnded = player.playbackState == Player.STATE_ENDED
        val shouldResumePlayback = shouldResumeAfterRetriedCompletion(
            playbackEnded = playbackEnded,
            completedEpisodeId = request.episodeId,
            currentEpisodeId = currentEpisodeId()
        )
        if (shouldResumePlayback) {
            reconcileQueueWithBackend(
                preferFirstEpisode = true,
                forcePlayPreferred = true
            )
        } else {
            reconcileQueueWithBackend()
        }
        queueInvalidator.refreshHome()
    }

    private fun currentEpisodeId(): Int? = player.currentMediaItem?.mediaId?.toIntOrNull()

    private fun currentDurationSeconds(): Int {
        val playerDuration = player.duration.takeIf { it > 0 }?.div(1_000L)?.toInt()
        val metadataDuration = player.currentMediaItem?.mediaMetadata?.extras
            ?.getInt(EXTRA_DURATION_SECONDS, 0)
        return (playerDuration ?: metadataDuration ?: 0).coerceAtLeast(0)
    }

    private fun durationForEpisode(episodeId: Int): Int {
        val item = (0 until player.mediaItemCount)
            .map(player::getMediaItemAt)
            .firstOrNull { it.mediaId == episodeId.toString() }
        return item?.mediaMetadata?.extras?.getInt(EXTRA_DURATION_SECONDS, 0)
            ?.coerceAtLeast(0)
            ?: 0
    }

    private fun PlaybackQueueEpisodeDto.toMediaItem(): MediaItem {
        val extras = android.os.Bundle().apply {
            putInt(EXTRA_DURATION_SECONDS, duration?.roundToInt() ?: 0)
        }
        return MediaItem.Builder()
            .setMediaId(id.toString())
            .setUri("${backendConfig.baseUrl}api/episodes/$id/audio")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title.orEmpty())
                    .setArtist(podcastTitle.orEmpty())
                    .setExtras(extras)
                    .build()
            )
            .build()
    }

    private fun audioRequestHeaders(): Map<String, String> {
        val cookies = cookieJar.loadForRequest(backendConfig.baseUrl.toHttpUrl())
        if (cookies.isEmpty()) return emptyMap()
        return mapOf("Cookie" to cookies.joinToString("; ") { "${it.name}=${it.value}" })
    }

    companion object {
        const val EXTRA_DURATION_SECONDS = "com.prod.mpod.duration_seconds"
    }
}

@UnstableApi
private class PlayPauseOnlyMediaNotificationProvider(
    context: android.content.Context
) : DefaultMediaNotificationProvider(context) {
    override fun getMediaButtons(
        session: MediaSession,
        playerCommands: Player.Commands,
        customLayout: ImmutableList<CommandButton>,
        showPauseButton: Boolean
    ): ImmutableList<CommandButton> =
        ImmutableList.copyOf(
            super.getMediaButtons(
                session,
                playerCommands,
                customLayout,
                showPauseButton
            ).filter { it.playerCommand == Player.COMMAND_PLAY_PAUSE }
        )
}

@UnstableApi
internal fun mediaNotificationPlayerCommands(): Player.Commands =
    MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS
        .buildUpon()
        .removeAll(
            Player.COMMAND_SEEK_TO_PREVIOUS,
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_NEXT,
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM
        )
        .build()

internal fun shouldResumeAfterRetriedCompletion(
    playbackEnded: Boolean,
    completedEpisodeId: Int,
    currentEpisodeId: Int?
): Boolean = playbackEnded && currentEpisodeId == completedEpisodeId

internal fun shouldSyncCurrentBeforeQueueReconciliation(
    currentEpisodeId: Int?,
    queuedEpisodeIds: Set<Int>,
    isPlaying: Boolean
): Boolean = isPlaying && currentEpisodeId != null && currentEpisodeId in queuedEpisodeIds

internal fun playbackRequest(
    episodeId: Int,
    positionSeconds: Int,
    durationSeconds: Int,
    completed: Boolean = false,
    didSeek: Boolean = false,
    clientUpdatedAt: String = Instant.now().toString()
) = PlaybackUpdateRequest(
    episodeId = episodeId,
    positionSeconds = positionSeconds.coerceAtLeast(0),
    durationSeconds = durationSeconds.coerceAtLeast(0),
    completed = completed,
    didSeek = didSeek,
    clientUpdatedAt = clientUpdatedAt
)

internal fun String?.toPlaybackSpeedOrNull(): Float? = when (this) {
    "Speed 0.5x" -> 0.5f
    "Speed 0.75x" -> 0.75f
    "Speed 1x" -> 1f
    "Speed 1.3x" -> 1.3f
    "Speed 1.5x" -> 1.5f
    "Speed 2x" -> 2f
    else -> null
}

internal fun resolvePlaybackSpeedForReconciliation(
    backendSpeedLabel: String?,
    pendingSpeedLabel: String?
): Float? = if (pendingSpeedLabel == null) {
    backendSpeedLabel.toPlaybackSpeedOrNull()
} else {
    null
}

internal fun Float.toPlaybackSpeedLabel(): String? = when (this) {
    0.5f -> "Speed 0.5x"
    0.75f -> "Speed 0.75x"
    1f -> "Speed 1x"
    1.3f -> "Speed 1.3x"
    1.5f -> "Speed 1.5x"
    2f -> "Speed 2x"
    else -> null
}
