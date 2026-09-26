package com.example.mpod.data.network

import com.example.mpod.data.local.preferences.AppSettings
import com.example.mpod.data.local.preferences.AppSettingsDataStore
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
                val s = currentSettings.get()
                if (s != null && s.isProxyEnabled && s.proxyUsername.isNotBlank()) {
                    return PasswordAuthentication(s.proxyUsername, s.proxyPassword.toCharArray())
                }
                return null
            }
        })
    }

    private val dynamicClient: OkHttpClient = OkHttpClient.Builder()
        .proxySelector(dynamicProxySelector)
        .proxyAuthenticator { _, response ->
            val s = currentSettings.get()
            if (s != null && s.isProxyEnabled && s.proxyUsername.isNotBlank()) {
                val credential = Credentials.basic(s.proxyUsername, s.proxyPassword)
                response.request.newBuilder()
                    .header("Proxy-Authorization", credential)
                    .build()
            } else null
        }
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    fun configure(dataStore: AppSettingsDataStore, scope: CoroutineScope) {
        scope.launch {
            dataStore.settingsFlow
                .map { Triple(it.isProxyEnabled, it.proxyHost, it.proxyPort to (it.proxyUsername to it.proxyPassword)) to it }
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
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
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
