package com.example.mpod.ui.screens.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomePlaybackIntentTest {

    @Test
    fun `buffering playback intent still exposes pause action`() {
        assertTrue(playbackIntentActive(playWhenReady = true))
    }

    @Test
    fun `paused playback intent exposes play action`() {
        assertFalse(playbackIntentActive(playWhenReady = false))
    }

    @Test
    fun `source error while playback requested exposes retry instead of pause`() {
        assertFalse(playbackIntentActive(playWhenReady = true, hasError = true))
    }

    @Test
    fun `source error after pause still exposes retry`() {
        assertFalse(playbackIntentActive(playWhenReady = false, hasError = true))
    }
}
