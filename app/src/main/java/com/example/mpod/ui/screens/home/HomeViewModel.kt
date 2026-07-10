package com.example.mpod.ui.screens.home

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
class HomeViewModel @Inject constructor(
    private val api: MpodApi
) : ViewModel() {
    private val _state = MutableStateFlow(HomeUiState(isLoading = true))
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, errorMessage = null)
            val nextState = runCatching { loadHomeState() }.getOrElse { error ->
                HomeUiState(errorMessage = error.message ?: "Could not load playlist.")
            }
            _state.value = nextState
        }
    }

    private suspend fun loadHomeState(): HomeUiState {
        val podcasts = api.getPodcasts().requireBody("Could not load podcasts.").podcasts
        if (podcasts.isEmpty()) {
            return HomeUiState(hasPodcasts = false)
        }

        val podcastsById = podcasts.associateBy { it.id }
        val playlistItems = api.getPlaylist()
            .requireBody("Could not load playlist.")
            .items
            .sortedBy { it.position }

        val queue = playlistItems.mapNotNull { item ->
            api.getEpisode(item.episodeId).body()?.episode?.toHomeEpisode(podcastsById)
        }

        return HomeUiState(
            hasPodcasts = true,
            queue = queue
        )
    }

    private fun EpisodeDto.toHomeEpisode(podcastsById: Map<Int, PodcastDto>): HomeEpisodeUi {
        val podcast = podcastsById[podcastId]
        return HomeEpisodeUi(
            id = id,
            title = title.orEmpty().ifBlank { "Untitled episode" },
            podcastTitle = podcast?.title.orEmpty().ifBlank { "Podcast" },
            durationSeconds = duration.toDurationSeconds()
        )
    }

    private fun <T> Response<T>.requireBody(defaultMessage: String): T {
        if (isSuccessful) {
            body()?.let { return it }
        }
        throw IllegalStateException(errorBody()?.string().orEmpty().ifBlank { defaultMessage })
    }
}

data class HomeUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val hasPodcasts: Boolean = true,
    val queue: List<HomeEpisodeUi> = emptyList()
)

data class HomeEpisodeUi(
    val id: Int,
    val title: String,
    val podcastTitle: String,
    val durationSeconds: Int?
)
