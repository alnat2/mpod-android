package com.example.mpod.ui.screens.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mpod.data.network.BackendConfig
import com.example.mpod.data.network.MpodApi
import com.example.mpod.data.network.model.ProxyStatusDto
import com.example.mpod.data.network.model.SchedulerStatusDto
import com.example.mpod.data.network.model.SettingsDto
import com.example.mpod.data.network.model.SettingsUpdateRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
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
    private val backendConfig: BackendConfig,
    private val api: MpodApi
) : ViewModel() {
    private val _state = MutableStateFlow(SettingsUiState(isLoading = true))
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, errorMessage = null)
            _state.value = runCatching { loadSettingsState() }.getOrElse { error ->
                _state.value.copy(
                    isLoading = false,
                    errorMessage = error.message ?: "Could not load settings."
                )
            }
        }
    }

    fun saveBackendAddress(value: String) {
        val result = backendConfig.saveAddress(value)
        _state.value = if (result.isSuccess) {
            _state.value.copy(
                backendAddress = result.getOrThrow(),
                backendMessage = "Backend address saved. Restart the app to reconnect.",
                errorMessage = null
            )
        } else {
            _state.value.copy(
                backendMessage = null,
                errorMessage = result.exceptionOrNull()?.message ?: "Could not save backend address."
            )
        }
    }

    fun saveDailyRefreshTime(value: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isSavingRefreshTime = true, errorMessage = null)
            val response = runCatching {
                api.updateSettings(SettingsUpdateRequest(dailyRefreshTime = value))
            }.getOrNull()
            if (response?.isSuccessful == true) {
                _state.value = runCatching { loadSettingsState() }.getOrElse {
                    _state.value.copy(isSavingRefreshTime = false)
                }
            } else {
                _state.value = _state.value.copy(
                    isSavingRefreshTime = false,
                    errorMessage = response.errorMessage("Could not save refresh time.")
                )
            }
        }
    }

    fun setProxyEnabled(enabled: Boolean) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isSavingProxy = true, errorMessage = null)
            val response = runCatching {
                api.updateSettings(SettingsUpdateRequest(proxyEnabled = enabled))
            }.getOrNull()
            if (response?.isSuccessful == true) {
                _state.value = runCatching { loadSettingsState() }.getOrElse {
                    _state.value.copy(isSavingProxy = false)
                }
            } else {
                _state.value = _state.value.copy(
                    isSavingProxy = false,
                    errorMessage = response.errorMessage("Could not update proxy setting.")
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

    private suspend fun loadSettingsState(): SettingsUiState {
        val settings = api.getSettings().requireBody("Could not load settings.").settings
        val scheduler = api.getJobsStatus().body()?.scheduler
        val proxy = api.getProxyStatus().body()?.proxy

        return SettingsUiState(
            backendAddress = backendConfig.address,
            dailyRefreshTime = settings.dailyRefreshTime ?: "03:00",
            proxyEnabled = settings.proxyEnabled == true,
            proxyConfigured = settings.proxyConfigured == true || proxy?.proxyConfigured == true,
            proxyStatusText = proxyStatusText(settings, proxy),
            schedulerStatusText = schedulerStatusText(scheduler),
            appBuild = settings.appBuild,
            backendMessage = _state.value.backendMessage,
            exportMessage = _state.value.exportMessage,
            isLoading = false
        )
    }

    private fun proxyStatusText(settings: SettingsDto, proxy: ProxyStatusDto?): String {
        val configured = settings.proxyConfigured == true || proxy?.proxyConfigured == true
        if (!configured) return "Proxy runtime configuration is not available."
        if (settings.proxyEnabled != true || proxy?.status == "off") return "Proxy is off"
        if (proxy?.status == "ok") {
            return listOfNotNull(
                proxy.externalIp?.let { "Current IP: $it" },
                proxy.country?.let { "Geo: $it" }
            ).joinToString(" · ").ifBlank { "Proxy is on" }
        }
        return proxy?.error ?: "Checking proxy status..."
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
        throw IllegalStateException(errorBody()?.string().orEmpty().ifBlank { defaultMessage })
    }

    private fun Response<*>?.errorMessage(defaultMessage: String): String {
        return this?.errorBody()?.string().orEmpty().ifBlank { defaultMessage }
    }
}

data class SettingsUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val backendAddress: String = BackendConfig.DEFAULT_ADDRESS,
    val backendMessage: String? = null,
    val dailyRefreshTime: String = "03:00",
    val isSavingRefreshTime: Boolean = false,
    val proxyEnabled: Boolean = false,
    val proxyConfigured: Boolean = false,
    val isSavingProxy: Boolean = false,
    val isExportingOpml: Boolean = false,
    val exportMessage: String? = null,
    val proxyStatusText: String = "Checking proxy status...",
    val schedulerStatusText: String = "Status: idle · last refresh never",
    val appBuild: String? = null
)
