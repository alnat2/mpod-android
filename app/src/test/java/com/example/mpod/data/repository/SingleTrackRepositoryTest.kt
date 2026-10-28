package com.example.mpod.data.repository

import com.example.mpod.data.local.dao.EpisodeDao
import com.example.mpod.data.local.dao.PlaylistDao
import com.example.mpod.data.local.dao.PodcastDao
import com.example.mpod.data.local.entity.EpisodeEntity
import com.example.mpod.data.local.entity.PlaylistItemEntity
import com.example.mpod.data.local.entity.PodcastEntity
import com.example.mpod.data.local.model.PlaylistItemWithEpisode
import com.example.mpod.data.local.preferences.FakeAppSettingsStore
import com.example.mpod.playback.PlaybackQueueInvalidator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Proxy

@OptIn(ExperimentalCoroutinesApi::class)
class SingleTrackRepositoryTest {

    private val podcasts = mutableMapOf<Long, PodcastEntity>()
    private val episodes = mutableMapOf<Long, EpisodeEntity>()
    private val playlistItems = mutableListOf<PlaylistItemWithEpisode>()
    private var podcastIdCounter = 1L
    private var episodeIdCounter = 100L
    private var playlistIdCounter = 1000L

    private val fakeAppSettingsStore = FakeAppSettingsStore()
    private val fakeQueueInvalidator = PlaybackQueueInvalidator()
    private val fakeAudioLinkResolver = object : com.example.mpod.data.network.resolver.AudioLinkResolver {
        var failureFor: String? = null
        var customMetadata: com.example.mpod.data.network.resolver.ResolvedAudioMetadata? = null

        override suspend fun resolve(rawUrl: String): Result<com.example.mpod.data.network.resolver.ResolvedAudioMetadata> {
            if (rawUrl == failureFor) {
                return Result.failure(IllegalArgumentException("Could not extract audio from link: $rawUrl"))
            }
            return Result.success(
                customMetadata ?: com.example.mpod.data.network.resolver.ResolvedAudioMetadata(
                    directAudioUrl = rawUrl,
                    title = rawUrl.substringAfterLast('/').substringBeforeLast('.').replace('-', ' ').replace('_', ' ')
                )
            )
        }
    }

    private lateinit var podcastDao: PodcastDao
    private lateinit var episodeDao: EpisodeDao
    private lateinit var playlistDao: PlaylistDao
    private lateinit var repository: SingleTrackRepository

    @Before
    fun setup() {
        podcasts.clear()
        episodes.clear()
        playlistItems.clear()
        fakeAudioLinkResolver.failureFor = null
        fakeAudioLinkResolver.customMetadata = null

        podcastDao = createPodcastDao()
        episodeDao = createEpisodeDao()
        playlistDao = createPlaylistDao()

        repository = SingleTrackRepository(
            podcastDao = podcastDao,
            episodeDao = episodeDao,
            playlistDao = playlistDao,
            appSettingsDataStore = fakeAppSettingsStore,
            queueInvalidator = fakeQueueInvalidator,
            audioLinkResolver = fakeAudioLinkResolver
        )
    }

    @Test
    fun getOrCreateSingleTracksPodcastId_createsContainerIfMissing() = runTest {
        val id = repository.getOrCreateSingleTracksPodcastId()
        assertTrue(id > 0)

        val podcast = podcasts[id]
        assertNotNull(podcast)
        assertEquals(SingleTrackRepository.SINGLE_TRACKS_FEED_URL, podcast?.feedUrl)
        assertEquals(SingleTrackRepository.SINGLE_TRACKS_PODCAST_TITLE, podcast?.title)

        // Calling it again returns the same ID
        val secondId = repository.getOrCreateSingleTracksPodcastId()
        assertEquals(id, secondId)
    }

    @Test
    fun playSingleTrack_insertsEpisodePrependsToPlaylistAndSetsActive() = runTest {
        var invalidationCount = 0
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            fakeQueueInvalidator.events.collect { invalidationCount++ }
        }

        // Given an existing item in the playlist
        val existingEpisode = EpisodeEntity(
            id = 50L,
            podcastId = 1L,
            guid = "existing-ep",
            title = "Existing Episode",
            audioUrl = "https://example.com/existing.mp3"
        )
        episodes[50L] = existingEpisode
        playlistItems.add(
            PlaylistItemWithEpisode(
                playlistItemId = 1L,
                position = 0,
                episode = existingEpisode,
                podcastTitle = "Podcast",
                podcastArtworkUrl = ""
            )
        )

        // When playing a single track
        val episodeId = repository.playSingleTrack(
            urlOrUri = "https://example.com/audio/my-special-song.mp3",
            title = "My Special Song"
        )

        // Then episode was inserted with correct metadata
        val inserted = episodes[episodeId]
        assertNotNull(inserted)
        assertEquals("My Special Song", inserted?.title)
        assertEquals("https://example.com/audio/my-special-song.mp3", inserted?.audioUrl)

        // Playlist reordered: single track at position 0, existing shifted to position 1
        assertEquals(2, playlistItems.size)
        assertEquals(episodeId, playlistItems[0].episode.id)
        assertEquals(0, playlistItems[0].position)
        assertEquals(50L, playlistItems[1].episode.id)
        assertEquals(1, playlistItems[1].position)

