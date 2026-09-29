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
