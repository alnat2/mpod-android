package com.example.mpod.ui.navigation

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mpod.data.network.MpodApi
import com.example.mpod.data.network.model.LoginRequest
import com.example.mpod.playback.PlaybackService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import retrofit2.Response
import javax.inject.Inject

@HiltViewModel
class AppLaunchViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val api: MpodApi
) : ViewModel() {
    private val _state = MutableStateFlow<AppLaunchState>(AppLaunchState.Loading)
    val state: StateFlow<AppLaunchState> = _state.asStateFlow()

    private val _authUiState = MutableStateFlow(AuthUiState())
    val authUiState: StateFlow<AuthUiState> = _authUiState.asStateFlow()

    init {
        refreshSession()
    }

    fun refreshSession() {
        viewModelScope.launch {
            _state.value = AppLaunchState.Loading
            val nextState = runCatching {
                val response = api.getSession()
                resolveLaunchState(response.isSuccessful, response.body())
            }.getOrElse {
                AppLaunchState.Unauthenticated
            }
            _state.value = nextState
        }
    }

    fun login(username: String, password: String) {
        if (!validateAuthInput(username, password)) return
        viewModelScope.launch {
            _authUiState.value = AuthUiState(isSubmitting = true)
            val response = runCatching {
                api.login(LoginRequest(username = username, password = password))
            }.getOrNull()
            if (response?.isSuccessful == true) {
                _authUiState.value = AuthUiState()
                _state.value = AppLaunchState.Authenticated
            } else {
                _authUiState.value = AuthUiState(
                    errorMessage = authErrorMessage(response, "Could not log in. Check your username and password.")
                )
            }
        }
    }

    fun register(username: String, password: String) {
        if (!validateAuthInput(username, password)) return
        viewModelScope.launch {
            _authUiState.value = AuthUiState(isSubmitting = true)
            val response = runCatching {
                api.register(LoginRequest(username = username, password = password))
            }.getOrNull()
            if (response?.isSuccessful == true) {
                _authUiState.value = AuthUiState()
                _state.value = AppLaunchState.Authenticated
            } else {
                _authUiState.value = AuthUiState(
                    errorMessage = authErrorMessage(response, "Could not create the account. Try another username.")
                )
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            context.stopService(Intent(context, PlaybackService::class.java))
            _state.value = AppLaunchState.Loading
            _authUiState.value = AuthUiState()
            runCatching { api.logout() }
            refreshSession()
        }
    }

    private fun validateAuthInput(username: String, password: String): Boolean {
        return if (username.isBlank() || password.isBlank()) {
            _authUiState.value = AuthUiState(errorMessage = "Enter username and password.")
            false
        } else {
            true
        }
    }

    private fun authErrorMessage(response: Response<*>?, fallback: String): String {
        if (response == null) return "Could not reach mpod backend."
        val rawError = runCatching { response.errorBody()?.string() }.getOrNull().orEmpty()
        if (rawError.isBlank()) return fallback

        return runCatching {
            val errorObject = JSONObject(rawError).optJSONObject("error")
            errorObject?.optString("message")?.takeIf { it.isNotBlank() }
                ?: JSONObject(rawError).optString("message").takeIf { it.isNotBlank() }
        }.getOrNull() ?: fallback
    }
}

data class AuthUiState(
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null
)

sealed interface AppLaunchState {
    data object Loading : AppLaunchState
    data object SetupRequired : AppLaunchState
    data object Unauthenticated : AppLaunchState
    data object Authenticated : AppLaunchState
}

internal fun resolveLaunchState(
    responseSuccessful: Boolean,
    session: com.example.mpod.data.network.model.SessionDto?
): AppLaunchState {
    return when {
        !responseSuccessful || session == null -> AppLaunchState.Unauthenticated
        session.setupRequired -> AppLaunchState.SetupRequired
        session.authenticated -> AppLaunchState.Authenticated
        else -> AppLaunchState.Unauthenticated
    }
}
