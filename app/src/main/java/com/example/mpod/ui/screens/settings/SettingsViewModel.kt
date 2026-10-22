package com.example.mpod.ui.screens.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mpod.BuildConfig
import com.example.mpod.data.local.preferences.AppSettings
import com.example.mpod.data.local.preferences.AppSettingsStore
import com.example.mpod.data.local.preferences.ProxyServerConfig
import com.example.mpod.data.network.IpGeoService
import com.example.mpod.data.network.ProxyCheckResult
import com.example.mpod.data.network.ProxyHealthService
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
    private val appSettingsDataStore: AppSettingsStore,
    private val podcastRepository: PodcastRepository,
    private val autoRefreshScheduler: AutoRefreshScheduler,
    private val proxyHttpClientFactory: ProxyHttpClientFactory,
    private val ipGeoService: IpGeoService,
    private val proxyHealthService: ProxyHealthService
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
                    proxyServers = prefs.proxyServers,
                    selectedProxyServerId = prefs.selectedProxyServerId,
                    activeProxyServer = prefs.activeProxyServer,
                    proxyType = prefs.proxyType,
                    proxyHost = prefs.proxyHost,
                    proxyPort = prefs.proxyPort,
                    proxyUsername = prefs.proxyUsername,
                    proxyPassword = prefs.proxyPassword,
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

        proxyHttpClientFactory.setProxyConnectFailedListener { _, _ ->
            viewModelScope.launch {
                val current = _state.value
                if (current.isProxyEnabled && current.activeProxyServer != null) {
                    val server = current.activeProxyServer
                    val isOnline = proxyHealthService.isDeviceOnline()
                    val errorText = if (!isOnline) {
                        "No internet connection"
                    } else {
                        "${server.name} cant connect to proxy"
                    }
                    _state.value = _state.value.copy(activeProxyError = errorText)
                }
            }
        }

        loadCurrentIpGeo()
    }

    fun loadCurrentIpGeo() {
        viewModelScope.launch {
            _state.value = _state.value.copy(currentIpGeoText = "Current IP: Checking...")
            val result = ipGeoService.fetchCurrentIpGeo()
            _state.value = _state.value.copy(
                currentIpGeoText = result.fold(
                    onSuccess = { (ip, geo) -> "Current IP: $ip • Geo: $geo" },
                    onFailure = { "Current IP: Unavailable" }
                )
            )
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
            if (!enabled) {
                _state.value = _state.value.copy(
                    isProxyChecking = false,
                    activeProxyError = null,
                    topProxyNotification = null
                )
                loadCurrentIpGeo()
            } else {
                val server = current.activeProxyServer
                if (server == null && current.proxyHost.isBlank()) {
                    _state.value = _state.value.copy(proxyMessage = "Enter proxy address and port below.")
                } else if (server != null) {
                    _state.value = _state.value.copy(
                        isProxyChecking = true,
                        activeProxyError = null
                    )
                    verifyProxyConnection(server, isBackgroundAddOrEdit = false)
                } else {
                    loadCurrentIpGeo()
                }
            }
        }
    }

    fun selectProxyServer(serverId: String) {
        viewModelScope.launch {
            appSettingsDataStore.selectProxyServer(serverId)
            val current = appSettingsDataStore.settingsFlow.first()
            proxyHttpClientFactory.updateProxy(current)
            val server = current.activeProxyServer
            if (server != null) {
                _state.value = _state.value.copy(
                    isProxyChecking = true,
                    activeProxyError = null
                )
                verifyProxyConnection(server, isBackgroundAddOrEdit = false)
            } else {
                loadCurrentIpGeo()
            }
        }
    }

    fun addProxyServer(
        name: String,
        host: String,
        port: Int,
        username: String = "",
        password: String = ""
    ): Result<ProxyServerConfig> {
        val trimmedName = name.trim()
        val trimmedHost = host.trim()
        if (trimmedName.isBlank()) {
            val msg = "Server name cannot be empty."
            _state.value = _state.value.copy(proxyMessage = msg)
            return Result.failure(IllegalArgumentException(msg))
        }
        if (trimmedHost.isBlank()) {
            val msg = "Proxy address cannot be empty."
            _state.value = _state.value.copy(proxyMessage = msg)
            return Result.failure(IllegalArgumentException(msg))
        }
        if (port !in 1..65535) {
            val msg = "Invalid proxy port (1-65535)."
            _state.value = _state.value.copy(proxyMessage = msg)
            return Result.failure(IllegalArgumentException(msg))
        }
        val credsError = validateProxyCredentials(username, password)
        if (credsError != null) {
            _state.value = _state.value.copy(proxyMessage = credsError)
            return Result.failure(IllegalArgumentException(credsError))
        }
        val newServer = ProxyServerConfig(
            name = trimmedName,
            host = trimmedHost,
            port = port,
            username = username.trim(),
            password = password
        )
        viewModelScope.launch {
            val wasEmpty = _state.value.proxyServers.isEmpty()
            appSettingsDataStore.addProxyServer(newServer)
            val current = appSettingsDataStore.settingsFlow.first()
            proxyHttpClientFactory.updateProxy(current)
            _state.value = _state.value.copy(proxyMessage = "Proxy server saved.")

            val isActive = current.activeProxyServer?.id == newServer.id || wasEmpty
            if (isActive) {
                _state.value = _state.value.copy(
                    isProxyChecking = true,
                    activeProxyError = null
                )
            }
            verifyProxyConnection(newServer, isBackgroundAddOrEdit = true)
        }
        return Result.success(newServer)
    }

    fun updateProxyServer(
        id: String,
        name: String,
        host: String,
        port: Int,
        username: String = "",
        password: String = ""
    ): Result<Unit> {
        val trimmedName = name.trim()
        val trimmedHost = host.trim()
        if (trimmedName.isBlank()) {
            val msg = "Server name cannot be empty."
            _state.value = _state.value.copy(proxyMessage = msg)
            return Result.failure(IllegalArgumentException(msg))
        }
        if (trimmedHost.isBlank()) {
            val msg = "Proxy address cannot be empty."
            _state.value = _state.value.copy(proxyMessage = msg)
            return Result.failure(IllegalArgumentException(msg))
        }
        if (port !in 1..65535) {
            val msg = "Invalid proxy port (1-65535)."
            _state.value = _state.value.copy(proxyMessage = msg)
            return Result.failure(IllegalArgumentException(msg))
        }
        val credsError = validateProxyCredentials(username, password)
        if (credsError != null) {
            _state.value = _state.value.copy(proxyMessage = credsError)
            return Result.failure(IllegalArgumentException(credsError))
        }
        val updatedServer = ProxyServerConfig(
            id = id,
            name = trimmedName,
            host = trimmedHost,
            port = port,
            username = username.trim(),
            password = password
        )
        // Optimistically update the in-memory list so the new name is immediately
        // visible in ChooseProxyBottomSheet after popBackStack, before DataStore emits.
        val optimisticServers = _state.value.proxyServers.map { s ->
            if (s.id == updatedServer.id) updatedServer else s
        }
        _state.value = _state.value.copy(proxyServers = optimisticServers)
        viewModelScope.launch {
            appSettingsDataStore.updateProxyServer(updatedServer)
            val current = appSettingsDataStore.settingsFlow.first()
            proxyHttpClientFactory.updateProxy(current)
            _state.value = _state.value.copy(proxyMessage = "Proxy server updated.")

            val isActive = current.activeProxyServer?.id == updatedServer.id
            if (isActive) {
                _state.value = _state.value.copy(
                    isProxyChecking = true,
                    activeProxyError = null
                )
            }
            verifyProxyConnection(updatedServer, isBackgroundAddOrEdit = true)
        }
        return Result.success(Unit)
    }

    private suspend fun verifyProxyConnection(server: ProxyServerConfig, isBackgroundAddOrEdit: Boolean) {
        val checkResult = proxyHealthService.checkProxy(server)
        val current = _state.value
        val isActive = current.activeProxyServer?.id == server.id

        when (checkResult) {
            is ProxyCheckResult.Success -> {
                if (isActive) {
                    _state.value = _state.value.copy(
                        isProxyChecking = false,
                        activeProxyError = null,
                        currentIpGeoText = "Current IP: ${checkResult.ip} • Geo: ${checkResult.geo}"
                    )
                }
                if (current.topProxyNotification?.serverId == server.id) {
                    _state.value = _state.value.copy(topProxyNotification = null)
                }
            }
            is ProxyCheckResult.ProxyFailed -> {
                val errorText = "${server.name} cant connect to proxy"
                _state.value = _state.value.copy(
                    isProxyChecking = false,
                    activeProxyError = if (isActive) errorText else current.activeProxyError,
                    topProxyNotification = if (isBackgroundAddOrEdit) {
                        ProxyErrorNotification(
                            serverId = server.id,
                            serverName = server.name,
                            message = errorText
                        )
                    } else current.topProxyNotification
                )
            }
            is ProxyCheckResult.NoInternet -> {
                val errorText = "No internet connection"
                _state.value = _state.value.copy(
                    isProxyChecking = false,
                    activeProxyError = if (isActive) errorText else current.activeProxyError,
                    topProxyNotification = if (isBackgroundAddOrEdit) {
                        ProxyErrorNotification(
                            serverId = server.id,
                            serverName = server.name,
                            message = errorText
                        )
                    } else current.topProxyNotification
                )
            }
        }
    }

    fun dismissTopProxyNotification() {
        _state.value = _state.value.copy(topProxyNotification = null)
    }

    fun deleteProxyServer(serverId: String) {
        viewModelScope.launch {
            if (_state.value.topProxyNotification?.serverId == serverId) {
                _state.value = _state.value.copy(topProxyNotification = null)
            }
            appSettingsDataStore.deleteProxyServer(serverId)
            val current = appSettingsDataStore.settingsFlow.first()
            proxyHttpClientFactory.updateProxy(current)
            _state.value = _state.value.copy(proxyMessage = "Proxy server deleted.")
            loadCurrentIpGeo()
        }
    }

    fun saveProxySettings(host: String, port: Int, username: String = "", password: String = "") {
        viewModelScope.launch {
            val trimmedHost = host.trim()
            if (trimmedHost.isBlank()) {
                _state.value = _state.value.copy(proxyMessage = "Proxy address cannot be empty.")
                return@launch
            }
            if (port !in 1..65535) {
                _state.value = _state.value.copy(proxyMessage = "Invalid proxy port (1-65535).")
                return@launch
            }
            val updated = AppSettings(
                isProxyEnabled = true,
                proxyHost = trimmedHost,
                proxyPort = port,
                proxyType = "SOCKS5",
                proxyUsername = username.trim(),
                proxyPassword = password
            )
            appSettingsDataStore.setProxySettings(
                enabled = true,
                host = trimmedHost,
                port = port,
                type = "SOCKS5",
                username = username.trim(),
                password = password
            )
            proxyHttpClientFactory.updateProxy(updated)
            _state.value = _state.value.copy(proxyMessage = "Proxy settings saved.")
            loadCurrentIpGeo()
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

data class ProxyErrorNotification(
    val serverId: String,
    val serverName: String,
    val message: String
)

data class SettingsUiState(
    val isAutoRefreshEnabled: Boolean = false,
    val dailyRefreshTime: String = "03:00",
    val isProxyEnabled: Boolean = false,
    val proxyServers: List<ProxyServerConfig> = emptyList(),
    val selectedProxyServerId: String? = null,
    val activeProxyServer: ProxyServerConfig? = null,
    val proxyType: String = "SOCKS5",
    val proxyHost: String = "",
    val proxyPort: Int = 1080,
    val proxyUsername: String = "",
    val proxyPassword: String = "",
    val proxyMessage: String? = null,
    val themeMode: String = "System",
    val lastRefreshHeaderText: String = "Last refresh never",
    val currentIpGeoText: String? = null,
    val isExportingOpml: Boolean = false,
    val exportMessage: String? = null,
    val errorMessage: String? = null,
    val appBuild: String = "mpoddy v${BuildConfig.VERSION_NAME}",
    val isProxyChecking: Boolean = false,
    val activeProxyError: String? = null,
    val topProxyNotification: ProxyErrorNotification? = null
)
