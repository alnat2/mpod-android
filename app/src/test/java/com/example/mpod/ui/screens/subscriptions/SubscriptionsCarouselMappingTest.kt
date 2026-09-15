package com.example.mpod.ui.screens.subscriptions

import org.junit.Assert.assertEquals
import org.junit.Test

class SubscriptionsCarouselMappingTest {

    @Test
    fun mapsInteriorPagesCorrectlyForThreePodcasts() {
        val count = 3
        // Page 1 -> Index 0 (First podcast)
        assertEquals(0, podcastIndexForCarouselPage(page = 1, podcastCount = count))
        // Page 2 -> Index 1 (Second podcast)
        assertEquals(1, podcastIndexForCarouselPage(page = 2, podcastCount = count))
        // Page 3 -> Index 2 (Third podcast)
        assertEquals(2, podcastIndexForCarouselPage(page = 3, podcastCount = count))
    }

    @Test
    fun mapsArtificialLeftWrapAroundBoundaryToLastPodcast() {
        val count = 3
        // Page 0 is the artificial leading page before page 1 -> wraps around to last podcast (index 2)
        assertEquals(2, podcastIndexForCarouselPage(page = 0, podcastCount = count))
    }

    @Test
    fun mapsArtificialRightWrapAroundBoundaryToFirstPodcast() {
        val count = 3
        // Page 4 (count + 1) is the artificial trailing page after page 3 -> wraps around to first podcast (index 0)
        assertEquals(0, podcastIndexForCarouselPage(page = 4, podcastCount = count))
    }

    @Test
    fun mapsOutOfBoundsPagesConsistently() {
        val count = 3
        // Any negative page wraps around to last podcast
        assertEquals(2, podcastIndexForCarouselPage(page = -1, podcastCount = count))
        assertEquals(2, podcastIndexForCarouselPage(page = -5, podcastCount = count))
        // Any page strictly greater than count + 1 wraps to first podcast
        assertEquals(0, podcastIndexForCarouselPage(page = 5, podcastCount = count))
        assertEquals(0, podcastIndexForCarouselPage(page = 10, podcastCount = count))
    }

    @Test
    fun handlesSingleAndZeroPodcastLibrariesGracefully() {
        assertEquals(0, podcastIndexForCarouselPage(page = 0, podcastCount = 1))
        assertEquals(0, podcastIndexForCarouselPage(page = 1, podcastCount = 1))
        assertEquals(0, podcastIndexForCarouselPage(page = -1, podcastCount = 1))
        assertEquals(0, podcastIndexForCarouselPage(page = 0, podcastCount = 0))
    }

    @Test
    fun selectedPodcastHelperReturnsMatchingPodcastForPageAndBoundaries() {
        val podcasts = listOf(
            mockPodcast(id = 101L, title = "Podcast One"),
            mockPodcast(id = 202L, title = "Podcast Two"),
            mockPodcast(id = 303L, title = "Podcast Three")
        )
        // Artificial left wrap-around page 0 -> last podcast (Three)
        assertEquals(303L, podcasts[podcastIndexForCarouselPage(0, podcasts.size)].id)
        // Interior pages
        assertEquals(101L, podcasts[podcastIndexForCarouselPage(1, podcasts.size)].id)
        assertEquals(202L, podcasts[podcastIndexForCarouselPage(2, podcasts.size)].id)
        assertEquals(303L, podcasts[podcastIndexForCarouselPage(3, podcasts.size)].id)
        // Artificial right wrap-around page 4 -> first podcast (One)
        assertEquals(101L, podcasts[podcastIndexForCarouselPage(4, podcasts.size)].id)
    }

    private fun mockPodcast(id: Long, title: String): SubscriptionPodcastUi {
        return SubscriptionPodcastUi(
            id = id,
            title = title,
            description = "Desc",
            imageUrl = null,
            totalEpisodeCount = 1,
            unlistenedEpisodeCount = 1,
            episodes = emptyList()
        )
    }
}
