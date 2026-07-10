package com.example.mpod.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mpod.data.network.MpodApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppLaunchViewModel @Inject constructor(
    private val api: MpodApi
) : ViewModel() {
    private val _state = MutableStateFlow<AppLaunchState>(AppLaunchState.Loading)
    val state: StateFlow<AppLaunchState> = _state.asStateFlow()

    init {
        refreshSession()
    }

    fun refreshSession() {
        viewModelScope.launch {
            _state.value = AppLaunchState.Loading
            val nextState = runCatching {
                val response = api.getSession()
                val session = response.body()
                when {
                    !response.isSuccessful || session == null -> AppLaunchState.Unauthenticated
                    session.setupRequired -> AppLaunchState.SetupRequired
                    session.authenticated -> AppLaunchState.Authenticated
                    else -> AppLaunchState.Unauthenticated
                }
            }.getOrElse {
                AppLaunchState.Unauthenticated
            }
            _state.value = nextState
        }
    }
}

sealed interface AppLaunchState {
    data object Loading : AppLaunchState
    data object SetupRequired : AppLaunchState
    data object Unauthenticated : AppLaunchState
    data object Authenticated : AppLaunchState
}
