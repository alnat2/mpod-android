package com.example.mpod.ui.screens.subscriptions

import com.example.mpod.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class SubscriptionVisibilityTest {
    @Test
    fun visibilityModeUsesMatchingViewIcon() {
        assertEquals(R.drawable.ic_huge_view, visibilityIconRes(SubscriptionVisibility.Unlistened))
        assertEquals(R.drawable.ic_huge_view_off, visibilityIconRes(SubscriptionVisibility.All))
    }

    @Test
    fun headerSubtitleCountsPodcastsWithUnlistenedEpisodes() {
        val podcasts = listOf(
            podcast(id = 1L, episodes = listOf(episode(id = 1L, isListened = false))),
            podcast(id = 2L, episodes = listOf(episode(id = 2L, isListened = true))),
            podcast(
                id = 3L,
                episodes = listOf(
                    episode(id = 3L, isListened = false),
                    episode(id = 4L, isListened = false)
                )
            )
        )

        assertEquals(
            "3 podcasts · 2 unlistened",
            subscriptionsHeaderSubtitle(podcasts)
        )
    }

    @Test
    fun unlistenedVisibilityHidesCaughtUpPodcastsAndListenedEpisodes() {
        val activePodcast = podcast(
            id = 1L,
            episodes = listOf(
                episode(id = 1L, isListened = true),
                episode(id = 2L, isListened = false)
            )
        )
        val caughtUpPodcast = podcast(
            id = 2L,
            episodes = listOf(episode(id = 3L, isListened = true))
        )

        val visible = listOf(activePodcast, caughtUpPodcast)
            .visibleFor(SubscriptionVisibility.Unlistened)

        assertEquals(listOf(1L), visible.map { it.id })
        assertEquals(listOf(2L), visible.single().episodes.map { it.id })
        assertEquals(2, visible.single().totalEpisodeCount)
        assertEquals(1, visible.single().unlistenedEpisodeCount)
    }

    @Test
    fun allVisibilityKeepsEveryPodcastAndEveryEpisode() {
        val podcasts = listOf(
            podcast(id = 1L, episodes = (1L..25L).map { episode(it, isListened = it <= 20L) }),
            podcast(id = 2L, episodes = listOf(episode(id = 26L, isListened = true)))
        )

        val visible = podcasts.visibleFor(SubscriptionVisibility.All)

        assertSame(podcasts, visible)
        assertEquals(25, visible.first().episodes.size)
        assertEquals(listOf(1L, 2L), visible.map { it.id })
    }

    @Test
    fun unlistenedVisibilityReturnsEmptyForAllCaughtUpLibrary() {
        val podcasts = listOf(
            podcast(id = 1L, episodes = listOf(episode(id = 1L, isListened = true)))
        )

        assertEquals(
            emptyList<SubscriptionPodcastUi>(),
            podcasts.visibleFor(SubscriptionVisibility.Unlistened)
        )
    }

    @Test
    fun unlistenedVisibilityKeepsPodcastWhoseEpisodesFailedToLoad() {
        val failedPodcast = podcast(id = 1L, episodes = emptyList()).copy(
            errorMessage = "Episodes unavailable.",
            episodesUnavailable = true
        )

        assertEquals(
            listOf(failedPodcast),
            listOf(failedPodcast).visibleFor(SubscriptionVisibility.Unlistened)
        )
    }

    @Test
    fun carouselIndexHandlesStalePagesAfterVisibilityChanges() {
        assertEquals(0, podcastIndexForCarouselPage(page = 12, podcastCount = 2))
        assertEquals(1, podcastIndexForCarouselPage(page = 0, podcastCount = 2))
        assertEquals(0, podcastIndexForCarouselPage(page = 1, podcastCount = 2))
        assertEquals(0, podcastIndexForCarouselPage(page = 0, podcastCount = 0))
    }

    private fun podcast(
        id: Long,
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

    private fun episode(id: Long, isListened: Boolean): SubscriptionEpisodeUi {
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
