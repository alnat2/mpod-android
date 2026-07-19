package com.example.mpod.ui.screens.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mpod.data.network.MpodApi
import com.example.mpod.data.network.model.ProxyStatusDto
import com.example.mpod.data.network.model.SchedulerStatusDto
import com.example.mpod.data.network.model.SettingsDto
import com.example.mpod.data.network.model.SettingsUpdateRequest
import com.example.mpod.ui.util.apiErrorMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Response
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val api: MpodApi
) : ViewModel() {
    private val _state = MutableStateFlow(
        SettingsUiState(
            isLoading = true,
            isRefreshLoading = true,
            isProxyLoading = true
        )
    )
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()
    private var hasResumed = false

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                isLoading = true,
                isRefreshLoading = true,
                refreshErrorMessage = null,
                isProxyLoading = true,
                proxyErrorMessage = null
            )
            _state.value = loadSettingsState(_state.value)
        }
    }

    fun onResume() {
        if (hasResumed) {
            refresh()
        } else {
            hasResumed = true
        }
    }

    fun saveDailyRefreshTime(value: String) {
        viewModelScope.launch {
            if (_state.value.isSavingRefreshTime || !_state.value.hasConfirmedSettings ||
                value == _state.value.dailyRefreshTime
            ) return@launch
            _state.value = _state.value.copy(
                isSavingRefreshTime = true,
                refreshErrorMessage = null
            )
            val response = runCatching {
                api.updateSettings(SettingsUpdateRequest(dailyRefreshTime = value))
            }.getOrNull()
            if (response?.isSuccessful == true) {
                val confirmed = response.body()?.settings
                val confirmedState = _state.value.withConfirmedSettings(confirmed).copy(
                    dailyRefreshTime = confirmed?.dailyRefreshTime ?: value,
                    isSavingRefreshTime = false,
                    hasConfirmedSettings = true
                )
                _state.value = reloadRefreshStatusAfterConfirmedSave(confirmedState)
            } else {
                _state.value = _state.value.copy(
                    isSavingRefreshTime = false,
                    refreshErrorMessage = response.errorMessage("Could not save refresh time.")
                )
            }
        }
    }

    fun setProxyEnabled(enabled: Boolean) {
        viewModelScope.launch {
            if (_state.value.isSavingProxy || !_state.value.hasConfirmedSettings ||
                !_state.value.proxyConfigured || enabled == _state.value.proxyEnabled
            ) return@launch
            _state.value = _state.value.copy(isSavingProxy = true, proxyErrorMessage = null)
            val response = runCatching {
                api.updateSettings(SettingsUpdateRequest(proxyEnabled = enabled))
            }.getOrNull()
            if (response?.isSuccessful == true) {
                val confirmed = response.body()?.settings
                val confirmedState = _state.value.withConfirmedSettings(confirmed).copy(
                    proxyEnabled = confirmed?.proxyEnabled ?: enabled,
                    isSavingProxy = false,
                    hasConfirmedSettings = true
                )
                _state.value = reloadProxyStatusAfterConfirmedSave(confirmedState)
            } else {
                _state.value = _state.value.copy(
                    isSavingProxy = false,
                    proxyErrorMessage = response.errorMessage("Could not update proxy setting.")
                )
            }
        }
    }

    fun exportOpml(uri: Uri?) {
        if (uri == null) return

        viewModelScope.launch {
            _state.value = _state.value.copy(
                isExportingOpml = true,
                exportMessage = null,
                errorMessage = null
            )
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val response = api.exportOpml()
                    if (!response.isSuccessful) {
                        error(response.errorBody()?.string().orEmpty().ifBlank { "Could not export OPML." })
                    }
                    val bytes = response.body()?.bytes() ?: error("Backend returned an empty OPML file.")
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        output.write(bytes)
                    } ?: error("Could not write to selected file.")
                }
            }

            _state.value = _state.value.copy(
                isExportingOpml = false,
                exportMessage = if (result.isSuccess) "OPML export saved." else null,
                errorMessage = result.exceptionOrNull()?.message ?: _state.value.errorMessage
            )
        }
    }

    private suspend fun loadSettingsState(base: SettingsUiState): SettingsUiState = coroutineScope {
        val settingsRequest = async {
            runCatching { api.getSettings().requireBody("Could not load settings.").settings }
        }
        val schedulerRequest = async {
            runCatching {
                api.getJobsStatus().requireBody("Could not load refresh status.").scheduler
            }
        }
        val proxyRequest = async {
            runCatching { api.getProxyStatus().requireBody("Could not load proxy status.").proxy }
        }
        val settingsResult = settingsRequest.await()
        val schedulerResult = schedulerRequest.await()
        val proxyResult = proxyRequest.await()
        val settings = settingsResult.getOrNull()
        val scheduler = schedulerResult.getOrNull()
        val proxy = proxyResult.getOrNull()
        val settingsError = settingsResult.exceptionOrNull()?.message ?: "Could not load settings."
        val proxyConfigured = settings?.proxyConfigured == true || proxy?.proxyConfigured == true

        base.withConfirmedSettings(settings).copy(
            isLoading = false,
            hasConfirmedSettings = settings != null,
            isRefreshLoading = false,
            refreshErrorMessage = when {
                settings == null -> settingsError
                schedulerResult.isFailure -> schedulerResult.exceptionOrNull()?.message
                    ?: "Could not load refresh status."
                else -> null
            },
            schedulerStatusText = scheduler?.let(::schedulerStatusText)
                ?: base.schedulerStatusText,
            isProxyLoading = false,
            proxyEnabled = settings?.proxyEnabled == true && proxyConfigured,
            proxyConfigured = proxyConfigured,
            proxyErrorMessage = when {
                settings == null -> settingsError
                proxyResult.isFailure -> proxyResult.exceptionOrNull()?.message
                    ?: "Could not load proxy status."
                else -> null
            },
            proxyStatusText = if (settings != null && proxyResult.isSuccess) {
                proxyStatusText(settings, proxy)
            } else {
                base.proxyStatusText
            }
        )
    }

    private suspend fun reloadRefreshStatusAfterConfirmedSave(
        confirmedState: SettingsUiState
    ): SettingsUiState = runCatching {
        val scheduler = api.getJobsStatus()
            .requireBody("Could not load refresh status.")
            .scheduler
        confirmedState.copy(
            schedulerStatusText = schedulerStatusText(scheduler),
            refreshErrorMessage = null
        )
    }.getOrElse {
        confirmedState.copy(
            refreshErrorMessage = "Refresh time was saved, but status could not be refreshed."
        )
    }

    private suspend fun reloadProxyStatusAfterConfirmedSave(
        confirmedState: SettingsUiState
    ): SettingsUiState = runCatching {
        val proxy = api.getProxyStatus()
            .requireBody("Could not load proxy status.")
            .proxy
        val settings = SettingsDto(
            dailyRefreshTime = confirmedState.dailyRefreshTime,
            playbackSpeed = null,
            proxyEnabled = confirmedState.proxyEnabled,
            proxyConfigured = confirmedState.proxyConfigured,
            appBuild = confirmedState.appBuild
        )
        confirmedState.copy(
            proxyConfigured = confirmedState.proxyConfigured || proxy?.proxyConfigured == true,
            proxyStatusText = proxyStatusText(settings, proxy),
            proxyErrorMessage = null
        )
    }.getOrElse {
        confirmedState.copy(
            proxyErrorMessage = "Proxy setting was saved, but status could not be refreshed."
        )
    }

    private fun proxyStatusText(settings: SettingsDto, proxy: ProxyStatusDto?): String {
        val configured = settings.proxyConfigured == true || proxy?.proxyConfigured == true
        if (!configured) return "Proxy is not configured."
        if (settings.proxyEnabled != true || proxy?.status == "off") return "Proxy is off"
        if (proxy?.status == "ok") {
            return listOfNotNull(
                proxy.externalIp?.let { "Current IP: $it" },
                proxy.country?.let { "Geo: $it" }
            ).joinToString(" · ").ifBlank { "Proxy is on" }
        }
        return proxy?.error ?: when (proxy?.status) {
            "error" -> "Proxy status error."
            else -> "Proxy status is unknown."
        }
    }

    private fun schedulerStatusText(scheduler: SchedulerStatusDto?): String {
        val state = scheduler?.state ?: "idle"
        val lastRefresh = scheduler?.lastRunAt ?: scheduler?.lastSuccessAt ?: scheduler?.lastFailureAt
        return if (lastRefresh == null) {
            "Status: $state · last refresh never"
        } else {
            val compact = lastRefresh.replace('T', ' ').take(16)
            "Status: $state · last refresh $compact"
        }
    }

    private fun <T> Response<T>.requireBody(defaultMessage: String): T {
        if (isSuccessful) {
            body()?.let { return it }
        }
        throw IllegalStateException(apiErrorMessage(errorBody()?.string(), defaultMessage))
    }

    private fun Response<*>?.errorMessage(defaultMessage: String): String {
        return apiErrorMessage(this?.errorBody()?.string(), defaultMessage)
    }
}

data class SettingsUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val hasConfirmedSettings: Boolean = false,
    val dailyRefreshTime: String = "03:00",
    val isRefreshLoading: Boolean = false,
    val refreshErrorMessage: String? = null,
    val isSavingRefreshTime: Boolean = false,
    val proxyEnabled: Boolean = false,
    val proxyConfigured: Boolean = false,
    val isProxyLoading: Boolean = false,
    val proxyErrorMessage: String? = null,
    val isSavingProxy: Boolean = false,
    val isExportingOpml: Boolean = false,
    val exportMessage: String? = null,
    val proxyStatusText: String = "Checking proxy status...",
    val schedulerStatusText: String = "Status: idle · last refresh never",
    val appBuild: String? = null
)

private fun SettingsUiState.withConfirmedSettings(settings: SettingsDto?): SettingsUiState {
    if (settings == null) return this
    return copy(
        dailyRefreshTime = settings.dailyRefreshTime ?: dailyRefreshTime,
        proxyEnabled = settings.proxyEnabled ?: proxyEnabled,
        proxyConfigured = settings.proxyConfigured ?: proxyConfigured,
        appBuild = settings.appBuild ?: appBuild
    )
}
