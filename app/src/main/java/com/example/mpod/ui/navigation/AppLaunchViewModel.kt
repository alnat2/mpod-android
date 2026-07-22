package com.example.mpod.ui.navigation

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mpod.data.network.MpodApi
import com.example.mpod.data.network.AuthSessionInvalidator
import com.example.mpod.data.network.model.LoginRequest
import com.example.mpod.playback.PlaybackService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.json.JSONObject
import retrofit2.Response
import javax.inject.Inject

@HiltViewModel
class AppLaunchViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val api: MpodApi,
    private val sessionInvalidator: AuthSessionInvalidator
) : ViewModel() {
    private val _state = MutableStateFlow<AppLaunchState>(AppLaunchState.Loading)
    val state: StateFlow<AppLaunchState> = _state.asStateFlow()

    private val _authUiState = MutableStateFlow(AuthUiState())
    val authUiState: StateFlow<AuthUiState> = _authUiState.asStateFlow()

    init {
        refreshSession()
        viewModelScope.launch {
            sessionInvalidator.events.collectLatest {
                context.stopService(Intent(context, PlaybackService::class.java))
                _authUiState.value = AuthUiState()
                _state.value = AppLaunchState.Unauthenticated
            }
        }
    }

    fun refreshSession() {
        viewModelScope.launch {
            _state.value = AppLaunchState.Loading
            _state.value = loadLaunchState(api::getSession)
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
            val response = runCatching { api.logout() }.getOrNull()
            when (resolveLogoutOutcome(response?.code())) {
                LogoutOutcome.RefreshSession -> refreshSession()
                LogoutOutcome.Unauthenticated -> _state.value = AppLaunchState.Unauthenticated
                LogoutOutcome.BackendUnavailable -> _state.value = AppLaunchState.BackendUnavailable
            }
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
    data object BackendUnavailable : AppLaunchState
    data object SetupRequired : AppLaunchState
    data object Unauthenticated : AppLaunchState
    data object Authenticated : AppLaunchState
}

internal enum class LogoutOutcome {
    RefreshSession,
    Unauthenticated,
    BackendUnavailable
}

internal fun resolveLogoutOutcome(responseCode: Int?): LogoutOutcome = when {
    responseCode in 200..299 -> LogoutOutcome.RefreshSession
    responseCode == 401 -> LogoutOutcome.Unauthenticated
    else -> LogoutOutcome.BackendUnavailable
}

internal fun resolveLaunchState(
    responseCode: Int?,
    session: com.example.mpod.data.network.model.SessionDto?
): AppLaunchState {
    return when {
        responseCode == 401 -> AppLaunchState.Unauthenticated
        responseCode == null || responseCode !in 200..299 || session == null -> {
            AppLaunchState.BackendUnavailable
        }
        session.setupRequired -> AppLaunchState.SetupRequired
        session.authenticated -> AppLaunchState.Authenticated
        else -> AppLaunchState.Unauthenticated
    }
}

internal suspend fun loadLaunchState(
    loadSession: suspend () -> Response<com.example.mpod.data.network.model.SessionDto>
): AppLaunchState {
    return runCatching {
        val response = loadSession()
        resolveLaunchState(response.code(), response.body())
    }.getOrElse {
        AppLaunchState.BackendUnavailable
    }
}
