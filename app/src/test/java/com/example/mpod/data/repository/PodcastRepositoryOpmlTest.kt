package com.example.mpod.data.repository

import com.example.mpod.data.local.dao.EpisodeDao
import com.example.mpod.data.local.dao.PodcastDao
import com.example.mpod.data.local.entity.EpisodeEntity
import com.example.mpod.data.local.entity.PodcastEntity
import com.example.mpod.data.local.model.EpisodeWithPodcast
import com.example.mpod.data.local.preferences.AppSettings
import com.example.mpod.data.local.preferences.AppSettingsDataStore
import com.example.mpod.data.network.ProxyHttpClientFactory
import com.example.mpod.playback.PlaybackQueueInvalidator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.atomic.AtomicInteger

class PodcastRepositoryOpmlTest {

    private lateinit var fakePodcastDao: FakePodcastDao
    private lateinit var fakeEpisodeDao: FakeEpisodeDao
    private lateinit var fakeAppSettingsDataStore: FakeAppSettingsDataStore
    private lateinit var proxyHttpClientFactory: ProxyHttpClientFactory
    private lateinit var repository: PodcastRepository

    @Before
    fun setUp() {
        fakePodcastDao = FakePodcastDao()
        fakeEpisodeDao = FakeEpisodeDao()
        fakeAppSettingsDataStore = FakeAppSettingsDataStore()
        proxyHttpClientFactory = ProxyHttpClientFactory()

        repository = PodcastRepository(
            podcastDao = fakePodcastDao,
            episodeDao = fakeEpisodeDao,
            appSettingsDataStore = fakeAppSettingsDataStore,
            proxyHttpClientFactory = proxyHttpClientFactory,
            smartListeningManager = repositoryCleanupManager(fakeEpisodeDao, proxyHttpClientFactory),
            queueInvalidator = PlaybackQueueInvalidator()
        )
    }

    @Test
    fun importOpml_readError_returnsFailure_andDoesNotTouchDatabaseOrParser() = runBlocking {
        val failingStream = object : InputStream() {
            override fun read(): Int {
                throw IOException("Simulated provider read failure")
            }
        }

        val result = repository.importOpml(failingStream)

        assertTrue("Expected failure result when stream throws IOException", result.isFailure)
        assertTrue(result.exceptionOrNull() is IOException)
        assertEquals(0, fakePodcastDao.insertCount.get())
        assertEquals(0, fakeEpisodeDao.insertCount.get())
    }

