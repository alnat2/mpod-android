package com.example.mpod.ui.components

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mpod.data.network.MpodApi
import com.example.mpod.data.network.model.CreatePodcastRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import retrofit2.Response
import java.net.URI
import javax.inject.Inject

@HiltViewModel
class AddPodcastViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
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
        if (!trimmedUrl.isHttpUrl()) {
            _state.value = AddPodcastUiState(errorMessage = "Enter a valid http or https RSS feed URL.")
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

    fun importOpml(uri: Uri?, onSuccess: () -> Unit) {
        if (uri == null) {
            _state.value = AddPodcastUiState(errorMessage = "Choose an OPML file.")
            return
        }

        viewModelScope.launch {
            _state.value = AddPodcastUiState(isSubmitting = true)
            val response = runCatching {
                val filePart = withContext(Dispatchers.IO) {
                    uri.toMultipartPart()
                }
                api.importOpml(filePart)
            }.getOrNull()

            if (response?.isSuccessful == true) {
                _state.value = AddPodcastUiState()
                onSuccess()
            } else {
                _state.value = AddPodcastUiState(
                    errorMessage = addPodcastErrorMessage(response, "Could not import this OPML file.")
                )
            }
        }
    }

    private fun Uri.toMultipartPart(): MultipartBody.Part {
        val resolver = context.contentResolver
        val mimeType = resolver.getType(this)?.toMediaTypeOrNull()
        val fileName = resolver.queryDisplayName(this) ?: "subscriptions.opml"
        val bytes = resolver.openInputStream(this)?.use { it.readBytes() }
            ?: error("Could not read selected OPML file.")
        return MultipartBody.Part.createFormData(
            name = "file",
            filename = fileName,
            body = bytes.toRequestBody(mimeType)
        )
    }

    private fun android.content.ContentResolver.queryDisplayName(uri: Uri): String? {
        return query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val columnIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (columnIndex >= 0 && cursor.moveToFirst()) cursor.getString(columnIndex) else null
        }
    }

    private fun addPodcastErrorMessage(
        response: Response<*>?,
        fallback: String = "Could not add this RSS feed."
    ): String {
        if (response == null) return "Could not reach mpod backend."
        val rawError = response.errorBody()?.string().orEmpty()
        if (rawError.isBlank()) return fallback

        return runCatching {
            val errorObject = JSONObject(rawError).optJSONObject("error")
            errorObject?.optString("message")?.takeIf { it.isNotBlank() }
                ?: JSONObject(rawError).optString("message").takeIf { it.isNotBlank() }
        }.getOrNull() ?: fallback
    }

    private fun String.isHttpUrl(): Boolean = runCatching {
        val uri = URI(this)
        uri.scheme in setOf("http", "https") && !uri.host.isNullOrBlank()
    }.getOrDefault(false)
}

data class AddPodcastUiState(
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null
)
