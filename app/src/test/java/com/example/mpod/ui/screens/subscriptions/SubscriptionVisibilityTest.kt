package com.example.mpod.ui.screens.subscriptions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class SubscriptionVisibilityTest {
    @Test
    fun unlistenedVisibilityHidesCaughtUpPodcastsAndListenedEpisodes() {
        val activePodcast = podcast(
            id = 1,
            episodes = listOf(
                episode(id = 1, isListened = true),
                episode(id = 2, isListened = false)
            )
        )
        val caughtUpPodcast = podcast(
            id = 2,
            episodes = listOf(episode(id = 3, isListened = true))
        )

        val visible = listOf(activePodcast, caughtUpPodcast)
            .visibleFor(SubscriptionVisibility.Unlistened)

        assertEquals(listOf(1), visible.map { it.id })
        assertEquals(listOf(2), visible.single().episodes.map { it.id })
        assertEquals(2, visible.single().totalEpisodeCount)
        assertEquals(1, visible.single().unlistenedEpisodeCount)
    }

    @Test
    fun allVisibilityKeepsEveryPodcastAndEveryEpisode() {
        val podcasts = listOf(
            podcast(id = 1, episodes = (1..25).map { episode(it, isListened = it <= 20) }),
            podcast(id = 2, episodes = listOf(episode(id = 26, isListened = true)))
        )

        val visible = podcasts.visibleFor(SubscriptionVisibility.All)

        assertSame(podcasts, visible)
        assertEquals(25, visible.first().episodes.size)
        assertEquals(listOf(1, 2), visible.map { it.id })
    }

    @Test
    fun unlistenedVisibilityReturnsEmptyForAllCaughtUpLibrary() {
        val podcasts = listOf(
            podcast(id = 1, episodes = listOf(episode(id = 1, isListened = true)))
        )

        assertEquals(
            emptyList<SubscriptionPodcastUi>(),
            podcasts.visibleFor(SubscriptionVisibility.Unlistened)
        )
    }

    @Test
    fun unlistenedVisibilityKeepsPodcastWhoseEpisodesFailedToLoad() {
        val failedPodcast = podcast(id = 1, episodes = emptyList()).copy(
            errorMessage = "Episodes unavailable.",
            episodesUnavailable = true
        )

        assertEquals(
            listOf(failedPodcast),
            listOf(failedPodcast).visibleFor(SubscriptionVisibility.Unlistened)
        )
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

    private fun episode(id: Int, isListened: Boolean): SubscriptionEpisodeUi {
        return SubscriptionEpisodeUi(
            id = id,
            title = "Episode $id",
            durationSeconds = null,
            publishedAt = null,
            isListened = isListened,
            downloaded = false,
            summary = null,
            inPlaylist = false
        )
    }
}
