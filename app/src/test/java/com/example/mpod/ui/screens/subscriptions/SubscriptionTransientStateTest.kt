package com.example.mpod.ui.screens.subscriptions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionTransientStateTest {
    @Test
    fun backgroundReloadKeepsPendingRefreshAndMutationGuards() {
        val podcast = SubscriptionPodcastUi(
            id = 7,
            title = "Podcast",
            description = "Description",
            imageUrl = null,
            totalEpisodeCount = 1,
            unlistenedEpisodeCount = 1,
            episodes = listOf(
                SubscriptionEpisodeUi(
                    id = 11,
                    title = "Episode",
                    durationSeconds = 60,
                    publishedAt = null,
                    isListened = false,
                    downloaded = false,
                    summary = null,
                    inPlaylist = false
                )
            )
        )
        val current = SubscriptionsUiState(
            isRefreshingAll = true,
            refreshingPodcastIds = setOf(7),
            markingAllListenedPodcastIds = setOf(7),
            unsubscribingPodcastIds = setOf(7),
            busyEpisodeIds = setOf(11),
            failedUnsubscribePodcastId = 7,
            failedMarkAllListenedPodcastId = 7,
            failedEpisodeAction = FailedEpisodeActionUi(11, FailedEpisodeActionType.MarkListened),
            podcasts = listOf(podcast)
        )

        val reloaded = SubscriptionsUiState(podcasts = listOf(podcast))
            .withTransientStateFrom(current)

        assertTrue(reloaded.isRefreshingAll)
        assertEquals(setOf(7), reloaded.refreshingPodcastIds)
        assertEquals(setOf(7), reloaded.markingAllListenedPodcastIds)
        assertEquals(setOf(7), reloaded.unsubscribingPodcastIds)
        assertEquals(setOf(11), reloaded.busyEpisodeIds)
        assertEquals(7, reloaded.failedUnsubscribePodcastId)
        assertEquals(7, reloaded.failedMarkAllListenedPodcastId)
        assertEquals(current.failedEpisodeAction, reloaded.failedEpisodeAction)
    }
}
