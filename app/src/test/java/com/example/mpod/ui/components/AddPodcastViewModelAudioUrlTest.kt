package com.example.mpod.ui.components

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import com.example.mpod.data.repository.PodcastRepository
import com.example.mpod.data.repository.SingleTrackRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock

@OptIn(ExperimentalCoroutinesApi::class)
class AddPodcastViewModelAudioUrlTest {

    private val testDispatcher = StandardTestDispatcher()
    private val context = mock(Context::class.java)
    private val podcastRepository = mock(PodcastRepository::class.java)
    private val singleTrackRepository = mock(SingleTrackRepository::class.java)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun addAudioUrl_rejectsInvalidUrl() = runTest {
        val viewModel = AddPodcastViewModel(
            context = context,
            podcastRepository = podcastRepository,
            singleTrackRepository = singleTrackRepository,
            savedStateHandle = SavedStateHandle()
        )

        var successCalled = false
        viewModel.addAudioUrl("ftp://example.com/audio.mp3") { successCalled = true }

        assertFalse(successCalled)
        assertEquals("Enter a valid audio link or file.", viewModel.state.value.errorMessage)
        assertEquals("Enter a valid audio link or file.", viewModel.bannerState.value?.message)
        assertEquals(StatusBannerType.Error, viewModel.bannerState.value?.type)
        assertFalse(viewModel.state.value.isSubmitting)
    }

    @Test
    fun addRssFeed_rejectsEmptyUrl() = runTest {
        val viewModel = AddPodcastViewModel(
            context = context,
            podcastRepository = podcastRepository,
            singleTrackRepository = singleTrackRepository,
            savedStateHandle = SavedStateHandle()
        )

        var successCalled = false
        viewModel.addRssFeed("   ") { successCalled = true }

        assertFalse(successCalled)
        assertEquals("Paste RSS feed URL.", viewModel.state.value.errorMessage)
        assertEquals("Paste RSS feed URL.", viewModel.bannerState.value?.message)
        assertEquals(StatusBannerType.Error, viewModel.bannerState.value?.type)
        assertFalse(viewModel.state.value.isSubmitting)
    }

    @Test
    fun addAudioUrl_handlesResolverFailureGracefully() = runTest {
        val failingRepo = object : SingleTrackRepository(
            podcastDao = mock(com.example.mpod.data.local.dao.PodcastDao::class.java),
            episodeDao = mock(com.example.mpod.data.local.dao.EpisodeDao::class.java),
            playlistDao = mock(com.example.mpod.data.local.dao.PlaylistDao::class.java),
            appSettingsDataStore = com.example.mpod.data.local.preferences.FakeAppSettingsStore(),
            queueInvalidator = com.example.mpod.playback.PlaybackQueueInvalidator(),
            audioLinkResolver = object : com.example.mpod.data.network.resolver.AudioLinkResolver {
                override suspend fun resolve(rawUrl: String): Result<com.example.mpod.data.network.resolver.ResolvedAudioMetadata> {
                    return Result.failure(IllegalArgumentException("Could not extract audio from this link. Make sure it's a valid podcast episode or direct audio link."))
                }
            }
        ) {
            override suspend fun playSingleTrack(urlOrUri: String, title: String?): Long {
                throw IllegalArgumentException("Could not extract audio from this link. Make sure it's a valid podcast episode or direct audio link.")
            }
        }

        val viewModel = AddPodcastViewModel(
            context = context,
            podcastRepository = podcastRepository,
            singleTrackRepository = failingRepo,
            savedStateHandle = SavedStateHandle()
        )

        var successCalled = false
        viewModel.addAudioUrl("https://example.com/broken-page") { successCalled = true }
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(successCalled)
        assertFalse(viewModel.state.value.isSubmitting)
        assertEquals(
            "Could not extract audio from this link. Make sure it's a valid podcast episode or direct audio link.",
            viewModel.state.value.errorMessage
        )
    }