    @Test
    fun importOpml_oversizedInput_returnsFailure_andDoesNotTouchDatabase() = runBlocking {
        val oversizedData = ByteArray(PodcastRepository.MAX_OPML_SIZE_BYTES + 10) { '<'.code.toByte() }
        val stream = ByteArrayInputStream(oversizedData)

        val result = repository.importOpml(stream)

        assertTrue("Expected failure result for oversized input", result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue(exception is IllegalStateException)
        assertEquals("OPML file too large (max 5 MB).", exception?.message)
        assertEquals(0, fakePodcastDao.insertCount.get())
        assertEquals(0, fakeEpisodeDao.insertCount.get())
    }

    @Test
    fun importOpml_oneByteOverLimit_isRejectedBeforeDatabaseOrNetwork() = runBlocking {
        val oneByteOver = ByteArray(PodcastRepository.MAX_OPML_SIZE_BYTES + 1) { '<'.code.toByte() }
        val stream = ByteArrayInputStream(oneByteOver)

        val result = repository.importOpml(stream)

        assertTrue("5MB + 1 byte must be rejected", result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue(exception is IllegalStateException)
        assertEquals("OPML file too large (max 5 MB).", exception?.message)
        assertEquals(0, fakePodcastDao.insertCount.get())
        assertEquals(0, fakeEpisodeDao.insertCount.get())
    }

    @Test
    fun importOpml_exactLimit_isDeterministicAndReturnsSuccess() = runBlocking {
        val prefix = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><opml version=\"2.0\"><head><title>Feeds</title></head><body><!--"
        val suffix = "--></body></opml>"
        val paddingNeeded = PodcastRepository.MAX_OPML_SIZE_BYTES - prefix.length - suffix.length
        val opmlContent = prefix + " ".repeat(maxOf(0, paddingNeeded)) + suffix
        val exactData = opmlContent.toByteArray(Charsets.UTF_8)
        assertEquals(PodcastRepository.MAX_OPML_SIZE_BYTES, exactData.size)

        val stream = ByteArrayInputStream(exactData)
        val result = repository.importOpml(stream)

        assertTrue("Exact 5MB valid OPML should succeed deterministically without network calls", result.isSuccess)
        val summary = result.getOrNull()
        org.junit.Assert.assertNotNull(summary)
        assertEquals(0, summary?.imported)
        assertEquals(0, summary?.skipped)
        assertEquals(0, summary?.errors?.size)
        assertEquals(0, fakePodcastDao.insertCount.get())
        assertEquals(0, fakeEpisodeDao.insertCount.get())
    }

    class FakeAppSettingsDataStore : AppSettingsDataStore() {
        val stateFlow = MutableStateFlow(AppSettings())
        override val settingsFlow: Flow<AppSettings> = stateFlow
    }

    class FakePodcastDao : PodcastDao {
        val insertCount = AtomicInteger(0)
        private val podcasts = mutableListOf<PodcastEntity>()

        override fun getAllPodcastsFlow(): Flow<List<PodcastEntity>> = emptyFlow()
        override fun getAllPodcasts(): List<PodcastEntity> = podcasts.toList()
        override fun getPodcastById(id: Long): PodcastEntity? = podcasts.find { it.id == id }
        override fun getPodcastByFeedUrl(feedUrl: String): PodcastEntity? = podcasts.find { it.feedUrl == feedUrl }
        override fun insert(podcast: PodcastEntity): Long {
            insertCount.incrementAndGet()
            val id = podcasts.size.toLong() + 1
            podcasts.add(podcast.copy(id = id))
            return id
        }
        override fun update(podcast: PodcastEntity) {}
        override fun deleteById(id: Long) {}
        override fun deleteAll() {}
    }

    class FakeEpisodeDao : EpisodeDao {
        val insertCount = AtomicInteger(0)
        private val episodes = mutableListOf<EpisodeEntity>()

        override fun getEpisodesByPodcastIdFlow(podcastId: Long): Flow<List<EpisodeEntity>> = emptyFlow()
        override fun getAllEpisodesWithPodcastFlow(): Flow<List<EpisodeWithPodcast>> = emptyFlow()
        override fun getEpisodeById(id: Long): EpisodeEntity? = episodes.find { it.id == id }
        override fun getEpisodeWithPodcastById(id: Long): EpisodeWithPodcast? = null
        override fun getEpisodeByPodcastIdAndGuid(podcastId: Long, guid: String): EpisodeEntity? = null
        override fun getEpisodesByPodcastId(podcastId: Long): List<EpisodeEntity> = episodes.filter { it.podcastId == podcastId }
        override fun insertEpisode(episode: EpisodeEntity): Long {
            insertCount.incrementAndGet()
            episodes.add(episode)
            return episodes.size.toLong()
        }
        override fun insertEpisodes(episodes: List<EpisodeEntity>): List<Long> {
            insertCount.addAndGet(episodes.size)
            this.episodes.addAll(episodes)
            return episodes.indices.map { it.toLong() }
        }
        override fun update(episode: EpisodeEntity) {}
        override fun setListened(episodeId: Long, listened: Boolean) {}
        override fun setAllListenedForPodcast(podcastId: Long, listened: Boolean) {}
        override fun getPlaylistEpisodeIdsForPodcast(podcastId: Long): List<Long> = emptyList()
        override fun setEpisodesListened(episodeIds: List<Long>): Int = error("OPML does not mark listened")
        override fun deletePlaylistEpisodes(episodeIds: List<Long>): Int = error("OPML does not delete playlist")
        override fun updatePlaybackPosition(episodeId: Long, positionMs: Long) {}
        override fun updateDownloadState(episodeId: Long, isDownloaded: Boolean, localFilePath: String?) {}
        override fun clearDownloadStateIfMatches(episodeId: Long, expectedIsDownloaded: Boolean, expectedLocalFilePath: String?): Int = 0
        override fun getDownloadedEpisodes(): List<EpisodeEntity> = episodes.filter { it.isDownloaded }
    }
}
