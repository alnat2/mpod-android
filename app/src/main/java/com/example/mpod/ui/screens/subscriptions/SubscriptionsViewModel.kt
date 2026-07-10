package com.example.mpod.ui.screens.subscriptions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mpod.data.network.MpodApi
import com.example.mpod.data.network.model.EpisodeDto
import com.example.mpod.data.network.model.PodcastDto
import com.example.mpod.ui.util.toDurationSeconds
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.Response
import javax.inject.Inject

@HiltViewModel
class SubscriptionsViewModel @Inject constructor(
    private val api: MpodApi
) : ViewModel() {
    private val _state = MutableStateFlow(SubscriptionsUiState(isLoading = true))
    val state: StateFlow<SubscriptionsUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, errorMessage = null)
            val nextState = runCatching { loadSubscriptionsState() }.getOrElse { error ->
                SubscriptionsUiState(errorMessage = error.message ?: "Could not load subscriptions.")
            }
            _state.value = nextState
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
            val episodes = api.getPodcastEpisodes(podcast.id)
                .requireBody("Could not load episodes.")
                .episodes
                .map { it.toSubscriptionEpisode(playlistEpisodeIds) }

            podcast.toSubscriptionPodcast(episodes)
        }

        return SubscriptionsUiState(podcasts = podcastItems)
    }

    private fun PodcastDto.toSubscriptionPodcast(episodes: List<SubscriptionEpisodeUi>): SubscriptionPodcastUi {
        return SubscriptionPodcastUi(
            id = id,
            title = title.orEmpty().ifBlank { "Untitled podcast" },
            description = description ?: rssUrl.orEmpty(),
            episodes = episodes
        )
    }

    private fun EpisodeDto.toSubscriptionEpisode(playlistEpisodeIds: Set<Int>): SubscriptionEpisodeUi {
        return SubscriptionEpisodeUi(
            id = id,
            title = title.orEmpty().ifBlank { "Untitled episode" },
            durationSeconds = duration.toDurationSeconds(),
            publishedAt = publishedAt,
            isListened = isListened,
            downloaded = downloaded == true,
            inPlaylist = id in playlistEpisodeIds
        )
    }

    private fun <T> Response<T>.requireBody(defaultMessage: String): T {
        if (isSuccessful) {
            body()?.let { return it }
        }
        throw IllegalStateException(errorBody()?.string().orEmpty().ifBlank { defaultMessage })
    }
}

data class SubscriptionsUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val podcasts: List<SubscriptionPodcastUi> = emptyList()
)

data class SubscriptionPodcastUi(
    val id: Int,
    val title: String,
    val description: String,
    val episodes: List<SubscriptionEpisodeUi>
)

data class SubscriptionEpisodeUi(
    val id: Int,
    val title: String,
    val durationSeconds: Int?,
    val publishedAt: String?,
    val isListened: Boolean,
    val downloaded: Boolean,
    val inPlaylist: Boolean
)
