package com.example.mpod.ui.components

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.SavedStateHandle
import com.example.mpod.data.network.MpodApi
import com.example.mpod.data.network.LimitedContentRequestBody
import com.example.mpod.data.network.OpmlReadException
import com.example.mpod.data.network.OpmlTooLargeException
import com.example.mpod.data.network.model.CreatePodcastRequest
import com.example.mpod.data.network.model.OpmlImportResponse
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
import org.json.JSONObject
import retrofit2.Response
import java.net.URI
import javax.inject.Inject

@HiltViewModel
class AddPodcastViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val api: MpodApi,
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
            val response = runCatching {
                api.createPodcast(CreatePodcastRequest(rssUrl = trimmedUrl))
            }.getOrNull()

            if (response?.isSuccessful == true) {
                reset()
                onSuccess()
            } else {
                _state.value = _state.value.copy(
                    isSubmitting = false,
                    errorMessage = addPodcastErrorMessage(response)
                )
            }
        }
    }

    fun importOpml(uri: Uri, onSuccess: () -> Unit) {
        if (_state.value.isSubmitting) return
        _state.value = _state.value.copy(isSubmitting = true, errorMessage = null)
        viewModelScope.launch {
            val result = runCatching {
                val filePart = withContext(Dispatchers.IO) {
                    uri.toMultipartPart()
                }
                api.importOpml(filePart)
            }
            val response = result.getOrNull()

            val payload = response?.takeIf { it.isSuccessful }?.body()
            if (payload?.success == true) {
                _state.value = _state.value.copy(
                    isSubmitting = false,
                    importResult = payload.toUiResult()
                )
                onSuccess()
            } else {
                _state.value = _state.value.copy(
                    isSubmitting = false,
                    errorMessage = importOpmlErrorMessage(response, result.exceptionOrNull())
                )
            }
        }
    }

    private fun Uri.toMultipartPart(): MultipartBody.Part {
        return try {
            val resolver = context.contentResolver
            val mimeType = resolver.getType(this)?.toMediaTypeOrNull()
            val metadata = resolver.queryMetadata(this)
            val requestBody = LimitedContentRequestBody(
                mediaType = mimeType,
                knownLength = metadata.size,
                openStream = {
                    resolver.openInputStream(this)
                        ?: throw OpmlReadException()
                }
            )
            MultipartBody.Part.createFormData(
                name = "file",
                filename = metadata.displayName ?: "subscriptions.opml",
                body = requestBody
            )
        } catch (error: OpmlTooLargeException) {
            throw error
        } catch (error: OpmlReadException) {
            throw error
        } catch (error: Exception) {
            throw OpmlReadException(error)
        }
    }

    private fun android.content.ContentResolver.queryMetadata(uri: Uri): OpmlMetadata {
        return query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return@use OpmlMetadata()
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            OpmlMetadata(
                displayName = nameIndex.takeIf { it >= 0 }?.let(cursor::getString),
                size = sizeIndex.takeIf { it >= 0 && !cursor.isNull(it) }?.let(cursor::getLong)
            )
        } ?: OpmlMetadata()
    }

    private fun importOpmlErrorMessage(response: Response<*>?, error: Throwable?): String {
        return when {
            error.hasCause<OpmlTooLargeException>() -> "OPML file is too large. Maximum size is 5 MB."
            error.hasCause<OpmlReadException>() -> "Could not read selected OPML file."
            else -> addPodcastErrorMessage(response, "Could not import this OPML file.")
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

private data class OpmlMetadata(
    val displayName: String? = null,
    val size: Long? = null
)

private inline fun <reified T : Throwable> Throwable?.hasCause(): Boolean {
    var current = this
    while (current != null) {
        if (current is T) return true
        current = current.cause
    }
    return false
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

private fun OpmlImportResponse.toUiResult() = OpmlImportResultUi(
    imported = imported.coerceAtLeast(0),
    skipped = skipped.coerceAtLeast(0)
)

private const val MODE_KEY = "add_podcast_mode"
private const val RSS_URL_KEY = "add_podcast_rss_url"
