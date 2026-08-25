package com.example.mpod.ui.components

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mpod.data.repository.PodcastRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URI
import javax.inject.Inject

@HiltViewModel
class AddPodcastViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val podcastRepository: PodcastRepository,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle()
) : ViewModel() {

    private val _state = MutableStateFlow(
        AddPodcastUiState(
            mode = savedStateHandle.get<String>(MODE_KEY)
                ?.let { saved -> runCatching { AddPodcastMode.valueOf(saved) }.getOrNull() }
                ?: AddPodcastMode.RssFeedUrl,
            rssUrl = savedStateHandle[RSS_URL_KEY] ?: ""
        )
    )
    val state: StateFlow<AddPodcastUiState> = _state.asStateFlow()

    fun begin(mode: AddPodcastMode) {
        savedStateHandle[MODE_KEY] = mode.name
        savedStateHandle[RSS_URL_KEY] = ""
        _state.value = AddPodcastUiState(mode = mode)
    }

    fun reset() {
        savedStateHandle.remove<String>(MODE_KEY)
        savedStateHandle.remove<String>(RSS_URL_KEY)
        _state.value = AddPodcastUiState()
    }

    fun setMode(mode: AddPodcastMode) {
        savedStateHandle[MODE_KEY] = mode.name
        _state.value = _state.value.copy(
            mode = mode,
            errorMessage = null,
            importResult = null
        )
    }

    fun setRssUrl(url: String) {
        savedStateHandle[RSS_URL_KEY] = url
        _state.value = _state.value.copy(
            rssUrl = url,
            errorMessage = null,
            importResult = null
        )
    }

    fun addRssFeed(url: String, onSuccess: () -> Unit) {
        if (_state.value.isSubmitting) return
        val trimmedUrl = url.trim()
        if (trimmedUrl.isBlank()) {
            _state.value = _state.value.copy(errorMessage = "Paste RSS feed URL.")
            return
        }
        if (!trimmedUrl.isHttpUrl()) {
            _state.value = _state.value.copy(
                errorMessage = "Enter a valid http or https RSS feed URL."
            )
            return
        }

        _state.value = _state.value.copy(isSubmitting = true, errorMessage = null)
        viewModelScope.launch {
            val result = podcastRepository.addPodcastByFeedUrl(trimmedUrl)
            if (result.isSuccess) {
                reset()
                onSuccess()
            } else {
                _state.value = _state.value.copy(
                    isSubmitting = false,
                    errorMessage = result.exceptionOrNull()?.message ?: "Could not parse or reach RSS feed."
                )
            }
        }
    }

    fun importOpml(uri: Uri, onSuccess: () -> Unit) {
        if (_state.value.isSubmitting) return
        _state.value = _state.value.copy(isSubmitting = true, errorMessage = null)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        podcastRepository.importOpml(stream)
                    } ?: Result.failure(IllegalStateException("Could not open file stream"))
                }.getOrElse { Result.failure(it) }
            }

            if (result.isSuccess) {
                val importedCount = result.getOrDefault(0)
                _state.value = _state.value.copy(
                    isSubmitting = false,
                    importResult = OpmlImportResultUi(imported = importedCount, skipped = 0)
                )
                onSuccess()
            } else {
                _state.value = _state.value.copy(
                    isSubmitting = false,
                    errorMessage = result.exceptionOrNull()?.message ?: "Could not read or parse OPML file."
                )
            }
        }
    }

    private fun String.isHttpUrl(): Boolean = runCatching {
        val uri = URI(this)
        uri.scheme in setOf("http", "https") && !uri.host.isNullOrBlank()
    }.getOrDefault(false)
}

data class AddPodcastUiState(
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val importResult: OpmlImportResultUi? = null,
    val mode: AddPodcastMode = AddPodcastMode.RssFeedUrl,
    val rssUrl: String = ""
)

data class OpmlImportResultUi(
    val imported: Int,
    val skipped: Int
)

private const val MODE_KEY = "add_podcast_mode"
private const val RSS_URL_KEY = "add_podcast_rss_url"
