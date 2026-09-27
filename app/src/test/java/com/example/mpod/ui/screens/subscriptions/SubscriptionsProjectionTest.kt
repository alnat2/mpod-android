package com.example.mpod.ui.screens.subscriptions

import com.example.mpod.data.local.entity.EpisodeEntity
import com.example.mpod.data.local.entity.PodcastEntity
import com.example.mpod.data.local.model.subscriptionRows
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SubscriptionsProjectionTest {
    @Test
    fun equalScreenProjectionSkipsMappingButVisibleChangesAreMapped() = runTest {
        val input = MutableSharedFlow<List<com.example.mpod.data.local.model.SubscriptionRow>>()
        val mapped = mutableListOf<List<SubscriptionPodcastUi>>()
        // There is no equality filter after mapping: each output counts a mapper invocation.
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            input.subscriptionUiFlow().collect { mapped += it }
        }
        val pod = PodcastEntity(id = 1, title = "Podcast", feedUrl = "feed")
        val ep = EpisodeEntity(id = 1, podcastId = 1, guid = "one", title = "Episode", audioUrl = "audio")
        input.emit(subscriptionRows(listOf(pod), listOf(ep)))
        input.emit(subscriptionRows(listOf(pod), listOf(ep.copy(playbackPositionMs = 5000))))
        input.emit(subscriptionRows(listOf(pod.copy(lastRefreshedAt = 100)), listOf(ep)))
        assertEquals(1, mapped.size)
        input.emit(subscriptionRows(listOf(pod), listOf(ep.copy(isListened = true))))
        assertEquals(0, mapped.last().single().unlistenedEpisodeCount)
        input.emit(subscriptionRows(listOf(pod), listOf(ep.copy(isDownloaded = true))))
        assertTrue(mapped.last().single().episodes.single().downloaded)
        input.emit(subscriptionRows(listOf(pod), listOf(ep), setOf(ep.id)))
        assertTrue(mapped.last().single().episodes.single().inPlaylist)
        input.emit(subscriptionRows(listOf(pod.copy(title = "Renamed")), listOf(ep.copy(title = "New title", description = "Notes"))))
        assertEquals("Renamed", mapped.last().single().title)
        assertEquals("Notes", mapped.last().single().episodes.single().summary)
        assertEquals(5, mapped.size)
    }

    @Test
    fun emptyPodcastsAndExistingOrderingAndCountersArePreserved() {
        val pods = listOf(PodcastEntity(id = 1, title = "Zulu", feedUrl = "z"),
            PodcastEntity(id = 2, title = "Alpha", feedUrl = "a"))
        val old = EpisodeEntity(id = 1, podcastId = 1, guid = "old", title = "Old", audioUrl = "a", publishedAt = 1)
        val newer = old.copy(id = 2, guid = "new", title = "New", publishedAt = 2, isListened = true)
        val result = mapSubscriptionRows(subscriptionRows(pods, listOf(old, newer)))
        assertEquals(listOf(2L, 1L), result.map { it.id })
        assertEquals(0, result.first().totalEpisodeCount)
        assertEquals(listOf(2L, 1L), result.last().episodes.map { it.id })
        assertEquals(2, result.last().totalEpisodeCount)
        assertEquals(1, result.last().unlistenedEpisodeCount)
    }
}
