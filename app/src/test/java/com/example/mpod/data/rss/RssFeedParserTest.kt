package com.example.mpod.data.rss

import org.junit.Assert.assertEquals
import org.junit.Test

class RssFeedParserTest {

    @Test
    fun parseValidRssFeedWithEnclosures() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0" xmlns:itunes="http://www.itunes.com/dtds/podcast-1.0.dtd">
              <channel>
                <title>Decoder Ring</title>
                <description>Culture stories behind everyday design</description>
                <itunes:image href="https://example.com/art.jpg" />
                <item>
                  <title>Why store loyalty cards became a UX minefield</title>
                  <guid>guid-ep-1</guid>
                  <pubDate>Mon, 31 Mar 2026 12:00:00 GMT</pubDate>
                  <description>A story about loyalty cards</description>
                  <itunes:duration>3240</itunes:duration>
                  <enclosure url="https://example.com/ep1.mp3" length="45000000" type="audio/mpeg" />
                </item>
                <item>
                  <title>How public transit maps teach invisible habits</title>
                  <guid>guid-ep-2</guid>
                  <pubDate>Mon, 24 Mar 2026 12:00:00 GMT</pubDate>
                  <description>Transit maps look simple</description>
                  <itunes:duration>45:20</itunes:duration>
                  <enclosure url="https://example.com/ep2.mp3" length="35000000" type="audio/mpeg" />
                </item>
              </channel>
            </rss>
        """.trimIndent()

        val feed = RssFeedParser.parse(xml.byteInputStream())
        assertEquals("Decoder Ring", feed.title)
        assertEquals("Culture stories behind everyday design", feed.description)
        assertEquals("https://example.com/art.jpg", feed.artworkUrl)
        assertEquals(2, feed.episodes.size)

        val ep1 = feed.episodes[0]
        assertEquals("Why store loyalty cards became a UX minefield", ep1.title)
        assertEquals("guid-ep-1", ep1.guid)
        assertEquals("https://example.com/ep1.mp3", ep1.audioUrl)
        assertEquals(3240L, ep1.durationSeconds)

        val ep2 = feed.episodes[1]
        assertEquals("How public transit maps teach invisible habits", ep2.title)
        assertEquals("guid-ep-2", ep2.guid)
        assertEquals("https://example.com/ep2.mp3", ep2.audioUrl)
        assertEquals(45 * 60L + 20L, ep2.durationSeconds)
    }

    @Test
    fun parseRssWithMissingFieldsDefaultsGracefully() {
        val xml = """
            <rss version="2.0">
              <channel>
                <title></title>
                <item>
                  <enclosure url="https://example.com/audio.mp3" />
                </item>
              </channel>
            </rss>
        """.trimIndent()

        val feed = RssFeedParser.parse(xml.byteInputStream())
        assertEquals("", feed.title)
        assertEquals(1, feed.episodes.size)
        assertEquals("https://example.com/audio.mp3", feed.episodes[0].audioUrl)
        assertEquals("https://example.com/audio.mp3", feed.episodes[0].guid)
    }

    @Test
    fun parseRssWithCustomNamespacePrefixes() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0"
                 xmlns:apple="http://www.itunes.com/dtds/podcast-1.0.dtd"
                 xmlns:c="http://purl.org/rss/1.0/modules/content/"
                 xmlns:dublin="http://purl.org/dc/elements/1.1/"
                 xmlns:fake="http://fake.com"
                 xmlns:itunes="http://wrong-itunes.com">
              <channel>
                <title>Namespace Ring</title>
                <dublin:creator>Alias Author</dublin:creator>
                <apple:image href="https://example.com/alias.jpg" />
                <!-- itunes prefix but wrong namespace, should be ignored or treated as unknown -->
                <itunes:summary>Wrong Summary</itunes:summary>
                <apple:summary>Correct Summary</apple:summary>
                <item>
                  <title>Episode with aliases</title>
                  <guid>guid-ep-alias</guid>
                  <pubDate>Mon, 31 Mar 2026 12:00:00 GMT</pubDate>
                  <c:encoded>Correct encoded description</c:encoded>
                  <apple:duration>1000</apple:duration>
                  <!-- Should not use duration from wrong namespace -->
                  <itunes:duration>9999</itunes:duration>
                  <enclosure url="https://example.com/alias.mp3" length="45000000" type="audio/mpeg" />
                </item>
              </channel>
            </rss>
        """.trimIndent()

