package com.example.mpod.util

import android.content.Intent
import android.net.Uri

data class SharedAudioData(
    val urlOrUri: String,
    val title: String? = null
)

object SharedIntentParser {
    private val HTTP_URL_REGEX = Regex("https?://[^\\s<>\"']+")

    fun parse(intent: Intent?): SharedAudioData? {
        if (intent == null) return null
        val action = intent.action ?: return null

        when (action) {
            Intent.ACTION_SEND -> {
                val type = intent.type
                if (type?.startsWith("audio/") == true) {
                    val streamUri = extractStreamUri(intent)
                    if (streamUri != null) {
                        return SharedAudioData(
                            urlOrUri = streamUri.toString(),
                            title = intent.getStringExtra(Intent.EXTRA_SUBJECT)
                                ?: intent.getStringExtra(Intent.EXTRA_TITLE)
                        )
                    }
                }
                // Also handle text/plain containing a URL
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                    ?: intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
                if (!text.isNullOrBlank()) {
                    val url = extractUrl(text)
                    if (url != null) {
                        return SharedAudioData(
                            urlOrUri = url,
                            title = intent.getStringExtra(Intent.EXTRA_SUBJECT)
                                ?: intent.getStringExtra(Intent.EXTRA_TITLE)
                        )
                    }
                }
            }
            Intent.ACTION_VIEW -> {
                val dataUri = intent.data
                if (dataUri != null) {
                    return SharedAudioData(
                        urlOrUri = dataUri.toString(),
                        title = intent.getStringExtra(Intent.EXTRA_TITLE)
                    )
                }
            }
        }
        return null
    }

    fun extractUrl(text: String): String? {
        val trimmed = text.trim()
        val match = HTTP_URL_REGEX.find(trimmed)
        return match?.value?.trimEnd('.', ',', ';', '!', '?')
    }

    private fun extractStreamUri(intent: Intent): Uri? {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
        } ?: intent.data
    }
}
