package com.example.mpod.data.network

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal object BackendAddressNormalizer {
    fun toDisplayAddressOrNull(input: String): String? {
        val url = toBaseUrlOrNull(input)?.toHttpUrlOrNull() ?: return null
        val usesDefaultPort = (url.scheme == "http" && url.port == 80) ||
            (url.scheme == "https" && url.port == 443)
        val port = if (usesDefaultPort) "" else ":${url.port}"
        return "${url.host}$port"
    }

    fun toBaseUrlOrNull(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return null

        val candidate = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "http://$trimmed"
        }

        return candidate.withTrailingSlash().toHttpUrlOrNull()
            ?.newBuilder()
            ?.encodedPath("/")
            ?.query(null)
            ?.fragment(null)
            ?.build()
            ?.toString()
    }

    private fun String.withTrailingSlash(): String = if (endsWith("/")) this else "$this/"
}
