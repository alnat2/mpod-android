package com.example.mpod.data.network

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Cookie
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NetworkReliabilityTest {
    private lateinit var server: MockWebServer
    private lateinit var cookieJar: PersistentCookieJar
    private lateinit var sessionInvalidator: AuthSessionInvalidator

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val context = ApplicationProvider.getApplicationContext<Context>()
        cookieJar = PersistentCookieJar(context).apply { clear() }
        sessionInvalidator = AuthSessionInvalidator()
    }

    @After
    fun tearDown() {
        cookieJar.clear()
        server.shutdown()
    }

    @Test
    fun coreClientUsesSingleThirtySecondDeadline() {
        val client = client()

        assertEquals(TimeUnit.SECONDS.toMillis(30), client.connectTimeoutMillis.toLong())
        assertEquals(TimeUnit.SECONDS.toMillis(30), client.readTimeoutMillis.toLong())
        assertEquals(TimeUnit.SECONDS.toMillis(30), client.writeTimeoutMillis.toLong())
        assertEquals(TimeUnit.SECONDS.toMillis(30), client.callTimeoutMillis.toLong())
    }

    @Test
    fun authenticated401ClearsCookieAndEmitsSessionExpiry() = runBlocking {
        val url = server.url("/api/settings")
        cookieJar.saveFromResponse(
            url,
            listOf(
                Cookie.Builder()
                    .name("session")
                    .value("expired")
                    .hostOnlyDomain(url.host)
                    .path("/")
                    .build()
            )
        )
        server.enqueue(MockResponse().setResponseCode(401))
        val expiry = async(start = CoroutineStart.UNDISPATCHED) {
            sessionInvalidator.events.first()
        }

        val response = client().newCall(Request.Builder().url(url).build()).execute()

        response.close()
        withTimeout(5_000) { expiry.await() }
        assertTrue(cookieJar.loadForRequest(url).isEmpty())
        Unit
    }

    private fun client() = NetworkModule.provideOkHttpClient(
        cookieJar = cookieJar,
        sessionExpiryInterceptor = SessionExpiryInterceptor(cookieJar, sessionInvalidator)
    )
}
