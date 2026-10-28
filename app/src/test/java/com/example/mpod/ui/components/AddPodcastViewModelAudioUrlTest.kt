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
        assertEquals("Enter a valid http or https audio link.", viewModel.state.value.errorMessage)
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
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(successCalled)
        assertFalse(viewModel.state.value.isSubmitting)
        assertNull(viewModel.state.value.errorMessage)
    }
}
