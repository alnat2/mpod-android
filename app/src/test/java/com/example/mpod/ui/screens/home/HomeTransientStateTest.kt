package com.example.mpod.ui.screens.home

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeTransientStateTest {
    @Test
    fun lifecycleReloadPreservesInFlightEpisodeGuards() {
        val current = HomeUiState(
            busyEpisodeIds = setOf(7)
        )

        val reloaded = HomeUiState(queue = listOf(episode(7)))
            .withTransientStateFrom(current)

        assertEquals(setOf(7), reloaded.busyEpisodeIds)
    }

    @Test
    fun completedActionsClearOnlyTheirOwnBusyIds() {
        val current = HomeUiState(
            busyEpisodeIds = setOf(7, 10)
        )

        val reloaded = HomeUiState().withTransientStateFrom(
            current = current,
            completedBusyEpisodeId = 7
        )

        assertEquals(setOf(10), reloaded.busyEpisodeIds)
    }

    private fun episode(id: Int) = HomeEpisodeUi(
        id = id,
        title = "Episode",
        podcastTitle = "Podcast",
        durationSeconds = 60,
        playbackPositionSeconds = 0,
        isListened = false,
        downloaded = false,
        summary = null
    )
}
