package com.example.mpod.ui.screens.subscriptions

import android.content.ContextWrapper
import androidx.lifecycle.viewModelScope
import com.example.mpod.data.local.dao.EpisodeDao
import com.example.mpod.data.local.dao.MarkAllListenedSnapshot
import com.example.mpod.data.local.dao.PlaylistDao
import com.example.mpod.data.local.dao.PodcastDao
import com.example.mpod.data.local.entity.EpisodeEntity
import com.example.mpod.data.local.entity.PlaylistItemEntity
import com.example.mpod.data.local.entity.PodcastEntity
import com.example.mpod.data.local.model.EpisodeWithPodcast
import com.example.mpod.data.local.model.PlaylistItemWithEpisode
import com.example.mpod.data.local.preferences.AppSettings
import com.example.mpod.data.local.preferences.AppSettingsDataStore
import com.example.mpod.data.network.ProxyHttpClientFactory
import com.example.mpod.data.repository.PlaylistRepository
import com.example.mpod.data.repository.PodcastRepository
import com.example.mpod.playback.CleanupResult
import com.example.mpod.playback.FileOperations
import com.example.mpod.playback.PlaybackQueueInvalidator
import com.example.mpod.playback.QueueEpisodeState
import com.example.mpod.playback.SmartListeningManager
import com.example.mpod.playback.resolveQueuePlaybackTarget
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * FAIL-before/PASS-after regression runs the real ViewModel/repositories/cleanup, not a copy.
 * Baseline gate preceded its bulk UPDATE; now it precedes the concrete transaction's lock.
 * Ownership is the returned operation snapshot, not affected-row counts or mandatory zero-row
 * DELETE calls. Issued SQL binds are separately checked, and queued owned IDs must disappear.
 * Synthetic prune/queued-new-episode tests are boundary stress, NOT current RSS refresh behavior.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SubscriptionsMarkAllListenedConsistencyTest {
    private lateinit var server: MockWebServer
    private lateinit var directory: File
    private lateinit var store: Store
    private lateinit var repository: PodcastRepository
    private lateinit var manager: SmartListeningManager
    private lateinit var invalidator: PlaybackQueueInvalidator
    private lateinit var viewModel: SubscriptionsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        server = MockWebServer().apply { start() }
        directory = Files.createTempDirectory("mpod-mark-all-").toFile()
        store = Store(PodcastEntity(id = 1, title = "Podcast", feedUrl = server.url("/feed").toString()))
        store.episodes.insertEpisodes(listOf(episode(101), episode(102)))
        val client = ProxyHttpClientFactory().apply { updateProxy(AppSettings(isProxyEnabled = false)) }
        manager = SmartListeningManager(object : ContextWrapper(null) {
            override fun getFilesDir(): File = directory
        }, store.playlist, store.episodes, client)
        invalidator = PlaybackQueueInvalidator()
        repository = PodcastRepository(store.podcasts, store.episodes, AppSettingsDataStore(), client, manager, invalidator)
        viewModel = SubscriptionsViewModel(store.podcasts, store.episodes, store.playlist,
            repository, PlaylistRepository(store.playlist), invalidator, manager)
    }

    @After
    fun tearDown() {
        store.bulkGate?.release?.countDown()
        store.downloadGate?.release?.countDown()
        store.afterTransactionGate?.release?.countDown()
        store.cleanupReadGate?.release?.countDown()
        viewModel.viewModelScope.cancel()
        manager.stopObserving()
        runBlocking { queueCollectors.forEach { it.cancel(); it.join() } }
        Dispatchers.resetMain()
        server.shutdown()
        directory.deleteRecursively()
    }

    @Test
    fun realFeedInsertionBetweenSnapshotAndBulk_usesOneOwnershipSet() = runBlocking {
        awaitLoaded()
        val events = recordQueueEvents()
        store.bulkGate = Gate()
        val markJobs = launchMarkAll()
        store.bulkGate!!.awaitEntered()
        refreshOneNewEpisode()
        val added = store.snapshot().single { it.guid == "new-guid" }
        assertFalse("Real refresh inserts an unlistened episode", added.isListened)
        assertFalse("Real refresh must not invent a download", added.isDownloaded)
        assertFalse("Real refresh must not invent playlist rows", store.playlist.isEpisodeInPlaylist(added.id))
        store.bulkGate!!.release.countDown()
        withTimeout(TIMEOUT) { markJobs.forEach { it.join() } }
        val queue = store.playlist.getPlaylistItemsWithEpisodes().map { it.episode.id }
        assertEquals("No queue invalidation when no playlist row changed", 0, events.count)
        println("REAL_INSERT snapshot=[101,102], bulk=${store.bulkTargets()}, playlistTargets=${store.removalTargets()}, cleanupTargets=${store.cleanupTargets()}, queue=$queue")
        assertOwnershipConsistent()
    }

    @Test
    fun realFeedInsertionAfterCommittedSnapshot_remainsUnlistenedAndOutsideCleanup() = runBlocking {
        seedPlaylistAndFiles()
        awaitLoaded()
        val events = recordQueueEvents()
        store.afterTransactionGate = Gate()
        val markJobs = launchMarkAll()
        store.afterTransactionGate!!.awaitEntered()
        refreshOneNewEpisode()
        val added = store.snapshot().single { it.guid == "new-guid" }
        store.afterTransactionGate!!.release.countDown()
        withTimeout(TIMEOUT) { markJobs.forEach { it.join() } }
        assertEquals(emptyList<Long>(), events.next())
        assertFalse("Post-snapshot feed insertion remains unlistened", store.snapshot().single { it.id == added.id }.isListened)
        assertFalse("Post-snapshot new ID is outside cleanup ownership", added.id in store.cleanupTargets())
        assertEquals(setOf(101L, 102L), store.operationTargets().toSet())
        assertOwnershipConsistent()
    }

    @Test
    fun syntheticQueuedDownloadedInsertion_afterRealRefresh_hasNoStaleQueueOrFile() = runBlocking {
        seedPlaylistAndFiles()
        awaitLoaded()
        val events = recordQueueEvents()
        store.bulkGate = Gate()
        viewModel.markAllListened(1)
        store.bulkGate!!.awaitEntered()
        refreshOneNewEpisode()
        val added = store.snapshot().single { it.guid == "new-guid" }
        // Explicit additional synthetic concurrent user/download state, not RSS insertion behavior.
        seedPlaylistAndFiles(listOf(added.id))
        store.bulkGate!!.release.countDown()
        val queue = events.next()
        val stale = queue.filter { id -> store.snapshot().any { it.id == id && it.isListened } }
        println("SYNTHETIC_QUEUE added=${added.id}, listened=${store.bulkTargets()}, queue=$queue, stale=$stale, remainingFiles=${directory.listFiles()?.map { it.name }}")
        assertTrue("No listened operation-owned episode may remain queued; stale=$stale", stale.isEmpty())
        assertFalse("Operation-owned new audio file must not remain", File(directory, "${added.id}.mp3").exists())
        assertOwnershipConsistent()
    }

    @Test
    fun syntheticPruneAndReplacement_betweenSnapshotAndBulk_usesOneOwnershipSet() = runBlocking {
        seedPlaylistAndFiles()
        awaitLoaded()
        val events = recordQueueEvents()
        store.bulkGate = Gate()
        viewModel.markAllListened(1)
        store.bulkGate!!.awaitEntered()
        // Current refresh never prunes. Model a raw DAO boundary stress with FK playlist cascade.
        store.replaceForStress(102, episode(103))
        store.bulkGate!!.release.countDown()
        val queue = events.next()
        println("SYNTHETIC_PRUNE snapshot=[101,102], bulk=${store.bulkTargets()}, playlistTargets=${store.removalTargets()}, cleanupTargets=${store.cleanupTargets()}, queue=$queue, orphan102=${File(directory, "102.mp3").exists()}")
        assertOwnershipConsistent()
    }

    @Test
    fun settledRepeat_isNoOp_dueToExistingViewModelGuard() = runBlocking {
        seedPlaylistAndFiles()
        awaitLoaded()
        val events = recordQueueEvents()
        viewModel.markAllListened(1)
        assertEquals(emptyList<Long>(), events.next())
        withTimeout(TIMEOUT) { viewModel.state.first { it.podcasts.single().unlistenedEpisodeCount == 0 } }
        val before = store.records()
        viewModel.markAllListened(1)
        assertEquals("Settled repeat must not dispatch any work", before, store.records())
        assertEquals("No extra invalidation on settled no-op", 1, events.count)
    }

    @Test
    fun immediateDoubleDispatch_doesNotDuplicateCleanupOrMutation() = runBlocking {
        seedPlaylistAndFiles()
        awaitLoaded()
        val events = recordQueueEvents()
        store.bulkGate = Gate()
        val initialJobs = viewModel.viewModelScope.coroutineContext.job.children.toSet()
        viewModel.markAllListened(1)
        viewModel.markAllListened(1)
        val markJobs = viewModel.viewModelScope.coroutineContext.job.children.filter { it !in initialJobs }.toList()
        store.bulkGate!!.awaitEntered()
        store.bulkGate!!.release.countDown()
        withTimeout(TIMEOUT) { markJobs.forEach { it.join() } }
        events.next()
        println("DOUBLE_DISPATCH records=${store.records()}, invalidations=${events.count}")
        assertEquals("Immediate double dispatch must perform one useful bulk mutation", 1, store.bulkCalls())
        assertEquals("Cleanup ownership must not be dispatched twice", listOf(101L, 102L), store.cleanupTargets().sorted())
        assertEquals("One useful queue invalidation", 1, events.count)
    }

    @Test
    fun deleteFailure_preservesDownloadState_andDirectCleanupRetryWorks() = runBlocking {
        seedPlaylistAndFiles(listOf(101))
        awaitLoaded()
        val path = store.snapshot().single { it.id == 101L }.localFilePath!!
        manager.fileOps = object : FileOperations { override fun delete(file: File) = false }
        assertEquals(CleanupResult.DeleteFailed(path), manager.cleanupEpisodeFile(101))
        assertTrue(store.snapshot().single { it.id == 101L }.isDownloaded)
        val events = recordQueueEvents()
        viewModel.markAllListened(1)
        events.next()
        assertTrue("Failed cleanup must preserve Room/file consistency", store.snapshot().single { it.id == 101L }.isDownloaded)
        assertTrue(File(path).exists())
        assertNull("Existing UI strings remain unchanged", viewModel.state.value.actionErrorMessage)
        manager.fileOps = object : FileOperations {}
        assertEquals(CleanupResult.Success, manager.cleanupEpisodeFile(101))
        assertFalse(store.snapshot().single { it.id == 101L }.isDownloaded)
        assertNull(store.snapshot().single { it.id == 101L }.localFilePath)
        assertFalse(File(path).exists())
    }

    @Test
    fun repositoryDeleteFailure_isTypedAndRetryOnlyCleansRemainingFile() = runBlocking {
        seedPlaylistAndFiles(listOf(101))
        val path = store.snapshot().single { it.id == 101L }.localFilePath!!
        val events = recordQueueEvents()
        manager.fileOps = object : FileOperations { override fun delete(file: File) = false }
        val first = repository.markAllEpisodesListened(1)
        assertEquals(setOf(101L, 102L), first.targetEpisodeIds.toSet())
        assertEquals(setOf(101L, 102L), first.listenedEpisodeIds.toSet())
        assertEquals(listOf(101L), first.removedPlaylistEpisodeIds)
        assertEquals(101L, first.cleanupFailures.single().episodeId)
        assertEquals(path, first.cleanupFailures.single().path)
        assertEquals(emptyList<Long>(), events.next())
        val mutationsBefore = store.queryRecords()
        val cleanupBefore = store.cleanupTargets().size
        manager.fileOps = object : FileOperations {}
        val retry = repository.markAllEpisodesListened(1)
        assertEquals(listOf(101L), retry.targetEpisodeIds)
        assertTrue(retry.listenedEpisodeIds.isEmpty())
        assertTrue(retry.removedPlaylistEpisodeIds.isEmpty())
        assertTrue(retry.cleanupFailures.isEmpty())
        assertEquals("Retry has no UPDATE/DELETE queries", mutationsBefore, store.queryRecords())
        assertEquals("Retry cleans only failed file ownership", listOf(101L), store.cleanupTargets().drop(cleanupBefore))
        assertFalse(File(path).exists())
        assertFalse(store.snapshot().single { it.id == 101L }.isDownloaded)
        assertEquals("Retry has no useful queue change", 1, events.count)
    }

    @Test
    fun repositorySettledAndEmptyPodcastWork_areStrictNoOps() = runBlocking {
        val events = recordQueueEvents()
        repository.markAllEpisodesListened(1)
        val before = store.records()
        val settled = repository.markAllEpisodesListened(1)
        val empty = repository.markAllEpisodesListened(999)
        assertTrue(settled.targetEpisodeIds.isEmpty())
        assertTrue(empty.targetEpisodeIds.isEmpty())
        assertEquals("No UPDATE/DELETE/download reset/cleanup on empty work", before, store.records())
        assertEquals("No invalidation for unqueued episodes or no-op", 0, events.count)
    }

    @Test
    fun alreadyListenedQueuedAndFlagOnlyDownloaded_workIsCleanedWithoutListenMutations() = runBlocking {
        store.episodes.update(episode(101).copy(isListened = true))
        store.episodes.update(episode(102).copy(isListened = true, isDownloaded = true))
        store.playlist.insertPlaylistItem(PlaylistItemEntity(episodeId = 101, position = 0))
        val events = recordQueueEvents()
        val result = repository.markAllEpisodesListened(1)
        assertEquals(setOf(101L, 102L), result.targetEpisodeIds.toSet())
        assertTrue("No needless listened UPDATE", result.listenedEpisodeIds.isEmpty())
        assertEquals(0, store.bulkCalls())
        assertEquals(listOf(101L), result.removedPlaylistEpisodeIds)
        assertEquals(setOf(101L, 102L), store.cleanupTargets().toSet())
        assertFalse("Downloaded flag without a file is not stale after cleanup", store.snapshot().single { it.id == 102L }.isDownloaded)
        assertEquals(emptyList<Long>(), events.next())
    }

    @Test
    fun cleanupException_isObservableAndCommittedQueueStillInvalidates() = runBlocking {
        seedPlaylistAndFiles(listOf(101))
        manager.fileOps = object : FileOperations { override fun delete(file: File): Boolean = throw IOException("simulated delete error") }
        val events = recordQueueEvents()
        val result = repository.markAllEpisodesListened(1)
        assertEquals("simulated delete error", result.cleanupFailures.single().error)
        assertEquals(101L, result.cleanupFailures.single().episodeId)
        assertTrue("Failed cleanup remains file-backed for retry", store.snapshot().single { it.id == 101L }.isDownloaded)
        assertEquals(emptyList<Long>(), events.next())
        assertEquals(1, events.count)
    }

    @Test
    fun largeFeed_usesBoundedSqlBatchesWithoutLosingOwnership() = runBlocking {
        store.episodes.insertEpisodes((1_000L..2_000L).map { episode(it) })
        val all = store.snapshot().map { it.id }
        store.playlist.insertPlaylistItems(all.mapIndexed { i, id -> PlaylistItemEntity(episodeId = id, position = i) })
        val events = recordQueueEvents()
        val result = repository.markAllEpisodesListened(1)
        assertEquals(1_003, result.targetEpisodeIds.size)
        assertEquals(all.toSet(), result.listenedEpisodeIds.toSet())
        assertEquals(all.toSet(), result.removedPlaylistEpisodeIds.toSet())
        assertTrue(store.batchSizes().all { it in 1..900 })
        assertEquals(all.toSet(), store.bulkTargets().toSet())
        assertEquals(all.toSet(), store.deleteQueryTargets().toSet())
        assertEquals(emptyList<Long>(), events.next())
        assertOwnershipConsistent()
    }

    @Test
    fun anotherPodcastAndItsPlaylistRows_areOutsideOperationOwnership() = runBlocking {
        val other = episode(301).copy(podcastId = 2)
        store.episodes.insertEpisode(other)
        store.playlist.insertPlaylistItem(PlaylistItemEntity(episodeId = 301, position = 0))
        val result = repository.markAllEpisodesListened(1)
        assertFalse(301L in result.targetEpisodeIds)
        assertEquals(other, store.snapshot().single { it.id == 301L })
        assertTrue(store.playlist.isEpisodeInPlaylist(301))
        assertFalse(301L in store.cleanupTargets())
    }

    @Test
    fun observerRemovesJobBeforeCleanup_noLateDownloadCommitOrFile() = runBlocking {
        store.episodes.update(episode(101).copy(audioUrl = server.url("/101.mp3").toString()))
        server.enqueue(MockResponse().setBody("actual audio bytes"))
        store.downloadGate = Gate()
        manager.debounceMs = 0
        manager.startObserving()
        store.playlist.insertPlaylistItem(PlaylistItemEntity(episodeId = 101, position = 0))
        store.downloadGate!!.awaitEntered()
        val actualJob = manager.getPendingDownloadJob(101)
        assertNotNull("Capture real observer-owned job before removal", actualJob)
        store.playlist.removeFromPlaylist(101)
        withTimeout(TIMEOUT) { while (!actualJob!!.isCancelled) yield() }
        assertFalse("DAO commit is parked; job has not finalized", actualJob!!.isCompleted)
        // Record/capture the cleanup's actual Room read, independently of download setup reads.
        store.cleanupReadGate = Gate()
        val missingOwner = manager.getPendingDownloadJob(101) == null
        val cleanup = async(Dispatchers.IO) { manager.cleanupEpisodeFile(101) }
        if (missingOwner) {
            // Old registry removal: force cleanup to consume the pre-commit snapshot first.
            store.cleanupReadGate!!.awaitEntered()
            store.cleanupReadGate!!.release.countDown()
            assertEquals(CleanupResult.Success, withTimeout(TIMEOUT) { cleanup.await() })
            store.downloadGate!!.release.countDown()
        } else {
            // Retained owner: cleanup must join before reading the post-commit snapshot.
            store.downloadGate!!.release.countDown()
            store.cleanupReadGate!!.awaitEntered()
            store.cleanupReadGate!!.release.countDown()
        }
        assertEquals(CleanupResult.Success, withTimeout(TIMEOUT) { cleanup.await() })
        withTimeout(TIMEOUT) { actualJob.join() }
        val ep = store.snapshot().single { it.id == 101L }
        println("OBSERVER_COMMIT after cleanup: downloaded=${ep.isDownloaded}, path=${ep.localFilePath}, exists=${ep.localFilePath?.let { File(it).exists() }}")
        assertFalse("Cleanup must prevent late downloaded=true commit by removed observer job", ep.isDownloaded)
        assertTrue("Cleanup must leave no finalized download file", File(directory, "podcasts").listFiles().orEmpty().isEmpty())
    }

    @Test
    fun cancelledEpisodeReaddedWhileCommitFinishes_keepsOldOwnerUntilItCanReschedule() = runBlocking {
        store.episodes.update(episode(101).copy(audioUrl = server.url("/101.mp3").toString()))
        server.enqueue(MockResponse().setBody("audio bytes"))
        store.downloadGate = Gate()
        manager.debounceMs = 0
        manager.startObserving()
        store.playlist.insertPlaylistItem(PlaylistItemEntity(episodeId = 101, position = 0))
        store.downloadGate!!.awaitEntered()
        val oldJob = manager.getPendingDownloadJob(101)!!
        store.playlist.removeFromPlaylist(101)
        withTimeout(TIMEOUT) { while (!oldJob.isCancelled) yield() }
        store.playlist.insertPlaylistItem(PlaylistItemEntity(episodeId = 101, position = 0))
        assertSame("Re-add cannot hide unfinished cancelled job from cleanup", oldJob, manager.getPendingDownloadJob(101))
        store.downloadGate!!.release.countDown()
        withTimeout(TIMEOUT) {
            oldJob.join()
            while (manager.getPendingDownloadJob(101) != null) yield()
        }
        assertEquals("Committed old file avoids a duplicate HTTP download", 1, server.requestCount)
        assertTrue("Re-added queued episode retains a valid download", store.snapshot().single { it.id == 101L }.isDownloaded)
        assertEquals(CleanupResult.Success, manager.cleanupEpisodeFile(101))
    }

    @Test
    fun repositoryCleansActiveQueuedDownload_beforeDownloadedStateWasCommitted() = runBlocking {
        store.episodes.update(episode(101).copy(isListened = true, audioUrl = server.url("/101.mp3").toString()))
        store.episodes.update(episode(102).copy(isListened = true))
        server.enqueue(MockResponse().setBody("audio before queued cancellation"))
        store.downloadGate = Gate()
        manager.debounceMs = 0
        manager.startObserving()
        store.playlist.insertPlaylistItem(PlaylistItemEntity(episodeId = 101, position = 0))
        store.downloadGate!!.awaitEntered()
        val download = manager.getPendingDownloadJob(101)!!
        assertFalse("Queue-owned active download is not yet committed", store.snapshot().single { it.id == 101L }.isDownloaded)
        assertNull(store.snapshot().single { it.id == 101L }.localFilePath)
        val events = recordQueueEvents()
        store.afterTransactionGate = Gate()
        val mark = async(Dispatchers.IO) { repository.markAllEpisodesListened(1) }
        store.afterTransactionGate!!.awaitEntered()
        withTimeout(TIMEOUT) { while (!download.isCancelled) yield() }
        store.afterTransactionGate!!.release.countDown()
        store.downloadGate!!.release.countDown()
        val result = withTimeout(TIMEOUT) { mark.await() }
        assertEquals("Queued already-listened ID still owns active cleanup", listOf(101L), result.targetEpisodeIds)
        assertTrue(result.listenedEpisodeIds.isEmpty())
        assertEquals(listOf(101L), result.removedPlaylistEpisodeIds)
        assertTrue(result.cleanupFailures.isEmpty())
        assertTrue("Cleanup awaited actual download completion", download.isCompleted)
        assertEquals(emptyList<Long>(), events.next())
        assertFalse(store.snapshot().single { it.id == 101L }.isDownloaded)
        assertNull(store.snapshot().single { it.id == 101L }.localFilePath)
        assertTrue(File(directory, "podcasts").listFiles().orEmpty().isEmpty())
    }

    @Test
    fun cleanupWithoutMetadata_readdedDownloadCommitsBeforeCleanupReturns_remainsConsistent() = runBlocking {
        awaitLoaded()
        store.episodes.update(episode(101).copy(audioUrl = server.url("/101.mp3").toString()))
        server.enqueue(MockResponse().setBody("readded audio"))
        manager.debounceMs = 0
        manager.startObserving()
        val read = Gate()
        store.cleanupReadGate = read
        val cleanup = async(Dispatchers.IO) { manager.cleanupEpisodeFile(101) }
        try {
            read.awaitEntered() // Cleanup has consumed false/null, after its job lookup/join.
            store.cleanupReadGate = null // New download setup reads must not share this barrier.
            viewModel.addEpisodeToPlaylist(101)
            awaitValidReaddedDownload()
            read.release.countDown()
            assertEquals(CleanupResult.Success, withTimeout(TIMEOUT) { cleanup.await() })
            assertReaddedDownloadConsistent()
        } finally {
            read.release.countDown()
            withTimeout(TIMEOUT) { cleanup.join() }
        }
    }

    @Test
    fun cleanupWithOldMetadata_readdWaitsForReset_thenDownloadsConsistently() = runBlocking {
        awaitLoaded()
        store.episodes.update(episode(101).copy(audioUrl = server.url("/101.mp3").toString()))
        seedPlaylistAndFiles(listOf(101))
        store.playlist.removeFromPlaylist(101)
        server.enqueue(MockResponse().setBody("replacement audio"))
        manager.debounceMs = 0
        manager.startObserving()
        val read = Gate()
        store.cleanupReadGate = read
        val cleanup = async(Dispatchers.IO) { manager.cleanupEpisodeFile(101) }
        try {
            read.awaitEntered() // Cleanup holds the old downloaded/path snapshot.
            store.cleanupReadGate = null
            viewModel.addEpisodeToPlaylist(101)
            withTimeout(TIMEOUT) { store.playlistFlow.first { items -> items.any { it.episode.id == 101L } } }
            assertTrue(store.snapshot().single { it.id == 101L }.isDownloaded)
            read.release.countDown()
            assertEquals(CleanupResult.Success, withTimeout(TIMEOUT) { cleanup.await() })
            awaitValidReaddedDownload()
            assertReaddedDownloadConsistent()
            assertFalse(File(directory, "101.mp3").exists())
        } finally {
            read.release.countDown()
            withTimeout(TIMEOUT) { cleanup.join() }
        }
    }

    @Test
    fun overlappingRepositoryAndDirectCleanup_readdMustNotLoseNewDownloadMetadata() = runBlocking {
        awaitLoaded()
        store.episodes.update(episode(101).copy(audioUrl = server.url("/101.mp3").toString()))
        seedPlaylistAndFiles(listOf(101))
        server.enqueue(MockResponse().setBody("new download after direct cleanup"))
        manager.debounceMs = 0
        manager.startObserving()
        val deletion = Gate()
        val firstDelete = AtomicBoolean(true)
        val oldFile = File(directory, "101.mp3")
        manager.fileOps = object : FileOperations {
            override fun delete(file: File): Boolean {
                if (file == oldFile && firstDelete.compareAndSet(true, false)) deletion.park()
                return if (file.exists()) file.delete() else true
            }
        }
        val mark = async(Dispatchers.IO) { repository.markAllEpisodesListened(1) }
        try {
            deletion.awaitEntered() // Repository cleanup read old metadata; its reset is pending.
            // Same public cleanup API used by individual mark-listened and playback completion.
            assertEquals(CleanupResult.Success, manager.cleanupEpisodeFile(101))
            viewModel.addEpisodeToPlaylist(101)
            awaitValidReaddedDownload()
            val newFile = File(store.snapshot().single { it.id == 101L }.localFilePath!!)
            deletion.release.countDown()
            assertTrue(withTimeout(TIMEOUT) { mark.await() }.cleanupFailures.isEmpty())
            val final = store.snapshot().single { it.id == 101L }
            println("OVERLAPPING_CLEANUP downloaded=${final.isDownloaded}, path=${final.localFilePath}, newFileExists=${newFile.exists()}, queued=${store.playlist.isEpisodeInPlaylist(101)}")
            assertReaddedDownloadConsistent()
        } finally {
            deletion.release.countDown()
            withTimeout(TIMEOUT) { mark.join() }
        }
    }

    private suspend fun awaitValidReaddedDownload() = withTimeout(TIMEOUT) {
        while (true) {
            val current = store.snapshot().single { it.id == 101L }
            if (current.isDownloaded && !current.localFilePath.isNullOrBlank() &&
                File(current.localFilePath).exists() && manager.getPendingDownloadJob(101) == null) break
            yield()
        }
    }

    private fun assertReaddedDownloadConsistent() {
        val current = store.snapshot().single { it.id == 101L }
        val files = File(directory, "podcasts").listFiles().orEmpty().toList()
        assertTrue("Re-added episode remains queued", store.playlist.isEpisodeInPlaylist(101))
        assertFalse("Re-add retains its unlistened state", current.isListened)
        assertTrue("Completed replacement download must retain downloaded metadata; files=$files", current.isDownloaded)
        assertNotNull("Completed replacement download must retain its path", current.localFilePath)
        assertEquals("No unlinked replacement audio file", listOf(File(current.localFilePath!!)), files)
        assertTrue(File(current.localFilePath).exists())
    }

    private fun assertOwnershipConsistent() {
        val canonical = store.operationTargets().toSet()
        assertEquals("One canonical SQL target snapshot must own playlist cleanup (not affected rows)", canonical, store.removalTargets().toSet())
        assertEquals("Same canonical snapshot must own Smart Listening cleanup", canonical, store.cleanupTargets().toSet())
        if (store.deleteQueryTargets().isNotEmpty()) {
            assertTrue("Issued DELETE bound IDs must belong to canonical snapshot", store.deleteQueryTargets().all { it in canonical })
        }
        assertTrue("No operation-owned playlist row may remain", store.playlist.getAllPlaylistItems().none { it.episodeId in canonical })
    }

    private fun launchMarkAll(): List<Job> {
        val initial = viewModel.viewModelScope.coroutineContext.job.children.toSet()
        viewModel.markAllListened(1)
        return viewModel.viewModelScope.coroutineContext.job.children.filter { it !in initial }.toList()
    }

    private suspend fun awaitLoaded() = withTimeout(TIMEOUT) {
        viewModel.state.first { !it.isLoading && it.podcasts.isNotEmpty() }
    }

    private suspend fun refreshOneNewEpisode() {
        server.enqueue(MockResponse().setBody("""<rss version="2.0"><channel><title>Updated</title><item><guid>new-guid</guid><title>New</title><enclosure url="https://audio.example/new.mp3" type="audio/mpeg" length="10"/></item></channel></rss>"""))
        val result = withTimeout(TIMEOUT) { repository.refreshPodcast(1) }
        assertTrue("Actual HTTP/RSS repository refresh must succeed: $result", result.isSuccess)
        assertEquals("/feed", server.takeRequest(5, TimeUnit.SECONDS)!!.path)
    }

    private fun seedPlaylistAndFiles(ids: List<Long> = listOf(101, 102)) {
        ids.forEachIndexed { index, id ->
            val file = File(directory, "$id.mp3").apply { writeText("seed audio") }
            store.episodes.update(store.snapshot().single { it.id == id }.copy(isDownloaded = true, localFilePath = file.absolutePath))
            store.playlist.insertPlaylistItem(PlaylistItemEntity(episodeId = id, position = index))
        }
    }

    /** Real invalidator events drive an actual playlist read and production queue target resolver.
     * This verifies reconciled IDs/target; Android ExoPlayer itself is outside local JVM coverage. */
    private fun kotlinx.coroutines.CoroutineScope.recordQueueEvents(): QueueEvents {
        val recorder = QueueEvents()
        val job = CoroutineScope(SupervisorJob() + Dispatchers.IO).launch(start = CoroutineStart.UNDISPATCHED) {
            invalidator.events.collect {
                val items = PlaylistRepository(store.playlist).getPlaylistItems()
                val queue = items.map { QueueEpisodeState(it.episode.id, it.episode.playbackPositionMs) }
                val target = resolveQueuePlaybackTarget(queue, null, 101, 0, false)
                if (queue.isEmpty()) assertNull(target) else assertTrue(target!!.episodeId in queue.map { it.episodeId })
                recorder.count++
                recorder.channel.send(queue.map { it.episodeId })
            }
        }
        // Stop observers in teardown, including assertion-failure exits.
        recorder.job = job
        queueCollectors += job
        return recorder
    }

    private val queueCollectors = mutableListOf<Job>()

    private inner class QueueEvents {
        val channel = Channel<List<Long>>(Channel.UNLIMITED)
        @Volatile var count = 0
        lateinit var job: Job
        suspend fun next(): List<Long> = withTimeout(TIMEOUT) { channel.receive() }
    }

    private class Gate(expectedEntrants: Int = 1) {
        val entered = CountDownLatch(expectedEntrants)
        val release = CountDownLatch(1)
        fun park() {
            entered.countDown()
            check(release.await(5, TimeUnit.SECONDS)) { "Diagnostic release barrier timed out" }
        }
        suspend fun awaitEntered() = withContext(Dispatchers.IO) {
            check(entered.await(5, TimeUnit.SECONDS)) { "Diagnostic entry barrier timed out" }
        }
    }

    private class Store(private var podcast: PodcastEntity) {
        private val lock = Any()
        private val rows = linkedMapOf<Long, EpisodeEntity>()
        private val playlistRows = linkedMapOf<Long, PlaylistItemEntity>()
        private val bulk = mutableListOf<List<Long>>()
        private val operations = mutableListOf<List<Long>>()
        private val deleteBatches = mutableListOf<List<Long>>()
        private val removed = mutableListOf<Long>()
        private val deleteTargets = mutableListOf<Long>()
        private val cleanup = mutableListOf<Long>()
        private val downloadWrites = mutableListOf<Long>()
        private var transactionDepth = 0
        private var notificationPending = false
        val podcastFlow = MutableStateFlow(listOf(podcast))
        val playlistFlow = MutableStateFlow(emptyList<PlaylistItemWithEpisode>())
        @Volatile var bulkGate: Gate? = null
        @Volatile var downloadGate: Gate? = null
        @Volatile var afterTransactionGate: Gate? = null
        @Volatile var cleanupReadGate: Gate? = null
        fun snapshot() = synchronized(lock) { rows.values.toList() }
        fun bulkTargets() = synchronized(lock) { bulk.flatten() }
        fun operationTargets() = synchronized(lock) { operations.flatten() }
        fun batchSizes() = synchronized(lock) { (bulk + deleteBatches).map { it.size } }
        fun queryRecords() = synchronized(lock) { listOf(bulk.toList(), deleteBatches.toList()) }
        fun removalTargets() = synchronized(lock) { removed.toList() }
        fun deleteQueryTargets() = synchronized(lock) { deleteTargets.toList() }
        fun cleanupTargets() = synchronized(lock) { cleanup.toList() }
        fun bulkCalls() = synchronized(lock) { bulk.size }
        fun records() = synchronized(lock) { listOf(bulk.toList(), removed.toList(), deleteTargets.toList(), cleanup.toList(), downloadWrites.toList()) }
        private fun joined() = playlistRows.values.sortedBy { it.position }.mapNotNull { item ->
            rows[item.episodeId]?.let { PlaylistItemWithEpisode(item.id, item.position, it, podcast.title, podcast.artworkUrl) }
        }
        private fun emit() {
            if (transactionDepth > 0) notificationPending = true
            else playlistFlow.value = joined()
        }
        fun replaceForStress(old: Long, replacement: EpisodeEntity) = synchronized(lock) {
            rows.remove(old)
            playlistRows.remove(old) // FK cascade, explicitly not Mark-all-owned removal.
            rows[replacement.id] = replacement
            emit()
        }
        val podcasts = object : PodcastDao {
            override fun getAllPodcastsFlow(): Flow<List<PodcastEntity>> = podcastFlow
            override fun getAllPodcasts() = synchronized(lock) { listOf(podcast) }
            override fun getPodcastById(id: Long) = synchronized(lock) { podcast.takeIf { it.id == id } }
            override fun getPodcastByFeedUrl(feedUrl: String) = synchronized(lock) { podcast.takeIf { it.feedUrl == feedUrl } }
            override fun insert(podcast: PodcastEntity): Long { update(podcast); return podcast.id }
            override fun update(podcast: PodcastEntity) = synchronized(lock) { this@Store.podcast = podcast; podcastFlow.value = listOf(podcast); emit() }
            override fun deleteById(id: Long) = error("Outside diagnostic scope")
            override fun deleteAll() = error("Outside diagnostic scope")
        }
        val episodes = object : EpisodeDao {
            override fun getEpisodesByPodcastIdFlow(podcastId: Long): Flow<List<EpisodeEntity>> = emptyFlow()
            override fun getAllEpisodesWithPodcastFlow(): Flow<List<EpisodeWithPodcast>> = emptyFlow()
            override fun getEpisodeById(id: Long): EpisodeEntity? {
                val episode = synchronized(lock) { cleanup.add(id); rows[id] }
                cleanupReadGate?.park() // Outside shared storage lock; captures actual read timing.
                return episode
            }
            override fun getEpisodeWithPodcastById(id: Long) = synchronized(lock) { rows[id]?.let { EpisodeWithPodcast(it, podcast) } }
            override fun getEpisodeByPodcastIdAndGuid(podcastId: Long, guid: String) = snapshot().firstOrNull { it.podcastId == podcastId && it.guid == guid }
            override fun getEpisodesByPodcastId(podcastId: Long) = snapshot().filter { it.podcastId == podcastId }.sortedByDescending { it.publishedAt }
            override fun insertEpisode(episode: EpisodeEntity): Long = synchronized(lock) {
                if (rows.values.any { it.podcastId == episode.podcastId && it.guid == episode.guid }) return@synchronized -1L
                val id = episode.id.takeIf { it != 0L } ?: ((rows.keys.maxOrNull() ?: 0) + 1)
                rows[id] = episode.copy(id = id); emit(); id
            }
            override fun insertEpisodes(episodes: List<EpisodeEntity>) = synchronized(lock) { episodes.map { insertEpisode(it) } }
            override fun update(episode: EpisodeEntity) = synchronized(lock) { rows[episode.id] = episode; emit() }
            override fun setListened(episodeId: Long, listened: Boolean) = synchronized(lock) { rows[episodeId]?.let { rows[episodeId] = it.copy(isListened = listened) }; emit() }
            override fun setAllListenedForPodcast(podcastId: Long, listened: Boolean) {
                bulkGate?.park()
                synchronized(lock) {
                    val ids = rows.values.filter { it.podcastId == podcastId }.map { it.id }
                    bulk.add(ids)
                    ids.forEach { rows[it] = rows.getValue(it).copy(isListened = listened) }
                    emit()
                }
            }
            override fun getPlaylistEpisodeIdsForPodcast(podcastId: Long) = synchronized(lock) {
                playlistRows.keys.filter { rows[it]?.podcastId == podcastId }.sorted()
            }
            override fun setEpisodesListened(episodeIds: List<Long>): Int = synchronized(lock) {
                bulk.add(episodeIds.toList())
                val changed = episodeIds.count { rows[it]?.isListened == false }
                episodeIds.forEach { id -> rows[id]?.takeIf { !it.isListened }?.let { rows[id] = it.copy(isListened = true) } }
                emit(); changed
            }
            override fun deletePlaylistEpisodes(episodeIds: List<Long>): Int = synchronized(lock) {
                deleteTargets.addAll(episodeIds)
                deleteBatches.add(episodeIds.toList())
                val changed = episodeIds.count { it in playlistRows }
                episodeIds.forEach { playlistRows.remove(it) }
                emit(); changed
            }
            override fun markAllListenedAndRemoveFromPlaylist(podcastId: Long): MarkAllListenedSnapshot {
                // Model Room transaction serialization; never wait for refresh under the lock.
                bulkGate?.park()
                val snapshot = synchronized(lock) {
                    transactionDepth++
                    try {
                        super<EpisodeDao>.markAllListenedAndRemoveFromPlaylist(podcastId).also {
                            // Logical removal ownership also exists when no DELETE is necessary.
                            removed.addAll(it.episodes.map { episode -> episode.id })
                            operations.add(it.episodes.map { episode -> episode.id })
                        }
                    } finally {
                        transactionDepth--
                        if (transactionDepth == 0 && notificationPending) {
                            notificationPending = false
                            emit() // Room invalidates observable queries only after commit.
                        }
                    }
                }
                afterTransactionGate?.park()
                return snapshot
            }
            override fun updatePlaybackPosition(episodeId: Long, positionMs: Long) = synchronized(lock) { rows[episodeId]?.let { rows[episodeId] = it.copy(playbackPositionMs = positionMs) }; emit() }
            override fun updateDownloadState(episodeId: Long, isDownloaded: Boolean, localFilePath: String?) {
                if (isDownloaded) downloadGate?.park()
                synchronized(lock) { downloadWrites.add(episodeId); rows[episodeId]?.let { rows[episodeId] = it.copy(isDownloaded = isDownloaded, localFilePath = localFilePath) }; emit() }
            }
            override fun clearDownloadStateIfMatches(episodeId: Long, expectedIsDownloaded: Boolean, expectedLocalFilePath: String?): Int = synchronized(lock) {
                val current = rows[episodeId]
                if (current?.isDownloaded != expectedIsDownloaded || current.localFilePath != expectedLocalFilePath) return@synchronized 0
                downloadWrites.add(episodeId)
                rows[episodeId] = current.copy(isDownloaded = false, localFilePath = null)
                emit()
                1
            }
            override fun getDownloadedEpisodes() = snapshot().filter { it.isDownloaded }
        }
        val playlist = object : PlaylistDao {
            override fun getPlaylistItemsWithEpisodesFlow(): Flow<List<PlaylistItemWithEpisode>> = playlistFlow
            override fun getPlaylistItemsWithEpisodes() = synchronized(lock) { joined() }
            override fun getAllPlaylistItems() = synchronized(lock) { playlistRows.values.toList() }
            override fun getMaxPosition() = synchronized(lock) { playlistRows.values.maxOfOrNull { it.position } }
            override fun isEpisodeInPlaylist(episodeId: Long) = synchronized(lock) { episodeId in playlistRows }
            override fun insertPlaylistItem(item: PlaylistItemEntity): Long = synchronized(lock) { playlistRows[item.episodeId] = item; emit(); item.episodeId }
            override fun insertPlaylistItems(items: List<PlaylistItemEntity>) = synchronized(lock) { items.map { insertPlaylistItem(it) } }
            override fun removeFromPlaylist(episodeId: Long) = synchronized(lock) { removed.add(episodeId); playlistRows.remove(episodeId); emit() }
            override fun clearPlaylist() = synchronized(lock) { playlistRows.clear(); emit() }
        }
    }

    companion object {
        private const val TIMEOUT = 5_000L
        private fun episode(id: Long) = EpisodeEntity(id = id, podcastId = 1, guid = "guid-$id", title = "Episode $id", audioUrl = "https://audio.example/$id.mp3")
    }
}
