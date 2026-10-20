package com.example.mpod.data.network

import com.example.mpod.data.local.preferences.AppSettings
import com.example.mpod.data.local.preferences.AppSettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.net.Authenticator
import java.net.PasswordAuthentication
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.Credentials

@Singleton
class ProxyHttpClientFactory @Inject constructor() {

    private val dynamicProxySelector = DynamicProxySelector()
    private val currentSettings = AtomicReference<AppSettings>(AppSettings())

    init {
        Authenticator.setDefault(object : Authenticator() {
            override fun getPasswordAuthentication(): PasswordAuthentication? {
                val s = currentSettings.get() ?: return null
                if (s.isProxyEnabled) {
                    val active = s.activeProxyServer
                    val user = active?.username ?: s.proxyUsername
                    val pass = active?.password ?: s.proxyPassword
                    if (user.isNotBlank()) {
                        return PasswordAuthentication(user, pass.toCharArray())
                    }
                }
                return null
            }
        })
    }

    private val dynamicClient: OkHttpClient = OkHttpClient.Builder()
        .proxySelector(dynamicProxySelector)
        .proxyAuthenticator { _, response ->
            val s = currentSettings.get()
            if (s != null && s.isProxyEnabled) {
                val active = s.activeProxyServer
                val user = active?.username ?: s.proxyUsername
                val pass = active?.password ?: s.proxyPassword
                if (user.isNotBlank()) {
                    val credential = Credentials.basic(user, pass)
                    response.request.newBuilder()
                        .header("Proxy-Authorization", credential)
                        .build()
                } else null
            } else null
        }
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    fun configure(dataStore: AppSettingsStore, scope: CoroutineScope) {
        scope.launch {
            dataStore.settingsFlow
                .map {
                    val active = it.activeProxyServer
                    listOf(
                        it.isProxyEnabled,
                        it.proxyType,
                        active?.host ?: it.proxyHost,
                        active?.port ?: it.proxyPort,
                        active?.username ?: it.proxyUsername,
                        active?.password ?: it.proxyPassword
                    ) to it
                }
                .distinctUntilChanged { old, new -> old.first == new.first }
                .collect { (_, settings) ->
                    updateProxy(settings)
                }
        }
    }

    fun updateProxy(settings: AppSettings) {
        currentSettings.set(settings)
        dynamicProxySelector.updateProxy(settings)
    }

    fun getProxySelector(): DynamicProxySelector = dynamicProxySelector

    fun setProxyConnectFailedListener(listener: ((java.net.Proxy, java.io.IOException?) -> Unit)?) {
        dynamicProxySelector.onProxyConnectFailed = listener
    }

    fun createClient(settings: AppSettings? = null): OkHttpClient {
        if (settings != null) {
            updateProxy(settings)
        }
        return dynamicClient
    }

    companion object {
        fun createDirectOkHttpClient(): OkHttpClient {
            return createOkHttpClient(proxyEnabled = false)
        }

        fun createOkHttpClient(
            proxyEnabled: Boolean,
            proxyHost: String = "",
            proxyPort: Int = 1080,
            proxyType: String = "SOCKS5",
            proxyUsername: String = "",
            proxyPassword: String = ""
        ): OkHttpClient {
            val builder = OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)

            if (proxyEnabled) {
                val proxy = DynamicProxySelector.resolveProxy(
                    AppSettings(
                        isProxyEnabled = true,
                        proxyHost = proxyHost,
                        proxyPort = proxyPort,
                        proxyType = proxyType,
                        proxyUsername = proxyUsername,
                        proxyPassword = proxyPassword
                    )
                )
                builder.proxy(proxy)
                if (proxyUsername.isNotBlank()) {
                    builder.proxyAuthenticator { _, response ->
                        val credential = Credentials.basic(proxyUsername, proxyPassword)
                        response.request.newBuilder()
                            .header("Proxy-Authorization", credential)
                            .build()
                    }
                }
            }

            return builder.build()
        }
    }
}
