package com.example.mpod.ui.screens.subscriptions

import androidx.lifecycle.viewModelScope
import com.example.mpod.data.local.dao.EpisodeDao
import com.example.mpod.data.local.dao.PlaylistDao
import com.example.mpod.data.local.dao.PodcastDao
import com.example.mpod.data.local.entity.EpisodeEntity
import com.example.mpod.data.local.entity.PodcastEntity
import com.example.mpod.data.local.model.PlaylistItemWithEpisode
import com.example.mpod.data.local.preferences.AppSettingsDataStore
import com.example.mpod.data.network.ProxyHttpClientFactory
import com.example.mpod.data.repository.PlaylistRepository
import com.example.mpod.data.repository.PodcastRepository
import com.example.mpod.data.repository.repositoryCleanupManager
import com.example.mpod.playback.PlaybackQueueInvalidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

@OptIn(ExperimentalCoroutinesApi::class)
class SubscriptionsStateConcurrencyTest {
    private val main = UnconfinedTestDispatcher()
    private val podcasts = MutableStateFlow(listOf(PodcastEntity(id = 1, title = "Original", feedUrl = "https://example.com/feed")))
    private val refreshGate = Gate()
    private val refreshCalls = AtomicInteger()
    private val deleteCalls = AtomicInteger()
    private lateinit var viewModel: SubscriptionsViewModel
    private lateinit var controlledState: ControlledState
    private var publicationGate: Gate? = null

    @Before
    fun setUp() = runBlocking {
        val testThread = Thread.currentThread()
        Dispatchers.setMain(main)
        // Strict fakes: any unexpected repository/DAO work fails the test.
        val podcastDao = fake<PodcastDao> { name, _ ->
            when (name) {
                "getAllPodcastsFlow" -> podcasts
                "getAllPodcasts" -> podcasts.value
                "getPodcastById" -> {
                    refreshCalls.incrementAndGet()
                    refreshGate.park()
                    throw IllegalStateException("controlled refresh failure")
                }
                "deleteById" -> { deleteCalls.incrementAndGet(); Unit }
                else -> error("Unexpected PodcastDao.$name")
            }
        }
        val episodes = fake<EpisodeDao> { name, _ ->
            when (name) {
                "getEpisodesByPodcastId" -> emptyList<EpisodeEntity>()
                else -> error("Unexpected EpisodeDao.$name")
            }
        }
        val playlist = fake<PlaylistDao> { name, _ ->
            when (name) {
                "getPlaylistItemsWithEpisodesFlow" -> MutableStateFlow(emptyList<PlaylistItemWithEpisode>())
                else -> error("Unexpected PlaylistDao.$name")
            }
        }
        val client = ProxyHttpClientFactory()
        val manager = repositoryCleanupManager(episodes, client)
        val invalidator = PlaybackQueueInvalidator()
        val repository = PodcastRepository(podcastDao, episodes, AppSettingsDataStore(), client, manager, invalidator)
        viewModel = SubscriptionsViewModel(podcastDao, episodes, playlist, repository,
            PlaylistRepository(playlist), invalidator, manager)
        withTimeout(TIMEOUT) { viewModel.state.first { !it.isLoading } }

        // Intercept the actual VM's read/CAS boundary without adding production test hooks.
        // The public StateFlow still observes the original delegate.
        // The reflected field name must match SubscriptionsViewModel._state.
        val field = SubscriptionsViewModel::class.java.getDeclaredField("_state").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        controlledState = ControlledState(field.get(viewModel) as MutableStateFlow<SubscriptionsUiState>, testThread)
        field.set(viewModel, controlledState)
    }

    @After
    fun tearDown() = runBlocking {
        publicationGate?.release?.countDown()
        refreshGate.release.countDown()
        val scopeJob = viewModel.viewModelScope.coroutineContext[kotlinx.coroutines.Job]!!
        viewModel.viewModelScope.cancel()
        withTimeout(TIMEOUT) { scopeJob.join() }
        Dispatchers.resetMain()
    }

    @Test
    fun libraryReadBeforeRefreshStart_doesNotClearSpinnerOrAllowDuplicateRefresh() = runBlocking {
        val gate = pauseLibraryPublication()
        viewModel.refreshAll()
        viewModel.refreshAll() // Guard must be claimed synchronously, before the IO work returns.
        refreshGate.awaitEntered()
        releaseLibrary(gate)
        assertTrue(viewModel.state.value.isRefreshingAll)
        assertEquals(1, refreshCalls.get())
        finishRefresh()
        assertFalse(viewModel.state.value.isRefreshingAll)
    }

    @Test
    fun libraryReadDuringRefresh_completionAndErrorSurvivePublication() = runBlocking {
        viewModel.refreshAll()
        refreshGate.awaitEntered()
        val gate = pauseLibraryPublication() // Captures spinner=true, error=null.
        finishRefresh()
        val error = viewModel.state.value.actionErrorMessage
        assertTrue(error.orEmpty().contains("controlled refresh failure"))
        releaseLibrary(gate)
        assertFalse("Late library publication must not restore the spinner", viewModel.state.value.isRefreshingAll)
        assertEquals(error, viewModel.state.value.actionErrorMessage)
        val afterClear = pauseLibraryPublication("After clear")
        viewModel.clearActionError()
        releaseLibrary(afterClear)
        assertNull(viewModel.state.value.actionErrorMessage)
    }

