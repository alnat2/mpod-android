package com.example.mpod.data.local.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

data class AppSettings(
    val isAutoRefreshEnabled: Boolean = false,
    val dailyRefreshTime: String = "03:00",
    val isProxyEnabled: Boolean = false,
    val proxyServers: List<ProxyServerConfig> = emptyList(),
    val selectedProxyServerId: String? = null,
    val proxyType: String = "SOCKS5",
    val proxyHost: String = "",
    val proxyPort: Int = 1080,
    val proxyUsername: String = "",
    val proxyPassword: String = "",
    val themeMode: String = "System",
    val activeEpisodeId: Long? = null,
    val playbackSpeed: Float = 1.0f,
    val lastRefreshTimeFormatted: String = ""
) {
    val activeProxyServer: ProxyServerConfig?
        get() {
            if (proxyServers.isNotEmpty()) {
                val found = proxyServers.firstOrNull { it.id == selectedProxyServerId }
                if (found != null) return found
                return proxyServers.first()
            }
            return null
        }
}

interface AppSettingsStore {
    val settingsFlow: Flow<AppSettings>

    suspend fun setAutoRefreshEnabled(enabled: Boolean)
    suspend fun setDailyRefreshTime(time: String)
    suspend fun setProxyEnabled(enabled: Boolean)
    suspend fun setProxySettings(
        enabled: Boolean,
        host: String,
        port: Int,
        type: String = "SOCKS5",
        username: String = "",
        password: String = ""
    )
    suspend fun addProxyServer(server: ProxyServerConfig): ProxyServerConfig = server
    suspend fun updateProxyServer(server: ProxyServerConfig) {}
    suspend fun deleteProxyServer(serverId: String) {}
    suspend fun selectProxyServer(serverId: String) {}
    suspend fun setThemeMode(theme: String)
    suspend fun setActiveEpisodeId(episodeId: Long?)
    suspend fun getActiveEpisodeId(): Long?
    suspend fun setPlaybackSpeed(speed: Float)
    suspend fun setLastRefreshTime(formatted: String)
}

class AppSettingsDataStore @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : AppSettingsStore {

    private object Keys {
        val AUTO_REFRESH_ENABLED = booleanPreferencesKey("auto_refresh_enabled")
        val DAILY_REFRESH_TIME = stringPreferencesKey("daily_refresh_time")
        val PROXY_ENABLED = booleanPreferencesKey("proxy_enabled")
        val PROXY_TYPE = stringPreferencesKey("proxy_type")
        val PROXY_HOST = stringPreferencesKey("proxy_host")
        val PROXY_PORT = intPreferencesKey("proxy_port")
        val PROXY_USERNAME = stringPreferencesKey("proxy_username")
        val PROXY_PASSWORD = stringPreferencesKey("proxy_password")
        val PROXY_SERVERS_JSON = stringPreferencesKey("proxy_servers_json")
        val SELECTED_PROXY_SERVER_ID = stringPreferencesKey("selected_proxy_server_id")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val ACTIVE_EPISODE_ID = longPreferencesKey("active_episode_id")
        val PLAYBACK_SPEED = floatPreferencesKey("playback_speed")
        val LAST_REFRESH_TIME = stringPreferencesKey("last_refresh_time")
    }

    override val settingsFlow: Flow<AppSettings> = dataStore.data.map { prefs ->
        val rawServersJson = prefs[Keys.PROXY_SERVERS_JSON]
        val servers = if (!rawServersJson.isNullOrBlank()) {
            parseProxyServersJson(rawServersJson)
        } else {
            emptyList()
        }
        val selectedId = prefs[Keys.SELECTED_PROXY_SERVER_ID] ?: servers.firstOrNull()?.id
        val activeServer = servers.firstOrNull { it.id == selectedId } ?: servers.firstOrNull()

        AppSettings(
            isAutoRefreshEnabled = prefs[Keys.AUTO_REFRESH_ENABLED] ?: false,
            dailyRefreshTime = prefs[Keys.DAILY_REFRESH_TIME] ?: "03:00",
            isProxyEnabled = prefs[Keys.PROXY_ENABLED] ?: false,
            proxyServers = servers,
            selectedProxyServerId = selectedId,
            proxyType = prefs[Keys.PROXY_TYPE] ?: "SOCKS5",
            proxyHost = activeServer?.host ?: "",
            proxyPort = activeServer?.port ?: 1080,
            proxyUsername = activeServer?.username ?: "",
            proxyPassword = activeServer?.password ?: "",
            themeMode = prefs[Keys.THEME_MODE] ?: "System",
            activeEpisodeId = prefs[Keys.ACTIVE_EPISODE_ID],
            playbackSpeed = prefs[Keys.PLAYBACK_SPEED] ?: 1.0f,
            lastRefreshTimeFormatted = prefs[Keys.LAST_REFRESH_TIME] ?: ""
        )
    }

