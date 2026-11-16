package com.example.mpod.playback

import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.example.mpod.BuildConfig
import com.example.mpod.data.local.dao.AudiobookDao
import com.example.mpod.data.local.dao.EpisodeDao
import com.example.mpod.data.local.model.PlayableItem
import com.example.mpod.data.local.model.PlaylistItemWithEpisode
import com.example.mpod.data.local.preferences.AppSettings
import com.example.mpod.data.local.preferences.AppSettingsStore
import com.example.mpod.data.repository.PlaylistRepository
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

private const val POSITION_SAVE_INTERVAL_MS = 15_000L
private const val POSITION_SYNC_THRESHOLD_MS = 1_000L

@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    @Inject lateinit var playlistRepository: PlaylistRepository
    @Inject lateinit var episodeDao: EpisodeDao
    @Inject lateinit var audiobookDao: AudiobookDao
    @Inject lateinit var appSettingsDataStore: AppSettingsStore
    @Inject lateinit var smartListeningManager: SmartListeningManager
    @Inject lateinit var queueInvalidator: PlaybackQueueInvalidator
    @Inject lateinit var playbackStatusEvents: PlaybackStatusEvents
    @Inject @MediaCacheDataSourceFactory lateinit var cacheDataSourceFactory: DataSource.Factory

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var player: ExoPlayer
    private var mediaSession: MediaSession? = null
    private var savePositionJob: Job? = null
    private var previousMediaId: String? = null
    private var lastSignificantMediaId: String? = null
    private var lastSignificantPositionMs: Long = 0L
    internal val completedEpisodeTracker = CompletedEpisodeTracker()
    private var applyingSettingsSpeed = false
    private var cachedSettings: AppSettings? = null
    private val queueReconciliationMutex = Mutex()

    @UnstableApi
    override fun onCreate() {
        super.onCreate()
        setMediaNotificationProvider(PlayPauseOnlyMediaNotificationProvider(this))

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
            .build()

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(cacheDataSourceFactory))
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setSeekBackIncrementMs(15_000L)
            .setSeekForwardIncrementMs(30_000L)
            .build()
            .also { it.addListener(playerListener) }

        mediaSession = MediaSession.Builder(this, player)
            .setCallback(mediaSessionCallback)
            .build()

        serviceScope.launch {
            appSettingsDataStore.settingsFlow.collectLatest { settings ->
                cachedSettings = settings
            }
        }

        serviceScope.launch {
            val settings = appSettingsDataStore.settingsFlow.first().also { cachedSettings = it }
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
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(SessionCommand(CUSTOM_COMMAND_PLAY_ITEM, android.os.Bundle.EMPTY))
                .build()
            val result = MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands)
            if (session.isMediaNotificationController(controller)) {
                result.setAvailablePlayerCommands(mediaNotificationPlayerCommands())
            }
            return result.build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: android.os.Bundle
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction == CUSTOM_COMMAND_PLAY_ITEM) {
                val targetId = args.getString(EXTRA_TARGET_ITEM_ID)
                val seekPosMs = if (args.containsKey(EXTRA_SEEK_POSITION_MS)) {
                    args.getLong(EXTRA_SEEK_POSITION_MS)
                } else null
                serviceScope.launch {
                    reconcileQueueWithDatabase(
                        targetQueueItemId = targetId,
                        forcePlayPreferred = true,
                        seekPositionMs = seekPosMs
                    )
                    player.play()
                }
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))
        }
    }

    override fun onDestroy() {
        savePositionJob?.cancel()
        currentMediaQueueId()?.let { mediaQueueId ->
            val mediaId = mediaQueueId.toMediaId()
            val pos = resolveMeaningfulPlaybackPosition(
                currentPositionMs = player.currentPosition,
                lastSignificantPositionMs = lastSignificantPositionMs.takeIf { lastSignificantMediaId == mediaId },
                allowExplicitZero = lastSignificantMediaId == mediaId && lastSignificantPositionMs == 0L
            ) ?: return@let
            runCatching {
                runBlocking(Dispatchers.IO) {
                    when (mediaQueueId) {
                        is MediaQueueId.Episode -> episodeDao.updatePlaybackPosition(mediaQueueId.episodeId, pos)
                        is MediaQueueId.Audiobook -> audiobookDao.updateTrackProgress(mediaQueueId.trackId, positionMs = pos, isListened = false)
                    }
                }
            }
        }
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

    private suspend fun reconcileQueueWithDatabase(
        targetQueueItemId: String? = null,
        preferredTrackId: Long? = null,
        preferFirstItem: Boolean = false,
        forcePlayPreferred: Boolean = false,
        seekPositionMs: Long? = null
    ) {
        queueReconciliationMutex.withLock {
            val playableItems = playlistRepository.getPlayableItems()
            if (playableItems.isEmpty()) {
                if (player.mediaItemCount == 0) return@withLock
                savePositionJob?.cancel()
                savePositionJob = null
                player.removeListener(playerListener)
                player.stop()
                player.clearMediaItems()
                previousMediaId = null
                completedEpisodeTracker.resetAll()
                player.addListener(playerListener)
                return@withLock
            }

            val settings = cachedSettings ?: appSettingsDataStore.settingsFlow.first().also { cachedSettings = it }
            val currentMediaQueueId = currentMediaQueueId()

            val effectiveTargetItemId = targetQueueItemId
                ?: if (preferFirstItem) playableItems.firstOrNull()?.queueItemId
                else currentMediaQueueId?.toMediaId()
                ?: settings.activeEpisodeId?.let { "episode:$it" }
                ?: playableItems.firstOrNull()?.queueItemId

            val window = SlidingWindowResolver.resolveWindow(
                queue = playableItems,
                targetQueueItemId = effectiveTargetItemId,
                preferredTrackId = preferredTrackId,
                currentMediaQueueId = currentMediaQueueId
            )

            val currentTrack = window.current
            if (currentTrack == null) {
                if (player.mediaItemCount == 0) return@withLock
                savePositionJob?.cancel()
                savePositionJob = null
                player.removeListener(playerListener)
                player.stop()
                player.clearMediaItems()
                previousMediaId = null
                completedEpisodeTracker.resetAll()
                player.addListener(playerListener)
                return@withLock
            }

            completedEpisodeTracker.resetMedia(currentTrack.toMediaId())
            if (currentTrack is PlaybackTrack.EpisodeTrack) {
                completedEpisodeTracker.reset(currentTrack.episode.id)
            }

            val currentMediaId = player.currentMediaItem?.mediaId
            val needsRebuild = currentMediaId != currentTrack.toMediaId() ||
                player.mediaItemCount == 0 ||
                forcePlayPreferred

            if (!needsRebuild) {
                // If ExoPlayer transitioned to index 1, remove finished item(s) so active item is at index 0
                while (player.currentMediaItemIndex > 0) {
                    player.removeMediaItem(0)
                }

                // Synchronize index 1 (the 'Next' buffered track in the sliding window)
                val expectedNext = window.next
                val currentCount = player.mediaItemCount
                if (expectedNext != null) {
                    val nextMediaItem = expectedNext.toMediaItem()
                    if (currentCount > 1) {
                        val existingNextMediaId = player.getMediaItemAt(1).mediaId
                        if (existingNextMediaId != expectedNext.toMediaId()) {
                            player.replaceMediaItem(1, nextMediaItem)
                        }
                    } else {
                        player.addMediaItem(1, nextMediaItem)
                    }
                } else if (currentCount > 1) {
                    player.removeMediaItem(1)
                }

                if (seekPositionMs != null) {
                    player.seekTo(0, seekPositionMs)
                }

                // Apply correct speed if track type changed
                val targetSpeed = if (currentTrack.isAudiobook) settings.audiobookPlaybackSpeed else settings.playbackSpeed
                if (player.playbackParameters.speed != targetSpeed) {
                    applyingSettingsSpeed = true
                    player.playbackParameters = PlaybackParameters(targetSpeed)
                    applyingSettingsSpeed = false
                }
                return@withLock
            }

            // Rebuild sliding window [Current, Next] in ExoPlayer
            if (currentMediaQueueId != null && player.playbackState != Player.STATE_IDLE && (player.isPlaying || player.currentPosition > 0L)) {
                saveCurrentPosition(force = true)
            }

            player.removeListener(playerListener)
            val initialPos = seekPositionMs ?: currentTrack.initialPositionMs
            rememberSignificantPosition(currentTrack.toMediaId(), initialPos)

            val mediaItems = listOfNotNull(currentTrack.toMediaItem(), window.next?.toMediaItem())
            previousMediaId = currentTrack.toMediaId()
            player.setMediaItems(mediaItems, 0, initialPos)

            val targetSpeed = if (currentTrack.isAudiobook) settings.audiobookPlaybackSpeed else settings.playbackSpeed
            applyingSettingsSpeed = true
            player.playbackParameters = PlaybackParameters(targetSpeed)
            applyingSettingsSpeed = false

            player.prepare()
            val shouldPlay = forcePlayPreferred || (player.playWhenReady && player.playbackState != Player.STATE_ENDED)
            player.playWhenReady = shouldPlay
            player.addListener(playerListener)

            if (shouldPlay) {
                startPeriodicSave()
                if (currentTrack is PlaybackTrack.EpisodeTrack) {
                    appSettingsDataStore.setActiveEpisodeId(currentTrack.episode.id)
                    smartListeningManager.forceDownloadImmediately(currentTrack.episode.id)
                }
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
                    smartListeningManager.forceDownloadImmediately(episodeId)
                    serviceScope.launch {
                        appSettingsDataStore.setActiveEpisodeId(episodeId)
                    }
                }
                startPeriodicSave()
            } else {
                savePositionJob?.cancel()
                savePositionJob = null
                if (!player.playWhenReady && player.playbackState != Player.STATE_ENDED) {
                    serviceScope.launch { saveCurrentPosition(force = true) }
                }
            }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (playWhenReady) {
                currentEpisodeId()?.let { episodeId ->
                    completedEpisodeTracker.reset(episodeId)
                    smartListeningManager.forceDownloadImmediately(episodeId)
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
                    serviceScope.launch { saveCurrentPosition(force = true, allowExplicitZero = true) }
                    currentMediaId()?.let { mediaId ->
                        completedEpisodeTracker.resetMedia(mediaId)
                    }
                } else {
                    val oldMediaId = oldPosition.mediaItemIndex
                        .takeIf { it in 0 until player.mediaItemCount }
                        ?.let(player::getMediaItemAt)
                        ?.mediaId
                    val oldQueueId = MediaQueueId.fromMediaId(oldMediaId)
                    if (oldQueueId != null) {
                        serviceScope.launch {
                            withContext(Dispatchers.IO) {
                                when (oldQueueId) {
                                    is MediaQueueId.Episode -> episodeDao.updatePlaybackPosition(oldQueueId.episodeId, oldPosition.positionMs)
                                    is MediaQueueId.Audiobook -> audiobookDao.updateTrackProgress(oldQueueId.trackId, positionMs = oldPosition.positionMs, isListened = false)
                                }
                            }
                        }
                    }
                }
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val nextMediaId = mediaItem?.mediaId
            val finishedMediaId = previousMediaId
            previousMediaId = nextMediaId

            if (nextMediaId != null) {
                completedEpisodeTracker.resetMedia(nextMediaId)
                rememberSignificantPosition(nextMediaId, player.currentPosition)

                val nextQueueId = MediaQueueId.fromMediaId(nextMediaId)
                if (nextQueueId is MediaQueueId.Episode && player.playWhenReady) {
                    smartListeningManager.forceDownloadImmediately(nextQueueId.episodeId)
                }

                // Update speed for current media type synchronously
                val isAbk = nextQueueId is MediaQueueId.Audiobook
                val targetSpeed = if (isAbk) {
                    cachedSettings?.audiobookPlaybackSpeed ?: 1.0f
                } else {
                    cachedSettings?.playbackSpeed ?: 1.3f
                }
                if (player.playbackParameters.speed != targetSpeed) {
                    applyingSettingsSpeed = true
                    player.playbackParameters = PlaybackParameters(targetSpeed)
                    applyingSettingsSpeed = false
                }
            }

            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && finishedMediaId != null) {
                handleTrackCompleted(finishedMediaId, nextMediaId)
            } else if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK && nextMediaId != null) {
                val nextQueueId = MediaQueueId.fromMediaId(nextMediaId)
                serviceScope.launch {
                    if (nextQueueId is MediaQueueId.Episode) {
                        appSettingsDataStore.setActiveEpisodeId(nextQueueId.episodeId)
                    }
                    reconcileQueueWithDatabase()
                }
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState != Player.STATE_ENDED) return
            val mediaId = currentMediaId() ?: return
            handleTrackCompleted(mediaId, preferFirstItem = true, forcePlayPreferred = true)
        }

        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
            if (applyingSettingsSpeed) return
            val queueId = currentMediaQueueId()
            serviceScope.launch {
                if (queueId is MediaQueueId.Audiobook) {
                    appSettingsDataStore.setAudiobookPlaybackSpeed(playbackParameters.speed)
                } else {
                    appSettingsDataStore.setPlaybackSpeed(playbackParameters.speed)
                }
            }
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            val queueId = currentMediaQueueId()
            if (queueId is MediaQueueId.Audiobook) {
                serviceScope.launch {
                    audiobookDao.updateTrackAvailability(queueId.trackId, isAvailable = false)
                    val track = audiobookDao.getTrackById(queueId.trackId)
                    val filename = track?.title ?: "audiobook file"
                    playbackStatusEvents.postError("File not found: $filename")
                    reconcileQueueWithDatabase(forcePlayPreferred = true)
                    queueInvalidator.invalidate()
                }
            }
        }
    }

    private var lastSavedPositionSec: Long = -1L
    private var lastSavedMediaId: String? = null

    private fun rememberSignificantPosition(mediaId: String, positionMs: Long) {
        lastSignificantMediaId = mediaId
        lastSignificantPositionMs = positionMs.coerceAtLeast(0L)
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

    private suspend fun saveCurrentPosition(force: Boolean = false, allowExplicitZero: Boolean = false) {
        val mediaQueueId = currentMediaQueueId() ?: return
        val currentMediaId = mediaQueueId.toMediaId()
        val pos = resolveMeaningfulPlaybackPosition(
            currentPositionMs = player.currentPosition,
            lastSignificantPositionMs = lastSignificantPositionMs.takeIf { lastSignificantMediaId == currentMediaId },
            allowExplicitZero = allowExplicitZero
        ) ?: return

        val posSec = pos / 1000L
        if (!force && currentMediaId == lastSavedMediaId && posSec == lastSavedPositionSec) {
            return
        }

        val durMs = player.duration
        val durSec = if (durMs > 0L) durMs / 1000L else 0L

        withContext(Dispatchers.IO) {
            when (mediaQueueId) {
                is MediaQueueId.Episode -> {
                    episodeDao.updatePlaybackPosition(mediaQueueId.episodeId, pos)
                    if (durSec > 0L) {
                        episodeDao.updateDurationIfMissing(mediaQueueId.episodeId, durSec)
                    }
                }
                is MediaQueueId.Audiobook -> {
                    audiobookDao.updateTrackProgress(mediaQueueId.trackId, positionMs = pos, isListened = false)
                }
            }
        }

        rememberSignificantPosition(currentMediaId, pos)
        lastSavedPositionSec = posSec
        lastSavedMediaId = currentMediaId
    }

    private suspend fun completeEpisode(episodeId: Long) {
        withContext(Dispatchers.IO) {
            episodeDao.setListened(episodeId, true)
        }
        playlistRepository.removeFromPlaylist(episodeId)
        smartListeningManager.cleanupEpisodeFile(episodeId)
    }

    private suspend fun completeAudiobookTrack(audiobookId: Long, trackId: Long) {
        withContext(Dispatchers.IO) {
            audiobookDao.updateTrackProgress(trackId, positionMs = 0L, isListened = true)
            val unlistened = audiobookDao.getFirstUnlistenedSelectedTrack(audiobookId)
            if (unlistened == null) {
                // Entire audiobook complete! (ABK-10)
                playlistRepository.removeAudiobookFromPlaylist(audiobookId)
                audiobookDao.resetAudiobookProgress(audiobookId)
            }
        }
    }

    private fun handleTrackCompleted(
        completedMediaId: String,
        nextMediaId: String? = null,
        preferFirstItem: Boolean = false,
        forcePlayPreferred: Boolean = false
    ) {
        if (!completedEpisodeTracker.markMediaCompleted(completedMediaId)) return
        val queueId = MediaQueueId.fromMediaId(completedMediaId) ?: return

        serviceScope.launch {
            when (queueId) {
                is MediaQueueId.Episode -> completeEpisode(queueId.episodeId)
                is MediaQueueId.Audiobook -> completeAudiobookTrack(queueId.audiobookId, queueId.trackId)
            }

            if (nextMediaId != null) {
                val nextQueueId = MediaQueueId.fromMediaId(nextMediaId)
                if (nextQueueId is MediaQueueId.Episode) {
                    appSettingsDataStore.setActiveEpisodeId(nextQueueId.episodeId)
                }
            }

            reconcileQueueWithDatabase(
                preferFirstItem = preferFirstItem,
                forcePlayPreferred = forcePlayPreferred
            )
            queueInvalidator.invalidate()
        }
    }

    private fun currentMediaId(): String? = player.currentMediaItem?.mediaId
    private fun currentMediaQueueId(): MediaQueueId? = MediaQueueId.fromMediaId(currentMediaId())
    private fun currentEpisodeId(): Long? = (currentMediaQueueId() as? MediaQueueId.Episode)?.episodeId

    companion object {
        const val EXTRA_DURATION_SECONDS = "com.prod.mpod.duration_seconds"
        const val CUSTOM_COMMAND_PLAY_ITEM = "com.prod.mpod.PLAY_ITEM"
        const val EXTRA_TARGET_ITEM_ID = "com.prod.mpod.target_item_id"
        const val EXTRA_SEEK_POSITION_MS = "com.prod.mpod.seek_position_ms"
    }
}

internal fun buildEpisodeCustomCacheKey(episodeId: Long): String = "episode_$episodeId"

internal fun resolveMeaningfulPlaybackPosition(
    currentPositionMs: Long,
    lastSignificantPositionMs: Long?,
    allowExplicitZero: Boolean
): Long? = when {
    currentPositionMs > 0L -> currentPositionMs
    allowExplicitZero -> 0L
    else -> lastSignificantPositionMs?.takeIf { it > 0L }
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