    @Test
    fun libraryReadBeforeCountdownTick_keepsLatestCountdown() = runBlocking {
        viewModel.schedulePodcastUnsubscribe(1)
        viewModel.schedulePodcastUnsubscribe(1)
        val gate = pauseLibraryPublication()
        main.scheduler.advanceTimeBy(1_000)
        main.scheduler.runCurrent()
        assertEquals(14, viewModel.state.value.pendingUnsubscribe?.secondsRemaining)
        releaseLibrary(gate)
        assertEquals(14, viewModel.state.value.pendingUnsubscribe?.secondsRemaining)
        viewModel.undoPodcastUnsubscribe(1)
        main.scheduler.advanceTimeBy(15_000)
        main.scheduler.runCurrent()
        assertEquals(0, deleteCalls.get())
    }

    @Test
    fun libraryReadBeforeUndo_doesNotRestoreCancelledUnsubscribe() = runBlocking {
        viewModel.schedulePodcastUnsubscribe(1)
        val gate = pauseLibraryPublication()
        viewModel.undoPodcastUnsubscribe(1)
        releaseLibrary(gate)
        assertNull(viewModel.state.value.pendingUnsubscribe)
        main.scheduler.advanceTimeBy(15_000)
        main.scheduler.runCurrent()
        assertNull(viewModel.state.value.pendingUnsubscribe)
        assertEquals(0, deleteCalls.get())
    }

    @Test
    fun immediateSinglePodcastRefreshRepeat_dispatchesOnceAndRetainsBusyId() = runBlocking {
        val gate = pauseLibraryPublication()
        viewModel.refreshPodcast(1)
        viewModel.refreshPodcast(1)
        refreshGate.awaitEntered()
        releaseLibrary(gate)
        assertEquals(setOf(1L), viewModel.state.value.refreshingPodcastIds)
        assertEquals(1, refreshCalls.get())
        refreshGate.release.countDown()
        withTimeout(TIMEOUT) { viewModel.state.first { it.refreshingPodcastIds.isEmpty() } }
        assertEquals("controlled refresh failure", viewModel.state.value.actionErrorMessage)
    }

    private fun pauseLibraryPublication(title: String = "Updated"): Gate {
        val gate = Gate()
        publicationGate = gate
        controlledState.nextRead.set(gate)
        podcasts.value = listOf(podcasts.value.single().copy(title = title))
        gate.awaitEntered()
        return gate
    }

    private fun releaseLibrary(gate: Gate) {
        gate.release.countDown()
        assertTrue("Library publication timed out", gate.published.await(TIMEOUT, TimeUnit.MILLISECONDS))
        assertEquals(podcasts.value.single().title, viewModel.state.value.podcasts.single().title)
    }

    private suspend fun finishRefresh() {
        refreshGate.release.countDown()
        withTimeout(TIMEOUT) {
            viewModel.state.first { !it.isRefreshingAll && it.actionErrorMessage != null }
        }
    }

    private class Gate {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val published = CountDownLatch(1)
        @Volatile var publisher: Thread? = null
        fun park() {
            publisher = Thread.currentThread()
            entered.countDown()
            check(release.await(TIMEOUT, TimeUnit.MILLISECONDS)) { "Barrier release timed out" }
        }
        fun awaitEntered() {
            assertTrue("Barrier entry timed out", entered.await(TIMEOUT, TimeUnit.MILLISECONDS))
        }
    }

    /** A single forced stale read; CAS retries use the real current value, without another barrier. */
    private class ControlledState(
        private val delegate: MutableStateFlow<SubscriptionsUiState>,
        private val mainThread: Thread
    ) : MutableStateFlow<SubscriptionsUiState> by delegate {
        val nextRead = AtomicReference<Gate?>()
        @Volatile private var activeGate: Gate? = null
        override var value: SubscriptionsUiState
            get() {
                val snapshot = delegate.value
                if (Thread.currentThread() !== mainThread) {
                    nextRead.getAndSet(null)?.let { gate -> activeGate = gate; gate.park() }
                }
                return snapshot
            }
            set(value) { delegate.value = value; signalPublication() }
        override fun compareAndSet(expect: SubscriptionsUiState, update: SubscriptionsUiState): Boolean {
            val success = delegate.compareAndSet(expect, update)
            if (success) signalPublication()
            return success
        }
        private fun signalPublication() {
            activeGate?.takeIf { it.publisher === Thread.currentThread() }?.published?.countDown()
        }
    }

    private inline fun <reified T> fake(crossinline call: (String, Array<out Any?>) -> Any?): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, args ->
            call(method.name, args ?: emptyArray())
        } as T

    companion object { private const val TIMEOUT = 5_000L }
}
