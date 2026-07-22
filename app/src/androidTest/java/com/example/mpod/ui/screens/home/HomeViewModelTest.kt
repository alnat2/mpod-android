package com.example.mpod.ui.screens.home

import com.example.mpod.data.network.MpodApi
import com.example.mpod.playback.PlaybackQueueInvalidator
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class HomeViewModelTest {
    private lateinit var server: MockWebServer
    private lateinit var viewModel: HomeViewModel
    private lateinit var queueInvalidator: PlaybackQueueInvalidator

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MpodApi::class.java)

        enqueuePodcasts()
        enqueueQueueWithEpisode()
        queueInvalidator = PlaybackQueueInvalidator()
        viewModel = HomeViewModel(api, queueInvalidator)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun duplicateEpisodeMutationIsBlockedWhileFirstRequestIsRunning() = runBlocking {
        awaitState { it.queue.singleOrNull()?.id == 51 }
        server.enqueue(
            MockResponse()
                .setResponseCode(204)
                .setHeadersDelay(300, TimeUnit.MILLISECONDS)
        )
        enqueuePodcasts()
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"queue":[],"activePlayback":null}"""
            )
        )

        viewModel.removeEpisodeFromPlaylist(51)
        viewModel.removeEpisodeFromPlaylist(51)
        awaitState { it.queue.isEmpty() && it.busyEpisodeIds.isEmpty() }

        val paths = List(server.requestCount) { server.takeRequest().path }
        assertEquals(1, paths.count { it == "/api/playlist/51" })
    }

    @Test
    fun immediateDuplicateReorderDispatchesOneRequestAndRollsBack() = runBlocking {
        awaitState { it.queue.singleOrNull()?.id == 51 }
        enqueuePodcasts()
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"queue":[{"id":51,"podcastId":41,"title":"First","podcastTitle":"Podcast","downloaded":false,"isListened":false},{"id":52,"podcastId":41,"title":"Second","podcastTitle":"Podcast","downloaded":false,"isListened":false}],"activePlayback":null}"""
            )
        )
        viewModel.refresh()
        awaitState { it.queue.map(HomeEpisodeUi::id) == listOf(51, 52) }
        server.enqueue(MockResponse().setResponseCode(500).setBody("Reorder failed"))

        viewModel.moveEpisode(51, 1)
        viewModel.moveEpisode(51, 1)
        val failed = awaitState { it.busyEpisodeIds.isEmpty() && it.actionErrorMessage != null }

        assertEquals(listOf(51, 52), failed.queue.map(HomeEpisodeUi::id))
        val paths = List(server.requestCount) { server.takeRequest().path }
        assertEquals(1, paths.count { it == "/api/playlist/reorder" })
    }

    @Test
    fun playbackCompletionRefreshesHomeWithoutReinvalidatingService() = runBlocking {
        awaitState { it.queue.singleOrNull()?.id == 51 }
        enqueuePodcasts()
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"queue":[],"activePlayback":null}"""
            )
        )
        val unexpectedServiceInvalidation = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeoutOrNull(300) { queueInvalidator.events.first() }
        }

        queueInvalidator.refreshHome()

        awaitState { !it.isLoading && it.queue.isEmpty() }
        assertEquals(4, server.requestCount)
        assertNull(unexpectedServiceInvalidation.await())
    }

    @Test
    fun malformedSuccessfulLoadShowsStableErrorAndRetryRecovers() = runBlocking {
        awaitState { it.queue.singleOrNull()?.id == 51 }
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"podcasts":null}"""))

        viewModel.refresh()
        val failed = awaitState { it.errorMessage == "Could not load podcasts." }

        assertEquals(true, failed.queue.isEmpty())
        enqueuePodcasts()
        enqueueQueueWithEpisode()
        viewModel.refresh()
        val recovered = awaitState { !it.isLoading && it.queue.singleOrNull()?.id == 51 }
        assertNull(recovered.errorMessage)
    }

    private fun enqueuePodcasts() {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"podcasts":[{"id":41,"title":"Podcast","rssUrl":"https://example.com/feed.xml"}]}"""
            )
        )
    }

    private fun enqueueQueueWithEpisode() {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"queue":[{"id":51,"podcastId":41,"title":"Episode","podcastTitle":"Podcast","downloaded":false,"isListened":false}],"activePlayback":{"episodeId":51,"lastUpdated":"2026-07-19T00:00:00Z"}}"""
            )
        )
    }

    private suspend fun awaitState(
        predicate: (HomeUiState) -> Boolean
    ): HomeUiState = withTimeout(5_000) {
        viewModel.state.first(predicate)
    }
}
