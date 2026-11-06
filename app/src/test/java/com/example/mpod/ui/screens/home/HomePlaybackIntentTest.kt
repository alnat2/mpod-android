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

    @Test
    fun `player with source error retains database playback position and duration`() {
        val mockPlayer = org.mockito.Mockito.mock(androidx.media3.common.Player::class.java)
        val mockError = androidx.media3.common.PlaybackException(
            "Source error",
            null,
            androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
        )
        org.mockito.Mockito.`when`(mockPlayer.playerError).thenReturn(mockError)
        org.mockito.Mockito.`when`(mockPlayer.currentPosition).thenReturn(0L)
        org.mockito.Mockito.`when`(mockPlayer.duration).thenReturn(androidx.media3.common.C.TIME_UNSET)

        val queue = listOf(
            HomeEpisodeUi(
                id = 42L,
                title = "Test Episode",
                podcastTitle = "Test Podcast",
                durationSeconds = 1800,
                playbackPositionSeconds = 620,
                isListened = false,
                downloaded = false,
                summary = null
            )
        )

        val state = mockPlayer.toHomePlaybackUiState(activeEpisodeId = 42L, queue = queue)
        org.junit.Assert.assertEquals(620, state.positionSeconds)
        org.junit.Assert.assertEquals(1800, state.durationSeconds)
        org.junit.Assert.assertNotNull(state.errorMessage)
    }
}
