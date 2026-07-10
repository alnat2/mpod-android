package com.example.mpod.ui.components

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mpod.data.network.MpodApi
import com.example.mpod.data.network.model.CreatePodcastRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import retrofit2.Response
import javax.inject.Inject

@HiltViewModel
class AddPodcastViewModel @Inject constructor(
    private val api: MpodApi
) : ViewModel() {
    private val _state = MutableStateFlow(AddPodcastUiState())
    val state: StateFlow<AddPodcastUiState> = _state.asStateFlow()

    fun addRssFeed(url: String, onSuccess: () -> Unit) {
        val trimmedUrl = url.trim()
        if (trimmedUrl.isBlank()) {
            _state.value = AddPodcastUiState(errorMessage = "Paste RSS feed URL.")
            return
        }

        viewModelScope.launch {
            _state.value = AddPodcastUiState(isSubmitting = true)
            val response = runCatching {
                api.createPodcast(CreatePodcastRequest(rssUrl = trimmedUrl))
            }.getOrNull()

            if (response?.isSuccessful == true) {
                _state.value = AddPodcastUiState()
                onSuccess()
            } else {
                _state.value = AddPodcastUiState(
                    errorMessage = addPodcastErrorMessage(response)
                )
            }
        }
    }

    private fun addPodcastErrorMessage(response: Response<*>?): String {
        if (response == null) return "Could not reach mpod backend."
        val rawError = response.errorBody()?.string().orEmpty()
        if (rawError.isBlank()) return "Could not add this RSS feed."

        return runCatching {
            val errorObject = JSONObject(rawError).optJSONObject("error")
            errorObject?.optString("message")?.takeIf { it.isNotBlank() }
                ?: JSONObject(rawError).optString("message").takeIf { it.isNotBlank() }
        }.getOrNull() ?: "Could not add this RSS feed."
    }
}

data class AddPodcastUiState(
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null
)
