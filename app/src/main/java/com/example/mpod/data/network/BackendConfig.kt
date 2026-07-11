package com.example.mpod.data.network

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackendConfig @Inject constructor(
    @ApplicationContext context: Context
) {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val address: String
        get() = preferences.getString(KEY_ADDRESS, null)?.takeIf { it.isNotBlank() } ?: DEFAULT_ADDRESS

    val baseUrl: String
        get() = address.toBaseUrlOrNull() ?: DEFAULT_BASE_URL

    fun saveAddress(input: String): Result<String> {
        val normalized = input.toDisplayAddressOrNull()
            ?: return Result.failure(IllegalArgumentException("Enter a valid backend address, for example 192.168.0.222:5051."))

        preferences.edit()
            .putString(KEY_ADDRESS, normalized)
            .apply()

        return Result.success(normalized)
    }

    private fun String.toDisplayAddressOrNull(): String? {
        val url = toBaseUrlOrNull()?.toHttpUrlOrNull() ?: return null
        val usesDefaultPort = (url.scheme == "http" && url.port == 80) ||
            (url.scheme == "https" && url.port == 443)
        val port = if (usesDefaultPort) "" else ":${url.port}"
        return "${url.host}$port"
    }

    private fun String.toBaseUrlOrNull(): String? {
        val trimmed = trim()
        if (trimmed.isBlank()) return null

        val candidate = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "http://$trimmed"
        }

        val withTrailingSlash = if (candidate.endsWith("/")) candidate else "$candidate/"
        return withTrailingSlash.toHttpUrlOrNull()
            ?.newBuilder()
            ?.encodedPath("/")
            ?.query(null)
            ?.fragment(null)
            ?.build()
            ?.toString()
    }

    companion object {
        const val DEFAULT_ADDRESS = "192.168.0.222:5051"
        private const val DEFAULT_BASE_URL = "http://$DEFAULT_ADDRESS/"
        private const val PREFS_NAME = "mpod_backend_config"
        private const val KEY_ADDRESS = "backend_address"
    }
}