    @Test
    fun addAudioUrl_success_triggersCallbackAndResets() = runTest {
        val successfulRepo = object : SingleTrackRepository(
            podcastDao = mock(com.example.mpod.data.local.dao.PodcastDao::class.java),
            episodeDao = mock(com.example.mpod.data.local.dao.EpisodeDao::class.java),
            playlistDao = mock(com.example.mpod.data.local.dao.PlaylistDao::class.java),
            appSettingsDataStore = com.example.mpod.data.local.preferences.FakeAppSettingsStore(),
            queueInvalidator = com.example.mpod.playback.PlaybackQueueInvalidator(),
            audioLinkResolver = object : com.example.mpod.data.network.resolver.AudioLinkResolver {
                override suspend fun resolve(rawUrl: String): Result<com.example.mpod.data.network.resolver.ResolvedAudioMetadata> {
                    return Result.success(com.example.mpod.data.network.resolver.ResolvedAudioMetadata(rawUrl, "Track"))
                }
            }
        ) {
            override suspend fun playSingleTrack(urlOrUri: String, title: String?): Long {
                return 42L
            }
        }

        val viewModel = AddPodcastViewModel(
            context = context,
            podcastRepository = podcastRepository,
            singleTrackRepository = successfulRepo,
            savedStateHandle = SavedStateHandle()
        )

        var successCalled = false
        viewModel.addAudioUrl("https://example.com/audio.mp3") { successCalled = true }
        testDispatcher.scheduler.runCurrent()

        assertTrue(successCalled)
        assertFalse(viewModel.state.value.isSubmitting)
        assertNull(viewModel.state.value.errorMessage)
        assertEquals(StatusBannerType.Info, viewModel.bannerState.value?.type)
        assertEquals("Audio track added to playlist", viewModel.bannerState.value?.message)

        testDispatcher.scheduler.advanceUntilIdle()
        assertNull(viewModel.bannerState.value)
    }

    @Test
    fun addAudioUrl_failure_setsErrorBannerWithRetry() = runTest {
        val failingRepo = object : SingleTrackRepository(
            podcastDao = mock(com.example.mpod.data.local.dao.PodcastDao::class.java),
            episodeDao = mock(com.example.mpod.data.local.dao.EpisodeDao::class.java),
            playlistDao = mock(com.example.mpod.data.local.dao.PlaylistDao::class.java),
            appSettingsDataStore = com.example.mpod.data.local.preferences.FakeAppSettingsStore(),
            queueInvalidator = com.example.mpod.playback.PlaybackQueueInvalidator(),
            audioLinkResolver = object : com.example.mpod.data.network.resolver.AudioLinkResolver {
                override suspend fun resolve(rawUrl: String): Result<com.example.mpod.data.network.resolver.ResolvedAudioMetadata> {
                    return Result.failure(IllegalArgumentException("Could not extract audio"))
                }
            }
        ) {
            override suspend fun playSingleTrack(urlOrUri: String, title: String?): Long {
                throw IllegalArgumentException("Could not extract audio")
            }
        }

        val viewModel = AddPodcastViewModel(
            context = context,
            podcastRepository = podcastRepository,
            singleTrackRepository = failingRepo,
            savedStateHandle = SavedStateHandle()
        )

        viewModel.addAudioUrl("https://example.com/bad-link") {}
        testDispatcher.scheduler.advanceUntilIdle()

        val banner = viewModel.bannerState.value
        assertEquals(StatusBannerType.Error, banner?.type)
        assertEquals("Could not extract audio", banner?.message)
        assertEquals("Try again", banner?.actionLabel)
    }