        // Active episode set in preferences
        val settings = fakeAppSettingsStore.settingsFlow.first()
        assertEquals(episodeId, settings.activeEpisodeId)

        // Invalidator notified
        assertEquals(1, invalidationCount)
    }

    @Test
    fun playSingleTrack_derivesTitleWhenNull() = runTest {
        val episodeId = repository.playSingleTrack(
            urlOrUri = "https://feeds.example.com/episodes/great_tech_talk.mp3",
            title = null
        )

        val inserted = episodes[episodeId]
        assertNotNull(inserted)
        assertEquals("great tech talk", inserted?.title)
    }

    @Test
    fun playSingleTrack_reusesExistingEpisodeIfSameUrl() = runTest {
        val firstId = repository.playSingleTrack("https://example.com/track.mp3", "Track 1")
        val secondId = repository.playSingleTrack("https://example.com/track.mp3", "Track 1")

        assertEquals(firstId, secondId)
        assertEquals(1, episodes.size)
        assertEquals(1, playlistItems.size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun playSingleTrack_failedResolution_throwsAndDoesNotTouchDatabaseOrPlaylist() = runTest {
        val brokenUrl = "https://example.com/bad-link"
        fakeAudioLinkResolver.failureFor = brokenUrl

        try {
            repository.playSingleTrack(brokenUrl)
        } finally {
            assertTrue(episodes.isEmpty())
            assertTrue(playlistItems.isEmpty())
        }
    }

    @Test
    fun playSingleTrack_withResolvedPodcastMetadata_createsSpecificContainerAndArtwork() = runTest {
        val sharedUrl = "https://podcasts.apple.com/us/podcast/id123?i=456"
        val directMp3 = "https://cdn.example.com/episode.mp3"
        fakeAudioLinkResolver.customMetadata = com.example.mpod.data.network.resolver.ResolvedAudioMetadata(
            directAudioUrl = directMp3,
            title = "Episode 42: Deep Dive",
            author = "Tech Network",
            podcastTitle = "The Tech Show",
            artworkUrl = "https://cdn.example.com/art600.jpg",
            durationSeconds = 1800,
            description = "Great conversation"
        )

        val episodeId = repository.playSingleTrack(sharedUrl)

        val ep = episodes[episodeId]
        assertNotNull(ep)
        assertEquals(directMp3, ep?.audioUrl)
        assertEquals("Episode 42: Deep Dive", ep?.title)
        assertEquals(1800L, ep?.durationSeconds)

        val podcast = podcasts[ep?.podcastId]
        assertNotNull(podcast)
        assertEquals("The Tech Show", podcast?.title)
        assertEquals("Tech Network", podcast?.author)
        assertEquals("https://cdn.example.com/art600.jpg", podcast?.artworkUrl)
    }

    @Suppress("UNCHECKED_CAST")
    private fun createPodcastDao(): PodcastDao {
        return Proxy.newProxyInstance(
            PodcastDao::class.java.classLoader,
            arrayOf(PodcastDao::class.java)
        ) { _, method, args ->
            when (method.name) {
                "getPodcastByFeedUrl" -> {
                    val url = args[0] as String
                    podcasts.values.firstOrNull { it.feedUrl == url }
                }
                "insert" -> {
                    val entity = args[0] as PodcastEntity
                    val id = podcastIdCounter++
                    podcasts[id] = entity.copy(id = id)
                    id
                }
                else -> null
            }
        } as PodcastDao
    }

    @Suppress("UNCHECKED_CAST")
    private fun createEpisodeDao(): EpisodeDao {
        return Proxy.newProxyInstance(
            EpisodeDao::class.java.classLoader,
            arrayOf(EpisodeDao::class.java)
        ) { _, method, args ->
            when (method.name) {
                "getEpisodeByPodcastIdAndGuid" -> {
                    val podcastId = args[0] as Long
                    val guid = args[1] as String
                    episodes.values.firstOrNull { it.podcastId == podcastId && it.guid == guid }
                }
                "insertEpisode" -> {
                    val entity = args[0] as EpisodeEntity
                    val id = episodeIdCounter++
                    episodes[id] = entity.copy(id = id)
                    id
                }
                else -> null
            }
        } as EpisodeDao
    }

    @Suppress("UNCHECKED_CAST")
    private fun createPlaylistDao(): PlaylistDao {
        return Proxy.newProxyInstance(
            PlaylistDao::class.java.classLoader,
            arrayOf(PlaylistDao::class.java)
        ) { _, method, args ->
            when (method.name) {
                "getPlaylistItemsWithEpisodes" -> {
                    playlistItems.toList()
                }
                "reorderPlaylist" -> {
                    val newItems = args[0] as List<PlaylistItemEntity>
                    playlistItems.clear()
                    newItems.forEach { item ->
                        val ep = episodes[item.episodeId] ?: error("Episode not found")
                        val id = if (item.id != 0L) item.id else playlistIdCounter++
                        playlistItems.add(
                            PlaylistItemWithEpisode(
                                playlistItemId = id,
                                position = item.position,
                                episode = ep,
                                podcastTitle = "Audio",
                                podcastArtworkUrl = ""
                            )
                        )
                    }
                    null
                }
                else -> null
            }
        } as PlaylistDao
    }
}
