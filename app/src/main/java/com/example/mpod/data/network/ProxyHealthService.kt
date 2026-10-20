package com.example.mpod.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.mpod.data.local.preferences.ProxyServerConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

sealed interface ProxyCheckResult {
    data class Success(val ip: String, val geo: String) : ProxyCheckResult
    data class ProxyFailed(val message: String) : ProxyCheckResult
    data object NoInternet : ProxyCheckResult
}

@Singleton
open class ProxyHealthService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    open fun isDeviceOnline(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    open suspend fun checkProxy(server: ProxyServerConfig): ProxyCheckResult = withContext(Dispatchers.IO) {
        if (!isDeviceOnline()) {
            return@withContext ProxyCheckResult.NoInternet
        }

        val proxy = Proxy(
            Proxy.Type.SOCKS,
            InetSocketAddress.createUnresolved(server.host.trim(), server.port)
        )

        val clientBuilder = OkHttpClient.Builder()
            .proxy(proxy)
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .callTimeout(8, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)

        if (server.username.isNotBlank()) {
            clientBuilder.proxyAuthenticator { _, response ->
                val credential = Credentials.basic(server.username.trim(), server.password)
                response.request.newBuilder()
                    .header("Proxy-Authorization", credential)
                    .build()
            }
        }

        val testClient = clientBuilder.build()

        val probeResult = runCatching {
            val request = Request.Builder()
                .url("https://api.country.is")
                .header("User-Agent", NetworkModule.USER_AGENT)
                .build()
            testClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("HTTP ${response.code}")
                val body = response.body?.string().orEmpty()
                parseIpGeoJson(body) ?: error("Failed to parse response: $body")
            }
        }.recoverCatching {
            val fallbackRequest = Request.Builder()
                .url("http://ip-api.com/json/?fields=query,countryCode,status")
                .header("User-Agent", NetworkModule.USER_AGENT)
                .build()
            testClient.newCall(fallbackRequest).execute().use { response ->
                if (!response.isSuccessful) error("HTTP ${response.code}")
                val body = response.body?.string().orEmpty()
                parseIpGeoJson(body) ?: error("Failed to parse fallback: $body")
            }
        }

        probeResult.fold(
            onSuccess = { (ip, geo) -> ProxyCheckResult.Success(ip, geo) },
            onFailure = { error ->
                if (!isDeviceOnline()) {
                    ProxyCheckResult.NoInternet
                } else {
                    ProxyCheckResult.ProxyFailed(error.message ?: "Failed to connect to proxy")
                }
            }
        )
    }
}
