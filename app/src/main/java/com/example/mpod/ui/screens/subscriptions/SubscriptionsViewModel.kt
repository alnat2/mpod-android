package com.example.mpod.ui.screens.subscriptions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mpod.data.network.MpodApi
import com.example.mpod.data.network.model.EpisodeListenedRequest
import com.example.mpod.data.network.model.EpisodeDto
import com.example.mpod.data.network.model.PlaylistAddRequest
import com.example.mpod.data.network.model.PodcastDto
import com.example.mpod.playback.PlaybackQueueInvalidator
import com.example.mpod.ui.util.cleanFeedText
import com.example.mpod.ui.util.apiErrorMessage
import com.example.mpod.ui.util.toDurationSeconds
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import retrofit2.Response
import javax.inject.Inject

private const val VISIBLE_EPISODE_LIMIT = 20

@HiltViewModel
class SubscriptionsViewModel @Inject constructor(
    private val api: MpodApi,
    private val queueInvalidator: PlaybackQueueInvalidator
) : ViewModel() {
    private val _state = MutableStateFlow(SubscriptionsUiState(isLoading = true))
    val state: StateFlow<SubscriptionsUiState> = _state.asStateFlow()
    private var pendingUnsubscribeJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, errorMessage = null)
            val nextState = runCatching { loadSubscriptionsState() }.getOrElse { error ->
                SubscriptionsUiState(errorMessage = error.message ?: "Could not load subscriptions.")
            }
            _state.value = nextState.withTransientStateFrom(_state.value)
        }
    }

    fun refreshAll() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isRefreshingAll = true, actionErrorMessage = null)
            val response = runCatching { api.refreshAllPodcasts() }.getOrNull()
            if (response?.isSuccessful == true) {
                val nextState = runCatching { loadSubscriptionsState() }.getOrElse { error ->
                    _state.value.copy(
                        isRefreshingAll = false,
                        actionErrorMessage = error.message ?: "Could not reload subscriptions."
                    )
                }
                _state.value = nextState.withTransientStateFrom(_state.value)
            } else {
                _state.value = _state.value.copy(
                    isRefreshingAll = false,
                    actionErrorMessage = response.errorMessage("Could not refresh subscriptions.")
                )
            }
        }
    }

    fun refreshPodcast(podcastId: Int) {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                refreshingPodcastIds = _state.value.refreshingPodcastIds + podcastId,
                actionErrorMessage = null
            )
            val response = runCatching { api.refreshPodcast(podcastId) }.getOrNull()
            if (response?.isSuccessful == true) {
                val nextState = runCatching { loadSubscriptionsState() }.getOrElse { error ->
                    _state.value.copy(
                        refreshingPodcastIds = _state.value.refreshingPodcastIds - podcastId,
                        actionErrorMessage = error.message ?: "Could not reload subscriptions."
                    )
                }
                _state.value = nextState.withTransientStateFrom(_state.value)
            } else {
                _state.value = _state.value.copy(
                    refreshingPodcastIds = _state.value.refreshingPodcastIds - podcastId,
                    actionErrorMessage = response.errorMessage("Could not refresh this podcast.")
                )
            }
        }
    }

    fun schedulePodcastUnsubscribe(podcastId: Int) {
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

            _state.value = _state.value.copy(
                pendingUnsubscribe = null,
                unsubscribingPodcastIds = _state.value.unsubscribingPodcastIds + podcastId,
                actionErrorMessage = null
            )
            val response = runCatching { api.removePodcast(podcastId) }.getOrNull()
            if (response?.isSuccessful == true) {
                queueInvalidator.invalidate()
                _state.value = _state.value.copy(
                    unsubscribingPodcastIds = _state.value.unsubscribingPodcastIds - podcastId
                )
                val nextState = runCatching { loadSubscriptionsState() }.getOrElse { error ->
                    _state.value.copy(
                        actionErrorMessage = error.message ?: "Could not reload subscriptions."
                    )
                }
                _state.value = nextState.withTransientStateFrom(_state.value)
            } else {
                _state.value = _state.value.copy(
                    unsubscribingPodcastIds = _state.value.unsubscribingPodcastIds - podcastId,
                    actionErrorMessage = response.errorMessage("Could not unsubscribe from this podcast.")
                )
            }
            pendingUnsubscribeJob = null
        }
    }

    fun undoPodcastUnsubscribe(podcastId: Int) {
        if (_state.value.pendingUnsubscribe?.podcastId != podcastId) return
        pendingUnsubscribeJob?.cancel()
        pendingUnsubscribeJob = null
        _state.value = _state.value.copy(pendingUnsubscribe = null)
    }

    fun markAllListened(podcastId: Int) {
        val podcast = _state.value.podcasts.firstOrNull { it.id == podcastId } ?: return
        if (podcast.unlistenedEpisodeCount == 0) return
        if (podcastId in _state.value.markingAllListenedPodcastIds) return

        viewModelScope.launch {
            _state.value = _state.value.copy(
                podcasts = _state.value.podcasts.markAllListenedOptimistically(podcastId),
                markingAllListenedPodcastIds = _state.value.markingAllListenedPodcastIds + podcastId,
                actionErrorMessage = null
            )

            val failure = runCatching {
                val episodes = api.getPodcastEpisodes(podcastId)
                    .requireBody("Could not load podcast episodes.")
                    .episodes
                episodes.filterNot { it.isListened }.forEach { episode ->
                    val response = api.setEpisodeListened(
                        episode.id,
                        EpisodeListenedRequest(isListened = true)
                    )
                    if (!response.isSuccessful) {
                        throw IllegalStateException(
                            apiErrorMessage(
                                response.errorBody()?.string(),
                                "Could not mark all episodes as listened."
                            )
                        )
                    }
                }
            }.exceptionOrNull()

            _state.value = _state.value.copy(
                markingAllListenedPodcastIds = _state.value.markingAllListenedPodcastIds - podcastId
            )
            if (failure == null) queueInvalidator.invalidate()

            val nextState = runCatching { loadSubscriptionsState() }.getOrElse { reloadError ->
                _state.value.copy(
                    actionErrorMessage = reloadError.message ?: "Could not reload subscriptions."
                )
            }.let { loaded ->
                if (failure == null) loaded else loaded.copy(
                    actionErrorMessage = failure.message ?: "Could not mark all episodes as listened."
                )
            }
            _state.value = nextState.withTransientStateFrom(_state.value)
        }
    }

    fun addEpisodeToPlaylist(episodeId: Int) {
        performEpisodeAction(
            episodeId = episodeId,
            defaultErrorMessage = "Could not add episode to playlist.",
            invalidatePlaybackQueue = true
        ) {
            api.addToPlaylist(PlaylistAddRequest(episodeId = episodeId))
        }
    }

    fun removeEpisodeFromPlaylist(episodeId: Int) {
        performEpisodeAction(
            episodeId = episodeId,
            defaultErrorMessage = "Could not remove episode from playlist.",
            invalidatePlaybackQueue = true
        ) {
            api.removeFromPlaylist(episodeId)
        }
    }

    fun setEpisodeListened(episodeId: Int, isListened: Boolean) {
        performEpisodeAction(
            episodeId = episodeId,
            defaultErrorMessage = if (isListened) {
                "Could not mark episode as listened."
            } else {
                "Could not mark episode as unlistened."
            },
            invalidatePlaybackQueue = isListened
        ) {
            api.setEpisodeListened(episodeId, EpisodeListenedRequest(isListened = isListened))
        }
    }

    fun downloadEpisode(episodeId: Int) {
        if (episodeId in _state.value.downloadingEpisodeIds) return

        viewModelScope.launch {
            _state.value = _state.value.copy(
                downloadingEpisodeIds = _state.value.downloadingEpisodeIds + episodeId,
                downloadFailure = null
            )
            val response = runCatching { api.downloadEpisode(episodeId) }.getOrNull()
            if (response?.isSuccessful == true) {
                val nextState = runCatching { loadSubscriptionsState() }.getOrElse { error ->
                    _state.value.copy(
                        downloadingEpisodeIds = _state.value.downloadingEpisodeIds - episodeId,
                        actionErrorMessage = error.message ?: "Could not reload subscriptions."
                    )
                }
                _state.value = nextState.withTransientStateFrom(
                    current = _state.value,
                    completedEpisodeId = episodeId
                )
            } else {
                val failure = SubscriptionDownloadFailureUi(
                    episodeId = episodeId,
                    message = apiErrorMessage(
                        response?.errorBody()?.string(),
                        "Could not download this episode. Try again."
                    )
                )
                _state.value = _state.value.copy(
                    downloadingEpisodeIds = _state.value.downloadingEpisodeIds - episodeId,
                    downloadFailure = failure
                )
                delay(DOWNLOAD_FAILURE_TIMEOUT_MS)
                if (_state.value.downloadFailure == failure) dismissDownloadFailure()
            }
        }
    }

    fun dismissDownloadFailure() {
        _state.value = _state.value.copy(downloadFailure = null)
    }

    private fun performEpisodeAction(
        episodeId: Int,
        defaultErrorMessage: String,
        invalidatePlaybackQueue: Boolean = false,
        request: suspend () -> Response<Unit>
    ) {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                busyEpisodeIds = _state.value.busyEpisodeIds + episodeId,
                actionErrorMessage = null
            )
            val response = runCatching { request() }.getOrNull()
            if (response?.isSuccessful == true) {
                if (invalidatePlaybackQueue) queueInvalidator.invalidate()
                val nextState = runCatching { loadSubscriptionsState() }.getOrElse { error ->
                    _state.value.copy(
                        busyEpisodeIds = _state.value.busyEpisodeIds - episodeId,
                        actionErrorMessage = error.message ?: "Could not reload subscriptions."
                    )
                }
                _state.value = nextState.withTransientStateFrom(_state.value)
            } else {
                _state.value = _state.value.copy(
                    busyEpisodeIds = _state.value.busyEpisodeIds - episodeId,
                    actionErrorMessage = response.errorMessage(defaultErrorMessage)
                )
            }
        }
    }


    private suspend fun loadSubscriptionsState(): SubscriptionsUiState {
        val podcasts = api.getPodcasts().requireBody("Could not load podcasts.").podcasts
        val playlistEpisodeIds = api.getPlaylist()
            .requireBody("Could not load playlist.")
            .items
            .map { it.episodeId }
            .toSet()

        val podcastItems = podcasts.map { podcast ->
            val allEpisodes = api.getPodcastEpisodes(podcast.id)
                .requireBody("Could not load episodes.")
                .episodes

            podcast.toSubscriptionPodcast(
                episodes = allEpisodes
                    .take(VISIBLE_EPISODE_LIMIT)
                    .map { it.toSubscriptionEpisode(playlistEpisodeIds) },
                totalEpisodeCount = allEpisodes.size,
                unlistenedEpisodeCount = allEpisodes.count { !it.isListened }
            )
        }

        return SubscriptionsUiState(podcasts = podcastItems)
    }

    private fun PodcastDto.toSubscriptionPodcast(
        episodes: List<SubscriptionEpisodeUi>,
        totalEpisodeCount: Int,
        unlistenedEpisodeCount: Int
    ): SubscriptionPodcastUi {
        return SubscriptionPodcastUi(
            id = id,
            title = cleanFeedText(title).ifBlank { "Untitled podcast" },
            description = cleanFeedText(description).ifBlank { rssUrl.orEmpty() },
            imageUrl = imageUrl,
            totalEpisodeCount = totalEpisodeCount,
            unlistenedEpisodeCount = unlistenedEpisodeCount,
            episodes = episodes
        )
    }

    private fun EpisodeDto.toSubscriptionEpisode(playlistEpisodeIds: Set<Int>): SubscriptionEpisodeUi {
        return SubscriptionEpisodeUi(
            id = id,
            title = cleanFeedText(title).ifBlank { "Untitled episode" },
            durationSeconds = duration.toDurationSeconds(),
            publishedAt = publishedAt,
            isListened = isListened,
            downloaded = downloaded == true,
            summary = cleanFeedText(summary).ifBlank { null },
            inPlaylist = id in playlistEpisodeIds
        )
    }

    private fun <T> Response<T>.requireBody(defaultMessage: String): T {
        if (isSuccessful) {
            body()?.let { return it }
        }
        throw IllegalStateException(errorBody()?.string().orEmpty().ifBlank { defaultMessage })
    }

    private fun Response<*>?.errorMessage(defaultMessage: String): String {
        return this?.errorBody()?.string().orEmpty().ifBlank { defaultMessage }
    }
}

