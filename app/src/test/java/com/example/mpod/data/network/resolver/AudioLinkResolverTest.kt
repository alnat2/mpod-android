package com.example.mpod.data.network.resolver

import com.example.mpod.data.network.ProxyHttpClientFactory
import com.example.mpod.data.rss.ParsedEpisodeItem
import com.example.mpod.data.rss.ParsedPodcastFeed
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
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

    @Test
    fun extractFeedUrlFromItunesResponse_findsFeedUrlInJson() {
        val json = """
            {
              "resultCount": 1,
              "results": [
                {
                  "wrapperType": "track",
                  "collectionName": "Нуар в большом городе",
                  "feedUrl": "https://cloud.mave.digital/38024"
                }
              ]
            }
        """.trimIndent()

        val feedUrl = resolver.extractFeedUrlFromItunesResponse(json)
        assertEquals("https://cloud.mave.digital/38024", feedUrl)
    }

    @Test
    fun extractSlugTitleFromAppleUrl_decodesCyrillicSlug() {
        val url = "https://podcasts.apple.com/ru/podcast/%D1%83%D0%B4%D0%B0%D1%87%D0%B0-%D0%BD%D0%B0-%D0%B4%D0%BE%D1%80%D0%BE%D0%B3%D0%B0%D1%85/id1358638122?i=1000453118551"
        val slugTitle = resolver.extractSlugTitleFromAppleUrl(url)
        assertEquals("удача на дорогах", slugTitle)
    }

    @Test
    fun normalizeForComparison_stripsPunctuationAndWhitespace() {
        assertEquals("удача на дорогах", resolver.normalizeForComparison("Удача на дорогах!"))
        assertEquals("why has gold got so expensive", resolver.normalizeForComparison("Why has gold got so expensive?"))
    }

    @Test
    fun findAudioUrlInHtml_extractsMaveCdnAudioUrl() {
        val html = """
            <html>
            <body>
              <script>
                var data = {"audioUrl": "https://cdn.mave.digital/storage/podcasts/cb1c2bc8/episodes/c6c0f679.mp3"};
              </script>
            </body>
            </html>
        """.trimIndent()

        val audioUrl = resolver.findAudioUrlInHtml(html)
        assertEquals("https://cdn.mave.digital/storage/podcasts/cb1c2bc8/episodes/c6c0f679.mp3", audioUrl)
    }

    @Test
    fun findAudioUrlInHtml_extractsMaveStoragePath() {
        val html = """
            <html>
            <body>
              <script id="__NUXT_DATA__">
                ["storage/podcasts/cb1c2bc8-1109-477c-9681-7fa344d51e95/episodes/c6c0f679-2c7a-4ccc-9239-aeb61fb2e4a4.mp3"]
              </script>
            </body>
            </html>
        """.trimIndent()

        val audioUrl = resolver.findAudioUrlInHtml(html)
        assertEquals(
            "https://cdn.mave.digital/storage/podcasts/cb1c2bc8-1109-477c-9681-7fa344d51e95/episodes/c6c0f679-2c7a-4ccc-9239-aeb61fb2e4a4.mp3",
            audioUrl
        )
    }

    @Test
    fun findRssFeedUrlInHtml_extractsMaveCloudFeedUrl() {
        val html = """
            <html>
            <head><title>Думаем дальше</title></head>
            <body>
              <p>Listen on <a href="https://cloud.mave.digital/51724">RSS</a></p>
            </body>
            </html>
        """.trimIndent()

        val feedUrl = resolver.findRssFeedUrlInHtml(html, "https://thinkon.mave.digital/ep-70")
        assertEquals("https://cloud.mave.digital/51724", feedUrl)
    }

    @Test
    fun findRssFeedUrlInHtml_extractsAlternateRssLink() {
        val html = """
            <html>
            <head>
              <link rel="alternate" type="application/rss+xml" title="Podcast RSS" href="/rss.xml" />
            </head>
            </html>
        """.trimIndent()

        val feedUrl = resolver.findRssFeedUrlInHtml(html, "https://myblog.com/podcast/ep-1")
        assertEquals("https://myblog.com/rss.xml", feedUrl)
    }

    @Test
    fun matchEpisodeInFeed_matchesByGuidSegment() {
        val feed = ParsedPodcastFeed(
            title = "Думаем дальше",
            description = "Интеллектуальный подкаст",
            author = "Слава",
            artworkUrl = "https://cdn.mave.digital/cover.jpg",
            link = "https://thinkon.mave.digital",
            lastBuildDate = "Fri, 01 Nov 2026 12:00:00 GMT",
            episodes = listOf(
                ParsedEpisodeItem(
                    guid = "https://thinkon.mave.digital/ep-71",
                    title = "71. Свежий выпуск",
                    description = "Описание 71",
                    audioUrl = "https://cdn.mave.digital/storage/podcasts/episodes/71.mp3",
                    durationSeconds = 3600,
                    publishedAt = 1000L,
                    publishedAtString = "01 Nov 2026"
                ),
                ParsedEpisodeItem(
                    guid = "https://thinkon.mave.digital/ep-70",
                    title = "70. Что такое удача?",
                    description = "Описание 70",
                    audioUrl = "https://cdn.mave.digital/storage/podcasts/episodes/70.mp3",
                    durationSeconds = 3000,
                    publishedAt = 500L,
                    publishedAtString = "25 Oct 2026"
                )
            )
        )

        val matched = resolver.matchEpisodeInFeed(feed, "https://thinkon.mave.digital/ep-70")
        assertNotNull(matched)
        assertEquals("70. Что такое удача?", matched?.title)
        assertEquals("https://cdn.mave.digital/storage/podcasts/episodes/70.mp3", matched?.audioUrl)
    }

    @Test
    fun matchEpisodeInFeed_returnsNullWhenSpecificEpisodeNotFound() {
        val feed = ParsedPodcastFeed(
            title = "Думаем дальше",
            description = "Интеллектуальный подкаст",
            author = "Слава",
            artworkUrl = "https://cdn.mave.digital/cover.jpg",
            link = "https://thinkon.mave.digital",
            lastBuildDate = "Fri, 01 Nov 2026 12:00:00 GMT",
            episodes = listOf(
                ParsedEpisodeItem(
                    guid = "https://thinkon.mave.digital/ep-71",
                    title = "71. Свежий выпуск",
                    description = "Описание 71",
                    audioUrl = "https://cdn.mave.digital/storage/podcasts/episodes/71.mp3",
                    durationSeconds = 3600,
                    publishedAt = 1000L,
                    publishedAtString = "01 Nov 2026"
                )
            )
        )

        // Does not match ep-999 and must NOT fallback to latest ep-71
        val matched = resolver.matchEpisodeInFeed(feed, "https://thinkon.mave.digital/ep-999")
        assertNull(matched)
    }

    @Test
    fun matchEpisodeInFeed_fallsBackToFirstEpisodeForRootUrl() {
        val feed = ParsedPodcastFeed(
            title = "Думаем дальше",
            description = "Интеллектуальный подкаст",
            author = "Слава",
            artworkUrl = "https://cdn.mave.digital/cover.jpg",
            link = "https://thinkon.mave.digital",
            lastBuildDate = "Fri, 01 Nov 2026 12:00:00 GMT",
            episodes = listOf(
                ParsedEpisodeItem(
                    guid = "https://thinkon.mave.digital/ep-71",
                    title = "71. Свежий выпуск",
                    description = "Описание 71",
                    audioUrl = "https://cdn.mave.digital/storage/podcasts/episodes/71.mp3",
                    durationSeconds = 3600,
                    publishedAt = 1000L,
                    publishedAtString = "01 Nov 2026"
                )
            )
        )

        val matched = resolver.matchEpisodeInFeed(feed, "https://thinkon.mave.digital/")
        assertNotNull(matched)
        assertEquals("71. Свежий выпуск", matched?.title)
    }

    @Test
    fun resolveEpisodeFromWebRss_resolvesMetadataUsingRssFeed() {
        val sampleRss = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0" xmlns:itunes="http://www.itunes.com/dtds/podcast-1.0.dtd">
              <channel>
                <title>Думаем дальше</title>
                <description>Интеллектуальный подкаст</description>
                <itunes:author>Слава</itunes:author>
                <itunes:image href="https://cdn.mave.digital/cover.jpg"/>
                <item>
                  <title>70. Что такое удача?</title>
                  <guid>https://thinkon.mave.digital/ep-70</guid>
                  <enclosure url="https://cdn.mave.digital/storage/podcasts/episodes/70.mp3" type="audio/mpeg" length="12345"/>
                  <itunes:duration>3000</itunes:duration>
                  <description>Выпуск про удачу</description>
                </item>
              </channel>
            </rss>
        """.trimIndent()

        val fakeClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(sampleRss.toResponseBody("application/rss+xml".toMediaType()))
                    .build()
            }
            .build()

        val resolved = resolver.resolveEpisodeFromWebRss(
            client = fakeClient,
            feedUrl = "https://cloud.mave.digital/51724",
            pageUrl = "https://thinkon.mave.digital/ep-70"
        )

        assertNotNull(resolved)
        assertEquals("https://cdn.mave.digital/storage/podcasts/episodes/70.mp3", resolved?.directAudioUrl)
        assertEquals("70. Что такое удача?", resolved?.title)
        assertEquals("Думаем дальше", resolved?.podcastTitle)
        assertEquals("Слава", resolved?.author)
        assertEquals("https://cdn.mave.digital/cover.jpg", resolved?.artworkUrl)
        assertEquals(3000, resolved?.durationSeconds)
        assertEquals("Выпуск про удачу", resolved?.description)
    }

    @Test
    fun matchEpisodeInFeed_matchesByPageTitle_whenGuidIsUuidAndTitleHasNoNumber() {
        val feed = ParsedPodcastFeed(
            title = "Думаем дальше",
            description = "Интеллектуальный подкаст",
            author = "Илья Бирман",
            artworkUrl = "https://cdn.mave.digital/cover.jpg",
            link = "https://thinkon.mave.digital",
            lastBuildDate = "Fri, 01 Nov 2026 12:00:00 GMT",
            episodes = listOf(
                ParsedEpisodeItem(
                    guid = "uuid-ep-70",
                    title = "Что такое удача?",
                    description = "Описание 70",
                    audioUrl = "https://cdn.mave.digital/storage/podcasts/episodes/70.mp3",
                    durationSeconds = 3000,
                    publishedAt = 500L,
                    publishedAtString = "25 Oct 2026"
                ),
                ParsedEpisodeItem(
                    guid = "uuid-ep-69-match-factory",
                    title = "Мне уже надо на спичечный завод",
                    description = "Выпуск с Викой Лисицыной",
                    audioUrl = "https://cdn.mave.digital/storage/podcasts/episodes/69.mp3",
                    durationSeconds = 4850,
                    publishedAt = 1000L,
                    publishedAtString = "01 Nov 2026"
                )
            )
        )

        val matched = resolver.matchEpisodeInFeed(
            parsedFeed = feed,
            pageUrl = "https://thinkon.mave.digital/ep-69",
            pageTitle = "Мне уже надо на спичечный завод"
        )
        assertNotNull(matched)
        assertEquals("Мне уже надо на спичечный завод", matched?.title)
        assertEquals("https://cdn.mave.digital/storage/podcasts/episodes/69.mp3", matched?.audioUrl)
        assertEquals(4850L, matched?.durationSeconds)
    }

    @Test
    fun findRssFeedUrlInHtml_extractsMaveCloudFeedUrl_fromEscapedNuxtPayload() {
        val html = """
            <html>
            <head><title>Думаем дальше</title></head>
            <body>
              <script id="__NUXT_DATA__">["https:\/\/cloud.mave.digital\/51724"]</script>
            </body>
            </html>
        """.trimIndent()

        val feedUrl = resolver.findRssFeedUrlInHtml(html, "https://thinkon.mave.digital/ep-69")
        assertEquals("https://cloud.mave.digital/51724", feedUrl)
    }
}
