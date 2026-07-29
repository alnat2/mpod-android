package com.example.mpod.ui.screens.subscriptions

import com.example.mpod.data.network.MpodApi
import com.example.mpod.playback.PlaybackQueueInvalidator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
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
import java.util.concurrent.TimeUnit

class SubscriptionsViewModelTest {
    private lateinit var server: MockWebServer
    private lateinit var api: MpodApi
    private lateinit var viewModel: SubscriptionsViewModel
    private lateinit var queueInvalidator: PlaybackQueueInvalidator
    private lateinit var sessionCache: SubscriptionsSessionCache

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MpodApi::class.java)

        enqueueLoadedPodcast()
        queueInvalidator = PlaybackQueueInvalidator()
        sessionCache = SubscriptionsSessionCache()
        viewModel = SubscriptionsViewModel(
            api = api,
            queueInvalidator = queueInvalidator,
            sessionCache = sessionCache
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun initialLoadFailureCanRetryIntoAuthoritativeLibrary() = runBlocking {
        awaitState { !it.isLoading && it.podcasts.singleOrNull()?.id == 41 }
        server.enqueue(
            MockResponse().setResponseCode(503).setBody(
                """{"error":{"message":"Subscriptions unavailable"}}"""
            )
        )
        val failedViewModel = SubscriptionsViewModel(api, PlaybackQueueInvalidator())

        val failed = withTimeout(5_000) {
            failedViewModel.state.first {
                !it.isLoading && it.errorMessage == "Subscriptions unavailable"
            }
        }
        assertEquals(emptyList<SubscriptionPodcastUi>(), failed.podcasts)

        enqueueLoadedPodcast()
        failedViewModel.refresh()
        val recovered = withTimeout(5_000) {
            failedViewModel.state.first {
                !it.isLoading && it.errorMessage == null &&
                    it.podcasts.singleOrNull()?.id == 41
            }
        }
        assertEquals("Test podcast", recovered.podcasts.single().title)
    }

    @Test
    fun recreatedViewModelShowsCachedLibraryWhileRefreshingInBackground() = runBlocking {
        val loaded = awaitState { !it.isLoading && it.podcasts.singleOrNull()?.id == 41 }
        assertEquals("Episode", loaded.podcasts.single().episodes.single().title)
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setHeadersDelay(300, TimeUnit.MILLISECONDS)
                .setBody(
                    """{"podcasts":[{"id":41,"title":"Test podcast","description":"Test","rssUrl":"https://example.com/feed.xml"}]}"""
                )
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"items":[]}"""))
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"episodes":[{"id":51,"podcastId":41,"title":"Updated episode","isListened":false}]}"""
            )
        )

        val recreated = SubscriptionsViewModel(
            api = api,
            queueInvalidator = PlaybackQueueInvalidator(),
            sessionCache = sessionCache
        )

        val cached = recreated.state.value
        assertEquals(true, cached.hasLoadedOnce)
        assertEquals(false, cached.isLoading)
        assertEquals(true, cached.isRefreshing)
        assertEquals("Episode", cached.podcasts.single().episodes.single().title)
        val refreshed = withTimeout(5_000) {
            recreated.state.first {
                !it.isRefreshing &&
                    it.podcasts.singleOrNull()?.episodes?.singleOrNull()?.title == "Updated episode"
            }
        }
        assertEquals("Updated episode", refreshed.podcasts.single().episodes.single().title)
    }

    @Test
    fun backgroundRefreshFailureKeepsCachedLibraryVisible() = runBlocking {
        awaitState { !it.isLoading && it.podcasts.singleOrNull()?.id == 41 }
        server.enqueue(
            MockResponse().setResponseCode(503).setBody(
                """{"error":{"message":"Subscriptions unavailable"}}"""
            )
        )

        val recreated = SubscriptionsViewModel(
            api = api,
            queueInvalidator = PlaybackQueueInvalidator(),
            sessionCache = sessionCache
        )
        val failed = withTimeout(5_000) {
            recreated.state.first {
                !it.isRefreshing && it.actionErrorMessage == "Subscriptions unavailable"
            }
        }

        assertEquals(null, failed.errorMessage)
        assertEquals("Episode", failed.podcasts.single().episodes.single().title)
    }

    @Test
    fun oneEpisodeEndpointFailureStaysScopedAndRetryRecoversBothPodcasts() = runBlocking {
        awaitState { !it.isLoading && it.podcasts.singleOrNull()?.id == 41 }
        enqueueTwoPodcasts()
        server.enqueue(
            MockResponse().setResponseCode(503).setBody(
                """{"error":{"message":"Episodes unavailable"}}"""
            )
        )
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"episodes":[{"id":52,"podcastId":42,"title":"Healthy episode","isListened":false}]}"""
            )
        )

        viewModel.refresh()
        val partial = awaitState {
            !it.isLoading && it.podcasts.size == 2 &&
                it.podcasts.first().episodesUnavailable &&
                it.podcasts.last().episodes.singleOrNull()?.id == 52
        }
        assertEquals("Some podcast episodes could not be loaded.", partial.actionErrorMessage)
        assertEquals("Healthy episode", partial.podcasts.last().episodes.single().title)

        enqueueTwoPodcasts()
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"episodes":[{"id":51,"podcastId":41,"title":"Recovered episode","isListened":false}]}"""
            )
        )
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"episodes":[{"id":52,"podcastId":42,"title":"Healthy episode","isListened":false}]}"""
            )
        )
        viewModel.retryLastAction()

        val recovered = awaitState {
            !it.isLoading && it.actionErrorMessage == null &&
                it.podcasts.size == 2 &&
                it.podcasts.none { podcast -> podcast.episodesUnavailable }
        }
        assertEquals("Recovered episode", recovered.podcasts.first().episodes.single().title)
        assertEquals("Healthy episode", recovered.podcasts.last().episodes.single().title)
    }

    @Test
    fun immediateDuplicateRefreshDispatchesOneLoadChain() = runBlocking {
        awaitState { !it.isLoading && it.podcasts.singleOrNull()?.id == 41 }
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setHeadersDelay(300, TimeUnit.MILLISECONDS)
                .setBody(
                    """{"podcasts":[{"id":41,"title":"Test podcast","description":"Test","rssUrl":"https://example.com/feed.xml"}]}"""
                )
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"items":[]}"""))
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"episodes":[{"id":51,"podcastId":41,"title":"Reloaded episode","isListened":false}]}"""
            )
        )

        viewModel.refresh()
        viewModel.refresh()
        awaitState {
            it.podcasts.singleOrNull()?.episodes?.singleOrNull()?.title == "Reloaded episode"
        }

        val paths = List(server.requestCount) { server.takeRequest().path }
        assertEquals(2, paths.count { it == "/api/podcasts" })
        assertEquals(2, paths.count { it == "/api/playlist" })
        assertEquals(2, paths.count { it == "/api/podcasts/41/episodes" })
    }

    @Test
    fun failedRefreshAllJobKeepsLoadedLibraryUsable() = runBlocking {
        awaitState { !it.isLoading && it.podcasts.singleOrNull()?.id == 41 }
        server.enqueue(MockResponse().setResponseCode(202))
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"scheduler":{"state":"failed","lastError":"Beta feed refresh failed"}}"""
            )
        )

        viewModel.refreshAll()
        val failed = withTimeout(5_000) {
            viewModel.state.first {
                !it.isRefreshingAll && it.actionErrorMessage == "Beta feed refresh failed"
            }
        }

        assertEquals(41, failed.podcasts.single().id)
        assertEquals("Episode", failed.podcasts.single().episodes.single().title)
        val paths = List(server.requestCount) { server.takeRequest().path }
        assertEquals(1, paths.count { it == "/api/podcasts/refresh-all" })
        assertEquals(1, paths.count { it == "/api/jobs/status" })
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
    fun immediateDuplicatesAcrossSubscriptionMutationsDispatchOnce() = runBlocking {
        awaitState { it.podcasts.singleOrNull()?.unlistenedEpisodeCount == 1 }

        server.enqueue(MockResponse().setResponseCode(500).setBody("Refresh all failed"))
        viewModel.refreshAll()
        viewModel.refreshAll()
        awaitState { !it.isRefreshingAll && it.actionErrorMessage != null }

        server.enqueue(MockResponse().setResponseCode(500).setBody("Refresh failed"))
        viewModel.refreshPodcast(41)
        viewModel.refreshPodcast(41)
        awaitState { it.refreshingPodcastIds.isEmpty() && it.actionErrorMessage != null }

        server.enqueue(MockResponse().setResponseCode(500).setBody("Mark all failed"))
        viewModel.markAllListened(41)
        viewModel.markAllListened(41)
        awaitState { it.failedMarkAllListenedPodcastId == 41 }

        server.enqueue(MockResponse().setResponseCode(500).setBody("Delete failed"))
        viewModel.unsubscribePodcastNow(41)
        viewModel.unsubscribePodcastNow(41)
        awaitState { it.failedUnsubscribePodcastId == 41 }

        val paths = List(server.requestCount) { server.takeRequest().path }
        assertEquals(1, paths.count { it == "/api/podcasts/refresh-all" })
        assertEquals(1, paths.count { it == "/api/podcasts/41/refresh" })
        assertEquals(1, paths.count { it == "/api/podcasts/41/mark-all-listened" })
        assertEquals(1, paths.count { it == "/api/podcasts/41" })
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

    @Test
    fun successfulForegroundReloadInvalidatesSharedPlaybackState() = runBlocking {
        awaitState { !it.isLoading }
        enqueueLoadedPodcast()
        val invalidation = async(start = CoroutineStart.UNDISPATCHED) {
            queueInvalidator.events.first()
        }

        viewModel.refresh()
        withTimeout(5_000) { invalidation.await() }

        awaitState { !it.isLoading }
        Unit
    }

    @Test
    fun malformedSuccessfulReloadPreservesLibraryAndNextReloadRecovers() = runBlocking {
        awaitState { it.podcasts.singleOrNull()?.id == 41 }
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"podcasts":null}"""))

        viewModel.refresh()
        val failed = awaitState { it.actionErrorMessage == "Could not load podcasts." }

        assertEquals(41, failed.podcasts.single().id)
        enqueueLoadedPodcast()
        viewModel.refresh()
        val recovered = awaitState {
            !it.isLoading && it.actionErrorMessage == null && it.podcasts.singleOrNull()?.id == 41
        }
        assertEquals(1, recovered.podcasts.size)
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

    private fun enqueueTwoPodcasts() {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"podcasts":[{"id":41,"title":"Failed podcast","description":"Test","rssUrl":"https://example.com/failed.xml"},{"id":42,"title":"Healthy podcast","description":"Test","rssUrl":"https://example.com/healthy.xml"}]}"""
            )
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"items":[]}"""))
    }

    private suspend fun awaitState(
        predicate: (SubscriptionsUiState) -> Boolean
    ): SubscriptionsUiState = withTimeout(5_000) {
        viewModel.state.first(predicate)
    }
}
