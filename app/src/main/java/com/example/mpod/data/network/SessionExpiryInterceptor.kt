package com.example.mpod.data.network

import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

class SessionExpiryInterceptor @Inject constructor(
    private val cookieJar: PersistentCookieJar,
    private val sessionInvalidator: AuthSessionInvalidator
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val hadSessionCookie = cookieJar.loadForRequest(request.url).isNotEmpty()
        val response = chain.proceed(request)
        if (
            shouldInvalidateAuthenticatedSession(
                responseCode = response.code,
                requestPath = request.url.encodedPath,
                hadSessionCookie = hadSessionCookie
            )
        ) {
            cookieJar.clear()
            sessionInvalidator.invalidate()
        }
        return response
    }
}

internal fun shouldInvalidateAuthenticatedSession(
    responseCode: Int,
    requestPath: String,
    hadSessionCookie: Boolean
): Boolean {
    if (responseCode != 401 || !hadSessionCookie) return false
    return requestPath !in setOf("/api/auth/login", "/api/auth/register")
}
