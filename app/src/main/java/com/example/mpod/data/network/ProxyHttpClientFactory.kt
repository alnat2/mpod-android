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
        return createOkHttpClient(
            proxyEnabled = settings?.isProxyEnabled == true,
            proxyHost = settings?.proxyHost.orEmpty(),
            proxyPort = settings?.proxyPort ?: 1080,
            proxyType = settings?.proxyType ?: "SOCKS5"
        )
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
