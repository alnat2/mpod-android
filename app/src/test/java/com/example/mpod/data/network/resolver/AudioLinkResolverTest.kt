package com.example.mpod.data.network.resolver

import com.example.mpod.data.network.ProxyHttpClientFactory
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AudioLinkResolverTest {

    private lateinit var resolver: DefaultAudioLinkResolver

    @Before
    fun setup() {
        // Direct resolver with empty/direct proxy factory for unit tests
        resolver = DefaultAudioLinkResolver(ProxyHttpClientFactory())
    }

    @Test
    fun directAudioExtension_resolvesSuccessfully() = runTest {
        val url = "https://example.com/episodes/awesome_science_talk.mp3"
        val result = resolver.resolve(url)

        assertTrue(result.isSuccess)
        val metadata = result.getOrThrow()
        assertEquals(url, metadata.directAudioUrl)
        assertEquals("awesome science talk", metadata.title)
    }

    @Test
    fun directM4aExtension_resolvesSuccessfully() = runTest {
        val url = "https://cdn.podcasts.org/shows/history-today.m4a?tracking=123"
        val result = resolver.resolve(url)

        assertTrue(result.isSuccess)
        val metadata = result.getOrThrow()
        assertEquals(url, metadata.directAudioUrl)
        assertEquals("history today", metadata.title)
    }

    @Test
    fun itunesLookupResponse_parsesEpisodeMetadata() {
        val sampleJson = """
            {
              "resultCount": 2,
              "results": [
                {
                  "wrapperType": "track",
                  "collectionName": "6 Minute English",
                  "artistName": "BBC Radio"
                },
                {
                  "wrapperType": "podcastEpisode",
                  "trackId": 1000796466292,
                  "trackName": "Why has gold got so expensive?",
                  "collectionName": "6 Minute English",
                  "artistName": "BBC Radio",
                  "episodeUrl": "http://open.live.bbc.co.uk/mediaset/audio/p0pgs15w.mp3",
                  "artworkUrl600": "https://is1-ssl.mzstatic.com/image/thumb/art/600x600bb.jpg",
                  "trackTimeMillis": 419000,
                  "shortDescription": "Many of us own jewellery or other items..."
                }
              ]
            }
        """.trimIndent()

        val parsed = resolver.parseItunesLookupResponse(sampleJson, "1000796466292")

        assertNotNull(parsed)
        assertEquals("http://open.live.bbc.co.uk/mediaset/audio/p0pgs15w.mp3", parsed?.directAudioUrl)
        assertEquals("Why has gold got so expensive?", parsed?.title)
        assertEquals("6 Minute English", parsed?.podcastTitle)
        assertEquals("BBC Radio", parsed?.author)
        assertEquals("https://is1-ssl.mzstatic.com/image/thumb/art/600x600bb.jpg", parsed?.artworkUrl)
        assertEquals(419, parsed?.durationSeconds)
    }

    @Test
    fun applePodcastsHtmlContent_parsesEmbeddedStreamUrl() {
        val sampleHtml = """
            <html>
            <head>
              <meta property="og:title" content="The Future of AI" />
              <meta property="og:site_name" content="Tech Talks" />
              <meta property="og:image" content="https://example.com/cover.jpg" />
            </head>
            <body>
              <script id="embedded-data">
                {"streamUrl":"https://audio.example.com/episode42.mp3","title":"The Future of AI","showOffer":{"title":"Tech Talks"},"duration":1850}
              </script>
            </body>
            </html>
        """.trimIndent()

        val parsed = resolver.parseApplePodcastsHtmlContent(sampleHtml)

        assertNotNull(parsed)
        assertEquals("https://audio.example.com/episode42.mp3", parsed?.directAudioUrl)
        assertEquals("The Future of AI", parsed?.title)
        assertEquals("Tech Talks", parsed?.podcastTitle)
        assertEquals(1850, parsed?.durationSeconds)
    }

    @Test
    fun genericWebHtml_parsesOpenGraphAudio() {
        val sampleHtml = """
            <!DOCTYPE html>
            <html>
            <head>
              <meta property="og:title" content="My Great Audio Blog" />
              <meta property="og:site_name" content="Dev Stories" />
              <meta property="og:audio" content="https://cdn.blog.com/audio/post.mp3" />
              <meta property="og:image" content="https://cdn.blog.com/images/art.png" />
            </head>
            <body>Content</body>
            </html>
        """.trimIndent()

        val parsed = resolver.parseGenericWebHtml(sampleHtml, "https://blog.com/post-1")

        assertNotNull(parsed)
        assertEquals("https://cdn.blog.com/audio/post.mp3", parsed?.directAudioUrl)
        assertEquals("My Great Audio Blog", parsed?.title)
        assertEquals("Dev Stories", parsed?.podcastTitle)
        assertEquals("https://cdn.blog.com/images/art.png", parsed?.artworkUrl)
    }

    @Test
    fun genericWebHtml_parsesAudioTagWithRelativeSrc() {
        val sampleHtml = """
            <!DOCTYPE html>
            <html>
            <head>
              <title>Episode 10: Kotlin Multiplatform</title>
            </head>
            <body>
              <audio src="/media/ep10.mp3" controls></audio>
            </body>
            </html>
        """.trimIndent()

        val parsed = resolver.parseGenericWebHtml(sampleHtml, "https://podcast.org/episodes/10")

        assertNotNull(parsed)
        assertEquals("https://podcast.org/media/ep10.mp3", parsed?.directAudioUrl)
        assertEquals("Episode 10: Kotlin Multiplatform", parsed?.title)
    }

    @Test
    fun genericWebHtml_returnsNullWhenNoAudioFound() {
        val sampleHtml = """
            <html>
            <head><title>Just a regular article</title></head>
            <body><p>No audio here</p></body>
            </html>
        """.trimIndent()

        val parsed = resolver.parseGenericWebHtml(sampleHtml, "https://news.com/article")
        assertNull(parsed)
    }
}
