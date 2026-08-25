package com.example.mpod.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mpod.data.local.dao.EpisodeDao
import com.example.mpod.data.local.dao.PlaylistDao
import com.example.mpod.data.local.dao.PodcastDao
import com.example.mpod.data.local.preferences.AppSettingsDataStore
import com.example.mpod.data.repository.PlaylistRepository
import com.example.mpod.data.repository.PodcastRepository
import com.example.mpod.playback.PlaybackQueueInvalidator
import com.example.mpod.ui.util.cleanFeedText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val appSettingsDataStore: AppSettingsDataStore,
    private val queueInvalidator: PlaybackQueueInvalidator
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState(isLoading = true))
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            combine(
                podcastDao.getAllPodcastsFlow(),
                playlistDao.getPlaylistItemsWithEpisodesFlow(),
                appSettingsDataStore.settingsFlow
            ) { podcasts, playlistItems, settings ->
                val hasPodcasts = podcasts.isNotEmpty()
                val queueUis = playlistItems.map { item ->
                    val ep = item.episode
                    HomeEpisodeUi(
                        id = ep.id,
                        title = cleanFeedText(ep.title).ifBlank { "Untitled episode" },
                        podcastTitle = cleanFeedText(item.podcastTitle).ifBlank { "Podcast" },
                        durationSeconds = ep.durationSeconds.toInt(),
                        playbackPositionSeconds = (ep.playbackPositionMs / 1000L).toInt(),
                        isListened = ep.isListened,
                        downloaded = ep.isDownloaded,
                        summary = ep.description.ifBlank { null }
                    )
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
    }

    fun refresh(invalidatePlaybackQueue: Boolean = true) {
        if (invalidatePlaybackQueue) {
            queueInvalidator.invalidate()
        }
    }

    fun removeEpisodeFromPlaylist(episodeId: Long) {
        viewModelScope.launch {
            playlistRepository.removeFromPlaylist(episodeId)
            queueInvalidator.invalidate()
        }
    }

    fun setEpisodeListened(episodeId: Long, isListened: Boolean) {
        viewModelScope.launch {
            podcastRepository.setEpisodeListened(episodeId, isListened)
            if (isListened) {
                playlistRepository.removeFromPlaylist(episodeId)
            }
            queueInvalidator.invalidate()
        }
    }

    fun moveEpisode(episodeId: Long, offset: Int) {
        val currentQueue = _state.value.queue
        val nextQueue = reorderEpisodes(currentQueue, episodeId, offset) ?: return
        viewModelScope.launch {
            playlistRepository.reorderPlaylist(nextQueue.map { it.id })
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
    val summary: String?
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
