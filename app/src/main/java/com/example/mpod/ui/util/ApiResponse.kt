package com.example.mpod.ui.util

import retrofit2.Response

internal class UserFacingApiException(message: String) : IllegalStateException(message)

internal fun <T> Response<T>.requireApiBody(defaultMessage: String): T {
    if (isSuccessful) {
        body()?.let { return it }
    }
    throw UserFacingApiException(apiErrorMessage(errorBody()?.string(), defaultMessage))
}

internal fun missingApiPayload(defaultMessage: String): Nothing {
    throw UserFacingApiException(defaultMessage)
}

internal fun Throwable.userFacingApiMessage(defaultMessage: String): String {
    return (this as? UserFacingApiException)?.message ?: defaultMessage
}
