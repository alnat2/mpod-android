package com.example.mpod.ui.screens.subscriptions

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.mpod.data.local.MpodDatabase
import com.example.mpod.data.local.entity.EpisodeEntity
import com.example.mpod.data.local.entity.PodcastEntity
import com.example.mpod.data.local.preferences.AppSettings
import com.example.mpod.data.local.preferences.AppSettingsDataStore
import com.example.mpod.data.network.ProxyHttpClientFactory
import com.example.mpod.data.repository.PlaylistRepository
import com.example.mpod.data.repository.PodcastRepository
import com.example.mpod.playback.*
import com.example.mpod.ui.theme.MpodTheme
import kotlinx.coroutines.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit

/** Real UI gestures/countdown, Room FK cascade and OkHttp body streaming on an emulator. */
class UnsubscribeDownloadTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var db: MpodDatabase
    private lateinit var server: MockWebServer
    private lateinit var directory: File
    private lateinit var manager: SmartListeningManager
    private lateinit var vm: SubscriptionsViewModel
    @Volatile private var active: Long? = 101

    @Before fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        directory = File(context.cacheDir, "unsubscribe-${System.nanoTime()}").apply { mkdirs() }
        db = Room.inMemoryDatabaseBuilder(context, MpodDatabase::class.java).build()
        server = MockWebServer().apply { start() }
        db.podcastDao().insert(PodcastEntity(id = 1, title = "Slow podcast", feedUrl = server.url("/feed").toString()))
        db.episodeDao().insertEpisode(EpisodeEntity(id = 101, podcastId = 1, guid = "slow", title = "Slow episode", audioUrl = server.url("/audio.mp3").toString()))
        val client = ProxyHttpClientFactory().apply { updateProxy(AppSettings(isProxyEnabled = false)) }
        manager = SmartListeningManager(object : ContextWrapper(context) { override fun getFilesDir() = directory }, db.playlistDao(), db.episodeDao(), client)
        manager.debounceMs = 0
        val settings = object : AppSettingsDataStore() {
            override suspend fun getActiveEpisodeId() = active
            override suspend fun setActiveEpisodeId(episodeId: Long?) { active = episodeId }
        }
        val invalidator = PlaybackQueueInvalidator()
        val repo = PodcastRepository(db.podcastDao(), db.episodeDao(), settings, client, manager, invalidator)
        compose.runOnUiThread {
            vm = SubscriptionsViewModel(db.podcastDao(), db.episodeDao(), db.playlistDao(), repo, PlaylistRepository(db.playlistDao()), invalidator, manager)
        }
    }

    private fun showScreen() {
        compose.setContent {
            val state by vm.state.collectAsState()
            MpodTheme {
                SubscriptionsScreen(state = state,
                    onUnsubscribePodcast = vm::schedulePodcastUnsubscribe,
                    onUndoPodcastUnsubscribe = vm::undoPodcastUnsubscribe,
                    onAddEpisodeToPlaylist = vm::addEpisodeToPlaylist,
                    onRetryRefresh = vm::retryLastAction)
            }
        }
        compose.waitUntil(5000) { vm.state.value.hasLoadedOnce }
    }

    @After fun teardown() {
        val owner = manager.getPendingDownloadJob(101)
        val observer = manager.activeObservationJobForTest
        compose.runOnUiThread { vm.viewModelScope.cancel() }
        manager.stopObserving()
        runBlocking { withTimeout(5000) { owner?.join(); observer?.join() } }
        server.shutdown()
        db.close()
        directory.deleteRecursively()
    }

    @Test fun slowBody_undoThenFinalUnsubscribe_removesRoomQueueAndFiles() {
        server.enqueue(MockResponse().setBody(Buffer().write(ByteArray(128 * 1024) { 42 }))
            .throttleBody(1024, 250, TimeUnit.MILLISECONDS))
        manager.startObserving()
        showScreen()
        compose.onNodeWithContentDescription("Add Slow episode to playlist").performClick()
        val downloads = File(directory, "podcasts")
        compose.waitUntil(10000) { downloads.listFiles().orEmpty().any { it.name.endsWith(".tmp") && it.length() > 0 } }
        val owner = manager.getPendingDownloadJob(101)!!
        compose.onNodeWithText("Unsubscribe").performClick()
        compose.onNodeWithText("Undo").assertIsDisplayed().performClick()
        assertNotNull(db.podcastDao().getPodcastById(1))
        assertFalse(owner.isCancelled)
        compose.onNodeWithText("Unsubscribe").performClick()
        compose.waitUntil(25000) { vm.state.value.hasLoadedOnce && vm.state.value.podcasts.isEmpty() && vm.state.value.unsubscribingPodcastIds.isEmpty() }
        compose.onNodeWithText("No podcasts").assertIsDisplayed()
        assertTrue(owner.isCompleted)
        assertNull(db.podcastDao().getPodcastById(1))
        assertNull(db.episodeDao().getEpisodeById(101))
        assertTrue(db.playlistDao().getAllPlaylistItems().isEmpty())
        assertTrue(downloads.listFiles().orEmpty().isEmpty())
        assertNull(active)
        assertEquals(1, server.requestCount)
    }

    @Test fun deleteFailure_keepsRealRoomLink_andUiRetryRepeatsUnsubscribe() {
        val file = File(directory, "podcasts/ep_101_saved.mp3").apply { parentFile!!.mkdirs(); writeText("audio") }
        db.episodeDao().updateDownloadState(101, true, file.absolutePath)
        manager.fileOps = object : FileOperations { override fun delete(file: File) = false }
        showScreen()
        compose.onNodeWithText("Unsubscribe").performClick()
        compose.waitUntil(25000) { vm.state.value.actionErrorMessage != null && vm.state.value.unsubscribingPodcastIds.isEmpty() }
        assertEquals(file.absolutePath, db.episodeDao().getEpisodeById(101)!!.localFilePath)
        assertTrue(file.exists())
        assertEquals(101L, active)
        manager.fileOps = DefaultFileOperations
        compose.onNodeWithText("Try again").performClick()
        compose.waitUntil(5000) { vm.state.value.podcasts.isEmpty() && vm.state.value.unsubscribingPodcastIds.isEmpty() }
        compose.onNodeWithText("No podcasts").assertIsDisplayed()
        assertNull(db.podcastDao().getPodcastById(1))
        assertNull(db.episodeDao().getEpisodeById(101))
        assertFalse(file.exists())
        assertNull(active)
    }
}
