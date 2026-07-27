package com.example.mpod.ui.screens.subscriptions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PodcastArtworkUrlTest {
    @Test
    fun externalArtworkIsLoadedThroughAuthenticatedBackendEndpoint() {
        assertEquals(
            "http://192.168.0.222:5050/api/podcasts/7/image",
            podcastArtworkUrl(
                backendBaseUrl = "http://192.168.0.222:5050/",
                podcastId = 7,
                sourceImageUrl = "https://i1.sndcdn.com/cover.jpg"
            )
        )
    }

    @Test
    fun missingArtworkKeepsFallback() {
        assertNull(
            podcastArtworkUrl(
                backendBaseUrl = "http://192.168.0.222:5050/",
                podcastId = 7,
                sourceImageUrl = null
            )
        )
    }
}
