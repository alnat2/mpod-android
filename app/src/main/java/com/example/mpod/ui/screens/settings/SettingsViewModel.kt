package com.example.mpod.ui.screens.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mpod.BuildConfig
import com.example.mpod.data.local.preferences.AppSettings
import com.example.mpod.data.local.preferences.AppSettingsDataStore
import com.example.mpod.data.network.ProxyHttpClientFactory
import com.example.mpod.data.repository.PodcastRepository
import com.example.mpod.playback.AutoRefreshScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appSettingsDataStore: AppSettingsDataStore,
    private val podcastRepository: PodcastRepository,
    private val autoRefreshScheduler: AutoRefreshScheduler,
    private val proxyHttpClientFactory: ProxyHttpClientFactory
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            appSettingsDataStore.settingsFlow.collectLatest { prefs ->
                _state.value = _state.value.copy(
                    isAutoRefreshEnabled = prefs.isAutoRefreshEnabled,
                    dailyRefreshTime = prefs.dailyRefreshTime,
                    isProxyEnabled = prefs.isProxyEnabled,
                    proxyType = prefs.proxyType,
                    proxyHost = prefs.proxyHost,
                    proxyPort = prefs.proxyPort,
                    themeMode = prefs.themeMode,
                    lastRefreshHeaderText = if (prefs.lastRefreshTimeFormatted.isNotBlank()) {
                        prefs.lastRefreshTimeFormatted
                    } else {
                        "Last refresh never"
                    },
                    appBuild = "mpoddy v${BuildConfig.VERSION_NAME}"
                )
                autoRefreshScheduler.schedule(prefs)
            }
        }
    }

    fun setAutoRefreshEnabled(enabled: Boolean) {
        viewModelScope.launch {
            appSettingsDataStore.setAutoRefreshEnabled(enabled)
        }
    }

    fun saveDailyRefreshTime(time: String) {
        viewModelScope.launch {
            appSettingsDataStore.setDailyRefreshTime(time)
        }
    }

    fun setProxyEnabled(enabled: Boolean) {
        viewModelScope.launch {
            appSettingsDataStore.setProxyEnabled(enabled)
            val current = appSettingsDataStore.settingsFlow.first()
            proxyHttpClientFactory.updateProxy(current)
        }
    }

    fun saveProxySettings(host: String, port: Int, type: String = "SOCKS5") {
        viewModelScope.launch {
            val updated = AppSettings(
                isProxyEnabled = true,
                proxyHost = host.trim(),
                proxyPort = port,
                proxyType = type
            )
            appSettingsDataStore.setProxySettings(
                enabled = true,
                host = host.trim(),
                port = port,
                type = type
            )
            proxyHttpClientFactory.updateProxy(updated)
            _state.value = _state.value.copy(proxyMessage = "Proxy settings saved.")
        }
    }

    fun clearProxyMessage() {
        _state.value = _state.value.copy(proxyMessage = null)
    }

    fun exportOpml(uri: Uri?) {
        if (uri == null) return
        viewModelScope.launch {
            _state.value = _state.value.copy(isExportingOpml = true, exportMessage = null, errorMessage = null)
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val xml = podcastRepository.exportOpml()
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        output.write(xml.toByteArray(Charsets.UTF_8))
                    } ?: error("Could not write to selected file.")
                }
            }
            _state.value = _state.value.copy(
                isExportingOpml = false,
                exportMessage = if (result.isSuccess) "OPML export saved." else null,
                errorMessage = result.exceptionOrNull()?.message
            )
        }
    }
}

data class SettingsUiState(
    val isAutoRefreshEnabled: Boolean = false,
    val dailyRefreshTime: String = "03:00",
    val isProxyEnabled: Boolean = false,
    val proxyType: String = "SOCKS5",
    val proxyHost: String = "",
    val proxyPort: Int = 1080,
    val proxyMessage: String? = null,
    val themeMode: String = "System",
    val lastRefreshHeaderText: String = "Last refresh never",
    val isExportingOpml: Boolean = false,
    val exportMessage: String? = null,
    val errorMessage: String? = null,
    val appBuild: String = "mpoddy v${BuildConfig.VERSION_NAME}"
)
