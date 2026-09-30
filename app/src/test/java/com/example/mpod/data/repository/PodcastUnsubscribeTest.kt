package com.example.mpod.data.repository

import android.content.ContextWrapper
import androidx.lifecycle.viewModelScope
import com.example.mpod.data.local.dao.*
import com.example.mpod.data.local.entity.*
import com.example.mpod.data.local.model.PlaylistItemWithEpisode
import com.example.mpod.data.local.model.subscriptionRows
import com.example.mpod.data.local.preferences.AppSettings
import com.example.mpod.data.local.preferences.FakeAppSettingsStore
import com.example.mpod.data.network.ProxyHttpClientFactory
import com.example.mpod.playback.*
import com.example.mpod.ui.screens.subscriptions.SubscriptionsViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.FilterOutputStream
import java.io.OutputStream
import java.lang.reflect.Proxy
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class PodcastUnsubscribeTest {
    private lateinit var server: MockWebServer
    private lateinit var directory: File
    private lateinit var manager: SmartListeningManager
    private lateinit var repository: PodcastRepository
    private lateinit var viewModel: SubscriptionsViewModel
    private lateinit var invalidator: PlaybackQueueInvalidator
    private val rows = ConcurrentHashMap<Long, EpisodeEntity>()
    private val queue = ConcurrentHashMap.newKeySet<Long>()
    private val podcastFlow = MutableStateFlow(listOf(PodcastEntity(id = 1, title = "Podcast", feedUrl = "https://example.com/feed")))
    private val playlistFlow = MutableStateFlow(emptyList<PlaylistItemWithEpisode>())
    private val releases = mutableListOf<CountDownLatch>()
    private var deleteGate: (() -> Unit)? = null
    private var downloadedWrites = 0
    private var active: Long? = 101

    @Before fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        server = MockWebServer().apply { start() }
        directory = Files.createTempDirectory("unsubscribe-").toFile()
        rows[101] = EpisodeEntity(id = 101, podcastId = 1, guid = "101", title = "Episode", audioUrl = server.url("/audio.mp3").toString())
        rows[201] = rows.getValue(101).copy(id = 201, podcastId = 2, guid = "201")
        queue.addAll(rows.keys)
        val episodes = dao<EpisodeDao> { name, args -> when (name) {
            "getEpisodeById" -> rows[args[0]]
            "getEpisodesByPodcastId" -> rows.values.filter { it.podcastId == args[0] }
            "updateDownloadState" -> { downloadedWrites++; rows.computeIfPresent(args[0] as Long) { _, ep -> ep.copy(isDownloaded = args[1] as Boolean, localFilePath = args[2] as String?) }; null }
            "clearDownloadStateIfMatches" -> {
                val id = args[0] as Long
                val ep = rows[id]
                if (ep != null && ep.isDownloaded == args[1] && ep.localFilePath == args[2]) {
                    rows[id] = ep.copy(isDownloaded = false, localFilePath = null); 1
                } else 0
            }
            else -> error("Unexpected episode DAO call: $name")
        } }
        val playlist = dao<PlaylistDao> { name, args -> when (name) {
            "getPlaylistItemsWithEpisodesFlow" -> playlistFlow
            "isEpisodeInPlaylist" -> args[0] in queue
            "getPlaylistItemsWithEpisodes" -> items()
            else -> error("Unexpected playlist DAO call: $name")
        } }
        val podcasts = dao<PodcastDao> { name, args -> when (name) {
            "getSubscriptionRowsFlow" -> podcastFlow.map { subscriptionRows(it, rows.values.toList(), queue.toSet()) }
            "deleteById" -> {
                deleteGate?.invoke()
                val ids = rows.values.filter { it.podcastId == args[0] }.map { it.id }
                ids.forEach { rows.remove(it); queue.remove(it) }
                podcastFlow.value = podcastFlow.value.filter { it.id != args[0] }
                playlistFlow.value = items()
                null
            }
            else -> error("Unexpected podcast DAO call: $name")
        } }
        val client = ProxyHttpClientFactory().apply { updateProxy(AppSettings(isProxyEnabled = false)) }
        manager = SmartListeningManager(object : ContextWrapper(null) { override fun getFilesDir() = directory }, playlist, episodes, client)
        manager.debounceMs = 0
        val settings = object : FakeAppSettingsStore() {
            override suspend fun getActiveEpisodeId() = active
            override suspend fun setActiveEpisodeId(episodeId: Long?) { active = episodeId }
        }
        invalidator = PlaybackQueueInvalidator()
        repository = PodcastRepository(podcasts, episodes, settings, client, manager, invalidator)
        viewModel = SubscriptionsViewModel(podcasts, episodes, playlist, repository, PlaylistRepository(playlist), invalidator, manager)
    }

    @After fun teardown() {
        releases.forEach { it.countDown() }
        viewModel.viewModelScope.cancel()
        val owners = rows.keys.mapNotNull { manager.getPendingDownloadJob(it) }
        runBlocking {
            manager.stopObserving()
            owners.forEach { it.join() }
        }
        server.shutdown()
        directory.deleteRecursively()
        Dispatchers.resetMain()
    }

    @Test fun unsubscribeBeforeRoomCommit_joinsOwner_andLeavesNoLateStateOrFiles() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        manager.preDaoHook = { entered.complete(Unit); awaitCancellation() }
        startDownload(101)
        withTimeout(5000) { entered.await() }
        assertNull(rows.getValue(101).localFilePath)
        val owner = manager.getPendingDownloadJob(101)!!
        val reconciled = async(start = CoroutineStart.UNDISPATCHED) {
            invalidator.events.first()
            resolveQueuePlaybackTarget(queue.map { QueueEpisodeState(it, 0) }, active, 101, 1200, true)
        }
        repository.unsubscribe(1)
        val target = withTimeout(5000) { reconciled.await() }!!
        assertEquals(201L, target.episodeId)
        assertFalse("Removed current episode must not keep playing", target.playWhenReady)
        assertNull(active)
        assertTrue(owner.isCompleted)
        assertEquals(0, downloadedWrites)
        assertGone()
    }

    @Test fun unsubscribeDuringCallback_waitsForActualWriterBeforeDeletingRoom() = runBlocking {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1).also { releases += it }
        manager.fileOps = object : FileOperations {
            override fun openOutputStream(file: File): OutputStream = object : FilterOutputStream(file.outputStream()) {
                override fun write(b: ByteArray, off: Int, len: Int) {
                    entered.countDown()
                    check(release.await(5, TimeUnit.SECONDS))
                    out.write(b, off, len)
                }
            }
        }
        startDownload(101)
        assertTrue(withContext(Dispatchers.IO) { entered.await(5, TimeUnit.SECONDS) })
        val owner = manager.getPendingDownloadJob(101)!!
        val unsubscribe = async(Dispatchers.IO) { repository.unsubscribe(1) }
        withTimeout(5000) { while (!owner.isCancelled && !unsubscribe.isCompleted) yield() }
        try {
            assertFalse("Callback is still writing, so unsubscribe must wait", unsubscribe.isCompleted)
            assertTrue(rows.containsKey(101))
        } finally { release.countDown() }
        withTimeout(5000) { unsubscribe.await() }
        assertTrue(owner.isCompleted)
        assertEquals(0, downloadedWrites)
        assertGone()
    }

    @Test fun staleQueueSnapshot_cannotRestartDuringDelete_orAfterRoomDeletion_otherOwnerUnaffected() = runBlocking {
        val otherEntered = CompletableDeferred<Unit>()
        manager.preDaoHook = { otherEntered.complete(Unit); awaitCancellation() }
        startDownload(201)
        withTimeout(5000) { otherEntered.await() }
        val otherOwner = manager.getPendingDownloadJob(201)!!
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1).also { releases += it }
        deleteGate = { entered.countDown(); check(release.await(5, TimeUnit.SECONDS)) }
        val unsubscribe = async(Dispatchers.IO) { repository.unsubscribe(1) }
        assertTrue(withContext(Dispatchers.IO) { entered.await(5, TimeUnit.SECONDS) })
        try {
            schedule(101) // Same entry point used by an observer retaining an old snapshot.
            assertNull("Suppression remains active until DELETE finishes", manager.getPendingDownloadJob(101))
            assertFalse(otherOwner.isCancelled)
        } finally { release.countDown() }
        withTimeout(5000) { unsubscribe.await() }
        schedule(101)
        assertNull(manager.getPendingDownloadJob(101))
        assertTrue(rows.containsKey(201))
        assertFalse(otherOwner.isCancelled)
    }

    @Test fun savedFileDeleteFailure_surfacesInUi_preservesOwnership_andRetrySucceeds() = runBlocking {
        withTimeout(5000) { viewModel.state.first { it.hasLoadedOnce } }
        val file = File(directory, "saved.mp3").apply { writeText("audio") }
        rows[101] = rows.getValue(101).copy(isDownloaded = true, localFilePath = file.absolutePath)
        manager.fileOps = object : FileOperations { override fun delete(file: File) = false }
        viewModel.unsubscribePodcastNow(1)
        withTimeout(5000) { viewModel.state.first { it.actionErrorMessage != null && it.unsubscribingPodcastIds.isEmpty() } }
        assertEquals(file.absolutePath, rows.getValue(101).localFilePath)
        assertTrue(file.exists())
        assertEquals(101L, active)
        manager.fileOps = DefaultFileOperations
        // Keep this test source compilable against the baseline for FAIL-before runs.
        viewModel.javaClass.getDeclaredMethod("retryLastAction").invoke(viewModel)
        withTimeout(5000) { viewModel.state.first { it.unsubscribingPodcastIds.isEmpty() && it.podcasts.isEmpty() } }
        assertFalse(file.exists())
        assertNull(active)
        assertNull(viewModel.state.value.actionErrorMessage)
        assertGone()
    }

    @Test fun unlinkedFileDeleteFailure_keepsPodcastForRetry_andReleasesSchedulingGuard() = runBlocking {
        val file = File(directory, "podcasts/ep_101_orphan.mp3.tmp").apply { parentFile!!.mkdirs(); writeText("audio") }
        manager.fileOps = object : FileOperations { override fun delete(file: File) = throw java.io.IOException("locked") }
        try { repository.unsubscribe(1); fail("Cleanup failure must fail unsubscribe") } catch (_: java.io.IOException) { }
        assertTrue(rows.containsKey(101))
        assertTrue(file.exists())
        manager.debounceMs = 60_000
        schedule(101)
        assertNotNull("Guard must be released after failure", manager.getPendingDownloadJob(101))
        manager.fileOps = DefaultFileOperations
        repository.unsubscribe(1)
        assertFalse(file.exists())
        assertGone()
    }

    @Test fun unsubscribeAfterRoomCommit_cleansSavedFile_andPreservesOtherActiveEpisode() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        manager.postDaoHook = { entered.complete(Unit); awaitCancellation() }
        startDownload(101)
        withTimeout(5000) { entered.await() }
        val path = rows.getValue(101).localFilePath!!
        active = 201
        repository.unsubscribe(1)
        assertFalse(File(path).exists())
        assertEquals(201L, active)
        assertGone()
    }

    @Test fun cancelledUnsubscribe_releasesGuard_andAllowsRetry() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        manager.preDaoHook = { withContext(NonCancellable) { entered.complete(Unit); release.await() } }
        startDownload(101)
        withTimeout(5000) { entered.await() }
        val owner = manager.getPendingDownloadJob(101)!!
        val unsubscribe = async(Dispatchers.IO) { repository.unsubscribe(1) }
        try {
            withTimeout(5000) { while (!owner.isCancelled) yield() }
            unsubscribe.cancelAndJoin()
            assertTrue(rows.containsKey(101))
        } finally { release.complete(Unit) }
        withTimeout(5000) { owner.join() }
        manager.debounceMs = 60_000
        schedule(101)
        assertNotNull(manager.getPendingDownloadJob(101))
        repository.unsubscribe(1)
        assertGone()
    }

    @Test fun undoWithinWindow_keepsRoomAndFiles_andDoesNotCancelDownload() = runBlocking {
        withTimeout(5000) { viewModel.state.first { it.hasLoadedOnce } }
        manager.debounceMs = 60_000
        schedule(101)
        val owner = manager.getPendingDownloadJob(101)!!
        val file = File(directory, "saved.mp3").apply { writeText("audio") }
        viewModel.schedulePodcastUnsubscribe(1)
        assertEquals(15, viewModel.state.value.pendingUnsubscribe!!.secondsRemaining)
        viewModel.undoPodcastUnsubscribe(1)
        assertNull(viewModel.state.value.pendingUnsubscribe)
        assertTrue(rows.containsKey(101))
        assertTrue(file.exists())
        assertFalse(owner.isCancelled)
    }

    private fun startDownload(id: Long) {
        server.enqueue(MockResponse().setBody("audio body"))
        schedule(id)
    }
    private fun schedule(id: Long) {
        // Reflection lets this regression run unchanged against the private baseline entry point.
        SmartListeningManager::class.java.getDeclaredMethod(
            "scheduleDebouncedDownload", Long::class.javaPrimitiveType, String::class.java, Long::class.javaObjectType
        ).apply { isAccessible = true }.invoke(manager, id, server.url("/audio.mp3").toString(), null)
    }
    private fun items() = queue.mapNotNull { id -> rows[id]?.let { PlaylistItemWithEpisode(id, 0, it, "Podcast", "") } }
    private fun assertGone() {
        assertFalse(rows.containsKey(101))
        assertFalse(101L in queue)
        assertNull(manager.getPendingDownloadJob(101))
        assertTrue(File(directory, "podcasts").listFiles().orEmpty().none { it.name.startsWith("ep_101_") })
        assertTrue(rows.containsKey(201))
    }
    private inline fun <reified T> dao(crossinline handler: (String, Array<out Any?>) -> Any?): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, args -> handler(method.name, args.orEmpty()) } as T
}
