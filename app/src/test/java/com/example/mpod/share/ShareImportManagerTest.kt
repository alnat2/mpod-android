package com.example.mpod.share

import android.content.Context
import androidx.work.WorkManager
import com.example.mpod.data.local.dao.PendingShareImportDao
import com.example.mpod.data.local.entity.PendingShareImportEntity
import com.example.mpod.data.local.entity.ShareImportStatus
import com.example.mpod.data.repository.SingleTrackRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import javax.inject.Provider

class ShareImportManagerTest {
    @Test
    fun fingerprintIsStableForWhitespaceAndRepresentsParsedPayload() {
        val dao = mock(PendingShareImportDao::class.java)
        `when`(dao.observeRecent()).thenReturn(flowOf(emptyList()))
        val manager = ShareImportManager(
            context = mock(Context::class.java),
            dao = dao,
            singleTrackRepository = mock(SingleTrackRepository::class.java),
            workManagerProvider = Provider { mock(WorkManager::class.java) }
        )

        assertEquals(manager.fingerprint(" https://example.com/audio.mp3 "), manager.fingerprint("https://example.com/audio.mp3"))
        assertNotEquals(manager.fingerprint("https://example.com/audio-a.mp3"), manager.fingerprint("https://example.com/audio-b.mp3"))
    }

    @Test
    fun redeliverySuppressionUsesGraceWindowAndPendingRecovery() {
        assertTrue(
            shouldSuppressShareRedelivery(
                now = 12_500L,
                previousCreatedAt = 10_000L,
                isPending = false,
                suppressExistingPending = false,
                graceWindowMs = ShareImportManager.REDELIVERY_GRACE_MS
            )
        )
        assertTrue(
            shouldSuppressShareRedelivery(
                now = 30_000L,
                previousCreatedAt = 10_000L,
                isPending = true,
                suppressExistingPending = true,
                graceWindowMs = ShareImportManager.REDELIVERY_GRACE_MS
            )
        )
        assertFalse(
            shouldSuppressShareRedelivery(
                now = 14_000L,
                previousCreatedAt = 10_000L,
                isPending = false,
                suppressExistingPending = false,
                graceWindowMs = ShareImportManager.REDELIVERY_GRACE_MS
            )
        )
    }

    @Test
    fun submitDeduplicatesWithinGraceAndCreatesNewEventAfterWindow() = kotlinx.coroutines.test.runTest {
        val dao = FakePendingShareImportDao()
        val manager = ShareImportManager(
            context = mock(Context::class.java),
            dao = dao,
            singleTrackRepository = mock(SingleTrackRepository::class.java),
            workManagerProvider = Provider { mock(WorkManager::class.java) }
        )
        val payload = "https://example.com/episode.mp3"
        val fingerprint = manager.fingerprint(payload)
        val first = manager.submit(payload, null, fingerprint = fingerprint, token = "first", now = 10_000L)
        val redelivery = manager.submit(payload, null, fingerprint = fingerprint, token = "redelivery", now = 12_500L)

        assertEquals(first.token, redelivery.token)
        assertEquals(1, dao.insertCount)

        val intentionalRepeat = manager.submit(
            payload,
            null,
            fingerprint = fingerprint,
            token = "intentional-repeat",
            now = 14_000L
        )
        assertNotEquals(first.token, intentionalRepeat.token)
        assertEquals(2, dao.insertCount)

        val activityRecovery = manager.submit(
            payload,
            null,
            fingerprint = fingerprint,
            token = "activity-recovery",
            now = 30_000L,
            suppressExistingPending = true
        )
        assertEquals(intentionalRepeat.token, activityRecovery.token)
        assertEquals(2, dao.insertCount)
    }

    @Test
    fun concurrentSubmissionsWithinQuietWindowShareOneBatchId() = kotlinx.coroutines.test.runTest {
        val dao = FakePendingShareImportDao()
        val manager = ShareImportManager(
            context = mock(Context::class.java),
            dao = dao,
            singleTrackRepository = mock(SingleTrackRepository::class.java),
            workManagerProvider = Provider { mock(WorkManager::class.java) }
        )

        val events = listOf(
            async {
                manager.submit(
                    payload = "https://example.com/one.mp3",
                    title = null,
                    token = "one",
                    now = 5_000L
                )
            },
            async {
                manager.submit(
                    payload = "https://example.com/two.mp3",
                    title = null,
                    token = "two",
                    now = 5_020L
                )
            }
        ).awaitAll()

        assertEquals(events[0].batchId, events[1].batchId)
        assertEquals(2, dao.insertCount)
    }

    @Test
    fun cancelImportMarksEnqueuedEventFailed() = kotlinx.coroutines.test.runTest {
        val dao = FakePendingShareImportDao()
        val event = PendingShareImportEntity(
            token = "queued-token",
            payload = "https://example.com/queued.mp3",
            title = null,
            status = ShareImportStatus.ENQUEUED,
            batchId = "queued-batch",
            fingerprint = "queued-fingerprint",
            createdAt = 1L,
            updatedAt = 1L
        )
        dao.insertOrReturn(event)
        val manager = ShareImportManager(
            context = mock(Context::class.java),
            dao = dao,
            singleTrackRepository = mock(SingleTrackRepository::class.java),
            workManagerProvider = Provider { mock(WorkManager::class.java) }
        )

        manager.cancelImport(event.token)

        assertEquals(ShareImportStatus.FAILED, dao.getByToken(event.token)?.status)
        assertEquals("Import cancelled.", dao.getByToken(event.token)?.error)
    }

    @Test
    fun visibleBatchPrefersUnfinishedImportOverNewerCompletedBatch() {
        val olderActive = PendingShareImportEntity(
            token = "large-copy",
            payload = "content://media/audio",
            title = null,
            status = ShareImportStatus.COPYING,
            sourceUri = "content://media/audio",
            batchId = "slow-batch",
            fingerprint = "slow",
            createdAt = 1_000L,
            updatedAt = 10_000L
        )
        val newerDone = PendingShareImportEntity(
            token = "fast-link",
            payload = "https://example.com/audio.mp3",
            title = null,
            status = ShareImportStatus.DONE,
            batchId = "fast-batch",
            fingerprint = "fast",
            createdAt = 5_000L,
            updatedAt = 5_500L,
            completionAcknowledged = true
        )

        assertEquals("slow-batch", selectVisibleShareBatchId(listOf(newerDone, olderActive)))
    }
}
