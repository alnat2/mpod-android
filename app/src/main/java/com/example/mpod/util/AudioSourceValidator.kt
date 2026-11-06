package com.example.mpod.util

import java.net.URI
import java.util.Locale

object AudioSourceValidator {
    fun isSupportedAudioSource(value: String): Boolean = runCatching {
        val uri = URI(value.trim())
        when (uri.scheme?.lowercase(Locale.ROOT)) {
            "http", "https" -> !uri.host.isNullOrBlank()
            "content", "file" -> !uri.schemeSpecificPart.isNullOrBlank()
            else -> false
        }
    }.getOrDefault(false)

    fun isHttpUrl(value: String): Boolean = runCatching {
        val uri = URI(value.trim())
        uri.scheme?.lowercase(Locale.ROOT) in setOf("http", "https") && !uri.host.isNullOrBlank()
    }.getOrDefault(false)
}
