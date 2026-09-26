package com.example.mpod.data.network

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class IpGeoServiceTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun parseIpGeoJson_parsesIpApiFormatCorrectly() {
        val json = """{"query":"43.32.112.45","countryCode":"UK","status":"success"}"""
        val result = parseIpGeoJson(json)
        assertNotNull(result)
        assertEquals("43.32.112.45", result?.first)
        assertEquals("UK", result?.second)
    }

    @Test
    fun parseIpGeoJson_parsesCountryIsFormatCorrectly() {
        val json = """{"ip":"8.8.8.8","country":"US"}"""
        val result = parseIpGeoJson(json)
        assertNotNull(result)
        assertEquals("8.8.8.8", result?.first)
        assertEquals("US", result?.second)
    }

    @Test
    fun parseIpGeoJson_returnsNullForInvalidJson() {
        val json = """{"error":"not found"}"""
        val result = parseIpGeoJson(json)
        assertNull(result)
    }

    @Test
    fun fetchCurrentIpGeo_parsesMockServerResponse() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"query":"43.32.112.45","countryCode":"UK","status":"success"}""")
        )

        val factory = ProxyHttpClientFactory()
        val service = object : IpGeoService(factory) {
            override suspend fun fetchCurrentIpGeo(client: OkHttpClient?): Result<Pair<String, String>> {
                val httpClient = client ?: OkHttpClient()
                val request = okhttp3.Request.Builder()
                    .url(server.url("/json"))
                    .build()
                return runCatching {
                    httpClient.newCall(request).execute().use { response ->
                        val body = response.body?.string().orEmpty()
                        parseIpGeoJson(body) ?: error("Failed to parse")
                    }
                }
            }
        }

        val result = service.fetchCurrentIpGeo()
        assertTrue(result.isSuccess)
        assertEquals("43.32.112.45" to "UK", result.getOrNull())
    }
}
