package com.example.mpod.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SharedIntentParserTest {

    @Test
    fun extractUrl_directHttpUrl_returnsUrl() {
        val url = "https://example.com/podcast/episode-34.mp3"
        assertEquals(url, SharedIntentParser.extractUrl(url))
    }

    @Test
    fun extractUrl_textWithSurroundingText_extractsCleanUrl() {
        val text = "Hey check out this episode: https://cdn.podcasts.org/ep1.mp3! It was great."
        assertEquals("https://cdn.podcasts.org/ep1.mp3", SharedIntentParser.extractUrl(text))
    }

    @Test
    fun extractUrl_urlWithTrailingPunctuation_trimsPunctuation() {
        val text = "Listen here: https://cdn.podcasts.org/ep1.mp3."
        assertEquals("https://cdn.podcasts.org/ep1.mp3", SharedIntentParser.extractUrl(text))
    }

    @Test
    fun extractUrl_noUrlInText_returnsNull() {
        val text = "Just some random text without any links"
        assertNull(SharedIntentParser.extractUrl(text))
    }
}
