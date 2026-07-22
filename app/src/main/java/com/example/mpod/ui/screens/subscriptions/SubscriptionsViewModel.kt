package com.example.mpod.ui.screens.subscriptions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mpod.data.network.MpodApi
import com.example.mpod.data.network.model.EpisodeListenedRequest
import com.example.mpod.data.network.model.EpisodeDto
import com.example.mpod.data.network.model.MarkAllListenedResponse
import com.example.mpod.data.network.model.PlaylistAddRequest
import com.example.mpod.data.network.model.PodcastDto
import com.example.mpod.data.network.model.SchedulerStatusDto
import com.example.mpod.playback.PlaybackQueueInvalidator
import com.example.mpod.ui.util.cleanFeedText
import com.example.mpod.ui.util.apiErrorMessage
import com.example.mpod.ui.util.toDurationSeconds
import com.example.mpod.ui.util.missingApiPayload
import com.example.mpod.ui.util.requireApiBody
import com.example.mpod.ui.util.userFacingApiMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import retrofit2.Response
import javax.inject.Inject

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
            val current = _state.value
            _state.value = current.copy(
                isLoading = current.podcasts.isEmpty(),
                errorMessage = null,
                actionErrorMessage = null
            )
            val loadResult = runCatching { loadSubscriptionsState() }
            val nextState = loadResult.getOrElse { error ->
                val message = error.userFacingApiMessage("Could not load subscriptions.")
                if (current.podcasts.isEmpty()) {
                    SubscriptionsUiState(errorMessage = message)
                } else {
                    current.copy(
                        isLoading = false,
                        actionErrorMessage = message
                    )
                }
            }
            val mergedState = nextState.withTransientStateFrom(_state.value)
            _state.value = if (loadResult.isSuccess) {
                mergedState.copy(
                    failedUnsubscribePodcastId = null,
                    failedMarkAllListenedPodcastId = null,
                    failedEpisodeAction = null
                )
            } else {
                mergedState
            }
            if (loadResult.isSuccess) queueInvalidator.invalidate()
        }
    }

    fun refreshAll() {
        if (_state.value.isRefreshingAll || _state.value.refreshingPodcastIds.isNotEmpty()) return
        _state.value = _state.value.copy(
            isRefreshingAll = true,
            failedUnsubscribePodcastId = null,
            failedMarkAllListenedPodcastId = null,
            failedEpisodeAction = null,
            actionErrorMessage = null
        )
        viewModelScope.launch {
            try {
                val response = runCatching { api.refreshAllPodcasts() }.getOrNull()
                if (response?.isSuccessful == true) {
                    when (val completion = awaitRefreshAllCompletion()) {
                        RefreshAllCompletion.Completed -> {
                            val nextState = runCatching { loadSubscriptionsState() }.getOrElse { error ->
                                _state.value.copy(
                                    actionErrorMessage = error.message ?: "Could not reload subscriptions."
                                )
                            }
                            _state.value = nextState.withTransientStateFrom(_state.value)
                        }
                        is RefreshAllCompletion.Failed -> {
                            _state.value = _state.value.copy(
                                actionErrorMessage = completion.message
                            )
                        }
                    }
                } else {
                    _state.value = _state.value.copy(
                        actionErrorMessage = response.errorMessage("Could not refresh subscriptions.")
                    )
                }
            } finally {
                _state.value = _state.value.copy(isRefreshingAll = false)
            }
        }
    }

    private suspend fun awaitRefreshAllCompletion(): RefreshAllCompletion {
        return awaitRefreshAllCompletion(
            loadStatus = {
                val response = api.getJobsStatus()
                response.takeIf { it.isSuccessful }?.body()?.scheduler
            }
        )
    }

    fun refreshPodcast(podcastId: Int) {
        if (_state.value.isRefreshingAll || podcastId in _state.value.refreshingPodcastIds) return
        _state.value = _state.value.copy(
            refreshingPodcastIds = _state.value.refreshingPodcastIds + podcastId,
            failedUnsubscribePodcastId = null,
            failedMarkAllListenedPodcastId = null,
            failedEpisodeAction = null,
            actionErrorMessage = null
        )
        viewModelScope.launch {
            try {
                val response = runCatching { api.refreshPodcast(podcastId) }.getOrNull()
                if (response?.isSuccessful == true) {
                    val nextState = runCatching { loadSubscriptionsState() }.getOrElse { error ->
                        _state.value.copy(
                            actionErrorMessage = error.message ?: "Could not reload subscriptions."
                        )
                    }
                    _state.value = nextState.withTransientStateFrom(_state.value)
                } else {
                    val message = response.errorMessage("Could not refresh this podcast.")
                    _state.value = _state.value.copy(
                        actionErrorMessage = message,
                        podcasts = _state.value.podcasts.withPodcastError(podcastId, message)
                    )
                }
            } finally {
                _state.value = _state.value.copy(
                    refreshingPodcastIds = _state.value.refreshingPodcastIds - podcastId
                )
            }
        }
    }

    fun retryLastAction() {
        val failedUnsubscribePodcastId = _state.value.failedUnsubscribePodcastId
        val failedMarkAllListenedPodcastId = _state.value.failedMarkAllListenedPodcastId
        val failedEpisodeAction = _state.value.failedEpisodeAction
        val failedPodcast = _state.value.podcasts.firstOrNull { it.errorMessage != null }
        when {
            failedUnsubscribePodcastId != null -> unsubscribePodcastNow(failedUnsubscribePodcastId)
            failedMarkAllListenedPodcastId != null -> markAllListened(failedMarkAllListenedPodcastId)
            failedEpisodeAction != null -> retryEpisodeAction(failedEpisodeAction)
            failedPodcast == null -> refreshAll()
            failedPodcast.episodesUnavailable -> refresh()
            else -> refreshPodcast(failedPodcast.id)
        }
    }

    private fun retryEpisodeAction(action: FailedEpisodeActionUi) {
        when (action.type) {
            FailedEpisodeActionType.AddToPlaylist -> addEpisodeToPlaylist(action.episodeId)
            FailedEpisodeActionType.RemoveFromPlaylist -> removeEpisodeFromPlaylist(action.episodeId)
            FailedEpisodeActionType.MarkListened -> setEpisodeListened(action.episodeId, true)
            FailedEpisodeActionType.MarkUnlistened -> setEpisodeListened(action.episodeId, false)
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
            pendingUnsubscribeJob = null
            unsubscribePodcastNow(podcastId)
        }
    }

    fun undoPodcastUnsubscribe(podcastId: Int) {
        if (_state.value.pendingUnsubscribe?.podcastId != podcastId) return
        pendingUnsubscribeJob?.cancel()
        pendingUnsubscribeJob = null
        _state.value = _state.value.copy(pendingUnsubscribe = null)
    }

    internal fun unsubscribePodcastNow(podcastId: Int) {
        if (podcastId in _state.value.unsubscribingPodcastIds) return
        if (_state.value.podcasts.none { it.id == podcastId }) {
            _state.value = _state.value.copy(
                failedUnsubscribePodcastId = null,
                actionErrorMessage = null
            )
            return
        }

        _state.value = _state.value.copy(
            pendingUnsubscribe = null,
            failedUnsubscribePodcastId = null,
            failedMarkAllListenedPodcastId = null,
            failedEpisodeAction = null,
            unsubscribingPodcastIds = _state.value.unsubscribingPodcastIds + podcastId,
            actionErrorMessage = null
        )
        viewModelScope.launch {
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
                    failedUnsubscribePodcastId = podcastId,
                    unsubscribingPodcastIds = _state.value.unsubscribingPodcastIds - podcastId,
                    actionErrorMessage = response.errorMessage("Could not unsubscribe from this podcast.")
                )
            }
        }
    }

    fun markAllListened(podcastId: Int) {
        val podcast = _state.value.podcasts.firstOrNull { it.id == podcastId } ?: return
        if (podcast.unlistenedEpisodeCount == 0) return
        if (podcastId in _state.value.markingAllListenedPodcastIds) return

        val previousPodcast = podcast
        _state.value = _state.value.copy(
            podcasts = _state.value.podcasts.markAllListenedOptimistically(podcastId),
            failedUnsubscribePodcastId = null,
            failedMarkAllListenedPodcastId = null,
            failedEpisodeAction = null,
            markingAllListenedPodcastIds = _state.value.markingAllListenedPodcastIds + podcastId,
            actionErrorMessage = null
        )
        viewModelScope.launch {
            when (val outcome = executeMarkAllListened { api.markAllListened(podcastId) }) {
                is MarkAllListenedOutcome.Success -> {
                    queueInvalidator.invalidate()
                    val nextState = runCatching { loadSubscriptionsState() }.getOrElse { reloadError ->
                        _state.value.copy(
                            actionErrorMessage = reloadError.message
                                ?: "Episodes were marked listened, but subscriptions could not be reloaded."
                        )
                    }
                    _state.value = nextState.withTransientStateFrom(_state.value).copy(
                        failedMarkAllListenedPodcastId = null,
                        markingAllListenedPodcastIds =
                            _state.value.markingAllListenedPodcastIds - podcastId
                    )
                }
                is MarkAllListenedOutcome.Failed -> {
                    _state.value = _state.value.copy(
                        podcasts = _state.value.podcasts.restorePodcast(previousPodcast),
                        failedMarkAllListenedPodcastId = podcastId,
                        markingAllListenedPodcastIds =
                            _state.value.markingAllListenedPodcastIds - podcastId,
                        actionErrorMessage = outcome.message
                    )
                }
            }
        }
    }

    fun addEpisodeToPlaylist(episodeId: Int) {
        performEpisodeAction(
            episodeId = episodeId,
            defaultErrorMessage = "Could not add episode to playlist.",
            invalidatePlaybackQueue = true,
            optimisticEpisodeUpdate = { episode ->
                episode.copy(inPlaylist = true, isListened = false)
            },
            failedActionType = FailedEpisodeActionType.AddToPlaylist
        ) {
            api.addToPlaylist(PlaylistAddRequest(episodeId = episodeId))
        }
    }

    fun removeEpisodeFromPlaylist(episodeId: Int) {
        performEpisodeAction(
            episodeId = episodeId,
            defaultErrorMessage = "Could not remove episode from playlist.",
            invalidatePlaybackQueue = true,
            optimisticEpisodeUpdate = { episode ->
                episode.copy(inPlaylist = false, downloaded = false)
            },
            failedActionType = FailedEpisodeActionType.RemoveFromPlaylist
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
            invalidatePlaybackQueue = isListened,
            optimisticEpisodeUpdate = { episode ->
                episode.copy(
                    isListened = isListened,
                    downloaded = if (isListened) false else episode.downloaded,
                    inPlaylist = if (isListened) false else episode.inPlaylist
                )
            },
            failedActionType = if (isListened) {
                FailedEpisodeActionType.MarkListened
            } else {
                FailedEpisodeActionType.MarkUnlistened
            }
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
        optimisticEpisodeUpdate: ((SubscriptionEpisodeUi) -> SubscriptionEpisodeUi)? = null,
        failedActionType: FailedEpisodeActionType,
        request: suspend () -> Response<Unit>
    ) {
        if (episodeId in _state.value.busyEpisodeIds) return
        val previousEpisode = _state.value.podcasts.findEpisode(episodeId)
        _state.value = _state.value.copy(
            podcasts = optimisticEpisodeUpdate?.let { update ->
                _state.value.podcasts.updateEpisode(episodeId, update)
            } ?: _state.value.podcasts,
            failedUnsubscribePodcastId = null,
            failedMarkAllListenedPodcastId = null,
            failedEpisodeAction = null,
            busyEpisodeIds = _state.value.busyEpisodeIds + episodeId,
            actionErrorMessage = null
        )

        viewModelScope.launch {
            val response = runCatching { request() }.getOrNull()
            if (response?.isSuccessful == true) {
                if (invalidatePlaybackQueue) queueInvalidator.invalidate()
                val nextState = runCatching { loadSubscriptionsState() }.getOrElse { error ->
                    _state.value.copy(
                        busyEpisodeIds = _state.value.busyEpisodeIds - episodeId,
                        actionErrorMessage = error.message ?: "Could not reload subscriptions."
                    )
                }
                val current = _state.value
                _state.value = nextState.withTransientStateFrom(current).copy(
                    busyEpisodeIds = current.busyEpisodeIds - episodeId
                )
            } else {
                _state.value = _state.value.copy(
                    podcasts = previousEpisode?.let { episode ->
                        _state.value.podcasts.replaceEpisode(episode)
                    } ?: _state.value.podcasts,
                    failedEpisodeAction = FailedEpisodeActionUi(episodeId, failedActionType),
                    busyEpisodeIds = _state.value.busyEpisodeIds - episodeId,
                    actionErrorMessage = response.errorMessage(defaultErrorMessage)
                )
            }
        }
    }


    private suspend fun loadSubscriptionsState(): SubscriptionsUiState {
        val podcasts = api.getPodcasts()
            .requireApiBody("Could not load podcasts.")
            .podcasts
            ?: missingApiPayload("Could not load podcasts.")
        val playlistEpisodeIds = (
            api.getPlaylist()
                .requireApiBody("Could not load playlist.")
                .items
                ?.map { it.episodeId }
                ?: missingApiPayload("Could not load playlist.")
            ).toSet()

        var hasEpisodeLoadFailures = false
        val podcastItems = podcasts.map { podcast ->
            val episodesResult = runCatching {
                api.getPodcastEpisodes(podcast.id)
                    .requireApiBody("Could not load episodes.")
                    .episodes
                    ?: missingApiPayload("Could not load episodes.")
            }
            val allEpisodes = episodesResult.getOrNull().orEmpty()
            val loadErrorMessage = if (episodesResult.isFailure) {
                hasEpisodeLoadFailures = true
                "Episodes unavailable. Refresh this podcast to try again."
            } else {
                null
            }

            podcast.toSubscriptionPodcast(
                episodes = allEpisodes.map { it.toSubscriptionEpisode(playlistEpisodeIds) },
                totalEpisodeCount = allEpisodes.size,
                unlistenedEpisodeCount = allEpisodes.count { !it.isListened },
                errorMessage = loadErrorMessage,
                episodesUnavailable = episodesResult.isFailure
            )
        }

        return SubscriptionsUiState(
            actionErrorMessage = if (hasEpisodeLoadFailures) {
                "Some podcast episodes could not be loaded."
            } else {
                null
            },
            podcasts = podcastItems
        )
    }

    private fun PodcastDto.toSubscriptionPodcast(
        episodes: List<SubscriptionEpisodeUi>,
        totalEpisodeCount: Int,
        unlistenedEpisodeCount: Int,
        errorMessage: String? = null,
        episodesUnavailable: Boolean = false
    ): SubscriptionPodcastUi {
        return SubscriptionPodcastUi(
            id = id,
            title = cleanFeedText(title).ifBlank { "Untitled podcast" },
            description = cleanFeedText(description).ifBlank { rssUrl.orEmpty() },
            imageUrl = imageUrl,
            totalEpisodeCount = totalEpisodeCount,
            unlistenedEpisodeCount = unlistenedEpisodeCount,
            episodes = episodes,
            errorMessage = errorMessage,
            episodesUnavailable = episodesUnavailable
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
            summary = subscriptionShowNotes(),
            inPlaylist = id in playlistEpisodeIds
        )
    }

    private fun Response<*>?.errorMessage(defaultMessage: String): String {
        return apiErrorMessage(this?.errorBody()?.string(), defaultMessage)
    }
}

