package com.example.mpod.ui.components

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mpod.data.repository.PodcastRepository
import com.example.mpod.data.repository.SingleTrackRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URI
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class AddPodcastViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val podcastRepository: PodcastRepository,
    private val singleTrackRepository: SingleTrackRepository,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle()
) : ViewModel() {

    private data class AudioIngestionTask(
        val url: String,
        val title: String?,
        val onSuccess: () -> Unit
    )

    private val audioIngestionChannel = Channel<AudioIngestionTask>(Channel.UNLIMITED)

    init {
        viewModelScope.launch {
            for (task in audioIngestionChannel) {
                processAudioIngestionTask(task)
            }
        }
    }

    private val _state = MutableStateFlow(
        AddPodcastUiState(
            mode = savedStateHandle.get<String>(MODE_KEY)
                ?.let { saved -> runCatching { AddPodcastMode.valueOf(saved) }.getOrNull() }
                ?: AddPodcastMode.RssFeedUrl,
            rssUrl = savedStateHandle[RSS_URL_KEY] ?: ""
        )
    )
    val state: StateFlow<AddPodcastUiState> = _state.asStateFlow()

    private val _bannerState = MutableStateFlow<AddPodcastBannerUiState?>(null)
    val bannerState: StateFlow<AddPodcastBannerUiState?> = _bannerState.asStateFlow()

    private var lastFailedAction: LastFailedAction? = null
    private var autoDismissJob: Job? = null

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

    fun dismissBanner() {
        autoDismissJob?.cancel()
        _bannerState.value = null
    }

    fun retryLastAction(
        onRssSuccess: () -> Unit = {},
        onAudioSuccess: () -> Unit = {},
        onOpmlSuccess: () -> Unit = {}
    ) {
        val last = lastFailedAction ?: return
        dismissBanner()
        when (last) {
            is LastFailedAction.Rss -> addRssFeed(last.url, onRssSuccess)
            is LastFailedAction.Audio -> addAudioUrl(last.url, last.title, onAudioSuccess)
            is LastFailedAction.Opml -> importOpml(last.uri, onOpmlSuccess)
        }
    }

    private fun scheduleAutoDismiss(delayMs: Long = 4000L) {
        autoDismissJob?.cancel()
        autoDismissJob = viewModelScope.launch {
            delay(delayMs)
            if (_bannerState.value?.type == StatusBannerType.Info) {
                _bannerState.value = null
            }
        }
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
            val errorMsg = "Paste RSS feed URL."
            _state.value = _state.value.copy(errorMessage = errorMsg)
            _bannerState.value = AddPodcastBannerUiState(
                message = errorMsg,
                type = StatusBannerType.Error
            )
            return
        }
        if (!trimmedUrl.isHttpUrl()) {
            val errorMsg = "Enter a valid http or https RSS feed URL."
            _state.value = _state.value.copy(errorMessage = errorMsg)
            _bannerState.value = AddPodcastBannerUiState(
                message = errorMsg,
                type = StatusBannerType.Error
            )
            return
        }

        _state.value = _state.value.copy(isSubmitting = true, errorMessage = null)
        _bannerState.value = AddPodcastBannerUiState(
            message = "Adding podcast...",
            type = StatusBannerType.Loading
        )
        viewModelScope.launch {
            val result = podcastRepository.addPodcastByFeedUrl(trimmedUrl)
            if (result.isSuccess) {
                reset()
                _bannerState.value = AddPodcastBannerUiState(
                    message = "Podcast added successfully",
                    type = StatusBannerType.Info
                )
                scheduleAutoDismiss(4000L)
                onSuccess()
            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: "Could not parse or reach RSS feed."
                lastFailedAction = LastFailedAction.Rss(trimmedUrl)
                _state.value = _state.value.copy(
                    isSubmitting = false,
                    errorMessage = errorMsg,
                    rssUrl = trimmedUrl
                )
                _bannerState.value = AddPodcastBannerUiState(
                    message = errorMsg,
                    type = StatusBannerType.Error,
                    actionLabel = "Try again"
                )
            }
        }
    }

    fun addAudioUrl(url: String, title: String? = null, onSuccess: () -> Unit = {}) {
        val trimmedUrl = url.trim()
        if (trimmedUrl.isBlank()) {
            val errorMsg = "Paste podcast link."
            _state.value = _state.value.copy(errorMessage = errorMsg)
            _bannerState.value = AddPodcastBannerUiState(
                message = errorMsg,
                type = StatusBannerType.Error
            )
            return
        }
        if (!isSupportedAudioSource(trimmedUrl)) {
            val errorMsg = "Enter a valid audio link or file."
            _state.value = _state.value.copy(errorMessage = errorMsg)
            _bannerState.value = AddPodcastBannerUiState(
                message = errorMsg,
                type = StatusBannerType.Error
            )
            return
        }

        audioIngestionChannel.trySend(AudioIngestionTask(trimmedUrl, title, onSuccess))
    }

    private suspend fun processAudioIngestionTask(task: AudioIngestionTask) {
        _state.value = _state.value.copy(isSubmitting = true, errorMessage = null)
        _bannerState.value = AddPodcastBannerUiState(
            message = "Resolving audio link...",
            type = StatusBannerType.Loading
        )
        val result = runCatching {
            singleTrackRepository.playSingleTrack(task.url, task.title)
        }
        if (result.isSuccess) {
            reset()
            _bannerState.value = AddPodcastBannerUiState(
                message = "Audio track added to playlist",
                type = StatusBannerType.Info
            )
            scheduleAutoDismiss(3000L)
            task.onSuccess()
        } else {
            val errorMsg = result.exceptionOrNull()?.message ?: "Could not load audio track."
            lastFailedAction = LastFailedAction.Audio(task.url, task.title)
            _state.value = _state.value.copy(
                errorMessage = errorMsg,
                rssUrl = task.url
            )
            _bannerState.value = AddPodcastBannerUiState(
                message = errorMsg,
                type = StatusBannerType.Error,
                actionLabel = "Try again"
            )
        }
        _state.value = _state.value.copy(isSubmitting = false)
    }

    fun importOpml(uri: Uri, onSuccess: () -> Unit) {
        if (_state.value.isSubmitting) return
        _state.value = _state.value.copy(isSubmitting = true, errorMessage = null)
        _bannerState.value = AddPodcastBannerUiState(
            message = "Importing OPML podcasts...",
            type = StatusBannerType.Loading
        )
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        podcastRepository.importOpml(stream)
                    } ?: Result.failure(IllegalStateException("Could not open file stream"))
                }.getOrElse { Result.failure(it) }
            }

            if (result.isSuccess) {
                val summary = result.getOrThrow()
                _state.value = _state.value.copy(
                    isSubmitting = false,
                    importResult = OpmlImportResultUi(
                        imported = summary.imported,
                        skipped = summary.skipped,
                        errors = summary.errors
                    )
                )
                val message = when {
                    summary.skipped == 0 -> "Imported ${summary.imported} podcasts"
                    summary.imported > 0 -> "Imported ${summary.imported} podcasts (${summary.skipped} skipped)"
                    else -> "No podcasts imported (${summary.skipped} skipped)"
                }
                val hasErrors = summary.errors.isNotEmpty()
                _bannerState.value = AddPodcastBannerUiState(
                    message = message,
                    type = if (hasErrors) StatusBannerType.Error else StatusBannerType.Info,
                    actionLabel = if (hasErrors) "Details" else null
                )
                if (summary.errors.isEmpty()) {
                    scheduleAutoDismiss(5000L)
                }
                onSuccess()
            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: "Could not read or parse OPML file."
                lastFailedAction = LastFailedAction.Opml(uri)
                _state.value = _state.value.copy(
                    isSubmitting = false,
                    errorMessage = errorMsg
                )
                _bannerState.value = AddPodcastBannerUiState(
                    message = errorMsg,
                    type = StatusBannerType.Error,
                    actionLabel = "Try again"
                )
            }
        }
    }

    internal fun isSupportedAudioSource(url: String): Boolean = runCatching {
        val uri = URI(url.trim())
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        when (scheme) {
            "http", "https" -> !uri.host.isNullOrBlank()
            "content", "file" -> !uri.schemeSpecificPart.isNullOrBlank()
            else -> false
        }
    }.getOrDefault(false)

    internal fun isHttpUrl(url: String): Boolean = runCatching {
        val uri = URI(url)
        uri.scheme in setOf("http", "https") && !uri.host.isNullOrBlank()
    }.getOrDefault(false)

    private fun String.isHttpUrl(): Boolean = isHttpUrl(this)
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
    val skipped: Int,
    val errors: List<String> = emptyList()
)

data class AddPodcastBannerUiState(
    val message: String,
    val type: StatusBannerType,
    val actionLabel: String? = null
)

sealed class LastFailedAction {
    data class Rss(val url: String) : LastFailedAction()
    data class Audio(val url: String, val title: String? = null) : LastFailedAction()
    data class Opml(val uri: Uri) : LastFailedAction()
}

private const val MODE_KEY = "add_podcast_mode"
private const val RSS_URL_KEY = "add_podcast_rss_url"
