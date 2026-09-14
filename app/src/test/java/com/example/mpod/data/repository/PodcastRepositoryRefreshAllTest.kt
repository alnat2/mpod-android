package com.example.mpod.data.repository

import com.example.mpod.data.local.dao.EpisodeDao
import com.example.mpod.data.local.dao.PodcastDao
import com.example.mpod.data.local.entity.EpisodeEntity
import com.example.mpod.data.local.entity.PodcastEntity
import com.example.mpod.data.local.model.EpisodeWithPodcast
import com.example.mpod.data.local.preferences.AppSettings
import com.example.mpod.data.local.preferences.AppSettingsDataStore
import com.example.mpod.data.network.ProxyHttpClientFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PodcastRepositoryRefreshAllTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun refreshAll_fullSuccess_recordsNewTimestampExactlyOnce() = runBlocking {
        server.respondByPath(
            "/first" to rssFeed("First updated", "first-new"),
            "/second" to rssFeed("Second updated", "second-new")
        )
        val podcasts = listOf(
            podcast(1L, "/first", "First old"),
            podcast(2L, "/second", "Second old")
        )
        val settings = RecordingAppSettingsDataStore(PREVIOUS_LAST_REFRESH)
        val repository = repository(podcasts, initialEpisodes(), settings)

        val result = repository.refreshAllPodcasts()

        assertTrue(result.isSuccess)
        assertEquals(1, settings.lastRefreshWrites.size)
        assertTrue(settings.lastRefreshWrites.single().startsWith("Last refresh today at "))
        assertFalse(settings.lastRefreshWrites.single() == PREVIOUS_LAST_REFRESH)
        assertEquals(settings.lastRefreshWrites.single(), settings.current.lastRefreshTimeFormatted)
    }

    @Test
    fun refreshAll_partialFailure_preservesTimestampAndCompleteLibrary() = runBlocking {
        server.respondByPath(
            "/healthy" to rssFeed("Healthy updated", "healthy-new"),
            "/failed" to MockResponse().setResponseCode(500).setBody("feed unavailable")
        )
        val initialPodcasts = listOf(
            podcast(1L, "/healthy", "Healthy old"),
            podcast(2L, "/failed", "Failed old")
        )
        val initialEpisodes = initialEpisodes()
        val settings = RecordingAppSettingsDataStore(PREVIOUS_LAST_REFRESH)
        val podcastDao = FakePodcastDao(initialPodcasts)
        val episodeDao = FakeEpisodeDao(initialEpisodes)
        val repository = repository(podcastDao, episodeDao, settings)

        val result = repository.refreshAllPodcasts()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("Failed old: HTTP 500"))
        assertTrue(settings.lastRefreshWrites.isEmpty())
        assertEquals(PREVIOUS_LAST_REFRESH, settings.current.lastRefreshTimeFormatted)

        assertEquals(2, podcastDao.snapshot().size)
        assertEquals("Healthy updated", podcastDao.getPodcastById(1L)?.title)
        assertEquals(initialPodcasts[1], podcastDao.getPodcastById(2L))
        assertTrue(episodeDao.snapshot().any { it.podcastId == 1L && it.guid == "healthy-new" })
        assertTrue(episodeDao.snapshot().contains(initialEpisodes[0]))
        assertEquals(
            initialEpisodes.filter { it.podcastId == 2L },
            episodeDao.snapshot().filter { it.podcastId == 2L }
        )
        assertTrue(podcastDao.deletedPodcastIds.isEmpty())
        assertEquals(0, podcastDao.deleteAllCalls)
    }

    @Test
    fun refreshAll_allFailed_preservesTimestampAndCompleteLibrary() = runBlocking {
        server.respondByPath(
            "/first-failed" to MockResponse().setResponseCode(500).setBody("first unavailable"),
            "/second-failed" to MockResponse().setResponseCode(503).setBody("second unavailable")
        )
        val initialPodcasts = listOf(
            podcast(1L, "/first-failed", "First failed"),
            podcast(2L, "/second-failed", "Second failed")
        )
        val initialEpisodes = initialEpisodes()
        val settings = RecordingAppSettingsDataStore(PREVIOUS_LAST_REFRESH)
        val podcastDao = FakePodcastDao(initialPodcasts)
        val episodeDao = FakeEpisodeDao(initialEpisodes)
        val repository = repository(podcastDao, episodeDao, settings)

        val result = repository.refreshAllPodcasts()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("First failed: HTTP 500"))
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("Second failed: HTTP 503"))
        assertTrue(settings.lastRefreshWrites.isEmpty())
        assertEquals(PREVIOUS_LAST_REFRESH, settings.current.lastRefreshTimeFormatted)
        assertEquals(initialPodcasts, podcastDao.snapshot())
        assertEquals(initialEpisodes, episodeDao.snapshot())
        assertTrue(podcastDao.deletedPodcastIds.isEmpty())
        assertEquals(0, podcastDao.deleteAllCalls)
    }

    private fun repository(
        podcasts: List<PodcastEntity>,
        episodes: List<EpisodeEntity>,
        settings: RecordingAppSettingsDataStore
    ): PodcastRepository = repository(
        FakePodcastDao(podcasts),
        FakeEpisodeDao(episodes),
        settings
    )

    private fun repository(
        podcastDao: PodcastDao,
        episodeDao: EpisodeDao,
        settings: AppSettingsDataStore
    ): PodcastRepository {
        val proxyHttpClientFactory = ProxyHttpClientFactory().apply {
            updateProxy(AppSettings(isProxyEnabled = false))
        }
        return PodcastRepository(
            podcastDao = podcastDao,
            episodeDao = episodeDao,
            appSettingsDataStore = settings,
            proxyHttpClientFactory = proxyHttpClientFactory
        )
    }

    private fun podcast(id: Long, path: String, title: String) = PodcastEntity(
        id = id,
        feedUrl = server.url(path).toString(),
        title = title,
        description = "$title description",
        lastBuildDate = "old build date",
        lastRefreshedAt = 100L + id
    )

    private fun initialEpisodes() = listOf(
        EpisodeEntity(
            id = 11L,
            podcastId = 1L,
            guid = "healthy-existing",
            title = "Healthy existing episode",
            audioUrl = "https://audio.example/healthy-existing.mp3",
            isListened = true,
            playbackPositionMs = 42_000L,
            isDownloaded = true,
            localFilePath = "/existing/healthy.mp3"
        ),
        EpisodeEntity(
            id = 22L,
            podcastId = 2L,
            guid = "failed-existing",
            title = "Failed existing episode",
            audioUrl = "https://audio.example/failed-existing.mp3",
            playbackPositionMs = 21_000L
        )
    )

    private fun rssFeed(title: String, newGuid: String) = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/rss+xml")
        .setBody(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0">
              <channel>
                <title>$title</title>
                <description>$title description</description>
                <lastBuildDate>Sun, 14 Sep 2026 20:00:00 +0000</lastBuildDate>
                <item>
                  <guid>$newGuid</guid>
                  <title>$title new episode</title>
                  <enclosure url="https://audio.example/$newGuid.mp3" type="audio/mpeg" />
                </item>
              </channel>
            </rss>
            """.trimIndent()
        )

    private fun MockWebServer.respondByPath(vararg responses: Pair<String, MockResponse>) {
        val responsesByPath = responses.toMap()
        dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                return responsesByPath[request.requestUrl?.encodedPath]
                    ?: MockResponse().setResponseCode(404)
            }
        }
    }

    private class RecordingAppSettingsDataStore(initialLastRefresh: String) : AppSettingsDataStore() {
        private val state = MutableStateFlow(
            AppSettings(lastRefreshTimeFormatted = initialLastRefresh)
        )
        override val settingsFlow: Flow<AppSettings> = state
        val lastRefreshWrites = mutableListOf<String>()
        val current: AppSettings
            get() = state.value

        override suspend fun setLastRefreshTime(formatted: String) {
            lastRefreshWrites += formatted
            state.value = state.value.copy(lastRefreshTimeFormatted = formatted)
        }
    }

    private class FakePodcastDao(initial: List<PodcastEntity>) : PodcastDao {
        private val podcasts = initial.toMutableList()
        val deletedPodcastIds = mutableListOf<Long>()
        var deleteAllCalls = 0

        override fun getAllPodcastsFlow(): Flow<List<PodcastEntity>> = emptyFlow()
        override fun getAllPodcasts(): List<PodcastEntity> = snapshot()
        override fun getPodcastById(id: Long): PodcastEntity? = podcasts.find { it.id == id }
        override fun getPodcastByFeedUrl(feedUrl: String): PodcastEntity? = podcasts.find { it.feedUrl == feedUrl }

        override fun insert(podcast: PodcastEntity): Long {
            val id = podcast.id.takeIf { it != 0L } ?: ((podcasts.maxOfOrNull { it.id } ?: 0L) + 1L)
            podcasts += podcast.copy(id = id)
            return id
        }

        override fun update(podcast: PodcastEntity) {
            val index = podcasts.indexOfFirst { it.id == podcast.id }
            if (index >= 0) podcasts[index] = podcast
        }

        override fun deleteById(id: Long) {
            deletedPodcastIds += id
            podcasts.removeAll { it.id == id }
        }

        override fun deleteAll() {
            deleteAllCalls += 1
            podcasts.clear()
        }

        fun snapshot(): List<PodcastEntity> = podcasts.toList()
    }

    private class FakeEpisodeDao(initial: List<EpisodeEntity>) : EpisodeDao {
        private val episodes = initial.toMutableList()

        override fun getEpisodesByPodcastIdFlow(podcastId: Long): Flow<List<EpisodeEntity>> = emptyFlow()
        override fun getAllEpisodesWithPodcastFlow(): Flow<List<EpisodeWithPodcast>> = emptyFlow()
        override fun getEpisodeById(id: Long): EpisodeEntity? = episodes.find { it.id == id }
        override fun getEpisodeWithPodcastById(id: Long): EpisodeWithPodcast? = null
        override fun getEpisodeByPodcastIdAndGuid(podcastId: Long, guid: String): EpisodeEntity? =
            episodes.find { it.podcastId == podcastId && it.guid == guid }

        override fun getEpisodesByPodcastId(podcastId: Long): List<EpisodeEntity> =
            episodes.filter { it.podcastId == podcastId }

        override fun insertEpisode(episode: EpisodeEntity): Long = insertEpisodes(listOf(episode)).single()

        override fun insertEpisodes(episodes: List<EpisodeEntity>): List<Long> = episodes.map { episode ->
            if (this.episodes.any { it.podcastId == episode.podcastId && it.guid == episode.guid }) {
                -1L
            } else {
                val id = episode.id.takeIf { it != 0L } ?: ((this.episodes.maxOfOrNull { it.id } ?: 0L) + 1L)
                this.episodes += episode.copy(id = id)
                id
            }
        }

        override fun update(episode: EpisodeEntity) {
            val index = episodes.indexOfFirst { it.id == episode.id }
            if (index >= 0) episodes[index] = episode
        }

        override fun setListened(episodeId: Long, listened: Boolean) {
            mutate(episodeId) { it.copy(isListened = listened) }
        }

        override fun setAllListenedForPodcast(podcastId: Long, listened: Boolean) {
            episodes.indices.forEach { index ->
                if (episodes[index].podcastId == podcastId) {
                    episodes[index] = episodes[index].copy(isListened = listened)
                }
            }
        }

        override fun updatePlaybackPosition(episodeId: Long, positionMs: Long) {
            mutate(episodeId) { it.copy(playbackPositionMs = positionMs) }
        }

        override fun updateDownloadState(episodeId: Long, isDownloaded: Boolean, localFilePath: String?) {
            mutate(episodeId) { it.copy(isDownloaded = isDownloaded, localFilePath = localFilePath) }
        }

        override fun getDownloadedEpisodes(): List<EpisodeEntity> = episodes.filter { it.isDownloaded }

        fun snapshot(): List<EpisodeEntity> = episodes.toList()

        private fun mutate(episodeId: Long, block: (EpisodeEntity) -> EpisodeEntity) {
            val index = episodes.indexOfFirst { it.id == episodeId }
            if (index >= 0) episodes[index] = block(episodes[index])
        }
    }

    private companion object {
        const val PREVIOUS_LAST_REFRESH = "Last refresh today at 07.09 22:39"
    }
}
