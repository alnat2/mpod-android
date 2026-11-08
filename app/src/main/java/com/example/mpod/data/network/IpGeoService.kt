package com.example.mpod.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class IpGeoService @Inject constructor(
    private val proxyHttpClientFactory: ProxyHttpClientFactory
) {
    open suspend fun fetchCurrentIpGeo(client: OkHttpClient? = null): Result<Pair<String, String>> = withContext(Dispatchers.IO) {
        val baseClient = client ?: proxyHttpClientFactory.createClient()
        val httpClient = baseClient.newBuilder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .callTimeout(8, TimeUnit.SECONDS)
            .build()

        runCatching {
            executeIpRequest(httpClient, "https://ipinfo.io/json")
        }.recoverCatching {
            executeIpRequest(httpClient, "https://api.country.is")
        }.recoverCatching {
            executeIpRequest(httpClient, "http://ip-api.com/json/?fields=query,countryCode,status")
        }.recoverCatching {
            executeIpRequest(httpClient, "https://api.ipify.org?format=json")
        }
    }

    private fun executeIpRequest(client: OkHttpClient, url: String): Pair<String, String> {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", NetworkModule.USER_AGENT)
            .build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code} from $url")
            val body = response.body?.string().orEmpty()
            parseIpGeoJson(body) ?: error("Failed to parse IP/Geo from response: $body")
        }
    }
}

internal fun parseIpGeoJson(jsonString: String): Pair<String, String>? {
    val ip = Regex(""""(?:query|ip)"\s*:\s*"([^"]+)"""").find(jsonString)?.groupValues?.get(1)
    val geo = Regex(""""(?:countryCode|country_code|country)"\s*:\s*"([^"]+)"""").find(jsonString)?.groupValues?.get(1) ?: "Unknown"
    return if (ip != null) ip to geo else null
}
