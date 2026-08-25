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
}
