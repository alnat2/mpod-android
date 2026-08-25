package com.example.mpod.data.rss

import com.example.mpod.data.local.entity.PodcastEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpmlParserTest {

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

        val items = OpmlParser.parse(opml.byteInputStream())
        assertEquals(2, items.size)
        assertEquals("Decoder Ring", items[0].title)
        assertEquals("https://example.com/feed1.xml", items[0].xmlUrl)
        assertEquals("Rude Emails", items[1].title)
        assertEquals("https://example.com/feed2.xml", items[1].xmlUrl)
    }

    @Test
    fun generateOpmlProducesValidStructure() {
        val podcasts = listOf(
            PodcastEntity(
                id = 1L,
                feedUrl = "https://example.com/feed1.xml",
                title = "Decoder Ring",
                description = "Design stories",
                author = "Slate",
                artworkUrl = "https://example.com/art.jpg",
                link = "https://example.com"
            ),
            PodcastEntity(
                id = 2L,
                feedUrl = "https://example.com/feed2.xml",
                title = "Rude Emails",
                description = "Work stories",
                author = "Work",
                artworkUrl = "",
                link = ""
            )
        )

        val xml = OpmlParser.generateOpml(podcasts)
        assertTrue(xml.contains("<opml version=\"2.0\">"))
        assertTrue(xml.contains("xmlUrl=\"https://example.com/feed1.xml\""))
        assertTrue(xml.contains("xmlUrl=\"https://example.com/feed2.xml\""))

        val parsed = OpmlParser.parse(xml.byteInputStream())
        assertEquals(2, parsed.size)
        assertEquals("Decoder Ring", parsed[0].title)
        assertEquals("https://example.com/feed1.xml", parsed[0].xmlUrl)
    }
}
