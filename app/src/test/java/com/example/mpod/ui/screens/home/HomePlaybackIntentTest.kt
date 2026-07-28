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
}
