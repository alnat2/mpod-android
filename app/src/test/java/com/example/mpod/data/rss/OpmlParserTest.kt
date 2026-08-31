package com.example.mpod.data.rss

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpmlParserTest {

    @Test
    fun parseValidOpml() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <opml version="2.0">
              <head><title>Test</title></head>
              <body>
                <outline text="feeds">
                  <outline type="rss" text="Podcast A" xmlUrl="https://example.com/feed1.xml" />
                  <outline type="rss" text="Podcast B" xmlUrl="https://example.com/feed2.xml" />
                </outline>
              </body>
            </opml>
        """.trimIndent()
        val items = OpmlParser.parse(xml)
        assertEquals(2, items.size)
        assertEquals("Podcast A", items[0].title)
        assertEquals("https://example.com/feed1.xml", items[0].xmlUrl)
        assertEquals("Podcast B", items[1].title)
        assertEquals("https://example.com/feed2.xml", items[1].xmlUrl)
    }

    @Test
    fun parseEmptyOpml() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <opml version="2.0">
              <head><title>Empty</title></head>
              <body></body>
            </opml>
        """.trimIndent()
        val items = OpmlParser.parse(xml)
        assertTrue(items.isEmpty())
    }

    @Test
    fun parseOpmlWithNoXmlUrl() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <opml version="2.0">
              <head><title>Test</title></head>
              <body>
                <outline text="No URL" />
                <outline text="Has URL" xmlUrl="https://example.com/feed.xml" />
              </body>
            </opml>
        """.trimIndent()
        val items = OpmlParser.parse(xml)
        assertEquals(1, items.size)
        assertEquals("Has URL", items[0].title)
    }

    @Test
    fun parseOpmlWithUrlAttribute() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <opml version="2.0">
              <head><title>Test</title></head>
              <body>
                <outline text="Alt URL" url="https://example.com/feed.xml" />
              </body>
            </opml>
        """.trimIndent()
        val items = OpmlParser.parse(xml)
        assertEquals(1, items.size)
        assertEquals("https://example.com/feed.xml", items[0].xmlUrl)
    }

    @Test
    fun parseOpmlUsesTitleAttributeFallback() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <opml version="2.0">
              <head><title>Test</title></head>
              <body>
                <outline text="Text Name" title="Title Name" xmlUrl="https://example.com/feed.xml" />
              </body>
            </opml>
        """.trimIndent()
        val items = OpmlParser.parse(xml)
        assertEquals(1, items.size)
        assertEquals("Title Name", items[0].title)
    }

    @Test
    fun parseOpmlFallsBackToUrlAsTitle() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <opml version="2.0">
              <head><title>Test</title></head>
              <body>
                <outline xmlUrl="https://example.com/feed.xml" />
              </body>
            </opml>
        """.trimIndent()
        val items = OpmlParser.parse(xml)
        assertEquals(1, items.size)
        assertEquals("https://example.com/feed.xml", items[0].title)
    }

    @Test
    fun generateOpmlContainsAllPodcasts() {
        val podcasts = listOf(
            com.example.mpod.data.local.entity.PodcastEntity(
                feedUrl = "https://example.com/feed1.xml",
                title = "Podcast A",
                description = "Desc A",
                author = "Author A",
                artworkUrl = "",
                link = "https://example.com/a",
                lastBuildDate = "",
                lastRefreshedAt = 0L
            ),
            com.example.mpod.data.local.entity.PodcastEntity(
                feedUrl = "https://example.com/feed2.xml",
                title = "Podcast B",
                description = "Desc B",
                author = "Author B",
                artworkUrl = "",
                link = "",
                lastBuildDate = "",
                lastRefreshedAt = 0L
            )
        )
        val opml = OpmlParser.generateOpml(podcasts)
        assertTrue(opml.contains("Podcast A"))
        assertTrue(opml.contains("Podcast B"))
        assertTrue(opml.contains("https://example.com/feed1.xml"))
        assertTrue(opml.contains("https://example.com/feed2.xml"))
    }

    @Test
    fun generateOpmlEscapesXmlSpecialChars() {
        val podcasts = listOf(
            com.example.mpod.data.local.entity.PodcastEntity(
                feedUrl = "https://example.com/feed.xml",
                title = "A & B <C> \"D\"",
                description = "",
                author = "",
                artworkUrl = "",
                link = "",
                lastBuildDate = "",
                lastRefreshedAt = 0L
            )
        )
        val opml = OpmlParser.generateOpml(podcasts)
        assertTrue(opml.contains("&amp;"))
        assertTrue(opml.contains("&lt;"))
        assertTrue(opml.contains("&gt;"))
        assertTrue(opml.contains("&quot;"))
    }
}
