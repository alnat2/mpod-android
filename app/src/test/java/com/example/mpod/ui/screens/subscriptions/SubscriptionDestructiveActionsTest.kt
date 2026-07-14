package com.example.mpod.ui.screens.subscriptions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionDestructiveActionsTest {
    @Test
    fun unsubscribeCountdownIncludesEverySecondFromFifteenToOne() {
        assertEquals((15 downTo 1).toList(), unsubscribeCountdownSeconds())
    }

    @Test
    fun markAllListenedOptimisticallyUpdatesOnlyUnlistenedEpisodesInTargetPodcast() {
        val listenedEpisode = episode(id = 1, isListened = true, downloaded = false, inPlaylist = false)
        val unlistenedEpisode = episode(id = 2, isListened = false, downloaded = true, inPlaylist = true)
        val otherPodcast = podcast(id = 2, episodes = listOf(episode(id = 3)))

        val result = listOf(
            podcast(id = 1, episodes = listOf(listenedEpisode, unlistenedEpisode)),
            otherPodcast
        ).markAllListenedOptimistically(podcastId = 1)

        val updated = result.first()
        assertEquals(0, updated.unlistenedEpisodeCount)
        assertEquals(listenedEpisode, updated.episodes.first())
        assertTrue(updated.episodes[1].isListened)
        assertFalse(updated.episodes[1].downloaded)
        assertFalse(updated.episodes[1].inPlaylist)
        assertEquals(otherPodcast, result[1])
    }

    @Test
    fun markAllListenedLeavesUnknownPodcastListUnchanged() {
        val podcasts = listOf(podcast(id = 1, episodes = listOf(episode(id = 1))))

        assertEquals(podcasts, podcasts.markAllListenedOptimistically(podcastId = 999))
    }

    private fun podcast(
        id: Int,
        episodes: List<SubscriptionEpisodeUi>
    ): SubscriptionPodcastUi {
        return SubscriptionPodcastUi(
            id = id,
            title = "Podcast $id",
            description = "Description",
            imageUrl = null,
            totalEpisodeCount = episodes.size,
            unlistenedEpisodeCount = episodes.count { !it.isListened },
            episodes = episodes
        )
    }

    private fun episode(
        id: Int,
        isListened: Boolean = false,
        downloaded: Boolean = false,
        inPlaylist: Boolean = false
    ): SubscriptionEpisodeUi {
        return SubscriptionEpisodeUi(
            id = id,
            title = "Episode $id",
            durationSeconds = 60,
            publishedAt = null,
            isListened = isListened,
            downloaded = downloaded,
            summary = null,
            inPlaylist = inPlaylist
        )
    }
}
