package com.example.mpod.util

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

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

    @Test
    fun parse_contentUriSharedAsTextIsUnsupported() {
        val intent = mock(Intent::class.java)
        `when`(intent.action).thenReturn(Intent.ACTION_SEND)
        `when`(intent.type).thenReturn("text/plain")
        `when`(intent.getStringExtra(Intent.EXTRA_TEXT)).thenReturn("content://media/external/audio/1")

        assertNull(SharedIntentParser.parse(intent))
    }

    @Test
    fun parse_sendMultipleIsUnsupported() {
        val intent = mock(Intent::class.java)
        `when`(intent.action).thenReturn(Intent.ACTION_SEND_MULTIPLE)

        assertNull(SharedIntentParser.parse(intent))
        assertEquals(true, SharedIntentParser.isShareIntent(intent))
    }
}
