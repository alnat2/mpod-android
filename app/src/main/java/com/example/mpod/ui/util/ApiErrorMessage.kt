package com.example.mpod.ui.util

import com.google.gson.JsonParser

internal fun apiErrorMessage(rawBody: String?, defaultMessage: String): String {
    val body = rawBody?.trim().orEmpty()
    if (body.isBlank()) return defaultMessage

    val parsedMessage = runCatching {
        val json = JsonParser.parseString(body).asJsonObject
        val error = json.get("error")
        when {
            error?.isJsonObject == true -> error.asJsonObject.get("message")?.asString
            error?.isJsonPrimitive == true -> error.asString
            else -> json.get("message")?.asString
        }
    }.getOrNull()

    return parsedMessage?.takeIf { it.isNotBlank() } ?: body
}
