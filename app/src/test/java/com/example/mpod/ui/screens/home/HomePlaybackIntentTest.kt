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

    @Test
    fun `audiobook chapter mediaItem populates chapter details in playback state`() {
        val mockPlayer = org.mockito.Mockito.mock(androidx.media3.common.Player::class.java)
        val extras = org.mockito.Mockito.mock(android.os.Bundle::class.java)
        org.mockito.Mockito.`when`(extras.getInt(com.example.mpod.playback.PlaybackTrack.EXTRA_CHAPTER_INDEX)).thenReturn(2)
        org.mockito.Mockito.`when`(extras.getInt(com.example.mpod.playback.PlaybackTrack.EXTRA_TOTAL_CHAPTERS)).thenReturn(5)

        val mediaMetadata = androidx.media3.common.MediaMetadata.Builder()
            .setTitle("02 - Chapter Two")
            .setArtist("Author Name")
            .setExtras(extras)
            .build()
        val mediaItem = androidx.media3.common.MediaItem.Builder()
            .setMediaId("audiobook:5:202")
            .setMediaMetadata(mediaMetadata)
            .build()

        org.mockito.Mockito.`when`(mockPlayer.currentMediaItem).thenReturn(mediaItem)
        org.mockito.Mockito.`when`(mockPlayer.currentPosition).thenReturn(15000L)
        org.mockito.Mockito.`when`(mockPlayer.duration).thenReturn(60000L)
        org.mockito.Mockito.`when`(mockPlayer.playWhenReady).thenReturn(true)

        val queue = listOf(
            HomeEpisodeUi(
                id = -5L,
                title = "Book Title",
                podcastTitle = "01 - Chapter One",
                durationSeconds = 60,
                playbackPositionSeconds = 0,
                isListened = false,
                downloaded = true,
                summary = null,
                isAudiobook = true,
                audiobookId = 5L,
                currentTrackId = 201L
            )
        )

        val state = mockPlayer.toHomePlaybackUiState(activeEpisodeId = null, queue = queue)
        org.junit.Assert.assertEquals(-5L, state.currentEpisodeId)
        org.junit.Assert.assertEquals(202L, state.currentTrackId)
        org.junit.Assert.assertEquals("02 - Chapter Two", state.currentChapterTitle)
        org.junit.Assert.assertEquals(2, state.currentChapterIndex)
        org.junit.Assert.assertEquals(5, state.totalChapters)
        org.junit.Assert.assertEquals(15, state.positionSeconds)
        org.junit.Assert.assertEquals(60, state.durationSeconds)
        org.junit.Assert.assertTrue(state.isPlaying)
    }
}
