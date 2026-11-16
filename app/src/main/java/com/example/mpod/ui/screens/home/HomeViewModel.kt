package com.example.mpod.ui.screens.home

import androidx.annotation.MainThread
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mpod.data.local.dao.EpisodeDao
import com.example.mpod.data.local.dao.PlaylistDao
import com.example.mpod.data.local.dao.PodcastDao
import com.example.mpod.data.local.entity.PlaylistItemEntity
import com.example.mpod.data.local.model.PlayableItem
import com.example.mpod.data.local.preferences.AppSettingsStore
import com.example.mpod.data.repository.PlaylistRepository
import com.example.mpod.data.repository.PodcastRepository
import com.example.mpod.playback.CleanupResult
import com.example.mpod.playback.PlaybackQueueInvalidator
import com.example.mpod.playback.PlaybackStatusEvent
import com.example.mpod.playback.PlaybackStatusEvents
import com.example.mpod.playback.SmartListeningManager
import com.example.mpod.ui.util.cleanFeedText
import com.example.mpod.ui.util.formatEpisodeDuration
import com.example.mpod.ui.util.formatProgressTime
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val podcastDao: PodcastDao,
    private val playlistDao: PlaylistDao,
    private val episodeDao: EpisodeDao,
    private val playlistRepository: PlaylistRepository,
    private val podcastRepository: PodcastRepository,
    private val appSettingsDataStore: AppSettingsStore,
    private val queueInvalidator: PlaybackQueueInvalidator,
    private val smartListeningManager: SmartListeningManager,
    private val playbackStatusEvents: PlaybackStatusEvents
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState(isLoading = true))
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            combine(
                podcastDao.getAllPodcastsFlow(),
                playlistRepository.getPlayableItemsFlow(),
                appSettingsDataStore.settingsFlow
            ) { podcasts, playableItems, settings ->
                val hasPodcasts = podcasts.isNotEmpty()
                val queueUis = playableItems.map { item ->
                    when (item) {
                        is PlayableItem.EpisodeItem -> {
                            val ep = item.episode
                            HomeEpisodeUi(
                                id = ep.id,
                                title = cleanFeedText(ep.title).ifBlank { "Untitled episode" },
                                podcastTitle = cleanFeedText(item.podcastTitle).ifBlank { "Podcast" },
                                durationSeconds = ep.durationSeconds.toInt(),
                                playbackPositionSeconds = (ep.playbackPositionMs / 1000L).toInt(),
                                isListened = ep.isListened,
                                downloaded = ep.isDownloaded,
                                summary = ep.description.ifBlank { null },
                                isAudiobook = false,
                                episodeId = ep.id
                            )
                        }
                        is PlayableItem.AudiobookItem -> {
                            val curTrack = item.currentTrack
                            val chapterProgressSec = (curTrack?.playbackPositionMs ?: 0L) / 1000L
                            val chapterDurationSec = curTrack?.durationSeconds ?: 0L
                            val posFormatted = formatProgressTime(curTrack?.playbackPositionMs ?: 0L)
                            val durFormatted = formatEpisodeDuration(chapterDurationSec.toInt())
                            val formattedDuration = "Chapter ${item.currentChapterIndex} of ${item.totalChaptersCount} • $posFormatted / $durFormatted"

                            HomeEpisodeUi(
                                id = -item.audiobook.id,
                                title = item.audiobook.title,
                                podcastTitle = item.audiobook.author,
                                durationSeconds = chapterDurationSec.toInt(),
                                playbackPositionSeconds = chapterProgressSec.toInt(),
                                isListened = false,
                                downloaded = true,
                                summary = null,
                                isAudiobook = true,
                                formattedDuration = formattedDuration,
                                audiobookId = item.audiobook.id
                            )
                        }
                    }
                }
                HomeUiState(
                    isLoading = false,
                    hasPodcasts = hasPodcasts,
                    activeEpisodeId = settings.activeEpisodeId,
                    queue = queueUis
                )
            }.collect { nextState ->
                _state.value = nextState
            }
        }

        viewModelScope.launch {
            playbackStatusEvents.events.collectLatest { event ->
                when (event) {
                    is PlaybackStatusEvent.Error -> {
                        _state.value = _state.value.copy(actionErrorMessage = event.message)
                    }
                }
            }
        }
    }

    @MainThread
    fun refresh(invalidatePlaybackQueue: Boolean = true) {
        if (invalidatePlaybackQueue) {
            queueInvalidator.invalidate()
        }
    }

    @MainThread
    fun removeEpisodeFromPlaylist(episodeId: Long) {
        viewModelScope.launch {
            if (episodeId < 0) {
                val audiobookId = -episodeId
                playlistRepository.removeAudiobookFromPlaylist(audiobookId)
            } else {
                podcastRepository.setEpisodeListened(episodeId, true)
                playlistRepository.removeFromPlaylist(episodeId)
                when (val cleanup = smartListeningManager.cleanupEpisodeFile(episodeId)) {
                    is CleanupResult.DeleteFailed -> {
                        android.util.Log.w("HomeViewModel", "Failed to delete file for episode $episodeId: ${cleanup.path}")
                    }
                    CleanupResult.Success -> Unit
                }
            }
            queueInvalidator.invalidate()
        }
    }

    @MainThread
    fun setEpisodeListened(episodeId: Long, isListened: Boolean) {
        viewModelScope.launch {
            if (episodeId < 0) {
                // Audiobooks: if user marks listened from playlist, remove from queue
                val audiobookId = -episodeId
                if (isListened) {
                    playlistRepository.removeAudiobookFromPlaylist(audiobookId)
                }
            } else {
                podcastRepository.setEpisodeListened(episodeId, isListened)
                if (isListened) {
                    playlistRepository.removeFromPlaylist(episodeId)
                    when (val cleanup = smartListeningManager.cleanupEpisodeFile(episodeId)) {
                        is CleanupResult.DeleteFailed -> {
                            android.util.Log.w("HomeViewModel", "Failed to delete file for episode $episodeId: ${cleanup.path}")
                        }
                        CleanupResult.Success -> Unit
                    }
                }
            }
            queueInvalidator.invalidate()
        }
    }

    @MainThread
    fun moveEpisode(episodeId: Long, offset: Int) {
        val currentQueue = _state.value.queue
        val nextQueue = reorderEpisodes(currentQueue, episodeId, offset) ?: return
        viewModelScope.launch {
            val entities = nextQueue.mapIndexed { index, ui ->
                if (ui.isAudiobook) {
                    PlaylistItemEntity(audiobookId = ui.audiobookId ?: -ui.id, position = index)
                } else {
                    PlaylistItemEntity(episodeId = ui.episodeId ?: ui.id, position = index)
                }
            }
            playlistRepository.reorderPlayableItems(entities)
            queueInvalidator.invalidate()
        }
    }
}

data class HomeUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val actionErrorMessage: String? = null,
    val busyEpisodeIds: Set<Long> = emptySet(),
    val hasPodcasts: Boolean = true,
    val activeEpisodeId: Long? = null,
    val queue: List<HomeEpisodeUi> = emptyList()
)

data class HomeEpisodeUi(
    val id: Long,
    val title: String,
    val podcastTitle: String,
    val durationSeconds: Int?,
    val playbackPositionSeconds: Int,
    val isListened: Boolean,
    val downloaded: Boolean,
    val summary: String?,
    val isAudiobook: Boolean = false,
    val formattedDuration: String? = null,
    val audiobookId: Long? = null,
    val episodeId: Long? = null
)

internal fun reorderEpisodes(
    episodes: List<HomeEpisodeUi>,
    episodeId: Long,
    offset: Int
): List<HomeEpisodeUi>? {
    val currentIndex = episodes.indexOfFirst { it.id == episodeId }
    if (currentIndex < 0) return null

    val targetIndex = (currentIndex + offset).coerceIn(0, episodes.lastIndex)
    if (currentIndex == targetIndex) return null

    return episodes.toMutableList().apply {
        val movedEpisode = removeAt(currentIndex)
        add(targetIndex, movedEpisode)
    }
}
