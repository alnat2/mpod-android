package com.example.mpod.ui.screens.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaylistReorderTest {
    @Test
    fun movesEpisodeWithinQueueBounds() {
        val reordered = reorderEpisodes(
            episodes = episodes(1, 2, 3, 4),
            episodeId = 2,
            offset = 2
        )

        assertEquals(listOf(1, 3, 4, 2), reordered?.map { it.id })
    }

    @Test
    fun clampsMoveToQueueEdges() {
        val reordered = reorderEpisodes(
            episodes = episodes(1, 2, 3),
            episodeId = 3,
            offset = -99
        )

        assertEquals(listOf(3, 1, 2), reordered?.map { it.id })
    }

    @Test
    fun returnsNullWhenMoveWouldNotChangeQueue() {
        assertNull(reorderEpisodes(episodes(1, 2, 3), episodeId = 1, offset = -1))
        assertNull(reorderEpisodes(episodes(1, 2, 3), episodeId = 99, offset = 1))
        assertNull(reorderEpisodes(episodes(1, 2, 3), episodeId = 2, offset = 0))
    }

    private fun episodes(vararg ids: Int): List<HomeEpisodeUi> {
        return ids.map { id ->
            HomeEpisodeUi(
                id = id,
                title = "Episode $id",
                podcastTitle = "Podcast",
                durationSeconds = 60,
                playbackPositionSeconds = 0,
                isListened = false,
                downloaded = false,
                summary = null
            )
        }
    }
}