data class SubscriptionsUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val actionErrorMessage: String? = null,
    val downloadFailure: SubscriptionDownloadFailureUi? = null,
    val downloadingEpisodeIds: Set<Int> = emptySet(),
    val pendingUnsubscribe: PendingUnsubscribeUi? = null,
    val markingAllListenedPodcastIds: Set<Int> = emptySet(),
    val isRefreshingAll: Boolean = false,
    val refreshingPodcastIds: Set<Int> = emptySet(),
    val unsubscribingPodcastIds: Set<Int> = emptySet(),
    val busyEpisodeIds: Set<Int> = emptySet(),
    val podcasts: List<SubscriptionPodcastUi> = emptyList()
)

data class SubscriptionDownloadFailureUi(
    val episodeId: Int,
    val message: String
)

data class PendingUnsubscribeUi(
    val podcastId: Int,
    val podcastTitle: String,
    val secondsRemaining: Int
)

private const val DOWNLOAD_FAILURE_TIMEOUT_MS = 10_000L
private const val UNSUBSCRIBE_WINDOW_SECONDS = 15
private const val UNSUBSCRIBE_TICK_MS = 1_000L

private fun SubscriptionsUiState.withTransientStateFrom(
    current: SubscriptionsUiState,
    completedEpisodeId: Int? = null
): SubscriptionsUiState {
    return copy(
        downloadFailure = current.downloadFailure,
        downloadingEpisodeIds = completedEpisodeId?.let {
            current.downloadingEpisodeIds - it
        } ?: current.downloadingEpisodeIds,
        pendingUnsubscribe = current.pendingUnsubscribe,
        markingAllListenedPodcastIds = current.markingAllListenedPodcastIds,
        unsubscribingPodcastIds = current.unsubscribingPodcastIds
    )
}

internal fun unsubscribeCountdownSeconds(windowSeconds: Int = UNSUBSCRIBE_WINDOW_SECONDS): List<Int> {
    return (windowSeconds downTo 1).toList()
}

internal fun List<SubscriptionPodcastUi>.markAllListenedOptimistically(
    podcastId: Int
): List<SubscriptionPodcastUi> {
    return map { podcast ->
        if (podcast.id != podcastId) return@map podcast
        podcast.copy(
            unlistenedEpisodeCount = 0,
            episodes = podcast.episodes.map { episode ->
                if (episode.isListened) episode else episode.copy(
                    isListened = true,
                    downloaded = false,
                    inPlaylist = false
                )
            }
        )
    }
}

data class SubscriptionPodcastUi(
    val id: Int,
    val title: String,
    val description: String,
    val imageUrl: String?,
    val totalEpisodeCount: Int,
    val unlistenedEpisodeCount: Int,
    val episodes: List<SubscriptionEpisodeUi>
)

data class SubscriptionEpisodeUi(
    val id: Int,
    val title: String,
    val durationSeconds: Int?,
    val publishedAt: String?,
    val isListened: Boolean,
    val downloaded: Boolean,
    val summary: String?,
    val inPlaylist: Boolean
)
