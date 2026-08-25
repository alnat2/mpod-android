package com.example.mpod.data.network

import com.example.mpod.data.local.preferences.AppSettings
import okhttp3.OkHttpClient
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProxyHttpClientFactory @Inject constructor() {
    fun createClient(settings: AppSettings? = null): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)

        if (settings != null && settings.isProxyEnabled && settings.proxyHost.isNotBlank()) {
            val proxyType = if (settings.proxyType.equals("HTTP", ignoreCase = true)) {
                Proxy.Type.HTTP
            } else {
                Proxy.Type.SOCKS
            }
            val address = InetSocketAddress(settings.proxyHost.trim(), settings.proxyPort)
            builder.proxy(Proxy(proxyType, address))
        }

        return builder.build()
    }
}
