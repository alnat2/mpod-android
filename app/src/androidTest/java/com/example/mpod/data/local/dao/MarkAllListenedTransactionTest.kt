package com.example.mpod.data.local.dao

import android.content.Context
import android.database.sqlite.SQLiteException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mpod.data.local.MpodDatabase
import com.example.mpod.data.local.entity.EpisodeEntity
import com.example.mpod.data.local.entity.PlaylistItemEntity
import com.example.mpod.data.local.entity.PodcastEntity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Real SQLite transaction tests, independent of the JVM fake DAO interleavings. */
@RunWith(AndroidJUnit4::class)
class MarkAllListenedTransactionTest {
    private lateinit var database: MpodDatabase
    private val statements = Collections.synchronizedList(mutableListOf<String>())
    private val writeBarrier = AtomicReference<WriteBarrier?>(null)
    private val workers = Executors.newFixedThreadPool(2)

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            MpodDatabase::class.java
        ).setQueryCallback({ sql, _ ->
            val normalized = sql.trim().replace(Regex("\\s+"), " ").uppercase()
            statements += normalized
            val barrier = writeBarrier.get()
            if (normalized.startsWith("BEGIN ") && barrier?.paused?.get() == true &&
                Thread.currentThread() === barrier.competingThread.get()
            ) {
                // Room 2.6.1 calls QueryCallback before delegate.beginTransaction().
                // This proves the competing writer reached SQLite, not merely its executor.
                barrier.competingTransactionAttempted.countDown()
            }
            // Direct execution pauses SQLite before UPDATE, after the transaction snapshot.
            if (normalized.startsWith("UPDATE EPISODES SET ISLISTENED") &&
                barrier != null && barrier.paused.compareAndSet(false, true)
            ) {
                barrier.snapshotTaken.countDown()
                check(barrier.resume.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    "Timed out waiting to resume Mark all transaction"
                }
            }
        }, Executor { command -> command.run() }).build()
        database.podcastDao().insert(podcast(1))
        database.podcastDao().insert(podcast(2))
    }

    @After
    fun tearDown() {
        writeBarrier.getAndSet(null)?.resume?.countDown()
        workers.shutdownNow()
        assertTrue("Test workers did not terminate", workers.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS))
        database.close()
    }

    @Test
    fun canonicalWorkSetIncludesListenedPlaylistAndDownloadResidueButPreservesOtherPodcast() {
        insert(episode(101))
        insert(episode(102, listened = true), queued = true)
        insert(episode(103, listened = true, downloaded = true, path = "/test/103.mp3"))
        insert(episode(104, listened = true, path = "/test/104.mp3"))
        insert(episode(105, listened = true))
        val other = episode(201, podcastId = 2, downloaded = true, path = "/test/201.mp3")
        insert(other, queued = true)

        val result = database.episodeDao().markAllListenedAndRemoveFromPlaylist(1)

        assertEquals(setOf(101L, 102L, 103L, 104L), result.episodes.map { it.id }.toSet())
        assertEquals(setOf(101L), result.listenedEpisodeIds.toSet())
        assertEquals(setOf(102L), result.removedPlaylistEpisodeIds.toSet())
        assertFalse(result.episodes.single { it.id == 101L }.isListened)
        assertEquals("/test/103.mp3", result.episodes.single { it.id == 103L }.localFilePath)
        assertTrue(database.episodeDao().getEpisodesByPodcastId(1).all { it.isListened })
        assertEquals(other, database.episodeDao().getEpisodeById(201))
        assertEquals(listOf(201L), playlistIds())
        // Files are deliberately not part of the ACID operation: retain metadata for cleanup/retry.
        assertEquals("/test/103.mp3", database.episodeDao().getEpisodeById(103)?.localFilePath)
    }

    @Test
    fun sequentialRepeatWithNoResiduePerformsNoEpisodeUpdateOrPlaylistDelete() {
        insert(episode(101), queued = true)
        database.episodeDao().markAllListenedAndRemoveFromPlaylist(1)
        statements.clear()

        val result = database.episodeDao().markAllListenedAndRemoveFromPlaylist(1)

        assertTrue(result.episodes.isEmpty())
        assertTrue(result.listenedEpisodeIds.isEmpty())
        assertTrue(result.removedPlaylistEpisodeIds.isEmpty())
        val sql = synchronized(statements) { statements.toList() }
        assertFalse(sql.toString(), sql.any { it.startsWith("UPDATE EPISODES") })
        assertFalse(sql.toString(), sql.any { it.startsWith("DELETE FROM PLAYLIST_ITEMS") })
    }

    @Test
    fun feedInsertionCompletedBeforeTransactionIsIncludedEvenWhenNotQueued() {
        insert(episode(101), queued = true)
        // Actual feed insertion semantics: new unlistened row, no playlist or local audio.
        workers.submit<Long> { database.episodeDao().insertEpisode(episode(103)) }
            .get(TIMEOUT_SECONDS, TimeUnit.SECONDS)

        val result = database.episodeDao().markAllListenedAndRemoveFromPlaylist(1)

        assertEquals(setOf(101L, 103L), result.episodes.map { it.id }.toSet())
        assertEquals(setOf(101L, 103L), result.listenedEpisodeIds.toSet())
        assertEquals(setOf(101L), result.removedPlaylistEpisodeIds.toSet())
        assertTrue(database.episodeDao().getEpisodeById(103)!!.isListened)
    }

    @Test
    fun feedInsertionCompetingAfterSnapshotRemainsUnlistenedAndOutsideTargets() {
        insert(episode(101), queued = true)
        insert(episode(102), queued = true)
        val barrier = WriteBarrier()
        writeBarrier.set(barrier)
        val markAll = workers.submit<MarkAllListenedSnapshot> { database.episodeDao().markAllListenedAndRemoveFromPlaylist(1) }
        await(barrier.snapshotTaken, "Mark all snapshot")
        val refresh = workers.submit<Long> {
            barrier.competingThread.set(Thread.currentThread())
            database.episodeDao().insertEpisode(episode(103))
        }
        try {
            await(barrier.competingTransactionAttempted, "Concurrent feed SQLite transaction attempt")
            assertFalse("A SQLite writer must not finish while the transaction is paused", refresh.isDone)
        } finally {
            barrier.resume.countDown()
        }

        val result = markAll.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        assertEquals(103L, refresh.get(TIMEOUT_SECONDS, TimeUnit.SECONDS))
        assertEquals(setOf(101L, 102L), result.episodes.map { it.id }.toSet())
        assertEquals(setOf(101L, 102L), result.listenedEpisodeIds.toSet())
        assertEquals(setOf(101L, 102L), result.removedPlaylistEpisodeIds.toSet())
        assertFalse(database.episodeDao().getEpisodeById(103)!!.isListened)
        assertTrue(playlistIds().isEmpty())
    }

    @Test
    fun syntheticTransactionalPruneReplacementCannotSplitSnapshotAndMutations() {
        insert(episode(201), queued = true)
        insert(episode(202, downloaded = true, path = "/test/202.mp3"), queued = true)
        val barrier = WriteBarrier()
        writeBarrier.set(barrier)
        val markAll = workers.submit<MarkAllListenedSnapshot> { database.episodeDao().markAllListenedAndRemoveFromPlaylist(1) }
        await(barrier.snapshotTaken, "Mark all snapshot")
        // Synthetic stress, NOT current RSS refresh behavior. Both prune and replacement
        // participate in a real SQLite transaction instead of bypassing a fake mutex.
        val prune = workers.submit {
            barrier.competingThread.set(Thread.currentThread())
            database.runInTransaction {
                database.openHelper.writableDatabase.execSQL("DELETE FROM episodes WHERE id = 202")
                database.episodeDao().insertEpisode(episode(203))
            }
        }
        try {
            await(barrier.competingTransactionAttempted, "Concurrent synthetic prune SQLite transaction attempt")
            assertFalse("Prune must not commit through the paused transaction", prune.isDone)
        } finally {
            barrier.resume.countDown()
        }

        val result = markAll.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        prune.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        assertEquals(setOf(201L, 202L), result.episodes.map { it.id }.toSet())
        assertEquals(setOf(201L, 202L), result.listenedEpisodeIds.toSet())
        assertEquals(setOf(201L, 202L), result.removedPlaylistEpisodeIds.toSet())
        assertEquals("/test/202.mp3", result.episodes.single { it.id == 202L }.localFilePath)
        assertEquals(null, database.episodeDao().getEpisodeById(202))
        assertFalse(database.episodeDao().getEpisodeById(203)!!.isListened)
        assertTrue(playlistIds().isEmpty())
    }

    @Test
    fun playlistDeleteFailureRollsBackEarlierListenedUpdate() {
        insert(episode(101), queued = true)
        // Test-only trigger: no production schema change and no exception swallowed by callbacks.
        database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER reject_test_playlist_delete BEFORE DELETE ON playlist_items " +
                "BEGIN SELECT RAISE(ABORT, 'test playlist deletion failure'); END"
        )

        try {
            database.episodeDao().markAllListenedAndRemoveFromPlaylist(1)
            fail("Expected playlist deletion to abort the Room transaction")
        } catch (expected: SQLiteException) {
            assertTrue(expected.message.orEmpty(), expected.message.orEmpty().contains("test playlist deletion failure"))
        }

        assertFalse(database.episodeDao().getEpisodeById(101)!!.isListened)
        assertEquals(listOf(101L), playlistIds())
    }

    @Test
    fun moreThanLegacySqliteBindLimitIsUpdatedAndRemovedWithinOneTransaction() {
        val episodes = (1L..1_005L).map { episode(it) }
        database.episodeDao().insertEpisodes(episodes)
        database.playlistDao().insertPlaylistItems(
            episodes.map { PlaylistItemEntity(episodeId = it.id, position = it.id.toInt()) }
        )

        val result = database.episodeDao().markAllListenedAndRemoveFromPlaylist(1)

        val ids = episodes.map { it.id }.toSet()
        assertEquals(ids, result.episodes.map { it.id }.toSet())
        assertEquals(ids, result.listenedEpisodeIds.toSet())
        assertEquals(ids, result.removedPlaylistEpisodeIds.toSet())
        assertTrue(database.episodeDao().getEpisodesByPodcastId(1).all { it.isListened })
        assertTrue(playlistIds().isEmpty())
    }

    @Test
    fun staleCleanupCannotClearNewerDownloadState() {
        insert(episode(101, downloaded = true, path = "/test/old.mp3"))

        database.episodeDao().updateDownloadState(101, isDownloaded = true, localFilePath = "/test/new.mp3")
        val staleRows = database.episodeDao().clearDownloadStateIfMatches(
            episodeId = 101,
            expectedIsDownloaded = true,
            expectedLocalFilePath = "/test/old.mp3"
        )

        assertEquals(0, staleRows)
        assertTrue(database.episodeDao().getEpisodeById(101)!!.isDownloaded)
        assertEquals("/test/new.mp3", database.episodeDao().getEpisodeById(101)!!.localFilePath)

        val ownerRows = database.episodeDao().clearDownloadStateIfMatches(
            episodeId = 101,
            expectedIsDownloaded = true,
            expectedLocalFilePath = "/test/new.mp3"
        )

        assertEquals(1, ownerRows)
        assertFalse(database.episodeDao().getEpisodeById(101)!!.isDownloaded)
        assertEquals(null, database.episodeDao().getEpisodeById(101)!!.localFilePath)

        database.episodeDao().updateDownloadState(101, isDownloaded = true, localFilePath = null)
        val staleFlagRows = database.episodeDao().clearDownloadStateIfMatches(
            episodeId = 101,
            expectedIsDownloaded = true,
            expectedLocalFilePath = null
        )

        assertEquals(1, staleFlagRows)
        assertFalse(database.episodeDao().getEpisodeById(101)!!.isDownloaded)
        assertEquals(null, database.episodeDao().getEpisodeById(101)!!.localFilePath)
    }

    private fun insert(episode: EpisodeEntity, queued: Boolean = false) {
        database.episodeDao().insertEpisode(episode)
        if (queued) {
            database.playlistDao().insertPlaylistItem(PlaylistItemEntity(episodeId = episode.id, position = episode.id.toInt()))
        }
    }

    private fun playlistIds() = database.playlistDao().getAllPlaylistItems().map { it.episodeId }

    private fun await(latch: CountDownLatch, label: String) {
        assertTrue("Timed out waiting for $label", latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
    }

    private fun podcast(id: Long) = PodcastEntity(id = id, feedUrl = "https://test/$id", title = "Podcast $id")

    private fun episode(
        id: Long,
        podcastId: Long = 1,
        listened: Boolean = false,
        downloaded: Boolean = false,
        path: String? = null
    ) = EpisodeEntity(
        id = id,
        podcastId = podcastId,
        guid = "guid-$id",
        title = "Episode $id",
        audioUrl = "https://test/$id.mp3",
        isListened = listened,
        isDownloaded = downloaded,
        localFilePath = path
    )

    private class WriteBarrier {
        val paused = AtomicBoolean(false)
        val competingThread = AtomicReference<Thread?>(null)
        val snapshotTaken = CountDownLatch(1)
        val competingTransactionAttempted = CountDownLatch(1)
        val resume = CountDownLatch(1)
    }

    private companion object {
        const val TIMEOUT_SECONDS = 15L
    }
}
