package com.example.mpod.ui.screens.subscriptions

import com.example.mpod.data.local.dao.EpisodeDao
import com.example.mpod.data.local.dao.PlaylistDao
import com.example.mpod.data.local.dao.PodcastDao
import com.example.mpod.data.local.model.SubscriptionRow
import com.example.mpod.data.local.preferences.FakeAppSettingsStore
import com.example.mpod.data.network.ProxyHttpClientFactory
import com.example.mpod.data.repository.PlaylistRepository
import com.example.mpod.data.repository.PodcastRepository
import com.example.mpod.data.repository.repositoryCleanupManager
import com.example.mpod.playback.PlaybackQueueInvalidator
import com.example.mpod.playback.SmartListeningManager
import com.example.mpod.testutil.mock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class SubscriptionsHeaderLastRefreshTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun subscriptionsHeaderSubtitleCombinesCountsAndLastRefresh() {
        val podcasts = listOf(
            SubscriptionPodcastUi(
                id = 1L,
                title = "Decoder Ring",
                description = "Culture stories",
                imageUrl = null,
                totalEpisodeCount = 10,
                unlistenedEpisodeCount = 2,
                episodes = emptyList()
            )
        )

        // Only counts when lastRefresh is null or blank
        assertEquals("1 podcast · 1 unlistened", subscriptionsHeaderSubtitle(podcasts, null))
        assertEquals("1 podcast · 1 unlistened", subscriptionsHeaderSubtitle(podcasts, ""))

        // Combines counts and last refresh on two lines
        val expected = "1 podcast · 1 unlistened\nLast refresh today at 03:04"
        assertEquals(expected, subscriptionsHeaderSubtitle(podcasts, "Last refresh today at 03:04"))
    }

    @Test
    fun viewModelObservesLastRefreshTimeFromSettingsStore() = runBlocking {
        val podcastDao: PodcastDao = mock()
        val episodeDao: EpisodeDao = mock()
        val playlistDao: PlaylistDao = mock()
        val client = ProxyHttpClientFactory()
        val manager: SmartListeningManager = repositoryCleanupManager(episodeDao, client)
        val invalidator = PlaybackQueueInvalidator()
        val store = FakeAppSettingsStore()
        val repository = PodcastRepository(podcastDao, episodeDao, store, client, manager, invalidator)
        val playlistRepo = PlaylistRepository(playlistDao, mock(), mock())

        val rowsFlow = MutableStateFlow<List<SubscriptionRow>>(emptyList())
        org.mockito.Mockito.`when`(podcastDao.getSubscriptionRowsFlow()).thenReturn(rowsFlow)

        val vm = SubscriptionsViewModel(
            podcastDao = podcastDao,
            episodeDao = episodeDao,
            playlistDao = playlistDao,
            podcastRepository = repository,
            playlistRepository = playlistRepo,
            queueInvalidator = invalidator,
            smartListeningManager = manager,
            appSettingsDataStore = store
        )

        // Initially blank
        assertNull(vm.state.value.lastRefreshHeaderText)

        // Set refresh time to a timestamp in the JVM's system timezone
        val zoneId = ZoneId.systemDefault()
        val nowMillis = java.time.LocalDate.now(zoneId).atTime(10, 0).atZone(zoneId).toInstant().toEpochMilli()
        store.setLastRefreshTime(nowMillis.toString())

        val updatedState = vm.state.first { it.lastRefreshHeaderText != null }
        assertEquals("Last refresh today at 10:00", updatedState.lastRefreshHeaderText)
    }
}
