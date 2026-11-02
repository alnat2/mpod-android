package com.example.mpod.ui.screens.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeScreenPlaybackPositionTest {

    private val queue = listOf(
        HomeEpisodeUi(
            id = 101L,
            title = "Episode 1",
            podcastTitle = "Podcast A",
            durationSeconds = 3600,
            playbackPositionSeconds = 0,
            isListened = false,
            downloaded = false
        ),
        HomeEpisodeUi(
            id = 102L,
            title = "rt podcast1036",
            podcastTitle = "Audio",
            durationSeconds = 9119,
            playbackPositionSeconds = 2700, // 45 minutes saved progress
            isListened = false,
            downloaded = false
        )
    )

    @Test
    fun selectingDifferentEpisodeInQueueSeeksToSavedPosition() {
        val seekPosMs = resolveEpisodeSeekPositionMs(
            episodeId = 102L,
            queue = queue,
            currentMediaItemIndex = 0,
            targetMediaItemIndex = 1,
            isPlayerIdle = false
        )

        assertEquals(2_700_000L, seekPosMs)
    }

    @Test
    fun selectingAlreadyActivePlayingEpisodeDoesNotSeekOrReset() {
        val seekPosMs = resolveEpisodeSeekPositionMs(
            episodeId = 102L,
            queue = queue,
            currentMediaItemIndex = 1,
            targetMediaItemIndex = 1,
            isPlayerIdle = false
        )

        assertNull("Should return null so player resumes without seekToDefaultPosition reset", seekPosMs)
    }

    @Test
    fun selectingEpisodeWhenPlayerIsIdleSeeksToSavedPosition() {
        val seekPosMs = resolveEpisodeSeekPositionMs(
            episodeId = 102L,
            queue = queue,
            currentMediaItemIndex = 1,
            targetMediaItemIndex = 1,
            isPlayerIdle = true
        )

        assertEquals(2_700_000L, seekPosMs)
    }

    @Test
    fun selectingNewEpisodeWithZeroProgressSeeksToZero() {
        val seekPosMs = resolveEpisodeSeekPositionMs(
            episodeId = 101L,
            queue = queue,
            currentMediaItemIndex = 1,
            targetMediaItemIndex = 0,
            isPlayerIdle = false
        )

        assertEquals(0L, seekPosMs)
    }
}