internal fun EpisodeDto.subscriptionShowNotes(): String? {
    return sequenceOf(showNotes, description, summary)
        .firstOrNull { !it.isNullOrBlank() }
        ?.trim()
}

data class SubscriptionsUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val actionErrorMessage: String? = null,
    val downloadFailure: SubscriptionDownloadFailureUi? = null,
    val downloadingEpisodeIds: Set<Int> = emptySet(),
    val pendingUnsubscribe: PendingUnsubscribeUi? = null,
    val failedUnsubscribePodcastId: Int? = null,
    val failedMarkAllListenedPodcastId: Int? = null,
    val failedEpisodeAction: FailedEpisodeActionUi? = null,
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

data class FailedEpisodeActionUi(
    val episodeId: Int,
    val type: FailedEpisodeActionType
)

enum class FailedEpisodeActionType {
    AddToPlaylist,
    RemoveFromPlaylist,
    MarkListened,
    MarkUnlistened
}

private const val DOWNLOAD_FAILURE_TIMEOUT_MS = 10_000L
private const val UNSUBSCRIBE_WINDOW_SECONDS = 15
private const val UNSUBSCRIBE_TICK_MS = 1_000L
private const val REFRESH_ALL_STATUS_POLL_MS = 3_000L

internal sealed interface RefreshAllCompletion {
    data object Completed : RefreshAllCompletion
    data class Failed(val message: String) : RefreshAllCompletion
}

