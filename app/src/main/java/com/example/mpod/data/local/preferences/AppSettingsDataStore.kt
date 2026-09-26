package com.example.mpod.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "mpoddy_settings")

data class AppSettings(
    val isAutoRefreshEnabled: Boolean = false,
    val dailyRefreshTime: String = "03:00",
    val isProxyEnabled: Boolean = false,
    val proxyType: String = "SOCKS5",
    val proxyHost: String = "",
    val proxyPort: Int = 1080,
    val proxyUsername: String = "",
    val proxyPassword: String = "",
    val themeMode: String = "System",
    val activeEpisodeId: Long? = null,
    val playbackSpeed: Float = 1.0f,
    val lastRefreshTimeFormatted: String = ""
)

@Singleton
open class AppSettingsDataStore {
    private val context: Context?

    @Inject
    constructor(@ApplicationContext context: Context) {
        this.context = context
    }

    /**
     * Testing constructor allowing unit tests without Android Context.
     */
    constructor() {
        this.context = null
    }

    private object Keys {
        val AUTO_REFRESH_ENABLED = booleanPreferencesKey("auto_refresh_enabled")
        val DAILY_REFRESH_TIME = stringPreferencesKey("daily_refresh_time")
        val PROXY_ENABLED = booleanPreferencesKey("proxy_enabled")
        val PROXY_TYPE = stringPreferencesKey("proxy_type")
        val PROXY_HOST = stringPreferencesKey("proxy_host")
        val PROXY_PORT = intPreferencesKey("proxy_port")
        val PROXY_USERNAME = stringPreferencesKey("proxy_username")
        val PROXY_PASSWORD = stringPreferencesKey("proxy_password")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val ACTIVE_EPISODE_ID = longPreferencesKey("active_episode_id")
        val PLAYBACK_SPEED = floatPreferencesKey("playback_speed")
        val LAST_REFRESH_TIME = stringPreferencesKey("last_refresh_time")
    }

    open val settingsFlow: Flow<AppSettings> by lazy {
        val ctx = context ?: return@lazy emptyFlow()
        ctx.dataStore.data.map { prefs ->
            AppSettings(
                isAutoRefreshEnabled = prefs[Keys.AUTO_REFRESH_ENABLED] ?: false,
                dailyRefreshTime = prefs[Keys.DAILY_REFRESH_TIME] ?: "03:00",
                isProxyEnabled = prefs[Keys.PROXY_ENABLED] ?: false,
                proxyType = prefs[Keys.PROXY_TYPE] ?: "SOCKS5",
                proxyHost = prefs[Keys.PROXY_HOST] ?: "",
                proxyPort = prefs[Keys.PROXY_PORT] ?: 1080,
                proxyUsername = prefs[Keys.PROXY_USERNAME] ?: "",
                proxyPassword = prefs[Keys.PROXY_PASSWORD] ?: "",
                themeMode = prefs[Keys.THEME_MODE] ?: "System",
                activeEpisodeId = prefs[Keys.ACTIVE_EPISODE_ID],
                playbackSpeed = prefs[Keys.PLAYBACK_SPEED] ?: 1.0f,
                lastRefreshTimeFormatted = prefs[Keys.LAST_REFRESH_TIME] ?: ""
            )
        }
    }

    open suspend fun setAutoRefreshEnabled(enabled: Boolean) {
        context?.dataStore?.edit { it[Keys.AUTO_REFRESH_ENABLED] = enabled }
    }

    open suspend fun setDailyRefreshTime(time: String) {
        context?.dataStore?.edit { it[Keys.DAILY_REFRESH_TIME] = time }
    }

    open suspend fun setProxyEnabled(enabled: Boolean) {
        context?.dataStore?.edit { it[Keys.PROXY_ENABLED] = enabled }
    }

    open suspend fun setProxySettings(
        enabled: Boolean,
        host: String,
        port: Int,
        type: String = "SOCKS5",
        username: String = "",
        password: String = ""
    ) {
        context?.dataStore?.edit {
            it[Keys.PROXY_ENABLED] = enabled
            it[Keys.PROXY_HOST] = host
            it[Keys.PROXY_PORT] = port
            it[Keys.PROXY_TYPE] = type
            it[Keys.PROXY_USERNAME] = username
            it[Keys.PROXY_PASSWORD] = password
        }
    }

    open suspend fun setThemeMode(theme: String) {
        context?.dataStore?.edit { it[Keys.THEME_MODE] = theme }
    }

    open suspend fun setActiveEpisodeId(episodeId: Long?) {
        context?.dataStore?.edit {
            if (episodeId == null) {
                it.remove(Keys.ACTIVE_EPISODE_ID)
            } else {
                it[Keys.ACTIVE_EPISODE_ID] = episodeId
            }
        }
    }

    open suspend fun getActiveEpisodeId(): Long? {
        val ctx = context ?: return null
        return ctx.dataStore.data.first()[Keys.ACTIVE_EPISODE_ID]
    }

    open suspend fun setPlaybackSpeed(speed: Float) {
        context?.dataStore?.edit { it[Keys.PLAYBACK_SPEED] = speed }
    }

    open suspend fun setLastRefreshTime(formatted: String) {
        context?.dataStore?.edit { it[Keys.LAST_REFRESH_TIME] = formatted }
    }
}
