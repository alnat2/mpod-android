package com.example.mpod.data.rss

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpmlParserTest {

    private val parser = OpmlParser()

    @Test
    fun parseOpmlExtractsOutlines() {
        val opml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <opml version="2.0">
              <head><title>My Subscriptions</title></head>
              <body>
                <outline text="Decoder Ring" title="Decoder Ring" type="rss" xmlUrl="https://example.com/feed1.xml" htmlUrl="https://example.com/1" />
                <outline text="Rude Emails" title="Rude Emails" type="rss" xmlUrl="https://example.com/feed2.xml" />
              </body>
            </opml>
        """.trimIndent()

        val items = parser.parse(opml.byteInputStream())
        assertEquals(2, items.size)
        assertEquals("Decoder Ring", items[0].title)
        assertEquals("https://example.com/feed1.xml", items[0].feedUrl)
        assertEquals("Rude Emails", items[1].title)
        assertEquals("https://example.com/feed2.xml", items[1].feedUrl)
    }

    @Test
    fun generateOpmlProducesValidStructure() {
        val feeds = listOf(
            OpmlFeedItem(title = "Decoder Ring", feedUrl = "https://example.com/feed1.xml", websiteUrl = "https://example.com"),
            OpmlFeedItem(title = "Rude Emails", feedUrl = "https://example.com/feed2.xml", websiteUrl = null)
        )

        val xml = parser.generate(feeds)
        assertTrue(xml.contains("<opml version=\"2.0\">"))
        assertTrue(xml.contains("xmlUrl=\"https://example.com/feed1.xml\""))
        assertTrue(xml.contains("xmlUrl=\"https://example.com/feed2.xml\""))

        // Roundtrip parse
        val parsed = parser.parse(xml.byteInputStream())
        assertEquals(2, parsed.size)
        assertEquals("Decoder Ring", parsed[0].title)
        assertEquals("https://example.com/feed1.xml", parsed[0].feedUrl)
    }
}
