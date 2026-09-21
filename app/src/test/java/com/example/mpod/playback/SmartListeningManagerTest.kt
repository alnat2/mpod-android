package com.example.mpod.playback

import android.content.Context
import android.content.ContextWrapper
import com.example.mpod.data.local.dao.EpisodeDao
import com.example.mpod.data.local.dao.PlaylistDao
import com.example.mpod.data.local.entity.EpisodeEntity
import com.example.mpod.data.local.entity.PlaylistItemEntity
import com.example.mpod.data.local.model.EpisodeWithPodcast
import com.example.mpod.data.local.model.PlaylistItemWithEpisode
import com.example.mpod.data.local.preferences.AppSettings
import com.example.mpod.data.network.ProxyHttpClientFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.OutputStream
import java.util.concurrent.TimeUnit

class SmartListeningManagerTest {

    private lateinit var server: MockWebServer
    private lateinit var tempDir: File
    private lateinit var fakeContext: Context
    private lateinit var fakePlaylistDao: FakePlaylistDao
    private lateinit var fakeEpisodeDao: FakeEpisodeDao
    private lateinit var proxyHttpClientFactory: ProxyHttpClientFactory
    private lateinit var manager: SmartListeningManager

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()

        tempDir = File(System.getProperty("java.io.tmpdir"), "test_mpod_${System.currentTimeMillis()}").apply { mkdirs() }
        fakeContext = object : ContextWrapper(null) {
            override fun getFilesDir(): File = tempDir
        }

        fakePlaylistDao = FakePlaylistDao()
        fakeEpisodeDao = FakeEpisodeDao()
        proxyHttpClientFactory = ProxyHttpClientFactory()
        proxyHttpClientFactory.updateProxy(AppSettings(isProxyEnabled = false))

