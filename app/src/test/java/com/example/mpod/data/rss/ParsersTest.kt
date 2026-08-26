package com.example.mpod.data.rss

import com.example.mpod.data.local.entity.PodcastEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale

class ParsersTest {

    // ---- RssFeedParser.parseDuration ----
    @Test
    fun parseDuration_handlesHms() {
        assertEquals(5025L, RssFeedParser.parseDuration("01:23:45"))
    }

    @Test
    fun parseDuration_handlesMinutesSeconds() {
        assertEquals(754L, RssFeedParser.parseDuration("12:34"))
    }

    @Test
    fun parseDuration_handlesSecondsOnly() {
        assertEquals(3661L, RssFeedParser.parseDuration("3661"))
    }

    @Test
    fun parseDuration_returnsZeroForGarbage() {
        assertEquals(0L, RssFeedParser.parseDuration("not a duration"))
        assertEquals(0L, RssFeedParser.parseDuration(""))
    }

    // ---- RssFeedParser date parsing ----
    @Test
    fun parseDateOrNull_parsesRfc822() {
        val expected = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US)
            .parse("Wed, 02 Oct 2002 13:00:00 GMT")!!.time
        assertEquals(expected, RssFeedParser.parseDateOrNull("Wed, 02 Oct 2002 13:00:00 GMT"))
    }

    @Test
    fun parseDateOrNull_parsesIso8601() {
        val expected = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            .parse("2023-01-15T10:30:00Z")!!.time
        assertEquals(expected, RssFeedParser.parseDateOrNull("2023-01-15T10:30:00Z"))
    }

    @Test
    fun parseDateOrNull_returnsNullForGarbage() {
        assertNull(RssFeedParser.parseDateOrNull("clearly not a date"))
    }

    @Test
    fun parseDate_returnsZeroForUnparseable() {
        // Fix #4: unparseable dates must map to epoch (0), not "now".
        assertEquals(0L, RssFeedParser.parseDate("clearly not a date"))
    }

    // ---- RssFeedParser.parse (full feed) ----
    @Test
    fun parse_readsChannelAndEpisode() {
        val rss = """
            <rss version="2.0">
              <channel>
                <title>Test Pod</title>
                <description>desc</description>
                <lastBuildDate>Wed, 02 Oct 2002 13:00:00 GMT</lastBuildDate>
                <item>
                  <guid>g1</guid>
                  <title>Ep1</title>
                  <description>ed</description>
                  <enclosure url="http://x/ep.mp3" type="audio/mpeg"/>
                  <itunes:duration>01:00:00</itunes:duration>
                  <pubDate>Tue, 03 Oct 2002 14:00:00 GMT</pubDate>
                </item>
              </channel>
            </rss>
        """.trimIndent()

        val feed = RssFeedParser.parse(rss)
        assertEquals("Test Pod", feed.title)
        assertEquals(1, feed.episodes.size)
        val ep = feed.episodes.first()
        assertEquals("Ep1", ep.title)
        assertEquals("http://x/ep.mp3", ep.audioUrl)
        assertEquals(3600L, ep.durationSeconds)
        assertEquals("g1", ep.guid)
    }

    // ---- OpmlParser.generateOpml round-trip ----
    @Test
    fun generateOpml_thenParse_preservesEntries() {
        val podcasts = listOf(
            PodcastEntity(feedUrl = "https://a.com/feed", title = "Pod A"),
            PodcastEntity(feedUrl = "https://b.com/feed", title = "Pod B")
        )
        val opml = OpmlParser.generateOpml(podcasts)
        val items = OpmlParser.parse(opml)
        assertEquals(2, items.size)
        assertEquals("Pod A", items[0].title)
        assertEquals("https://a.com/feed", items[0].xmlUrl)
        assertEquals("Pod B", items[1].title)
    }

    // ---- OpmlParser.parse (hand-written OPML) ----
    @Test
    fun parse_readsOutlineEntries() {
        val opml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <opml version="2.0">
              <body>
                <outline text="Pod A" title="Pod A" type="rss" xmlUrl="https://a.com/feed"/>
                <outline text="Pod B" title="Pod B" type="rss" xmlUrl="https://b.com/feed" htmlUrl="https://b.com"/>
              </body>
            </opml>
        """.trimIndent()

        val items = OpmlParser.parse(opml)
        assertEquals(2, items.size)
        assertEquals("Pod A", items[0].title)
        assertEquals("https://a.com/feed", items[0].xmlUrl)
        assertEquals("https://b.com", items[1].htmlUrl)
    }

    @Test
    fun parse_ignoresOutlineWithoutXmlUrl() {
        val opml = """
            <opml version="2.0"><body>
              <outline text="No feed"/>
              <outline text="Pod A" xmlUrl="https://a.com/feed"/>
            </body></opml>
        """.trimIndent()
        val items = OpmlParser.parse(opml)
        assertEquals(1, items.size)
        assertTrue(items[0].xmlUrl.isNotBlank())
    }
}
