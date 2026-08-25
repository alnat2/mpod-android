package com.example.mpod.ui.screens.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mpod.data.local.preferences.AppSettingsDataStore
import com.example.mpod.data.repository.PodcastRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appSettingsDataStore: AppSettingsDataStore,
    private val podcastRepository: PodcastRepository
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
                    appBuild = "mpoddy v1.0.17"
                )
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
        }
    }

    fun saveProxySettings(host: String, port: Int, type: String = "SOCKS5") {
        viewModelScope.launch {
            appSettingsDataStore.setProxySettings(
                enabled = true,
                host = host.trim(),
                port = port,
                type = type
            )
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
    val appBuild: String = "mpoddy v1.0.17"
)

internal fun installedEnvironment(packageName: String): String {
    return if (packageName.endsWith(".test")) "Test" else "Production"
}

internal fun formatSchedulerTimestamp(
    rawTimestamp: String,
    zoneId: java.time.ZoneId = java.time.ZoneId.systemDefault()
): String {
    return runCatching {
        val instant = java.time.Instant.parse(rawTimestamp)
        val zonedDateTime = instant.atZone(zoneId)
        val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        formatter.format(zonedDateTime)
    }.getOrElse {
        rawTimestamp.replace('T', ' ').take(16)
    }
}

internal fun formatSettingsLastRefreshText(
    rawTimestamp: String,
    zoneId: java.time.ZoneId = java.time.ZoneId.systemDefault(),
    today: java.time.LocalDate = java.time.LocalDate.now(zoneId)
): String {
    return runCatching {
        val instant = java.time.Instant.parse(rawTimestamp)
        val zonedDateTime = instant.atZone(zoneId)
        val refreshDate = zonedDateTime.toLocalDate()
        val timeFormatter = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
        val formattedTime = timeFormatter.format(zonedDateTime)

        if (refreshDate == today) {
            "Last refresh today at $formattedTime"
        } else {
            val dateFormatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")
            "Last refresh ${dateFormatter.format(refreshDate)} at $formattedTime"
        }
    }.getOrElse {
        "Last refresh $rawTimestamp"
    }
}