        val feed = RssFeedParser.parse(xml.byteInputStream())

        assertEquals("Namespace Ring", feed.title)
        assertEquals("Correct Summary", feed.description)
        assertEquals("Alias Author", feed.author)
        assertEquals("https://example.com/alias.jpg", feed.artworkUrl)
        assertEquals(1, feed.episodes.size)

        val ep1 = feed.episodes[0]
        assertEquals("Episode with aliases", ep1.title)
        assertEquals("guid-ep-alias", ep1.guid)
        assertEquals("Correct encoded description", ep1.description)
        assertEquals(1000L, ep1.durationSeconds)
        assertEquals("https://example.com/alias.mp3", ep1.audioUrl)
    }

    @Test
    fun parseRssWithCdataAndHtmlEntitiesInDescription() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0">
              <channel>
                <title>Test CDATA Feed</title>
                <description>Feed &amp; Description</description>
                <item>
                  <title><![CDATA[Episode & Title with CDATA]]></title>
                  <guid>ep-cdata-1</guid>
                  <description>Start text <![CDATA[Inside CDATA]]> &amp; end text</description>
                  <enclosure url="https://example.com/audio.mp3" />
                </item>
              </channel>
            </rss>
        """.trimIndent()

        val feed = RssFeedParser.parse(xml.byteInputStream())
        assertEquals("Test CDATA Feed", feed.title)
        assertEquals("Feed & Description", feed.description)
        val ep = feed.episodes.single()
        assertEquals("Episode & Title with CDATA", ep.title)
        assertEquals("Start text Inside CDATA & end text", ep.description)
    }

    @Test(expected = IllegalArgumentException::class)
    fun parseHtmlWebpageThrowsClearException() {
        val html = """
            <!DOCTYPE html>
            <html>
              <head><title>Cloudflare DDoS protection</title></head>
              <body>Just a moment...</body>
            </html>
        """.trimIndent()

        RssFeedParser.parse(html.byteInputStream())
    }

    @Test
    fun parseValidAtomFeedWithEnclosures() {
        val xml = """
            <?xml version="1.0" encoding="utf-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom" xmlns:itunes="http://www.itunes.com/dtds/podcast-1.0.dtd">
              <title>The Changelog</title>
              <subtitle>Software's best podcast</subtitle>
              <link rel="alternate" type="text/html" href="https://changelog.com" />
              <updated>2026-03-31T15:30:00Z</updated>
              <author>
                <name>Jerod &amp; Adam</name>
                <email>editors@changelog.com</email>
              </author>
              <logo>https://changelog.com/logo.png</logo>
              <entry>
                <title>Interview with AI Creators</title>
                <id>tag:changelog.com,2026:podcast/500</id>
                <published>2026-03-31T12:00:00Z</published>
                <updated>2026-03-31T14:00:00Z</updated>
                <summary>Deep dive into modern autonomous coding agents.</summary>
                <itunes:duration>01:15:30</itunes:duration>
                <link rel="enclosure" type="audio/mpeg" href="https://changelog.com/mp3/500.mp3" length="50000000" />
              </entry>
              <entry>
                <title>Bonus Episode</title>
                <id>tag:changelog.com,2026:podcast/bonus</id>
                <updated>2026-03-25T10:00:00.500Z</updated>
                <content type="text">Bonus discussion after the show.</content>
                <itunes:duration>900</itunes:duration>
                <link rel="alternate" type="text/html" href="https://changelog.com/bonus" />
                <link type="audio/aac" href="https://changelog.com/audio/bonus.m4a" />
              </entry>
            </feed>
        """.trimIndent()

        val feed = RssFeedParser.parse(xml.byteInputStream())
        assertEquals("The Changelog", feed.title)
        assertEquals("Software's best podcast", feed.description)
        assertEquals("Jerod & Adam", feed.author)
        assertEquals("https://changelog.com", feed.link)
        assertEquals("https://changelog.com/logo.png", feed.artworkUrl)
        assertEquals("2026-03-31T15:30:00Z", feed.lastBuildDate)
        assertEquals(2, feed.episodes.size)

        val ep1 = feed.episodes[0]
        assertEquals("Interview with AI Creators", ep1.title)
        assertEquals("tag:changelog.com,2026:podcast/500", ep1.guid)
        assertEquals("Deep dive into modern autonomous coding agents.", ep1.description)
        assertEquals("https://changelog.com/mp3/500.mp3", ep1.audioUrl)
        assertEquals(4530L, ep1.durationSeconds)
        assertEquals("2026-03-31T12:00:00Z", ep1.publishedAtString)
        assertEquals(RssFeedParser.parseDate("2026-03-31T12:00:00Z"), ep1.publishedAt)

        val ep2 = feed.episodes[1]
        assertEquals("Bonus Episode", ep2.title)
        assertEquals("tag:changelog.com,2026:podcast/bonus", ep2.guid)
        assertEquals("Bonus discussion after the show.", ep2.description)
        assertEquals("https://changelog.com/audio/bonus.m4a", ep2.audioUrl)
        assertEquals(900L, ep2.durationSeconds)
        assertEquals("2026-03-25T10:00:00.500Z", ep2.publishedAtString)
        assertEquals(RssFeedParser.parseDate("2026-03-25T10:00:00.500Z"), ep2.publishedAt)
    }

    @Test
    fun parseAtomFeedWithDirectAuthorTextAndItunesImage() {
        val xml = """
            <feed xmlns:itunes="http://www.itunes.com/dtds/podcast-1.0.dtd">
              <title>Simple Atom Pod</title>
              <author>Sole Creator</author>
              <itunes:image href="https://example.com/atom-art.jpg" />
              <entry>
                <title>Ep 1</title>
                <enclosure url="https://example.com/ep1.mp3" />
              </entry>
            </feed>
        """.trimIndent()

        val feed = RssFeedParser.parse(xml)
        assertEquals("Simple Atom Pod", feed.title)
        assertEquals("Sole Creator", feed.author)
        assertEquals("https://example.com/atom-art.jpg", feed.artworkUrl)
        assertEquals(1, feed.episodes.size)
        assertEquals("https://example.com/ep1.mp3", feed.episodes[0].audioUrl)
    }

    @Test
    fun parseAtomFeedWithDefaultNamespaceAndEnclosureTag() {
        val xml = """
            <feed xmlns="http://www.w3.org/2005/Atom">
              <title>Namespace Enclosure Pod</title>
              <entry>
                <id>ep-1</id>
                <title>Ep 1</title>
                <!-- enclosure element inside default Atom namespace -->
                <enclosure url="https://example.com/audio.mp3?token=secret123" />
              </entry>
            </feed>
        """.trimIndent()

        val feed = RssFeedParser.parse(xml)
        assertEquals("https://example.com/audio.mp3?token=secret123", feed.episodes.single().audioUrl)
    }

    @Test
    fun parseAtomFeedPrioritizesExplicitEnclosureOverSecondaryAudioLink() {
        val xml = """
            <feed xmlns="http://www.w3.org/2005/Atom">
              <title>Multi Link Pod</title>
              <entry>
                <id>ep-multi</id>
                <title>Multi Link Episode</title>
                <link rel="enclosure" type="audio/mpeg" href="https://example.com/podcast.mp3?cdn=fast" />
                <link rel="alternate" type="audio/x-custom" href="https://example.com/web-player" />
              </entry>
            </feed>
        """.trimIndent()

        val feed = RssFeedParser.parse(xml)
        assertEquals("https://example.com/podcast.mp3?cdn=fast", feed.episodes.single().audioUrl)
    }

    @Test(expected = IllegalArgumentException::class)
    fun parseUnknownRootElementThrowsException() {
        val xml = "<unknown><title>Unknown</title></unknown>"
        RssFeedParser.parse(xml)
    }
}
