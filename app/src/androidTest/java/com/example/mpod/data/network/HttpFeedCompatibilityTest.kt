package com.example.mpod.data.network

import android.security.NetworkSecurityPolicy
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mpod.data.local.preferences.AppSettings
import com.example.mpod.data.rss.RssFeedParser
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HttpFeedCompatibilityTest {
    @Test
    fun userProvidedHttpHostsRemainAllowedWithoutBackendWhitelist() {
        val policy = NetworkSecurityPolicy.getInstance()

        assertTrue(policy.isCleartextTrafficPermitted("feeds.example.org"))
        assertTrue(policy.isCleartextTrafficPermitted("audio.example.org"))
    }

    @Test
    fun directClientFetchesAndParsesHttpFeed() {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "application/rss+xml")
                    .setBody(
                        """
                        <rss version="2.0"><channel>
                          <title>HTTP podcast</title>
                          <item>
                            <guid>episode-1</guid><title>HTTP episode</title>
                            <enclosure url="${server.url("/episode.mp3")}" type="audio/mpeg"/>
                          </item>
                        </channel></rss>
                        """.trimIndent()
                    )
            )
            val client = ProxyHttpClientFactory().createClient(AppSettings(isProxyEnabled = false))
            val request = Request.Builder().url(server.url("/feed.xml")).build()

            client.newCall(request).execute().use { response ->
                assertTrue(response.isSuccessful)
                val feed = RssFeedParser.parse(checkNotNull(response.body).byteStream())
                assertEquals("HTTP podcast", feed.title)
                assertEquals("HTTP episode", feed.episodes.single().title)
            }
        } finally {
            server.shutdown()
        }
    }
}