    @Test
    fun addAudioUrl_showsLoadingBannerImmediately() = runTest {
        var resolveStarted = false
        val slowRepo = object : SingleTrackRepository(
            podcastDao = mock(com.example.mpod.data.local.dao.PodcastDao::class.java),
            episodeDao = mock(com.example.mpod.data.local.dao.EpisodeDao::class.java),
            playlistDao = mock(com.example.mpod.data.local.dao.PlaylistDao::class.java),
            appSettingsDataStore = com.example.mpod.data.local.preferences.FakeAppSettingsStore(),
            queueInvalidator = com.example.mpod.playback.PlaybackQueueInvalidator(),
            audioLinkResolver = object : com.example.mpod.data.network.resolver.AudioLinkResolver {
                override suspend fun resolve(rawUrl: String): Result<com.example.mpod.data.network.resolver.ResolvedAudioMetadata> {
                    return Result.success(com.example.mpod.data.network.resolver.ResolvedAudioMetadata(rawUrl, "Track"))
                }
            }
        ) {
            override suspend fun playSingleTrack(urlOrUri: String, title: String?): Long {
                resolveStarted = true
                kotlinx.coroutines.delay(5000L)
                return 42L
            }
        }

        val viewModel = AddPodcastViewModel(
            context = context,
            podcastRepository = podcastRepository,
            singleTrackRepository = slowRepo,
            savedStateHandle = SavedStateHandle()
        )

        viewModel.addAudioUrl("https://example.com/slow-stream.mp3", "Shared Title")
        assertEquals(StatusBannerType.Loading, viewModel.bannerState.value?.type)
        assertEquals("Resolving audio link...", viewModel.bannerState.value?.message)
        assertTrue(viewModel.state.value.isSubmitting)

        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(resolveStarted)
        assertEquals(StatusBannerType.Info, viewModel.bannerState.value?.type)
        assertEquals("Audio track added to playlist", viewModel.bannerState.value?.message)
    }

    @Test
    fun retryLastAction_retriesWithOriginalTitle() = runTest {
        var attempts = 0
        var receivedTitle: String? = null
        val repo = object : SingleTrackRepository(
            podcastDao = mock(com.example.mpod.data.local.dao.PodcastDao::class.java),
            episodeDao = mock(com.example.mpod.data.local.dao.EpisodeDao::class.java),
            playlistDao = mock(com.example.mpod.data.local.dao.PlaylistDao::class.java),
            appSettingsDataStore = com.example.mpod.data.local.preferences.FakeAppSettingsStore(),
            queueInvalidator = com.example.mpod.playback.PlaybackQueueInvalidator(),
            audioLinkResolver = object : com.example.mpod.data.network.resolver.AudioLinkResolver {
                override suspend fun resolve(rawUrl: String): Result<com.example.mpod.data.network.resolver.ResolvedAudioMetadata> {
                    return Result.success(com.example.mpod.data.network.resolver.ResolvedAudioMetadata(rawUrl, "Track"))
                }
            }
        ) {
            override suspend fun playSingleTrack(urlOrUri: String, title: String?): Long {
                attempts++
                receivedTitle = title
                if (attempts == 1) {
                    throw IllegalArgumentException("Temporary network timeout")
                }
                return 42L
            }
        }

        val viewModel = AddPodcastViewModel(
            context = context,
            podcastRepository = podcastRepository,
            singleTrackRepository = repo,
            savedStateHandle = SavedStateHandle()
        )

        viewModel.addAudioUrl("https://example.com/track.mp3", "Important Podcast")
        testDispatcher.scheduler.runCurrent()

        assertEquals(1, attempts)
        assertEquals(StatusBannerType.Error, viewModel.bannerState.value?.type)
        assertEquals("Try again", viewModel.bannerState.value?.actionLabel)

        viewModel.retryLastAction()
        testDispatcher.scheduler.runCurrent()

        assertEquals(2, attempts)
        assertEquals("Important Podcast", receivedTitle)
        assertEquals(StatusBannerType.Info, viewModel.bannerState.value?.type)
    }

