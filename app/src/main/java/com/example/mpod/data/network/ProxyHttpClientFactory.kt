package com.example.mpod.data.network

import com.example.mpod.data.local.preferences.AppSettings
import com.example.mpod.data.local.preferences.AppSettingsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProxyHttpClientFactory @Inject constructor() {

    private val dynamicProxySelector = DynamicProxySelector()

    private val dynamicClient: OkHttpClient = OkHttpClient.Builder()
        .proxySelector(dynamicProxySelector)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    fun configure(dataStore: AppSettingsDataStore, scope: CoroutineScope) {
        scope.launch {
            dataStore.settingsFlow
                .map { Triple(it.isProxyEnabled, it.proxyHost, it.proxyPort to it.proxyType) to it }
                .distinctUntilChanged { old, new -> old.first == new.first }
                .collect { (_, settings) ->
                    dynamicProxySelector.updateProxy(settings)
                }
        }
    }

    fun updateProxy(settings: AppSettings) {
        dynamicProxySelector.updateProxy(settings)
    }

    fun getProxySelector(): DynamicProxySelector = dynamicProxySelector

    fun createClient(settings: AppSettings? = null): OkHttpClient {
        if (settings != null) {
            dynamicProxySelector.updateProxy(settings)
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
            proxyType: String = "SOCKS5"
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
                        proxyType = proxyType
                    )
                )
                builder.proxy(proxy)
            }

            return builder.build()
        }
    }
}
