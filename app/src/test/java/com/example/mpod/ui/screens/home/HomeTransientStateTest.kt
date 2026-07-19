package com.example.mpod.ui.screens.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeTransientStateTest {
    @Test
    fun lifecycleReloadPreservesInFlightEpisodeGuards() {
        val current = HomeUiState(
            busyEpisodeIds = setOf(7),
            downloadingEpisodeIds = setOf(8),
            downloadFailure = DownloadFailureUi(9, "Download failed")
        )

        val reloaded = HomeUiState(queue = listOf(episode(7)))
            .withTransientStateFrom(current)

        assertEquals(setOf(7), reloaded.busyEpisodeIds)
        assertEquals(setOf(8), reloaded.downloadingEpisodeIds)
        assertEquals(current.downloadFailure, reloaded.downloadFailure)
    }

    @Test
    fun completedActionsClearOnlyTheirOwnTransientIds() {
        val current = HomeUiState(
            busyEpisodeIds = setOf(7, 10),
            downloadingEpisodeIds = setOf(8, 11),
            downloadFailure = DownloadFailureUi(9, "Download failed")
        )

        val reloaded = HomeUiState().withTransientStateFrom(
            current = current,
            completedDownloadEpisodeId = 8,
            completedBusyEpisodeId = 7
        )

        assertEquals(setOf(10), reloaded.busyEpisodeIds)
        assertEquals(setOf(11), reloaded.downloadingEpisodeIds)
        assertEquals(current.downloadFailure, reloaded.downloadFailure)
    }

    @Test
    fun noDownloadedFailureIsInventedDuringReload() {
        val reloaded = HomeUiState().withTransientStateFrom(HomeUiState())

        assertNull(reloaded.downloadFailure)
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
