package com.example.mpod.playback

import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.mpod.BuildConfig
import com.example.mpod.data.local.dao.EpisodeDao
import com.example.mpod.data.local.model.PlaylistItemWithEpisode
import com.example.mpod.data.local.preferences.AppSettingsDataStore
import com.example.mpod.data.network.ProxyHttpClientFactory
import com.example.mpod.data.repository.PlaylistRepository
import com.google.common.collect.ImmutableList
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import javax.inject.Inject

private const val POSITION_SAVE_INTERVAL_MS = 5_000L
private const val POSITION_SYNC_THRESHOLD_MS = 1_000L

@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    @Inject lateinit var playlistRepository: PlaylistRepository
    @Inject lateinit var episodeDao: EpisodeDao
    @Inject lateinit var appSettingsDataStore: AppSettingsDataStore
    @Inject lateinit var proxyHttpClientFactory: ProxyHttpClientFactory
    @Inject lateinit var smartListeningManager: SmartListeningManager
    @Inject lateinit var queueInvalidator: PlaybackQueueInvalidator

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var player: ExoPlayer
    private var mediaSession: MediaSession? = null
    private var savePositionJob: Job? = null
    private var previousEpisodeId: Long? = null
    private val completedEpisodeIds = HashSet<Long>()
    private var applyingSettingsSpeed = false
    private val queueReconciliationMutex = Mutex()

    @UnstableApi
    override fun onCreate() {
        super.onCreate()
        setMediaNotificationProvider(PlayPauseOnlyMediaNotificationProvider(this))

        val okHttpClient = proxyHttpClientFactory.createClient()
        val okHttpDataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
            .setUserAgent("mpoddy/${BuildConfig.VERSION_NAME} (Android Podcast Player)")
        val defaultDataSourceFactory = DefaultDataSource.Factory(this, okHttpDataSourceFactory)

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
            .build()

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(defaultDataSourceFactory))
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
            .also { it.addListener(playerListener) }

        mediaSession = MediaSession.Builder(this, player)
            .setCallback(mediaSessionCallback)
            .build()

        serviceScope.launch {
            val settings = appSettingsDataStore.settingsFlow.first()
            applyingSettingsSpeed = true
            player.playbackParameters = PlaybackParameters(settings.playbackSpeed)
            applyingSettingsSpeed = false

            loadInitialQueue()
        }

        serviceScope.launch {
            queueInvalidator.events.collectLatest {
                reconcileQueueWithDatabase()
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
        savePositionJob?.cancel()
        serviceScope.cancel()
        mediaSession?.release()
        mediaSession = null
        player.removeListener(playerListener)
        player.release()
        super.onDestroy()
    }

    private suspend fun loadInitialQueue() {
        reconcileQueueWithDatabase()
    }

    suspend fun reconcileQueueWithDatabase(
        preferredEpisodeId: Long? = null,
        preferFirstEpisode: Boolean = false,
        forcePlayPreferred: Boolean = false
    ): Unit {
        queueReconciliationMutex.withLock {
            val playlistItems = playlistRepository.getPlaylistItems()
            val settings = appSettingsDataStore.settingsFlow.first()
            val currentEpisodeId = currentEpisodeId()

            val target = resolveQueuePlaybackTarget(
                queue = playlistItems.map {
                    QueueEpisodeState(
                        episodeId = it.episode.id,
                        savedPositionMs = it.episode.playbackPositionMs
                    )
                },
                savedActiveEpisodeId = settings.activeEpisodeId,
                currentEpisodeId = currentEpisodeId,
                currentPositionMs = player.currentPosition,
                currentPlayWhenReady = player.playWhenReady,
                isPlaying = player.isPlaying,
                preferredEpisodeId = preferredEpisodeId,
                preferFirstEpisode = preferFirstEpisode,
                forcePlayPreferred = forcePlayPreferred
            )

            if (target == null) {
                if (player.mediaItemCount == 0) return@withLock
                savePositionJob?.cancel()
                savePositionJob = null
                player.removeListener(playerListener)
                player.stop()
                player.clearMediaItems()
                previousEpisodeId = null
                player.addListener(playerListener)
                return@withLock
            }

            val roomQueueEpisodeIds = playlistItems.map { it.episode.id }
            val currentQueueEpisodeIds = (0 until player.mediaItemCount).mapNotNull { index ->
                player.getMediaItemAt(index).mediaId.toLongOrNull()
            }

            val requiresRebuild = requiresPlayerQueueRebuild(
                currentQueueEpisodeIds = currentQueueEpisodeIds,
                roomQueueEpisodeIds = roomQueueEpisodeIds,
                currentEpisodeId = currentEpisodeId,
                targetEpisodeId = target.episodeId,
                preferredEpisodeId = preferredEpisodeId
            )

            if (!requiresRebuild) {
                if (!player.isPlaying && !player.playWhenReady) {
                    val positionDelta = kotlin.math.abs(player.currentPosition - target.positionMs)
                    if (positionDelta >= POSITION_SYNC_THRESHOLD_MS) {
                        player.seekTo(target.positionMs)
                    }
                }
                if (target.playWhenReady) {
                    startPeriodicSave()
                } else {
                    savePositionJob?.cancel()
                    savePositionJob = null
                }
                return@withLock
            }

            val targetIndex = playlistItems.indexOfFirst { it.episode.id == target.episodeId }
            player.removeListener(playerListener)
            player.setMediaItems(playlistItems.map { it.toMediaItem() }, targetIndex, target.positionMs)
            previousEpisodeId = target.episodeId
            lastCompletedEpisodeId = null
            player.prepare()
            player.playWhenReady = target.playWhenReady
            player.addListener(playerListener)

            if (target.playWhenReady) {
                startPeriodicSave()
                appSettingsDataStore.setActiveEpisodeId(target.episodeId)
            } else {
                savePositionJob?.cancel()
                savePositionJob = null
            }
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                currentEpisodeId()?.let { episodeId ->
                    serviceScope.launch {
                        appSettingsDataStore.setActiveEpisodeId(episodeId)
                    }
                }
                startPeriodicSave()
            } else {
                savePositionJob?.cancel()
                savePositionJob = null
                if (player.playbackState != Player.STATE_ENDED) {
                    serviceScope.launch { saveCurrentPosition() }
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
                    serviceScope.launch { saveCurrentPosition() }
                } else {
                    val oldEpisodeId = oldPosition.mediaItemIndex
                        .takeIf { it in 0 until player.mediaItemCount }
                        ?.let(player::getMediaItemAt)
                        ?.mediaId
                        ?.toLongOrNull()
                    if (oldEpisodeId != null) {
                        serviceScope.launch {
                            withContext(Dispatchers.IO) {
                                episodeDao.updatePlaybackPosition(oldEpisodeId, oldPosition.positionMs)
                            }
                        }
                    }
                }
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val nextEpisodeId = mediaItem?.mediaId?.toLongOrNull()
            val finishedEpisodeId = previousEpisodeId
            previousEpisodeId = nextEpisodeId

            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && finishedEpisodeId != null) {
                handleEpisodeCompleted(finishedEpisodeId, nextEpisodeId)
            } else if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK && nextEpisodeId != null) {
                serviceScope.launch {
                    appSettingsDataStore.setActiveEpisodeId(nextEpisodeId)
                }
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState != Player.STATE_ENDED) return
        val episodeId = currentEpisodeId() ?: return
        handleEpisodeCompleted(episodeId, preferFirstEpisode = true, forcePlayPreferred = true)
    }

        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
            if (applyingSettingsSpeed) return
            serviceScope.launch {
                appSettingsDataStore.setPlaybackSpeed(playbackParameters.speed)
            }
        }
    }

    private fun startPeriodicSave() {
        savePositionJob?.cancel()
        savePositionJob = serviceScope.launch {
            while (isActive) {
                delay(POSITION_SAVE_INTERVAL_MS)
                saveCurrentPosition()
            }
        }
    }

    private suspend fun saveCurrentPosition() {
        val episodeId = currentEpisodeId() ?: return
        val pos = player.currentPosition.coerceAtLeast(0L)
        withContext(Dispatchers.IO) {
            episodeDao.updatePlaybackPosition(episodeId, pos)
        }
    }

    private suspend fun completeEpisode(episodeId: Long) {
        withContext(Dispatchers.IO) {
            episodeDao.setListened(episodeId, true)
        }
        playlistRepository.removeFromPlaylist(episodeId)
        smartListeningManager.cleanupEpisodeFile(episodeId)
    }

    private fun handleEpisodeCompleted(
        episodeId: Long,
        nextEpisodeId: Long? = null,
        preferFirstEpisode: Boolean = false,
        forcePlayPreferred: Boolean = false
    ) {
        if (!completedEpisodeIds.add(episodeId)) return
        serviceScope.launch {
            if (nextEpisodeId != null) appSettingsDataStore.setActiveEpisodeId(nextEpisodeId)
            completeEpisode(episodeId)
            reconcileQueueWithDatabase(preferFirstEpisode = preferFirstEpisode, forcePlayPreferred = forcePlayPreferred)
            queueInvalidator.refreshHome()
        }
    }

    private fun currentEpisodeId(): Long? = player.currentMediaItem?.mediaId?.toLongOrNull()

    private fun PlaylistItemWithEpisode.toMediaItem(): MediaItem {
        val ep = this.episode
        val uri = if (ep.isDownloaded && !ep.localFilePath.isNullOrBlank() && File(ep.localFilePath).exists()) {
            Uri.fromFile(File(ep.localFilePath))
        } else {
            Uri.parse(ep.audioUrl)
        }

        val extras = android.os.Bundle().apply {
            putLong(EXTRA_DURATION_SECONDS, ep.durationSeconds)
        }

        return MediaItem.Builder()
            .setMediaId(ep.id.toString())
            .setUri(uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(ep.title)
                    .setArtist(this.podcastTitle)
                    .setArtworkUri(if (this.podcastArtworkUrl.isNotBlank()) Uri.parse(this.podcastArtworkUrl) else null)
                    .setExtras(extras)
                    .build()
            )
            .build()
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