    @Test
    fun addAudioUrl_acceptsContentUri_callsRepositoryWithUriAndTitle() = runTest {
        var calledUri: String? = null
        var calledTitle: String? = null
        val repo = object : SingleTrackRepository(
            podcastDao = mock(com.example.mpod.data.local.dao.PodcastDao::class.java),
            episodeDao = mock(com.example.mpod.data.local.dao.EpisodeDao::class.java),
            playlistDao = mock(com.example.mpod.data.local.dao.PlaylistDao::class.java),
            appSettingsDataStore = com.example.mpod.data.local.preferences.FakeAppSettingsStore(),
            queueInvalidator = com.example.mpod.playback.PlaybackQueueInvalidator(),
            audioLinkResolver = object : com.example.mpod.data.network.resolver.AudioLinkResolver {
                override suspend fun resolve(rawUrl: String): Result<com.example.mpod.data.network.resolver.ResolvedAudioMetadata> {
                    return Result.success(com.example.mpod.data.network.resolver.ResolvedAudioMetadata(rawUrl, "Local Audio"))
                }
            }
        ) {
            override suspend fun playSingleTrack(urlOrUri: String, title: String?): Long {
                calledUri = urlOrUri
                calledTitle = title
                return 77L
            }
        }

        val viewModel = AddPodcastViewModel(
            context = context,
            podcastRepository = podcastRepository,
            singleTrackRepository = repo,
            savedStateHandle = SavedStateHandle()
        )

        var successCalled = false
        val contentUri = "content://media/external/audio/media/100"
        viewModel.addAudioUrl(contentUri, "My Shared Voice Note") { successCalled = true }
        testDispatcher.scheduler.runCurrent()

        assertTrue(successCalled)
        assertEquals(contentUri, calledUri)
        assertEquals("My Shared Voice Note", calledTitle)
        assertEquals(StatusBannerType.Info, viewModel.bannerState.value?.type)
        assertEquals("Audio track added to playlist", viewModel.bannerState.value?.message)
    }

    @Test
    fun addAudioUrl_acceptsFileUri_callsRepositoryWithUriAndTitle() = runTest {
        var calledUri: String? = null
        var calledTitle: String? = null
        val repo = object : SingleTrackRepository(
            podcastDao = mock(com.example.mpod.data.local.dao.PodcastDao::class.java),
            episodeDao = mock(com.example.mpod.data.local.dao.EpisodeDao::class.java),
            playlistDao = mock(com.example.mpod.data.local.dao.PlaylistDao::class.java),
            appSettingsDataStore = com.example.mpod.data.local.preferences.FakeAppSettingsStore(),
            queueInvalidator = com.example.mpod.playback.PlaybackQueueInvalidator(),
            audioLinkResolver = object : com.example.mpod.data.network.resolver.AudioLinkResolver {
                override suspend fun resolve(rawUrl: String): Result<com.example.mpod.data.network.resolver.ResolvedAudioMetadata> {
                    return Result.success(com.example.mpod.data.network.resolver.ResolvedAudioMetadata(rawUrl, "File Audio"))
                }
            }
        ) {
            override suspend fun playSingleTrack(urlOrUri: String, title: String?): Long {
                calledUri = urlOrUri
                calledTitle = title
                return 88L
            }
        }

        val viewModel = AddPodcastViewModel(
            context = context,
            podcastRepository = podcastRepository,
            singleTrackRepository = repo,
            savedStateHandle = SavedStateHandle()
        )

        var successCalled = false
        val fileUri = "file:///storage/emulated/0/Download/recording.m4a"
        viewModel.addAudioUrl(fileUri, "Downloaded Audio") { successCalled = true }
        testDispatcher.scheduler.runCurrent()

        assertTrue(successCalled)
        assertEquals(fileUri, calledUri)
        assertEquals("Downloaded Audio", calledTitle)
        assertEquals(StatusBannerType.Info, viewModel.bannerState.value?.type)
    }

