package com.example.mpod.ui.screens.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaylistReorderTest {
    @Test
    fun movesEpisodeWithinQueueBounds() {
        val reordered = reorderEpisodes(
            episodes = episodes(1L, 2L, 3L, 4L),
            episodeId = 2L,
            offset = 2
        )

        assertEquals(listOf(1L, 3L, 4L, 2L), reordered?.map { it.id })
    }

    @Test
    fun clampsMoveToQueueEdges() {
        val reordered = reorderEpisodes(
            episodes = episodes(1L, 2L, 3L),
            episodeId = 3L,
            offset = -99
        )

        assertEquals(listOf(3L, 1L, 2L), reordered?.map { it.id })
    }

    @Test
    fun returnsNullWhenMoveWouldNotChangeQueue() {
        assertNull(reorderEpisodes(episodes(1L, 2L, 3L), episodeId = 1L, offset = -1))
        assertNull(reorderEpisodes(episodes(1L, 2L, 3L), episodeId = 99L, offset = 1))
        assertNull(reorderEpisodes(episodes(1L, 2L, 3L), episodeId = 2L, offset = 0))
    }

    private fun episodes(vararg ids: Long): List<HomeEpisodeUi> {
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
