package com.example.mpod.ui.screens.home

import com.example.mpod.data.network.MpodApi
import com.example.mpod.playback.PlaybackQueueInvalidator
import java.util.concurrent.TimeUnit
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

class HomeViewModelTest {
    private lateinit var server: MockWebServer
    private lateinit var viewModel: HomeViewModel

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
        viewModel = HomeViewModel(api, PlaybackQueueInvalidator())
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
