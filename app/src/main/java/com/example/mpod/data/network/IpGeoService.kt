package com.example.mpod.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class IpGeoService @Inject constructor(
    private val proxyHttpClientFactory: ProxyHttpClientFactory
) {
    open suspend fun fetchCurrentIpGeo(client: OkHttpClient? = null): Result<Pair<String, String>> = withContext(Dispatchers.IO) {
        val httpClient = client ?: proxyHttpClientFactory.createClient()
        runCatching {
            val request = Request.Builder()
                .url("http://ip-api.com/json/?fields=query,countryCode,status")
                .header("User-Agent", "mpoddy/1.0")
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("HTTP ${response.code}")
                val body = response.body?.string().orEmpty()
                parseIpGeoJson(body) ?: error("Failed to parse IP/Geo from response: $body")
            }
        }.recoverCatching {
            val fallbackRequest = Request.Builder()
                .url("https://api.country.is")
                .header("User-Agent", "mpoddy/1.0")
                .build()
            httpClient.newCall(fallbackRequest).execute().use { response ->
                if (!response.isSuccessful) error("HTTP ${response.code}")
                val body = response.body?.string().orEmpty()
                parseIpGeoJson(body) ?: error("Failed to parse IP/Geo from fallback: $body")
            }
        }
    }
}

internal fun parseIpGeoJson(jsonString: String): Pair<String, String>? {
    val ip = Regex(""""(?:query|ip)"\s*:\s*"([^"]+)"""").find(jsonString)?.groupValues?.get(1)
    val geo = Regex(""""(?:countryCode|country)"\s*:\s*"([^"]+)"""").find(jsonString)?.groupValues?.get(1) ?: "Unknown"
    return if (ip != null) ip to geo else null
}