        manager = SmartListeningManager(
            context = fakeContext,
            playlistDao = fakePlaylistDao,
            episodeDao = fakeEpisodeDao,
            proxyHttpClientFactory = proxyHttpClientFactory
        )
    }

    @After
    fun tearDown() {
        manager.stopObserving()
        server.shutdown()
        tempDir.deleteRecursively()
    }

    // ── Existing tests (updated for DownloadOutcome / CleanupResult API) ──────────────────────

    @Test
    fun successfulDownload_createsTargetFile_updatesDao_andCleansTemp() = runBlocking {
        val audioData = "test audio content 12345".toByteArray()
        server.enqueue(MockResponse().setResponseCode(200).setBody(Buffer().write(audioData)))

        val outcome = manager.downloadAudioFile(101L, server.url("/ep101.mp3").toString())
        assertTrue("Download should succeed", outcome is DownloadOutcome.Success)

        val podcastsDir = File(tempDir, "podcasts")
        val files = podcastsDir.listFiles() ?: emptyArray()
        assertEquals("Should have exactly 1 final file in podcasts dir", 1, files.size)
        assertTrue(files[0].name.startsWith("ep_101_"))
        assertTrue(files[0].name.endsWith(".mp3"))
        assertEquals(audioData.size.toLong(), files[0].length())

        assertEquals(1, fakeEpisodeDao.downloadedStates.size)
        assertEquals(101L, fakeEpisodeDao.downloadedStates[0].episodeId)
        assertTrue(fakeEpisodeDao.downloadedStates[0].isDownloaded)
    }

    @Test
    fun coroutineCancellation_abortsActiveOkHttpBodyImmediately_andCleansTempAndFinalFiles() = runBlocking {
        val chunk = ByteArray(512) { 0x42 }
        val buffer = Buffer()
        for (i in 0 until 1000) { buffer.write(chunk) }

        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(buffer)
                .throttleBody(512, 50, TimeUnit.MILLISECONDS)
        )

        val downloadJob = async(Dispatchers.IO) {
            manager.downloadAudioFile(102L, server.url("/slow_ep102.mp3").toString())
        }

        val podcastsDir = File(tempDir, "podcasts")

        withTimeout(5000) {
            while (true) {
                val tempFiles = podcastsDir.listFiles { _, name -> name.endsWith(".tmp") }
                if (tempFiles != null && tempFiles.isNotEmpty() && tempFiles[0].length() > 0) break
                delay(20)
            }
        }

        val tempFiles = podcastsDir.listFiles { _, name -> name.endsWith(".tmp") }
        assertNotNull(tempFiles)
        assertTrue("Temp file must exist with partial bytes before cancel", tempFiles!!.isNotEmpty() && tempFiles[0].length() > 0)

        downloadJob.cancel()

        var caughtCancellation = false
        try {
            withTimeout(3000) { downloadJob.await() }
        } catch (e: CancellationException) {
            caughtCancellation = true
        }

        assertTrue("Expected CancellationException from cancelled download job", caughtCancellation)

        val remainingFiles = podcastsDir.listFiles() ?: emptyArray()
        assertEquals("All temp and target files must be deleted immediately on cancellation", 0, remainingFiles.size)
        assertEquals("DAO state must not be updated on cancellation", 0, fakeEpisodeDao.downloadedStates.size)
    }

    @Test
    fun httpError_doesNotUpdateDao_andCleansFiles() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404).setBody("Not Found"))

        val outcome = manager.downloadAudioFile(103L, server.url("/ep103.mp3").toString())
        assertTrue("Download should fail on 404", outcome is DownloadOutcome.Failure)

        val podcastsDir = File(tempDir, "podcasts")
        val files = podcastsDir.listFiles() ?: emptyArray()
        assertEquals("No files should remain on HTTP error", 0, files.size)
        assertEquals(0, fakeEpisodeDao.downloadedStates.size)
    }

    @Test
    fun failedFileDeleteInCleanupEpisodeFile_doesNotClearRoomState() = runBlocking {
        val podcastsDir = File(tempDir, "podcasts").apply { mkdirs() }
        val readOnlyDir = File(podcastsDir, "locked_dir").apply { mkdirs() }
        val lockedFile = File(readOnlyDir, "locked.mp3").apply { writeText("audio data") }

        fakeEpisodeDao.insertEpisode(
            EpisodeEntity(
                id = 201L, podcastId = 1L, guid = "guid201", title = "Episode 201",
                description = "", audioUrl = "https://example.com/201.mp3",
                durationSeconds = 100, publishedAt = 0L, publishedAtString = "",
                isListened = false, playbackPositionMs = 0L,
                isDownloaded = true, localFilePath = lockedFile.absolutePath
            )
        )

        readOnlyDir.setWritable(false)
        readOnlyDir.setExecutable(false)

        try {
            manager.cleanupEpisodeFile(201L)
            if (lockedFile.exists()) {
                val ep = fakeEpisodeDao.getEpisodeById(201L)
                assertTrue("Room state must remain isDownloaded=true if file delete failed", ep?.isDownloaded == true)
            }
        } finally {
            readOnlyDir.setWritable(true)
            readOnlyDir.setExecutable(true)
        }
    }

    @Test
    fun rapidFailedAttempt_doesNotLeaveCompletedJobInMap_andAllowsSubsequentAttempt() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500).setBody("Server Error"))

        val outcome1 = manager.downloadAudioFile(301L, server.url("/ep301.mp3").toString())
        assertTrue("Initial download should fail", outcome1 is DownloadOutcome.Failure)
        assertNull("Job must not remain in pending map", manager.getPendingDownloadJob(301L))

        val audioData = "valid audio".toByteArray()
        server.enqueue(MockResponse().setResponseCode(200).setBody(Buffer().write(audioData)))

        val outcome2 = manager.downloadAudioFile(301L, server.url("/ep301.mp3").toString())
        assertTrue("Subsequent download should succeed", outcome2 is DownloadOutcome.Success)
    }

    @Test
    fun stopObserving_cancelsAllPendingJobs() = runBlocking {
        manager.startObserving()
        val item = createPlaylistItemWithEpisode(episodeId = 401L, podcastId = 1L, isDownloaded = false)
        fakePlaylistDao.playlistFlow.value = listOf(item)

        delay(50)
        assertTrue("Pending job should be registered", manager.getPendingDownloadCount() > 0)

        manager.stopObserving()
        assertEquals("All pending jobs should be cleared on stopObserving", 0, manager.getPendingDownloadCount())
    }

    @Test
    fun startObserving_calledTwice_idempotentAndSingleJob() = runBlocking {
        manager.startObserving()
        val firstJob = manager.activeObservationJobForTest
        assertNotNull("Job must be created on first start", firstJob)

        manager.startObserving()
        val secondJob = manager.activeObservationJobForTest
        assertTrue("Subsequent start must not create a new job instance", firstJob === secondJob)
    }

    @Test
    fun removedFromPlaylistBeforeDebounce_cancelsPendingJob() = runBlocking {
        manager.startObserving()
        val item = createPlaylistItemWithEpisode(episodeId = 501L, podcastId = 1L, isDownloaded = false)
        fakePlaylistDao.playlistFlow.value = listOf(item)

        delay(50)
        assertNotNull("Job should be scheduled for episode 501", manager.getPendingDownloadJob(501L))

        fakePlaylistDao.playlistFlow.value = emptyList()

        delay(50)
        assertNull("Job should be removed and cancelled when episode removed from playlist", manager.getPendingDownloadJob(501L))
    }

    // ── SL-FIX-01: cancellation vs Room commit race ───────────────────────────────────────────

    @Test
    fun cancellationBeforeDao_noRoomUpdate_noFilesLeft() = runBlocking {
        val audioData = "small audio content".toByteArray()
        server.enqueue(MockResponse().setResponseCode(200).setBody(Buffer().write(audioData)))

        val hookEntered = CompletableDeferred<Unit>()
        manager.preDaoHook = {
            hookEntered.complete(Unit)
            // Suspend indefinitely — cancellable. CancellationException propagates out.
            suspendCancellableCoroutine<Unit> { }
        }

        val downloadJob = async(Dispatchers.IO) {
            manager.downloadAudioFile(1001L, server.url("/ep1001.mp3").toString())
        }

        // Wait until we are parked in the preDaoHook (streaming+move complete, DAO not yet called)
        withTimeout(5000) { hookEntered.await() }

        downloadJob.cancel()

        var caughtCancellation = false
        try {
            withTimeout(3000) { downloadJob.await() }
        } catch (e: CancellationException) {
            caughtCancellation = true
        }

        assertTrue("CancellationException must propagate", caughtCancellation)
        assertEquals("DAO must not be called when cancelled before commit", 0, fakeEpisodeDao.downloadedStates.size)

        val podcastsDir = File(tempDir, "podcasts")
        val files = podcastsDir.listFiles() ?: emptyArray()
        assertEquals("No temp or final files must remain after pre-DAO cancellation", 0, files.size)
    }

    @Test
    fun cancellationAfterRoomCommit_filePreservedAndRoomPathValid() = runBlocking {
        val audioData = "committed audio content".toByteArray()
        server.enqueue(MockResponse().setResponseCode(200).setBody(Buffer().write(audioData)))

        val hookEntered = CompletableDeferred<Unit>()
        manager.postDaoHook = {
            hookEntered.complete(Unit)
            // Suspend indefinitely — simulates cancellation arriving just after Room commit.
            suspendCancellableCoroutine<Unit> { }
        }

        val downloadJob = async(Dispatchers.IO) {
            manager.downloadAudioFile(1002L, server.url("/ep1002.mp3").toString())
        }

        // Wait until Room has been committed
        withTimeout(5000) { hookEntered.await() }

        assertEquals("Room must have been committed", 1, fakeEpisodeDao.downloadedStates.size)
        assertTrue("isDownloaded must be true", fakeEpisodeDao.downloadedStates[0].isDownloaded)
        val committedPath = fakeEpisodeDao.downloadedStates[0].path!!

        // Cancellation arrives after commit
        downloadJob.cancel()
        try {
            withTimeout(3000) { downloadJob.await() }
        } catch (_: CancellationException) { }

        // File must still exist — cancellation after roomCommitted=true must NOT delete it
        val file = File(committedPath)
        assertTrue("Final file must still exist after post-commit cancellation", file.exists())
        assertTrue("Final file must be non-empty", file.length() > 0)

        // No additional DAO calls (no false reset)
        assertEquals("No extra DAO calls after commit", 1, fakeEpisodeDao.downloadedStates.size)
    }

    // ── SL-FIX-02: safe cleanupEpisodeFile ───────────────────────────────────────────────────

    @Test
    fun cleanupEpisodeFile_cancelsActiveJob_andJoinsBeforeRead() = runBlocking {
        manager.debounceMs = 0
        manager.startObserving()

        val chunk = ByteArray(512) { 0x42 }
        val buffer = Buffer()
        repeat(1000) { buffer.write(chunk) }
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(buffer)
                .throttleBody(512, 50, TimeUnit.MILLISECONDS)
        )

        val item = createPlaylistItemWithEpisode(episodeId = 601L, podcastId = 1L, isDownloaded = false)
        fakePlaylistDao.playlistFlow.value = listOf(item)

        val podcastsDir = File(tempDir, "podcasts")
        // Wait for download to be in-flight (temp file has partial data)
        withTimeout(5000) {
            while (true) {
                val tmp = podcastsDir.listFiles { _, n -> n.endsWith(".tmp") }
                if (!tmp.isNullOrEmpty() && tmp[0].length() > 0) break
                delay(20)
            }
        }

        // cleanupEpisodeFile must cancelAndJoin — returns only after download job is fully done
        val result = manager.cleanupEpisodeFile(601L)

        assertEquals("cleanupEpisodeFile must succeed", CleanupResult.Success, result)

        // Job is fully joined — no late DAO commit can arrive after this point
        val successfulCommits = fakeEpisodeDao.downloadedStates.filter { it.episodeId == 601L && it.isDownloaded }
        assertTrue("No successful download commit must have happened", successfulCommits.isEmpty())

        val files = podcastsDir.listFiles() ?: emptyArray()
        assertEquals("No temp or final files must remain", 0, files.size)
    }

    @Test
    fun cleanupEpisodeFile_deleteFailure_roomNotReset() = runBlocking {
        val localFile = File(tempDir, "ep602.mp3").apply { writeText("audio content for 602") }
        fakeEpisodeDao.insertEpisode(
            EpisodeEntity(
                id = 602L, podcastId = 1L, guid = "guid602", title = "Episode 602",
                description = "", audioUrl = "https://example.com/602.mp3",
                durationSeconds = 100, publishedAt = 0L, publishedAtString = "",
                isListened = false, playbackPositionMs = 0L,
                isDownloaded = true, localFilePath = localFile.absolutePath
            )
        )

        // Fake fileOps: delete always fails (no OS permission tricks needed)
        manager.fileOps = object : FileOperations {
            override fun delete(file: File): Boolean = false
        }

        val result = manager.cleanupEpisodeFile(602L)

        assertTrue("Result must be DeleteFailed", result is CleanupResult.DeleteFailed)
        assertEquals(localFile.absolutePath, (result as CleanupResult.DeleteFailed).path)

        // Room must NOT be reset when file deletion fails
        val ep = fakeEpisodeDao.getEpisodeById(602L)
        assertTrue("Room isDownloaded must remain true", ep?.isDownloaded == true)
        val resetCalls = fakeEpisodeDao.downloadedStates.filter { it.episodeId == 602L && !it.isDownloaded }
        assertTrue("Room state must not be reset on delete failure", resetCalls.isEmpty())
    }

    @Test
    fun cleanupEpisodeFile_success_fileGoneAndRoomReset() = runBlocking {
        val localFile = File(tempDir, "ep603.mp3").apply { writeText("real audio content for 603") }
        fakeEpisodeDao.insertEpisode(
            EpisodeEntity(
                id = 603L, podcastId = 1L, guid = "guid603", title = "Episode 603",
                description = "", audioUrl = "https://example.com/603.mp3",
                durationSeconds = 120, publishedAt = 0L, publishedAtString = "",
                isListened = false, playbackPositionMs = 0L,
                isDownloaded = true, localFilePath = localFile.absolutePath
            )
        )

        val result = manager.cleanupEpisodeFile(603L)

        assertEquals("Result must be Success", CleanupResult.Success, result)
        assertFalse("File must be deleted", localFile.exists())

        val resetCalls = fakeEpisodeDao.downloadedStates.filter { it.episodeId == 603L && !it.isDownloaded }
        assertEquals("Room must be reset to isDownloaded=false", 1, resetCalls.size)
        assertNull("localFilePath must be null after cleanup", resetCalls[0].path)
    }

    @Test
    fun cleanupEpisodeFile_downloadedFlagWithoutPath_clearsStaleMetadata() = runBlocking {
        fakeEpisodeDao.insertEpisode(EpisodeEntity(id = 604, podcastId = 1, guid = "guid604",
            title = "No linked file", audioUrl = "https://example.com/604.mp3", isDownloaded = true))
        assertEquals(CleanupResult.Success, manager.cleanupEpisodeFile(604))
        assertFalse(fakeEpisodeDao.getEpisodeById(604)!!.isDownloaded)
        assertEquals(listOf(DownloadedState(604, false, null)), fakeEpisodeDao.downloadedStates)
    }

    // ── SL-FIX-03: cleanupFailed observable by caller ────────────────────────────────────────

    @Test
    fun tempCleanupFailure_afterStreamWriteFailure_observedByCaller() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody(Buffer().write("data".toByteArray())))

        // openOutputStream throws → stream write fails. delete also returns false → cleanupFailed.
        manager.fileOps = object : FileOperations {
            override fun exists(file: File): Boolean = true
            override fun openOutputStream(file: File): OutputStream = object : OutputStream() {
                override fun write(b: Int) = throw java.io.IOException("Disk full")
                override fun write(b: ByteArray, off: Int, len: Int) = throw java.io.IOException("Disk full")
            }
            override fun delete(file: File): Boolean = false
        }

        val outcome = manager.downloadAudioFile(701L, server.url("/ep701.mp3").toString())

        assertTrue("Outcome must be Failure", outcome is DownloadOutcome.Failure)
        val failure = outcome as DownloadOutcome.Failure
        assertTrue("cleanupFailed must be true when temp cannot be deleted", failure.cleanupFailed)
        assertTrue("No downloaded state must be in Room", fakeEpisodeDao.downloadedStates.isEmpty())
    }

    @Test
    fun targetCompensationFailure_afterDaoException_observedByCaller() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody(Buffer().write("audio content".toByteArray())))

        // DAO throws on commit; delete returns false so compensation cleanup also fails
        fakeEpisodeDao.throwOnUpdateDownloadState = true
        manager.fileOps = object : FileOperations {
            override fun delete(file: File): Boolean = false
            // All other ops use interface defaults (real I/O for write + rename)
        }

        val outcome = manager.downloadAudioFile(702L, server.url("/ep702.mp3").toString())

        assertTrue("Outcome must be Failure", outcome is DownloadOutcome.Failure)
        val failure = outcome as DownloadOutcome.Failure
        assertTrue("cleanupFailed must be true when target compensation fails", failure.cleanupFailed)
        assertTrue("No successful downloaded state must be in Room",
            fakeEpisodeDao.downloadedStates.none { it.isDownloaded })
    }

    // ── SL-FIX-04: job registry and debounce ─────────────────────────────────────────────────

    @Test
    fun failedAttempt_clearsJobRegistry_allowsSubsequentNetworkRequest() = runBlocking {
        manager.debounceMs = 0
        manager.startObserving()

        server.enqueue(MockResponse().setResponseCode(500).setBody("Server Error"))

        val item = createPlaylistItemWithEpisode(episodeId = 801L, podcastId = 1L, isDownloaded = false)
        fakePlaylistDao.playlistFlow.value = listOf(item)

        // Wait for the first attempt to complete and job to be removed from registry
        withTimeout(5000) {
            while (server.requestCount < 1 || manager.getPendingDownloadCount() > 0) {
                delay(20)
            }
        }
        assertEquals("First network request must be made", 1, server.requestCount)
        assertNull("Job must be removed from registry after failure", manager.getPendingDownloadJob(801L))

        // Enqueue second response before triggering re-emission
        server.enqueue(MockResponse().setResponseCode(200).setBody(Buffer().write("audio".toByteArray())))

        // Force a new emission: toggle through emptyList so collectLatest re-runs its lambda
        fakePlaylistDao.playlistFlow.value = emptyList()
        delay(20)
        fakePlaylistDao.playlistFlow.value = listOf(item)

        // Wait for second attempt
        withTimeout(5000) {
            while (server.requestCount < 2 || fakeEpisodeDao.downloadedStates.none { it.isDownloaded }) {
                delay(20)
            }
        }
        assertEquals("Second network request must be made after re-emission", 2, server.requestCount)
        assertEquals("Second attempt must commit to Room", 1,
            fakeEpisodeDao.downloadedStates.count { it.episodeId == 801L && it.isDownloaded })
    }

    @Test
    fun rapidTwoEmissions_singleNetworkRequest_singleDaoCommit() = runBlocking {
        manager.debounceMs = 0
        manager.startObserving()

        // Throttled so the download is in-flight when the second emission arrives
        val audioData = "audio".toByteArray()
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(Buffer().write(audioData))
                .throttleBody(1, 10, TimeUnit.MILLISECONDS)
        )

        val item = createPlaylistItemWithEpisode(episodeId = 802L, podcastId = 1L, isDownloaded = false)
        fakePlaylistDao.playlistFlow.value = listOf(item)
        delay(20) // first emission processed; job is in-flight (throttled server)

        // Second emission: same episode, different position value forces StateFlow to emit
        // (PlaylistItemWithEpisode is a data class so content equality is checked)
        fakePlaylistDao.playlistFlow.value = listOf(item.copy(position = 1))

        // Wait for the download to finish
        withTimeout(5000) {
            while (server.requestCount < 1 || manager.getPendingDownloadCount() > 0) {
                delay(20)
            }
        }

        assertEquals("Exactly one network request", 1, server.requestCount)
        val successCommits = fakeEpisodeDao.downloadedStates.filter { it.episodeId == 802L && it.isDownloaded }
        assertEquals("Exactly one DAO commit", 1, successCommits.size)
    }

    @Test
    fun episodeRemovedBeforeDebounce_noNetworkRequest_noDaoCommit() = runBlocking {
        manager.debounceMs = 200
        manager.startObserving()

        val item = createPlaylistItemWithEpisode(episodeId = 803L, podcastId = 1L, isDownloaded = false)
        fakePlaylistDao.playlistFlow.value = listOf(item)
        delay(50) // episode is scheduled, debounce window has NOT expired

        // Remove from playlist — triggers cancellation of the debounce job
        fakePlaylistDao.playlistFlow.value = emptyList()

        // Wait well past the debounce window
        delay(300)

        assertEquals("No network requests must be made", 0, server.requestCount)
        assertTrue("No DAO commits must happen", fakeEpisodeDao.downloadedStates.isEmpty())
    }

    // ── SL-FIX-05: active download cancellation via playlist lifecycle ────────────────────────

    @Test
    fun episodeRemovedDuringActiveDownload_stopsBodyAndCleansFiles() = runBlocking {
        manager.debounceMs = 0
        manager.startObserving()

        val chunk = ByteArray(512) { 0x42 }
        val buffer = Buffer()
        repeat(1000) { buffer.write(chunk) }
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(buffer)
                .throttleBody(512, 50, TimeUnit.MILLISECONDS)
        )

        val item = createPlaylistItemWithEpisode(episodeId = 901L, podcastId = 1L, isDownloaded = false)
        fakePlaylistDao.playlistFlow.value = listOf(item)

        val podcastsDir = File(tempDir, "podcasts")

        // Wait until download is active (partial .tmp file exists with data)
        withTimeout(5000) {
            while (true) {
                val tmp = podcastsDir.listFiles { _, n -> n.endsWith(".tmp") }
                if (!tmp.isNullOrEmpty() && tmp[0].length() > 0) break
                delay(20)
            }
        }

        // Remove episode from playlist — triggers job cancellation via collectLatest
        fakePlaylistDao.playlistFlow.value = emptyList()

        // Wait for filesystem cleanup to complete
        withTimeout(5000) {
            while (true) {
                val files = podcastsDir.listFiles()
                if (files == null || files.isEmpty()) break
                delay(20)
            }
        }

        val files = podcastsDir.listFiles() ?: emptyArray()
        assertEquals("No temp or final files must remain after lifecycle cancellation", 0, files.size)

        assertTrue("No successful DAO commit must happen after cancellation",
            fakeEpisodeDao.downloadedStates.none { it.episodeId == 901L && it.isDownloaded })

        // Verify no late DAO commit arrives
        delay(200)
        assertTrue("Still no late DAO commit",
            fakeEpisodeDao.downloadedStates.none { it.episodeId == 901L && it.isDownloaded })
    }

    @Test
    fun stopObservingDuringActiveDownload_stopsBodyAndCleansFiles() = runBlocking {
        manager.debounceMs = 0
        manager.startObserving()

        val chunk = ByteArray(512) { 0x42 }
        val buffer = Buffer()
        repeat(1000) { buffer.write(chunk) }
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(buffer)
                .throttleBody(512, 50, TimeUnit.MILLISECONDS)
        )

        val item = createPlaylistItemWithEpisode(episodeId = 902L, podcastId = 1L, isDownloaded = false)
        fakePlaylistDao.playlistFlow.value = listOf(item)

        val podcastsDir = File(tempDir, "podcasts")

        withTimeout(5000) {
            while (true) {
                val tmp = podcastsDir.listFiles { _, n -> n.endsWith(".tmp") }
                if (!tmp.isNullOrEmpty() && tmp[0].length() > 0) break
                delay(20)
            }
        }

        // stopObserving cancels both the observation job and all pending download jobs
        manager.stopObserving()

        withTimeout(5000) {
            while (true) {
                val files = podcastsDir.listFiles()
                if (files == null || files.isEmpty()) break
                delay(20)
            }
        }

        val files = podcastsDir.listFiles() ?: emptyArray()
        assertEquals("No temp or final files must remain after stopObserving", 0, files.size)

        assertTrue("No successful DAO commit must happen",
            fakeEpisodeDao.downloadedStates.none { it.episodeId == 902L && it.isDownloaded })

        delay(200)
        assertTrue("Still no late DAO commit after stopObserving",
            fakeEpisodeDao.downloadedStates.none { it.episodeId == 902L && it.isDownloaded })
    }

    // ── Helpers ───────────────────────────────────────────────────────────────────────────────

    private fun createPlaylistItemWithEpisode(
        episodeId: Long,
        podcastId: Long,
        isDownloaded: Boolean
    ): PlaylistItemWithEpisode {
        val episode = EpisodeEntity(
            id = episodeId,
            podcastId = podcastId,
            guid = "guid_$episodeId",
            title = "Episode $episodeId",
            description = "Description",
            audioUrl = server.url("/ep_$episodeId.mp3").toString(),
            durationSeconds = 120,
            publishedAt = System.currentTimeMillis(),
            publishedAtString = "Today",
            isListened = false,
            playbackPositionMs = 0L,
            isDownloaded = isDownloaded,
            localFilePath = null
        )
        fakeEpisodeDao.insertEpisode(episode)
        return PlaylistItemWithEpisode(
            playlistItemId = episodeId,
            position = 0,
            episode = episode,
            podcastTitle = "Podcast $podcastId",
            podcastArtworkUrl = "https://example.com/art.jpg"
        )
    }

    // ── Fakes ─────────────────────────────────────────────────────────────────────────────────

    class FakePlaylistDao : PlaylistDao {
        val playlistFlow = MutableStateFlow<List<PlaylistItemWithEpisode>>(emptyList())
        override fun getPlaylistItemsWithEpisodesFlow(): Flow<List<PlaylistItemWithEpisode>> = playlistFlow
        override fun getPlaylistItemsWithEpisodes(): List<PlaylistItemWithEpisode> = playlistFlow.value
        override fun getAllPlaylistItems(): List<PlaylistItemEntity> = emptyList()
        override fun getMaxPosition(): Int? = null
        override fun isEpisodeInPlaylist(episodeId: Long): Boolean = playlistFlow.value.any { it.episode.id == episodeId }
        override fun insertPlaylistItem(item: PlaylistItemEntity): Long = 1L
        override fun insertPlaylistItems(items: List<PlaylistItemEntity>): List<Long> = items.indices.map { it.toLong() }
        override fun removeFromPlaylist(episodeId: Long) {}
        override fun clearPlaylist() {}
    }

    data class DownloadedState(val episodeId: Long, val isDownloaded: Boolean, val path: String?)

    class FakeEpisodeDao : EpisodeDao {
        val downloadedStates = mutableListOf<DownloadedState>()
        private val episodes = mutableListOf<EpisodeEntity>()

        /** Set to true to make updateDownloadState throw a SQLException (SL-FIX-03 tests). */
        var throwOnUpdateDownloadState: Boolean = false

        override fun getEpisodesByPodcastIdFlow(podcastId: Long): Flow<List<EpisodeEntity>> = emptyFlow()
        override fun getAllEpisodesWithPodcastFlow(): Flow<List<EpisodeWithPodcast>> = emptyFlow()
        override fun getEpisodeById(id: Long): EpisodeEntity? = episodes.find { it.id == id }
        override fun getEpisodeWithPodcastById(id: Long): EpisodeWithPodcast? = null
        override fun getEpisodeByPodcastIdAndGuid(podcastId: Long, guid: String): EpisodeEntity? = null
        override fun getEpisodesByPodcastId(podcastId: Long): List<EpisodeEntity> = episodes.filter { it.podcastId == podcastId }
        override fun insertEpisode(episode: EpisodeEntity): Long {
            episodes.removeAll { it.id == episode.id }
            episodes.add(episode)
            return episode.id
        }
        override fun insertEpisodes(episodes: List<EpisodeEntity>): List<Long> = emptyList()
        override fun update(episode: EpisodeEntity) {
            episodes.removeAll { it.id == episode.id }
            episodes.add(episode)
        }
        override fun setListened(episodeId: Long, listened: Boolean) {}
        override fun setAllListenedForPodcast(podcastId: Long, listened: Boolean) {}
        override fun getPlaylistEpisodeIdsForPodcast(podcastId: Long): List<Long> = emptyList()
        override fun setEpisodesListened(episodeIds: List<Long>): Int = error("Manager does not mark listened")
        override fun deletePlaylistEpisodes(episodeIds: List<Long>): Int = error("Manager does not delete playlist")
        override fun updatePlaybackPosition(episodeId: Long, positionMs: Long) {}
        override fun updateDownloadState(episodeId: Long, isDownloaded: Boolean, localFilePath: String?) {
            if (throwOnUpdateDownloadState) {
                throw java.sql.SQLException("Simulated Room constraint failure")
            }
            downloadedStates.add(DownloadedState(episodeId, isDownloaded, localFilePath))
            val existing = episodes.find { it.id == episodeId }
            if (existing != null) {
                episodes.removeAll { it.id == episodeId }
                episodes.add(existing.copy(isDownloaded = isDownloaded, localFilePath = localFilePath))
            }
        }
        override fun clearDownloadStateIfMatches(episodeId: Long, expectedIsDownloaded: Boolean, expectedLocalFilePath: String?): Int {
            val existing = episodes.find { it.id == episodeId } ?: return 0
            if (existing.isDownloaded != expectedIsDownloaded || existing.localFilePath != expectedLocalFilePath) return 0
            updateDownloadState(episodeId, isDownloaded = false, localFilePath = null)
            return 1
        }
        override fun getDownloadedEpisodes(): List<EpisodeEntity> = emptyList()
    }
}