    override suspend fun setAutoRefreshEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.AUTO_REFRESH_ENABLED] = enabled }
    }

    override suspend fun setDailyRefreshTime(time: String) {
        dataStore.edit { it[Keys.DAILY_REFRESH_TIME] = time }
    }

    override suspend fun setProxyEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.PROXY_ENABLED] = enabled }
    }

    override suspend fun setProxySettings(
        enabled: Boolean,
        host: String,
        port: Int,
        type: String,
        username: String,
        password: String
    ) {
        dataStore.edit { prefs ->
            prefs[Keys.PROXY_ENABLED] = enabled
            prefs[Keys.PROXY_HOST] = host
            prefs[Keys.PROXY_PORT] = port
            prefs[Keys.PROXY_TYPE] = type
            prefs[Keys.PROXY_USERNAME] = username
            prefs[Keys.PROXY_PASSWORD] = password

            val currentList = parseProxyServersJson(prefs[Keys.PROXY_SERVERS_JSON] ?: "").toMutableList()
            if (currentList.isEmpty() && host.isNotBlank()) {
                val newServer = ProxyServerConfig(
                    name = "Default Proxy",
                    host = host,
                    port = port,
                    username = username,
                    password = password
                )
                currentList.add(newServer)
                prefs[Keys.PROXY_SERVERS_JSON] = serializeProxyServers(currentList)
                prefs[Keys.SELECTED_PROXY_SERVER_ID] = newServer.id
            } else if (currentList.isNotEmpty() && host.isNotBlank()) {
                val selectedId = prefs[Keys.SELECTED_PROXY_SERVER_ID] ?: currentList.first().id
                val index = currentList.indexOfFirst { it.id == selectedId }
                if (index >= 0) {
                    currentList[index] = currentList[index].copy(
                        host = host,
                        port = port,
                        username = username,
                        password = password
                    )
                    prefs[Keys.PROXY_SERVERS_JSON] = serializeProxyServers(currentList)
                }
            }
        }
    }

    override suspend fun addProxyServer(server: ProxyServerConfig): ProxyServerConfig {
        var addedServer = server
        dataStore.edit { prefs ->
            val currentList = parseProxyServersJson(prefs[Keys.PROXY_SERVERS_JSON] ?: "").toMutableList()
            currentList.add(addedServer)
            prefs[Keys.PROXY_SERVERS_JSON] = serializeProxyServers(currentList)
            val selectedId = prefs[Keys.SELECTED_PROXY_SERVER_ID]
            if (selectedId.isNullOrBlank() || currentList.size == 1) {
                prefs[Keys.SELECTED_PROXY_SERVER_ID] = addedServer.id
                prefs[Keys.PROXY_HOST] = addedServer.host
                prefs[Keys.PROXY_PORT] = addedServer.port
                prefs[Keys.PROXY_USERNAME] = addedServer.username
                prefs[Keys.PROXY_PASSWORD] = addedServer.password
            }
        }
        return addedServer
    }

    override suspend fun updateProxyServer(server: ProxyServerConfig) {
        dataStore.edit { prefs ->
            val currentList = parseProxyServersJson(prefs[Keys.PROXY_SERVERS_JSON] ?: "").toMutableList()
            val index = currentList.indexOfFirst { it.id == server.id }
            if (index >= 0) {
                currentList[index] = server
                prefs[Keys.PROXY_SERVERS_JSON] = serializeProxyServers(currentList)
                if (prefs[Keys.SELECTED_PROXY_SERVER_ID] == server.id) {
                    prefs[Keys.PROXY_HOST] = server.host
                    prefs[Keys.PROXY_PORT] = server.port
                    prefs[Keys.PROXY_USERNAME] = server.username
                    prefs[Keys.PROXY_PASSWORD] = server.password
                }
            }
        }
    }

    override suspend fun deleteProxyServer(serverId: String) {
        dataStore.edit { prefs ->
            val currentList = parseProxyServersJson(prefs[Keys.PROXY_SERVERS_JSON] ?: "").toMutableList()
            currentList.removeAll { it.id == serverId }
            prefs[Keys.PROXY_SERVERS_JSON] = serializeProxyServers(currentList)
            if (prefs[Keys.SELECTED_PROXY_SERVER_ID] == serverId) {
                val nextActive = currentList.firstOrNull()
                if (nextActive != null) {
                    prefs[Keys.SELECTED_PROXY_SERVER_ID] = nextActive.id
                    prefs[Keys.PROXY_HOST] = nextActive.host
                    prefs[Keys.PROXY_PORT] = nextActive.port
                    prefs[Keys.PROXY_USERNAME] = nextActive.username
                    prefs[Keys.PROXY_PASSWORD] = nextActive.password
                } else {
                    prefs.remove(Keys.SELECTED_PROXY_SERVER_ID)
                    prefs[Keys.PROXY_HOST] = ""
                    prefs[Keys.PROXY_USERNAME] = ""
                    prefs[Keys.PROXY_PASSWORD] = ""
                }
            }
        }
    }

    override suspend fun selectProxyServer(serverId: String) {
        dataStore.edit { prefs ->
            val currentList = parseProxyServersJson(prefs[Keys.PROXY_SERVERS_JSON] ?: "")
            val found = currentList.firstOrNull { it.id == serverId }
            if (found != null) {
                prefs[Keys.SELECTED_PROXY_SERVER_ID] = found.id
                prefs[Keys.PROXY_HOST] = found.host
                prefs[Keys.PROXY_PORT] = found.port
                prefs[Keys.PROXY_USERNAME] = found.username
                prefs[Keys.PROXY_PASSWORD] = found.password
            }
        }
    }

    override suspend fun setThemeMode(theme: String) {
        dataStore.edit { it[Keys.THEME_MODE] = theme }
    }

    override suspend fun setActiveEpisodeId(episodeId: Long?) {
        dataStore.edit {
            if (episodeId == null) {
                it.remove(Keys.ACTIVE_EPISODE_ID)
            } else {
                it[Keys.ACTIVE_EPISODE_ID] = episodeId
            }
        }
    }

    override suspend fun getActiveEpisodeId(): Long? {
        return dataStore.data.first()[Keys.ACTIVE_EPISODE_ID]
    }

    override suspend fun setPlaybackSpeed(speed: Float) {
        dataStore.edit { it[Keys.PLAYBACK_SPEED] = speed }
    }

    override suspend fun setLastRefreshTime(formatted: String) {
        dataStore.edit { it[Keys.LAST_REFRESH_TIME] = formatted }
    }
}
