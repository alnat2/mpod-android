package com.example.mpod.ui.screens.subscriptions

import com.example.mpod.data.network.MpodApi
import com.example.mpod.playback.PlaybackQueueInvalidator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class SubscriptionsViewModelTest {
    private lateinit var server: MockWebServer
    private lateinit var viewModel: SubscriptionsViewModel

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MpodApi::class.java)

        enqueueLoadedPodcast()
        viewModel = SubscriptionsViewModel(api, PlaybackQueueInvalidator())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun failedUnsubscribeRetryRepeatsDeleteInsteadOfRefreshAll() = runBlocking {
        awaitState { it.podcasts.size == 1 }
        server.enqueue(
            MockResponse().setResponseCode(500).setBody(
                """{"error":{"message":"Delete failed"}}"""
            )
        )

        viewModel.unsubscribePodcastNow(41)
        val failed = awaitState { it.failedUnsubscribePodcastId == 41 }

        assertEquals("Delete failed", failed.actionErrorMessage)
        server.enqueue(MockResponse().setResponseCode(204))
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"podcasts":[]}"""))
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"items":[]}"""))

        viewModel.retryLastAction()
        val recovered = awaitState {
            it.podcasts.isEmpty() && it.failedUnsubscribePodcastId == null &&
                it.unsubscribingPodcastIds.isEmpty()
        }

        assertEquals(null, recovered.actionErrorMessage)
        val paths = List(server.requestCount) { server.takeRequest().path }
        assertEquals(2, paths.count { it == "/api/podcasts/41" })
        assertEquals(0, paths.count { it == "/api/podcasts/refresh-all" })
    }

    @Test
    fun failedMarkAllRetryRepeatsMarkAllInsteadOfRefreshAll() = runBlocking {
        awaitState { it.podcasts.singleOrNull()?.unlistenedEpisodeCount == 1 }
        server.enqueue(
            MockResponse().setResponseCode(500).setBody(
                """{"error":{"message":"Mark all failed"}}"""
            )
        )

        viewModel.markAllListened(41)
        val failed = awaitState { it.failedMarkAllListenedPodcastId == 41 }

        assertEquals(1, failed.podcasts.single().unlistenedEpisodeCount)
        assertEquals("Mark all failed", failed.actionErrorMessage)
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"success":true,"markedEpisodes":1}"""
            )
        )
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"podcasts":[{"id":41,"title":"Test podcast","description":"Test","rssUrl":"https://example.com/feed.xml"}]}"""
            )
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"items":[]}"""))
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"episodes":[{"id":51,"podcastId":41,"title":"Episode","isListened":true}]}"""
            )
        )

        viewModel.retryLastAction()
        val recovered = awaitState {
            it.failedMarkAllListenedPodcastId == null &&
                it.markingAllListenedPodcastIds.isEmpty() &&
                it.podcasts.singleOrNull()?.unlistenedEpisodeCount == 0
        }

        assertEquals(null, recovered.actionErrorMessage)
        val paths = List(server.requestCount) { server.takeRequest().path }
        assertEquals(2, paths.count { it == "/api/podcasts/41/mark-all-listened" })
        assertEquals(0, paths.count { it == "/api/podcasts/refresh-all" })
    }

    @Test
    fun failedListenedRetryRepeatsEpisodePatchInsteadOfRefreshAll() = runBlocking {
        awaitState { it.podcasts.singleOrNull()?.unlistenedEpisodeCount == 1 }
        server.enqueue(MockResponse().setResponseCode(500).setBody("Listened failed"))

        viewModel.setEpisodeListened(51, true)
        val failed = awaitState { it.failedEpisodeAction?.episodeId == 51 }

        assertEquals(false, failed.podcasts.single().episodes.single().isListened)
        assertEquals(FailedEpisodeActionType.MarkListened, failed.failedEpisodeAction?.type)
        server.enqueue(MockResponse().setResponseCode(204))
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"podcasts":[{"id":41,"title":"Test podcast","description":"Test","rssUrl":"https://example.com/feed.xml"}]}"""
            )
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"items":[]}"""))
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"episodes":[{"id":51,"podcastId":41,"title":"Episode","isListened":true}]}"""
            )
        )

        viewModel.retryLastAction()
        val recovered = awaitState {
            it.failedEpisodeAction == null &&
                it.busyEpisodeIds.isEmpty() &&
                it.podcasts.singleOrNull()?.episodes?.singleOrNull()?.isListened == true
        }

        assertEquals(null, recovered.actionErrorMessage)
        val paths = List(server.requestCount) { server.takeRequest().path }
        assertEquals(2, paths.count { it == "/api/episodes/51" })
        assertEquals(0, paths.count { it == "/api/podcasts/refresh-all" })
    }

    @Test
    fun successfulAuthoritativeReloadClearsStaleMutationRetry() = runBlocking {
        awaitState { it.podcasts.singleOrNull()?.unlistenedEpisodeCount == 1 }
        server.enqueue(MockResponse().setResponseCode(500).setBody("Listened failed"))

        viewModel.setEpisodeListened(51, true)
        awaitState { it.failedEpisodeAction?.episodeId == 51 }
        enqueueLoadedPodcast()

        viewModel.refresh()
        val reloaded = awaitState {
            !it.isLoading && it.failedEpisodeAction == null &&
                it.podcasts.singleOrNull()?.episodes?.singleOrNull()?.isListened == false
        }

        assertEquals(null, reloaded.actionErrorMessage)
    }

    private fun enqueueLoadedPodcast() {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"podcasts":[{"id":41,"title":"Test podcast","description":"Test","rssUrl":"https://example.com/feed.xml"}]}"""
            )
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"items":[]}"""))
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"episodes":[{"id":51,"podcastId":41,"title":"Episode","isListened":false}]}"""
            )
        )
    }

    private suspend fun awaitState(
        predicate: (SubscriptionsUiState) -> Boolean
    ): SubscriptionsUiState = withTimeout(5_000) {
        viewModel.state.first(predicate)
    }
}
