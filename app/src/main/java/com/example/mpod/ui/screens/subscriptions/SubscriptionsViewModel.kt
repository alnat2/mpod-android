package com.example.mpod.ui.screens.subscriptions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mpod.data.local.dao.EpisodeDao
import com.example.mpod.data.local.dao.PlaylistDao
import com.example.mpod.data.local.dao.PodcastDao
import com.example.mpod.data.repository.PlaylistRepository
import com.example.mpod.data.repository.PodcastRepository
import com.example.mpod.playback.PlaybackQueueInvalidator
import com.example.mpod.ui.util.cleanFeedText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class SubscriptionsViewModel @Inject constructor(
    private val podcastDao: PodcastDao,
    private val episodeDao: EpisodeDao,
    private val playlistDao: PlaylistDao,
    private val podcastRepository: PodcastRepository,
    private val playlistRepository: PlaylistRepository,
    private val queueInvalidator: PlaybackQueueInvalidator
) : ViewModel() {

    private val _state = MutableStateFlow(SubscriptionsUiState(isLoading = true))
    val state: StateFlow<SubscriptionsUiState> = _state.asStateFlow()

    private var pendingUnsubscribeJob: Job? = null
    private var refreshInFlight = false

    init {
        observeData()
    }

    private fun observeData() {
        viewModelScope.launch(Dispatchers.IO) {
            combine(
                podcastDao.getAllPodcastsFlow(),
                playlistDao.getPlaylistItemsWithEpisodesFlow()
            ) { podcasts, playlistItems ->
                val playlistEpisodeIds = playlistItems.map { it.episode.id }.toSet()
                val podcastUis = podcasts.map { pod ->
                    val episodes = episodeDao.getEpisodesByPodcastId(pod.id)
                    val episodeUis = episodes.map { ep ->
                        SubscriptionEpisodeUi(
                            id = ep.id,
                            title = cleanFeedText(ep.title).ifBlank { "Untitled episode" },
                            durationSeconds = ep.durationSeconds.toInt(),
                            publishedAt = ep.publishedAtString.ifBlank { null },
                            isListened = ep.isListened,
                            downloaded = ep.isDownloaded,
                            summary = ep.description.ifBlank { null },
                            inPlaylist = ep.id in playlistEpisodeIds
                        )
                    }
                    SubscriptionPodcastUi(
                        id = pod.id,
                        title = cleanFeedText(pod.title).ifBlank { "Untitled podcast" },
                        description = cleanFeedText(pod.description).ifBlank { pod.feedUrl },
                        imageUrl = pod.artworkUrl.ifBlank { null },
                        totalEpisodeCount = episodes.size,
                        unlistenedEpisodeCount = episodes.count { !it.isListened },
                        episodes = episodeUis
                    )
                }

                _state.value = _state.value.copy(
                    isLoading = false,
                    hasLoadedOnce = true,
                    podcasts = podcastUis
                )
            }.collect { }
        }
    }

    fun refresh() {
        refreshAll()
    }

    fun refreshAll() {
        if (_state.value.isRefreshingAll || refreshInFlight) return
        refreshInFlight = true
        _state.value = _state.value.copy(isRefreshingAll = true, actionErrorMessage = null)

        viewModelScope.launch {
            try {
                val result = podcastRepository.refreshAllPodcasts()
                if (result.isFailure) {
                    _state.value = _state.value.copy(
                        actionErrorMessage = result.exceptionOrNull()?.message ?: "Failed to refresh some podcasts."
                    )
                }
                queueInvalidator.invalidate()
            } finally {
                refreshInFlight = false
                _state.value = _state.value.copy(isRefreshingAll = false)
            }
        }
    }

    fun refreshPodcast(podcastId: Long) {
        if (_state.value.isRefreshingAll || podcastId in _state.value.refreshingPodcastIds) return
        _state.value = _state.value.copy(
            refreshingPodcastIds = _state.value.refreshingPodcastIds + podcastId,
            actionErrorMessage = null
        )
        viewModelScope.launch {
            try {
                val result = podcastRepository.refreshPodcast(podcastId)
                if (result.isFailure) {
                    _state.value = _state.value.copy(
                        actionErrorMessage = result.exceptionOrNull()?.message ?: "Could not refresh podcast."
                    )
                }
                queueInvalidator.invalidate()
            } finally {
                _state.value = _state.value.copy(
                    refreshingPodcastIds = _state.value.refreshingPodcastIds - podcastId
                )
            }
        }
    }

    fun schedulePodcastUnsubscribe(podcastId: Long) {
        if (_state.value.pendingUnsubscribe != null) return
        val podcast = _state.value.podcasts.firstOrNull { it.id == podcastId } ?: return
        val countdown = unsubscribeCountdownSeconds()
        _state.value = _state.value.copy(
            pendingUnsubscribe = PendingUnsubscribeUi(
                podcastId = podcast.id,
                podcastTitle = podcast.title,
                secondsRemaining = countdown.first()
            )
        )

        pendingUnsubscribeJob = viewModelScope.launch {
            for (secondsRemaining in countdown.drop(1)) {
                delay(UNSUBSCRIBE_TICK_MS)
                _state.value = _state.value.copy(
                    pendingUnsubscribe = PendingUnsubscribeUi(
                        podcastId = podcast.id,
                        podcastTitle = podcast.title,
                        secondsRemaining = secondsRemaining
                    )
                )
            }
            delay(UNSUBSCRIBE_TICK_MS)
            pendingUnsubscribeJob = null
            unsubscribePodcastNow(podcastId)
        }
    }

    fun undoPodcastUnsubscribe(podcastId: Long) {
        if (_state.value.pendingUnsubscribe?.podcastId != podcastId) return
        pendingUnsubscribeJob?.cancel()
        pendingUnsubscribeJob = null
        _state.value = _state.value.copy(pendingUnsubscribe = null)
    }

    internal fun unsubscribePodcastNow(podcastId: Long) {
        _state.value = _state.value.copy(
            pendingUnsubscribe = null,
            unsubscribingPodcastIds = _state.value.unsubscribingPodcastIds + podcastId
        )
        viewModelScope.launch {
            try {
                podcastRepository.unsubscribe(podcastId)
                queueInvalidator.invalidate()
            } finally {
                _state.value = _state.value.copy(
                    unsubscribingPodcastIds = _state.value.unsubscribingPodcastIds - podcastId
                )
            }
        }
    }

    fun markAllListened(podcastId: Long) {
        val podcast = _state.value.podcasts.firstOrNull { it.id == podcastId } ?: return
        if (podcast.unlistenedEpisodeCount == 0) return
        viewModelScope.launch {
            val episodes = withContext(Dispatchers.IO) { episodeDao.getEpisodesByPodcastId(podcastId) }
            podcastRepository.markAllEpisodesListened(podcastId, true)
            for (ep in episodes) {
                playlistRepository.removeFromPlaylist(ep.id)
            }
            queueInvalidator.invalidate()
        }
    }

    fun addEpisodeToPlaylist(episodeId: Long) {
        viewModelScope.launch {
            podcastRepository.setEpisodeListened(episodeId, false)
            playlistRepository.addEpisodeToPlaylist(episodeId)
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

    fun clearActionError() {
        _state.value = _state.value.copy(actionErrorMessage = null)
    }
}

data class SubscriptionsUiState(
    val hasLoadedOnce: Boolean = true,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
    val actionErrorMessage: String? = null,
    val pendingUnsubscribe: PendingUnsubscribeUi? = null,
    val isRefreshingAll: Boolean = false,
    val refreshingPodcastIds: Set<Long> = emptySet(),
    val unsubscribingPodcastIds: Set<Long> = emptySet(),
    val busyEpisodeIds: Set<Long> = emptySet(),
    val podcasts: List<SubscriptionPodcastUi> = emptyList()
)

data class PendingUnsubscribeUi(
    val podcastId: Long,
    val podcastTitle: String,
    val secondsRemaining: Int
)

data class SubscriptionPodcastUi(
    val id: Long,
    val title: String,
    val description: String,
    val imageUrl: String?,
    val totalEpisodeCount: Int,
    val unlistenedEpisodeCount: Int,
    val episodes: List<SubscriptionEpisodeUi>,
    val errorMessage: String? = null,
    val episodesUnavailable: Boolean = false
)

data class SubscriptionEpisodeUi(
    val id: Long,
    val title: String,
    val durationSeconds: Int?,
    val publishedAt: String?,
    val isListened: Boolean,
    val downloaded: Boolean,
    val summary: String?,
    val inPlaylist: Boolean
)

private const val UNSUBSCRIBE_WINDOW_SECONDS = 15
private const val UNSUBSCRIBE_TICK_MS = 1_000L

internal fun unsubscribeCountdownSeconds(windowSeconds: Int = UNSUBSCRIBE_WINDOW_SECONDS): List<Int> {
    return (windowSeconds downTo 1).toList()
}