internal suspend fun awaitRefreshAllCompletion(
    pollIntervalMs: Long = REFRESH_ALL_STATUS_POLL_MS,
    loadStatus: suspend () -> SchedulerStatusDto?
): RefreshAllCompletion {
    while (true) {
        delay(pollIntervalMs)
        val scheduler = runCatching { loadStatus() }.getOrNull() ?: continue
        when (scheduler.state?.lowercase()) {
            "running" -> Unit
            "completed", "idle" -> return RefreshAllCompletion.Completed
            "failed" -> return RefreshAllCompletion.Failed(
                scheduler.lastError?.takeIf { it.isNotBlank() }
                    ?: "Failed to refresh podcasts."
            )
            else -> return RefreshAllCompletion.Failed("Backend returned an unknown refresh status.")
        }
    }
}

internal fun SubscriptionsUiState.withTransientStateFrom(
    current: SubscriptionsUiState,
    completedEpisodeId: Int? = null
): SubscriptionsUiState {
    return copy(
        downloadFailure = current.downloadFailure,
        downloadingEpisodeIds = completedEpisodeId?.let {
            current.downloadingEpisodeIds - it
        } ?: current.downloadingEpisodeIds,
        pendingUnsubscribe = current.pendingUnsubscribe,
        failedUnsubscribePodcastId = current.failedUnsubscribePodcastId?.takeIf { podcastId ->
            podcasts.any { it.id == podcastId }
        },
        failedMarkAllListenedPodcastId = current.failedMarkAllListenedPodcastId?.takeIf { podcastId ->
            podcasts.any { it.id == podcastId && it.unlistenedEpisodeCount > 0 }
        },
        failedEpisodeAction = current.failedEpisodeAction?.takeIf { failedAction ->
            podcasts.findEpisode(failedAction.episodeId) != null
        },
        markingAllListenedPodcastIds = current.markingAllListenedPodcastIds,
        isRefreshingAll = current.isRefreshingAll,
        refreshingPodcastIds = current.refreshingPodcastIds,
        unsubscribingPodcastIds = current.unsubscribingPodcastIds,
        busyEpisodeIds = current.busyEpisodeIds
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

internal fun List<SubscriptionPodcastUi>.restorePodcast(
    previousPodcast: SubscriptionPodcastUi
): List<SubscriptionPodcastUi> {
    return map { podcast ->
        if (podcast.id == previousPodcast.id) previousPodcast else podcast
    }
}

internal sealed interface MarkAllListenedOutcome {
    data class Success(val markedEpisodes: Int) : MarkAllListenedOutcome
    data class Failed(val message: String) : MarkAllListenedOutcome
}

internal suspend fun executeMarkAllListened(
    request: suspend () -> Response<MarkAllListenedResponse>
): MarkAllListenedOutcome {
    val response = runCatching { request() }.getOrNull()
        ?: return MarkAllListenedOutcome.Failed("Could not reach mpod backend.")
    val body = response.body()
    return if (response.isSuccessful && body?.success == true) {
        MarkAllListenedOutcome.Success(body.markedEpisodes)
    } else {
        MarkAllListenedOutcome.Failed(
            apiErrorMessage(
                response.errorBody()?.string(),
                "Could not mark all episodes as listened."
            )
        )
    }
}

internal fun List<SubscriptionPodcastUi>.updateEpisode(
    episodeId: Int,
    update: (SubscriptionEpisodeUi) -> SubscriptionEpisodeUi
): List<SubscriptionPodcastUi> {
    return map { podcast ->
        if (podcast.episodes.none { it.id == episodeId }) return@map podcast
        val updatedEpisodes = podcast.episodes.map { episode ->
            if (episode.id == episodeId) update(episode) else episode
        }
        podcast.copy(
            episodes = updatedEpisodes,
            unlistenedEpisodeCount = updatedEpisodes.count { !it.isListened }
        )
    }
}

private fun List<SubscriptionPodcastUi>.findEpisode(episodeId: Int): SubscriptionEpisodeUi? {
    return firstNotNullOfOrNull { podcast ->
        podcast.episodes.firstOrNull { it.id == episodeId }
    }
}

private fun List<SubscriptionPodcastUi>.replaceEpisode(
    episode: SubscriptionEpisodeUi
): List<SubscriptionPodcastUi> {
    return updateEpisode(episode.id) { episode }
}

private fun List<SubscriptionPodcastUi>.withPodcastError(
    podcastId: Int,
    errorMessage: String?
): List<SubscriptionPodcastUi> {
    return map { podcast ->
        if (podcast.id == podcastId) podcast.copy(errorMessage = errorMessage) else podcast
    }
}

data class SubscriptionPodcastUi(
    val id: Int,
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
    val id: Int,
    val title: String,
    val durationSeconds: Int?,
    val publishedAt: String?,
    val isListened: Boolean,
    val downloaded: Boolean,
    val summary: String?,
    val inPlaylist: Boolean
)