    @Test
    fun addAudioUrl_sequentialQueue_processesSecondTaskOnlyAfterFirstFinishes() = runTest {
        val executionOrder = mutableListOf<String>()
        val firstStarted = kotlinx.coroutines.CompletableDeferred<Unit>()
        val allowFirstToFinish = kotlinx.coroutines.CompletableDeferred<Unit>()

        val repo = object : SingleTrackRepository(
            podcastDao = mock(com.example.mpod.data.local.dao.PodcastDao::class.java),
            episodeDao = mock(com.example.mpod.data.local.dao.EpisodeDao::class.java),
            playlistDao = mock(com.example.mpod.data.local.dao.PlaylistDao::class.java),
            appSettingsDataStore = com.example.mpod.data.local.preferences.FakeAppSettingsStore(),
            queueInvalidator = com.example.mpod.playback.PlaybackQueueInvalidator(),
            audioLinkResolver = object : com.example.mpod.data.network.resolver.AudioLinkResolver {
                override suspend fun resolve(rawUrl: String): Result<com.example.mpod.data.network.resolver.ResolvedAudioMetadata> {
                    return Result.success(com.example.mpod.data.network.resolver.ResolvedAudioMetadata(rawUrl, "Track"))
                }
            }
        ) {
            override suspend fun playSingleTrack(urlOrUri: String, title: String?): Long {
                if (urlOrUri.contains("track1")) {
                    executionOrder.add("start1")
                    firstStarted.complete(Unit)
                    allowFirstToFinish.await()
                    executionOrder.add("end1")
                    return 1L
                } else {
                    executionOrder.add("start2")
                    executionOrder.add("end2")
                    return 2L
                }
            }
        }

        val viewModel = AddPodcastViewModel(
            context = context,
            podcastRepository = podcastRepository,
            singleTrackRepository = repo,
            savedStateHandle = SavedStateHandle()
        )

        var success1Called = false
        var success2Called = false

        viewModel.addAudioUrl("https://example.com/track1.mp3") { success1Called = true }
        viewModel.addAudioUrl("https://example.com/track2.mp3") { success2Called = true }

        testDispatcher.scheduler.runCurrent()
        assertTrue(firstStarted.isCompleted)
        assertEquals(listOf("start1"), executionOrder)
        assertFalse("Second task must not start before first task completes", success2Called)

        allowFirstToFinish.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("start1", "end1", "start2", "end2"), executionOrder)
        assertTrue(success1Called)
        assertTrue(success2Called)
    }

    @Test
    fun addAudioUrl_independentErrorHandling_failureOnFirstDoesNotBlockSecond() = runTest {
        val completedUris = mutableListOf<String>()

        val repo = object : SingleTrackRepository(
            podcastDao = mock(com.example.mpod.data.local.dao.PodcastDao::class.java),
            episodeDao = mock(com.example.mpod.data.local.dao.EpisodeDao::class.java),
            playlistDao = mock(com.example.mpod.data.local.dao.PlaylistDao::class.java),
            appSettingsDataStore = com.example.mpod.data.local.preferences.FakeAppSettingsStore(),
            queueInvalidator = com.example.mpod.playback.PlaybackQueueInvalidator(),
            audioLinkResolver = object : com.example.mpod.data.network.resolver.AudioLinkResolver {
                override suspend fun resolve(rawUrl: String): Result<com.example.mpod.data.network.resolver.ResolvedAudioMetadata> {
                    return Result.success(com.example.mpod.data.network.resolver.ResolvedAudioMetadata(rawUrl, "Track"))
                }
            }
        ) {
            override suspend fun playSingleTrack(urlOrUri: String, title: String?): Long {
                if (urlOrUri.contains("bad")) {
                    throw IllegalArgumentException("Network error on bad track")
                }
                completedUris.add(urlOrUri)
                return 42L
            }
        }

        val viewModel = AddPodcastViewModel(
            context = context,
            podcastRepository = podcastRepository,
            singleTrackRepository = repo,
            savedStateHandle = SavedStateHandle()
        )

        var success1Called = false
        var success2Called = false

        viewModel.addAudioUrl("https://example.com/bad.mp3") { success1Called = true }
        viewModel.addAudioUrl("https://example.com/good.mp3") { success2Called = true }

        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(success1Called)
        assertTrue("Second task must succeed even if first task failed", success2Called)
        assertEquals(listOf("https://example.com/good.mp3"), completedUris)
        assertEquals(StatusBannerType.Info, viewModel.bannerState.value?.type)
    }
}
