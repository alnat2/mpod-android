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

    @Volatile
    private var directClient: OkHttpClient? = null
    @Volatile
    private var cachedProxyKey: String? = null
    @Volatile
    private var cachedProxyClient: OkHttpClient? = null

    fun createClient(settings: AppSettings? = null): OkHttpClient {
        val proxyEnabled = settings?.isProxyEnabled == true
        val host = settings?.proxyHost.orEmpty()
        val port = settings?.proxyPort ?: 1080
        val type = settings?.proxyType ?: "SOCKS5"
        if (!proxyEnabled || host.isBlank()) {
            return directClient ?: createOkHttpClient(proxyEnabled = false).also { directClient = it }
        }
        val key = "$host:$port:$type"
        cachedProxyClient?.let { if (cachedProxyKey == key) return it }
        val client = createOkHttpClient(
            proxyEnabled = true,
            proxyHost = host,
            proxyPort = port,
            proxyType = type
        )
        cachedProxyKey = key
        cachedProxyClient = client
        return client
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

            if (proxyEnabled && proxyHost.isNotBlank()) {
                val type = if (proxyType.equals("HTTP", ignoreCase = true)) {
                    Proxy.Type.HTTP
                } else {
                    Proxy.Type.SOCKS
                }
                val address = InetSocketAddress(proxyHost.trim(), proxyPort)
                builder.proxy(Proxy(type, address))
            }

            return builder.build()
        }
    }
}
