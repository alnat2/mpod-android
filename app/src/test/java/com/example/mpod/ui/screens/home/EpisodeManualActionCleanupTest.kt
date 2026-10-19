package com.example.mpod.ui.screens.home

import android.content.ContextWrapper
import androidx.lifecycle.viewModelScope
import com.example.mpod.data.local.dao.EpisodeDao
import com.example.mpod.data.local.dao.PlaylistDao
import com.example.mpod.data.local.dao.PodcastDao
import com.example.mpod.data.local.entity.EpisodeEntity
import com.example.mpod.data.local.entity.PlaylistItemEntity
import com.example.mpod.data.local.entity.PodcastEntity
import com.example.mpod.data.local.model.EpisodeWithPodcast
import com.example.mpod.data.local.model.PlaylistItemWithEpisode
import com.example.mpod.data.local.model.SubscriptionRow
import com.example.mpod.data.local.model.subscriptionRows
import com.example.mpod.data.local.preferences.AppSettings
import com.example.mpod.data.local.preferences.FakeAppSettingsStore
import com.example.mpod.data.network.ProxyHttpClientFactory
import com.example.mpod.data.repository.PlaylistRepository
import com.example.mpod.data.repository.PodcastRepository
import com.example.mpod.playback.PlaybackQueueInvalidator
import com.example.mpod.playback.SmartListeningManager
import com.example.mpod.ui.screens.subscriptions.SubscriptionsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class EpisodeManualActionCleanupTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var tempDir: File
    private lateinit var podcastsDir: File
    private lateinit var fakeStorage: FakeStorage
    private lateinit var smartListeningManager: SmartListeningManager
    private lateinit var homeViewModel: HomeViewModel
    private lateinit var subscriptionsViewModel: SubscriptionsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        tempDir = Files.createTempDirectory("mpod_cleanup_test_").toFile()
        podcastsDir = File(tempDir, "podcasts").apply { mkdirs() }

        fakeStorage = FakeStorage()

        val context = object : ContextWrapper(null) {
            override fun getFilesDir(): File = tempDir
        }
        val clientFactory = ProxyHttpClientFactory().apply {
            updateProxy(AppSettings(isProxyEnabled = false))
        }

        smartListeningManager = SmartListeningManager(
            context = context,
            playlistDao = fakeStorage.playlistDao,
            episodeDao = fakeStorage.episodeDao,
            proxyHttpClientFactory = clientFactory
        )

        val queueInvalidator = PlaybackQueueInvalidator()
        val appSettingsStore = FakeAppSettingsStore()
        val playlistRepository = PlaylistRepository(fakeStorage.playlistDao)
        val podcastRepository = PodcastRepository(
            podcastDao = fakeStorage.podcastDao,
            episodeDao = fakeStorage.episodeDao,
            appSettingsDataStore = appSettingsStore,
            proxyHttpClientFactory = clientFactory,
            smartListeningManager = smartListeningManager,
            queueInvalidator = queueInvalidator
        )

        homeViewModel = HomeViewModel(
            podcastDao = fakeStorage.podcastDao,
            playlistDao = fakeStorage.playlistDao,
            episodeDao = fakeStorage.episodeDao,
            playlistRepository = playlistRepository,
            podcastRepository = podcastRepository,
            appSettingsDataStore = appSettingsStore,
            queueInvalidator = queueInvalidator,
            smartListeningManager = smartListeningManager
        )

        subscriptionsViewModel = SubscriptionsViewModel(
            podcastDao = fakeStorage.podcastDao,
            episodeDao = fakeStorage.episodeDao,
            playlistDao = fakeStorage.playlistDao,
            podcastRepository = podcastRepository,
            playlistRepository = playlistRepository,
            queueInvalidator = queueInvalidator,
            smartListeningManager = smartListeningManager
        )
    }

    @After
    fun tearDown() {
        runBlocking {
            smartListeningManager.stopObserving()
        }
        homeViewModel.viewModelScope.cancel()
        subscriptionsViewModel.viewModelScope.cancel()
        Dispatchers.resetMain()
        tempDir.deleteRecursively()
    }

    @Test
    fun homeViewModel_removeEpisodeFromPlaylist_cleansUpDownloadedFileAndDaoState() = runBlocking {
        val episodeId = 101L
        val audioFile = File(podcastsDir, "ep_${episodeId}_audio.mp3").apply { writeText("audio content") }
        fakeStorage.addEpisode(
            EpisodeEntity(
                id = episodeId,
                podcastId = 1L,
                guid = "ep-101",
                title = "Episode 101",
                audioUrl = "https://example.com/101.mp3",
                isDownloaded = true,
                localFilePath = audioFile.absolutePath,
                isListened = false
            ),
            inPlaylist = true
        )

        assertTrue("File should exist before removal", audioFile.exists())
        assertTrue("Episode should be downloaded in DB before removal", fakeStorage.episodes[episodeId]!!.isDownloaded)

        val latch = CountDownLatch(1)
        fakeStorage.cleanupLatch = latch

        homeViewModel.removeEpisodeFromPlaylist(episodeId)
        assertTrue("Cleanup should finish within timeout", latch.await(3, TimeUnit.SECONDS))

        assertFalse("Audio file should be deleted on manual removal from playlist", audioFile.exists())
        val updated = fakeStorage.episodes[episodeId]!!
        assertFalse("Episode isDownloaded should be reset to false", updated.isDownloaded)
        assertNull("Episode localFilePath should be cleared", updated.localFilePath)
        assertFalse("Episode should be removed from playlist", fakeStorage.playlist.containsKey(episodeId))
        assertTrue("Episode isListened should be set to true on removal from playlist", updated.isListened)
    }

    @Test
    fun homeViewModel_setEpisodeListened_true_cleansUpDownloadedFile() = runBlocking {
        val episodeId = 102L
        val audioFile = File(podcastsDir, "ep_${episodeId}_audio.mp3").apply { writeText("audio content") }
        fakeStorage.addEpisode(
            EpisodeEntity(
                id = episodeId,
                podcastId = 1L,
                guid = "ep-102",
                title = "Episode 102",
                audioUrl = "https://example.com/102.mp3",
                isDownloaded = true,
                localFilePath = audioFile.absolutePath,
                isListened = false
            ),
            inPlaylist = true
        )

        assertTrue(audioFile.exists())

        val latch = CountDownLatch(1)
        fakeStorage.cleanupLatch = latch

        homeViewModel.setEpisodeListened(episodeId, isListened = true)
        assertTrue("Cleanup should finish within timeout", latch.await(3, TimeUnit.SECONDS))

        assertFalse("Audio file should be deleted when marked as listened in Home", audioFile.exists())
        val updated = fakeStorage.episodes[episodeId]!!
        assertFalse("isDownloaded should be false", updated.isDownloaded)
        assertNull("localFilePath should be null", updated.localFilePath)
        assertTrue("isListened should be true", updated.isListened)
    }

    @Test
    fun homeViewModel_setEpisodeListened_false_doesNotDeleteFile() = runBlocking {
        val episodeId = 103L
        val audioFile = File(podcastsDir, "ep_${episodeId}_audio.mp3").apply { writeText("audio content") }
        fakeStorage.addEpisode(
            EpisodeEntity(
                id = episodeId,
                podcastId = 1L,
                guid = "ep-103",
                title = "Episode 103",
                audioUrl = "https://example.com/103.mp3",
                isDownloaded = true,
                localFilePath = audioFile.absolutePath,
                isListened = false
            ),
            inPlaylist = true
        )

        val latch = CountDownLatch(1)
        fakeStorage.listenedLatch = latch

        homeViewModel.setEpisodeListened(episodeId, isListened = false)
        assertTrue("setListened should finish within timeout", latch.await(3, TimeUnit.SECONDS))

        assertTrue("File should remain when marking unlistened", audioFile.exists())
        val updated = fakeStorage.episodes[episodeId]!!
        assertTrue("isDownloaded should remain true", updated.isDownloaded)
    }

    @Test
    fun subscriptionsViewModel_removeEpisodeFromPlaylist_cleansUpDownloadedFile() = runBlocking {
        val episodeId = 104L
        val audioFile = File(podcastsDir, "ep_${episodeId}_audio.mp3").apply { writeText("audio content") }
        fakeStorage.addEpisode(
            EpisodeEntity(
                id = episodeId,
                podcastId = 1L,
                guid = "ep-104",
                title = "Episode 104",
                audioUrl = "https://example.com/104.mp3",
                isDownloaded = true,
                localFilePath = audioFile.absolutePath,
                isListened = false
            ),
            inPlaylist = true
        )

        assertTrue(audioFile.exists())

        val latch = CountDownLatch(1)
        fakeStorage.cleanupLatch = latch

        subscriptionsViewModel.removeEpisodeFromPlaylist(episodeId)
        assertTrue("Cleanup should finish within timeout", latch.await(3, TimeUnit.SECONDS))

        assertFalse("Audio file should be deleted when removed from playlist in Subscriptions", audioFile.exists())
        val updated = fakeStorage.episodes[episodeId]!!
        assertFalse("isDownloaded should be false", updated.isDownloaded)
        assertNull("localFilePath should be null", updated.localFilePath)
        assertFalse("Episode should be removed from playlist", fakeStorage.playlist.containsKey(episodeId))
        assertTrue("isListened should be true on removal from playlist", updated.isListened)
    }

    @Test
    fun subscriptionsViewModel_setEpisodeListened_true_cleansUpDownloadedFile() = runBlocking {
        val episodeId = 105L
        val audioFile = File(podcastsDir, "ep_${episodeId}_audio.mp3").apply { writeText("audio content") }
        fakeStorage.addEpisode(
            EpisodeEntity(
                id = episodeId,
                podcastId = 1L,
                guid = "ep-105",
                title = "Episode 105",
                audioUrl = "https://example.com/105.mp3",
                isDownloaded = true,
                localFilePath = audioFile.absolutePath,
                isListened = false
            ),
            inPlaylist = true
        )

        assertTrue(audioFile.exists())

        val latch = CountDownLatch(1)
        fakeStorage.cleanupLatch = latch

        subscriptionsViewModel.setEpisodeListened(episodeId, isListened = true)
        assertTrue("Cleanup should finish within timeout", latch.await(3, TimeUnit.SECONDS))

        assertFalse("Audio file should be deleted when marked listened in Subscriptions", audioFile.exists())
        val updated = fakeStorage.episodes[episodeId]!!
        assertFalse("isDownloaded should be false", updated.isDownloaded)
        assertNull("localFilePath should be null", updated.localFilePath)
        assertTrue("isListened should be true", updated.isListened)
        assertFalse("Episode should be removed from playlist", fakeStorage.playlist.containsKey(episodeId))
    }

    private class FakeStorage {
        var cleanupLatch: CountDownLatch? = null
        var listenedLatch: CountDownLatch? = null
        val podcasts = mutableMapOf(
            1L to PodcastEntity(id = 1L, title = "Test Podcast", feedUrl = "https://example.com/feed")
        )
        val episodes = mutableMapOf<Long, EpisodeEntity>()
        val playlist = mutableMapOf<Long, PlaylistItemEntity>()

        val podcastFlow = MutableStateFlow(podcasts.values.toList())
        val playlistFlow = MutableStateFlow<List<PlaylistItemWithEpisode>>(emptyList())
        val subscriptionsFlow = MutableStateFlow<List<SubscriptionRow>>(emptyList())

        fun addEpisode(episode: EpisodeEntity, inPlaylist: Boolean = false) {
            episodes[episode.id] = episode
            if (inPlaylist) {
                playlist[episode.id] = PlaylistItemEntity(id = episode.id, episodeId = episode.id, position = playlist.size)
            }
            emit()
        }

        private fun emit() {
            podcastFlow.value = podcasts.values.toList()
            val podcast = podcasts.values.first()
            val items = playlist.values.sortedBy { it.position }.mapNotNull { item ->
                episodes[item.episodeId]?.let { ep ->
                    PlaylistItemWithEpisode(
                        playlistItemId = item.id,
                        position = item.position,
                        episode = ep,
                        podcastTitle = podcast.title,
                        podcastArtworkUrl = "https://example.com/art.jpg"
                    )
                }
            }
            playlistFlow.value = items
            subscriptionsFlow.value = subscriptionRows(podcasts.values.toList(), episodes.values.toList(), playlist.keys)
        }

        val podcastDao = object : PodcastDao {
            override fun getAllPodcastsFlow(): Flow<List<PodcastEntity>> = podcastFlow
            override fun getSubscriptionRowsFlow(): Flow<List<SubscriptionRow>> = subscriptionsFlow
            override fun getAllPodcasts(): List<PodcastEntity> = podcasts.values.toList()
            override fun getPodcastById(id: Long): PodcastEntity? = podcasts[id]
            override fun getPodcastByFeedUrl(feedUrl: String): PodcastEntity? = podcasts.values.firstOrNull { it.feedUrl == feedUrl }
            override fun insert(podcast: PodcastEntity): Long { podcasts[podcast.id] = podcast; emit(); return podcast.id }
            override fun update(podcast: PodcastEntity) { podcasts[podcast.id] = podcast; emit() }
            override fun deleteById(id: Long) { podcasts.remove(id); emit() }
            override fun deleteAll() { podcasts.clear(); emit() }
        }

        val episodeDao = object : EpisodeDao {
            override fun getEpisodesByPodcastIdFlow(podcastId: Long): Flow<List<EpisodeEntity>> = emptyFlow()
            override fun getAllEpisodesWithPodcastFlow(): Flow<List<EpisodeWithPodcast>> = emptyFlow()
            override fun getEpisodeById(id: Long): EpisodeEntity? = episodes[id]
            override fun getEpisodeWithPodcastById(id: Long): EpisodeWithPodcast? = null
            override fun getEpisodeByPodcastIdAndGuid(podcastId: Long, guid: String): EpisodeEntity? = null
            override fun getEpisodesByPodcastId(podcastId: Long): List<EpisodeEntity> = episodes.values.filter { it.podcastId == podcastId }
            override fun insertEpisode(episode: EpisodeEntity): Long { episodes[episode.id] = episode; emit(); return episode.id }
            override fun insertEpisodes(episodes: List<EpisodeEntity>): List<Long> = episodes.map { insertEpisode(it) }
            override fun update(episode: EpisodeEntity) { episodes[episode.id] = episode; emit() }
            override fun setListened(episodeId: Long, listened: Boolean) {
                episodes[episodeId]?.let { episodes[episodeId] = it.copy(isListened = listened) }
                emit()
                listenedLatch?.countDown()
            }
            override fun setAllListenedForPodcast(podcastId: Long, listened: Boolean) {}
            override fun getPlaylistEpisodeIdsForPodcast(podcastId: Long): List<Long> = emptyList()
            override fun setEpisodesListened(episodeIds: List<Long>): Int = 0
            override fun deletePlaylistEpisodes(episodeIds: List<Long>): Int = 0
            override fun updatePlaybackPosition(episodeId: Long, positionMs: Long) {}
            override fun updateDurationIfMissing(episodeId: Long, durationSeconds: Long) {}
            override fun updateDownloadState(episodeId: Long, isDownloaded: Boolean, localFilePath: String?) {
                episodes[episodeId]?.let {
                    episodes[episodeId] = it.copy(isDownloaded = isDownloaded, localFilePath = localFilePath)
                }
                emit()
            }
            override fun clearDownloadStateIfMatches(episodeId: Long, expectedIsDownloaded: Boolean, expectedLocalFilePath: String?): Int {
                val current = episodes[episodeId]
                if (current?.isDownloaded != expectedIsDownloaded || current.localFilePath != expectedLocalFilePath) return 0
                episodes[episodeId] = current.copy(isDownloaded = false, localFilePath = null)
                emit()
                cleanupLatch?.countDown()
                return 1
            }
            override fun getDownloadedEpisodes(): List<EpisodeEntity> = episodes.values.filter { it.isDownloaded }
        }

        val playlistDao = object : PlaylistDao {
            override fun getPlaylistItemsWithEpisodesFlow(): Flow<List<PlaylistItemWithEpisode>> = playlistFlow
            override fun getPlaylistItemsWithEpisodes(): List<PlaylistItemWithEpisode> = playlistFlow.value
            override fun getAllPlaylistItems(): List<PlaylistItemEntity> = playlist.values.toList()
            override fun getMaxPosition(): Int? = playlist.values.maxOfOrNull { it.position }
            override fun isEpisodeInPlaylist(episodeId: Long): Boolean = episodeId in playlist
            override fun insertPlaylistItem(item: PlaylistItemEntity): Long {
                playlist[item.episodeId] = item
                emit()
                return item.id
            }
            override fun insertPlaylistItems(items: List<PlaylistItemEntity>): List<Long> = items.map { insertPlaylistItem(it) }
            override fun removeFromPlaylist(episodeId: Long) {
                playlist.remove(episodeId)
                emit()
            }
            override fun clearPlaylist() {
                playlist.clear()
                emit()
            }
        }
    }
}
