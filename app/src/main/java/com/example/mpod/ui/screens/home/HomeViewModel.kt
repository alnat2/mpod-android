package com.example.mpod.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mpod.data.network.MpodApi
import com.example.mpod.data.network.model.EpisodeListenedRequest
import com.example.mpod.data.network.model.PlaybackQueueEpisodeDto
import com.example.mpod.data.network.model.PlaylistReorderRequest
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import retrofit2.Response
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val api: MpodApi,
    private val queueInvalidator: PlaybackQueueInvalidator
) : ViewModel() {
    private val _state = MutableStateFlow(HomeUiState(isLoading = true))
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        refresh()
        viewModelScope.launch {
            queueInvalidator.homeRefreshEvents.collectLatest {
                reloadNow(invalidatePlaybackQueue = false)
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            reloadNow(invalidatePlaybackQueue = true)
        }
    }

    private suspend fun reloadNow(invalidatePlaybackQueue: Boolean) {
        _state.value = _state.value.copy(isLoading = true, errorMessage = null)
        val nextState = runCatching { loadHomeState() }.getOrElse { error ->
            HomeUiState(errorMessage = error.userFacingApiMessage("Could not load playlist."))
        }
        _state.value = nextState.withTransientStateFrom(_state.value)
        if (invalidatePlaybackQueue && nextState.errorMessage == null) {
            queueInvalidator.invalidate()
        }
    }

    private suspend fun loadHomeState(): HomeUiState {
        val podcasts = api.getPodcasts()
            .requireApiBody("Could not load podcasts.")
            .podcasts
            ?: missingApiPayload("Could not load podcasts.")
        if (podcasts.isEmpty()) {
            return HomeUiState(hasPodcasts = false)
        }

        val playbackQueue = api.getPlaybackQueue()
            .requireApiBody("Could not load playback queue.")
        val queue = playbackQueue.queue
            ?: missingApiPayload("Could not load playback queue.")

        return HomeUiState(
            hasPodcasts = true,
            activeEpisodeId = playbackQueue.activePlayback?.episodeId,
            queue = queue.map { it.toHomeEpisode() }
        )
    }

    private fun PlaybackQueueEpisodeDto.toHomeEpisode(): HomeEpisodeUi {
        return HomeEpisodeUi(
            id = id,
            title = cleanFeedText(title).ifBlank { "Untitled episode" },
            podcastTitle = cleanFeedText(podcastTitle).ifBlank { "Podcast" },
            durationSeconds = duration.toDurationSeconds(),
            playbackPositionSeconds = playback?.positionSeconds ?: 0,
            isListened = isListened,
            downloaded = downloaded,
            summary = cleanFeedText(showNotes ?: description).ifBlank { null }
        )
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
                val nextState = runCatching { loadHomeState() }.getOrElse { error ->
                    _state.value.copy(
                        downloadingEpisodeIds = _state.value.downloadingEpisodeIds - episodeId,
                        actionErrorMessage = error.message ?: "Could not reload playlist."
                    )
                }
                _state.value = nextState.withTransientStateFrom(
                    current = _state.value,
                    completedDownloadEpisodeId = episodeId
                )
            } else {
                val failure = DownloadFailureUi(
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

    fun moveEpisode(episodeId: Int, offset: Int) {
        val currentQueue = _state.value.queue
        if (episodeId in _state.value.busyEpisodeIds) return

        val nextQueue = reorderEpisodes(
            episodes = currentQueue,
            episodeId = episodeId,
            offset = offset
        ) ?: return

        _state.value = _state.value.copy(
            queue = nextQueue,
            busyEpisodeIds = _state.value.busyEpisodeIds + episodeId,
            actionErrorMessage = null
        )
        viewModelScope.launch {
            val response = runCatching {
                api.reorderPlaylist(PlaylistReorderRequest(nextQueue.map { it.id }))
            }.getOrNull()

            if (response?.isSuccessful == true) {
                queueInvalidator.invalidate()
                val nextState = runCatching { loadHomeState() }.getOrElse { error ->
                    _state.value.copy(
                        busyEpisodeIds = _state.value.busyEpisodeIds - episodeId,
                        actionErrorMessage = error.message ?: "Could not reload playlist."
                    )
                }
                _state.value = nextState.withTransientStateFrom(
                    current = _state.value,
                    completedBusyEpisodeId = episodeId
                )
            } else {
                _state.value = _state.value.copy(
                    queue = currentQueue,
                    busyEpisodeIds = _state.value.busyEpisodeIds - episodeId,
                    actionErrorMessage = response.errorMessage("Could not reorder playlist.")
                )
            }
        }
    }

    private fun performEpisodeAction(
        episodeId: Int,
        defaultErrorMessage: String,
        invalidatePlaybackQueue: Boolean = false,
        request: suspend () -> Response<Unit>
    ) {
        if (episodeId in _state.value.busyEpisodeIds) return
        _state.value = _state.value.copy(
            busyEpisodeIds = _state.value.busyEpisodeIds + episodeId,
            actionErrorMessage = null
        )

        viewModelScope.launch {
            val response = runCatching { request() }.getOrNull()
            if (response?.isSuccessful == true) {
                if (invalidatePlaybackQueue) queueInvalidator.invalidate()
                val nextState = runCatching { loadHomeState() }.getOrElse { error ->
                    _state.value.copy(
                        busyEpisodeIds = _state.value.busyEpisodeIds - episodeId,
                        actionErrorMessage = error.message ?: "Could not reload playlist."
                    )
                }
                _state.value = nextState.withTransientStateFrom(
                    current = _state.value,
                    completedBusyEpisodeId = episodeId
                )
            } else {
                _state.value = _state.value.copy(
                    busyEpisodeIds = _state.value.busyEpisodeIds - episodeId,
                    actionErrorMessage = response.errorMessage(defaultErrorMessage)
                )
            }
        }
    }

    private fun Response<*>?.errorMessage(defaultMessage: String): String {
        return apiErrorMessage(this?.errorBody()?.string(), defaultMessage)
    }

}

data class HomeUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val actionErrorMessage: String? = null,
    val downloadFailure: DownloadFailureUi? = null,
    val downloadingEpisodeIds: Set<Int> = emptySet(),
    val busyEpisodeIds: Set<Int> = emptySet(),
    val hasPodcasts: Boolean = true,
    val activeEpisodeId: Int? = null,
    val queue: List<HomeEpisodeUi> = emptyList()
)

data class DownloadFailureUi(
    val episodeId: Int,
    val message: String
)

private const val DOWNLOAD_FAILURE_TIMEOUT_MS = 10_000L

internal fun HomeUiState.withTransientStateFrom(
    current: HomeUiState,
    completedDownloadEpisodeId: Int? = null,
    completedBusyEpisodeId: Int? = null
): HomeUiState {
    return copy(
        downloadFailure = current.downloadFailure,
        downloadingEpisodeIds = completedDownloadEpisodeId?.let {
            current.downloadingEpisodeIds - it
        } ?: current.downloadingEpisodeIds,
        busyEpisodeIds = completedBusyEpisodeId?.let {
            current.busyEpisodeIds - it
        } ?: current.busyEpisodeIds
    )
}

data class HomeEpisodeUi(
    val id: Int,
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
    episodeId: Int,
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
