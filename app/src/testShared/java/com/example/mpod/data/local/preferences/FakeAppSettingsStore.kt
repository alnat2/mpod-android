package com.example.mpod.data.local.preferences

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

open class FakeAppSettingsStore(initial: AppSettings = AppSettings()) : AppSettingsStore {
    val stateFlow = MutableStateFlow(initial)
    override val settingsFlow: Flow<AppSettings> = stateFlow

    override suspend fun setAutoRefreshEnabled(enabled: Boolean) {
        stateFlow.value = stateFlow.value.copy(isAutoRefreshEnabled = enabled)
    }

    override suspend fun setDailyRefreshTime(time: String) {
        stateFlow.value = stateFlow.value.copy(dailyRefreshTime = time)
    }

    override suspend fun setProxyEnabled(enabled: Boolean) {
        stateFlow.value = stateFlow.value.copy(isProxyEnabled = enabled)
    }

    override suspend fun setProxySettings(
        enabled: Boolean,
        host: String,
        port: Int,
        type: String,
        username: String,
        password: String
    ) {
        stateFlow.value = stateFlow.value.copy(
            isProxyEnabled = enabled,
            proxyHost = host,
            proxyPort = port,
            proxyType = type,
            proxyUsername = username,
            proxyPassword = password
        )
    }

    override suspend fun addProxyServer(server: ProxyServerConfig): ProxyServerConfig {
        val current = stateFlow.value.proxyServers.toMutableList()
        current.add(server)
        val shouldSelect = stateFlow.value.selectedProxyServerId.isNullOrBlank() || current.size == 1
        val newSelected = if (shouldSelect) server.id else stateFlow.value.selectedProxyServerId
        stateFlow.value = stateFlow.value.copy(
            proxyServers = current,
            selectedProxyServerId = newSelected,
            proxyHost = if (shouldSelect) server.host else stateFlow.value.proxyHost,
            proxyPort = if (shouldSelect) server.port else stateFlow.value.proxyPort,
            proxyUsername = if (shouldSelect) server.username else stateFlow.value.proxyUsername,
            proxyPassword = if (shouldSelect) server.password else stateFlow.value.proxyPassword
        )
        return server
    }

    override suspend fun updateProxyServer(server: ProxyServerConfig) {
        val current = stateFlow.value.proxyServers.map {
            if (it.id == server.id) server else it
        }
        val isSelected = stateFlow.value.selectedProxyServerId == server.id
        stateFlow.value = stateFlow.value.copy(
            proxyServers = current,
            proxyHost = if (isSelected) server.host else stateFlow.value.proxyHost,
            proxyPort = if (isSelected) server.port else stateFlow.value.proxyPort,
            proxyUsername = if (isSelected) server.username else stateFlow.value.proxyUsername,
            proxyPassword = if (isSelected) server.password else stateFlow.value.proxyPassword
        )
    }

    override suspend fun deleteProxyServer(serverId: String) {
        val current = stateFlow.value.proxyServers.filter { it.id != serverId }
        val isSelected = stateFlow.value.selectedProxyServerId == serverId
        val nextActive = if (isSelected) current.firstOrNull() else null
        val newSelected = if (isSelected) nextActive?.id else stateFlow.value.selectedProxyServerId
        stateFlow.value = stateFlow.value.copy(
            proxyServers = current,
            selectedProxyServerId = newSelected,
            proxyHost = if (isSelected) (nextActive?.host ?: "") else stateFlow.value.proxyHost,
            proxyPort = if (isSelected) (nextActive?.port ?: 1080) else stateFlow.value.proxyPort,
            proxyUsername = if (isSelected) (nextActive?.username ?: "") else stateFlow.value.proxyUsername,
            proxyPassword = if (isSelected) (nextActive?.password ?: "") else stateFlow.value.proxyPassword
        )
    }

    override suspend fun selectProxyServer(serverId: String) {
        val server = stateFlow.value.proxyServers.firstOrNull { it.id == serverId }
        stateFlow.value = stateFlow.value.copy(
            selectedProxyServerId = serverId,
            proxyHost = server?.host ?: stateFlow.value.proxyHost,
            proxyPort = server?.port ?: stateFlow.value.proxyPort,
            proxyUsername = server?.username ?: stateFlow.value.proxyUsername,
            proxyPassword = server?.password ?: stateFlow.value.proxyPassword
        )
    }

    override suspend fun setThemeMode(theme: String) {
        stateFlow.value = stateFlow.value.copy(themeMode = theme)
    }

    override suspend fun setActiveEpisodeId(episodeId: Long?) {
        stateFlow.value = stateFlow.value.copy(activeEpisodeId = episodeId)
    }

    override suspend fun getActiveEpisodeId(): Long? = stateFlow.value.activeEpisodeId

    override suspend fun setPlaybackSpeed(speed: Float) {
        stateFlow.value = stateFlow.value.copy(playbackSpeed = speed)
    }

    override suspend fun setLastRefreshTime(formatted: String) {
        stateFlow.value = stateFlow.value.copy(lastRefreshTimeFormatted = formatted)
    }
}
