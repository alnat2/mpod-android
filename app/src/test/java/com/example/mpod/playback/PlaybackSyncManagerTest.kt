package com.example.mpod.playback

import com.example.mpod.data.network.model.PlaybackStateDto
import com.example.mpod.data.network.model.PlaybackUpdateRequest
import com.example.mpod.data.network.model.PlaybackUpdateResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackSyncManagerTest {
    @Test
    fun transientPlaybackFailureRemainsPendingUntilSuccessfulRetry() = runBlocking {
        val store = InMemoryPendingPlaybackSyncStore()
        val transport = FakePlaybackSyncTransport().apply {
            playbackResults += SyncAttempt.RetryableFailure
            playbackResults += SyncAttempt.Success(responseFor(episodeId = 7, positionSeconds = 42))
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val manager = PlaybackSyncManager(transport, store, scope)

        assertNull(manager.submitPlayback(request(episodeId = 7, positionSeconds = 42)))
        assertFalse(store.snapshot().isEmpty)

        manager.flushPendingOnce()

        assertTrue(store.snapshot().isEmpty)
        assertEquals(2, transport.playbackRequests.size)
        scope.cancel()
    }

    @Test
    fun permanentPlaybackFailureIsNotRetriedForever() = runBlocking {
        val store = InMemoryPendingPlaybackSyncStore()
        val transport = FakePlaybackSyncTransport().apply {
            playbackResults += SyncAttempt.PermanentFailure
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val manager = PlaybackSyncManager(transport, store, scope)

        assertNull(manager.submitPlayback(request(episodeId = 3, positionSeconds = 9)))

        assertTrue(store.snapshot().isEmpty)
        assertEquals(1, transport.playbackRequests.size)
        scope.cancel()
    }

    @Test
    fun delayedCompletionNotifiesServiceAfterRetry() = runBlocking {
        val store = InMemoryPendingPlaybackSyncStore()
        val completion = request(episodeId = 4, positionSeconds = 100, completed = true)
        val response = responseFor(episodeId = 4, positionSeconds = 100, nextEpisodeId = 2)
        val transport = FakePlaybackSyncTransport().apply {
            playbackResults += SyncAttempt.RetryableFailure
            playbackResults += SyncAttempt.Success(response)
        }
        val callbacks = mutableListOf<Pair<PlaybackUpdateRequest, PlaybackUpdateResponse>>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val manager = PlaybackSyncManager(
            transport = transport,
            store = store,
            scope = scope,
            nowIso = { "2026-07-16T12:03:00Z" },
            onRetriedCompletion = { request, result -> callbacks += request to result }
        )

        assertNull(manager.submitPlayback(completion))
        manager.flushPendingOnce()

        assertEquals(listOf(completion to response), callbacks)
        assertEquals("2026-07-16T12:03:00Z", transport.playbackRequests.last().clientUpdatedAt)
        assertTrue(store.snapshot().isEmpty)
        scope.cancel()
    }

    @Test
    fun activeAndSpeedFailuresRemainPendingAndUseLatestValues() = runBlocking {
        val store = InMemoryPendingPlaybackSyncStore()
        val transport = FakePlaybackSyncTransport().apply {
            activeResults += SyncAttempt.RetryableFailure
            activeResults += SyncAttempt.RetryableFailure
            activeResults += SyncAttempt.Success(Unit)
            speedResults += SyncAttempt.RetryableFailure
            speedResults += SyncAttempt.RetryableFailure
            speedResults += SyncAttempt.Success(Unit)
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val manager = PlaybackSyncManager(transport, store, scope)

        assertFalse(manager.submitActive(1))
        assertFalse(manager.submitSpeed("Speed 1x"))
        assertFalse(manager.submitActive(2))
        assertFalse(manager.submitSpeed("Speed 1.5x"))
        manager.flushPendingOnce()

        assertTrue(store.snapshot().isEmpty)
        assertEquals(listOf(1, 2, 2), transport.activeEpisodeIds)
        assertEquals(listOf("Speed 1x", "Speed 1.5x", "Speed 1.5x"), transport.speedLabels)
        scope.cancel()
    }

    @Test
    fun completionCannotBeOverwrittenByLaterProgress() {
        val completion = request(episodeId = 8, positionSeconds = 120, completed = true)
        val laterProgress = request(
            episodeId = 8,
            positionSeconds = 12,
            clientUpdatedAt = "2026-07-16T12:01:00Z"
        )

        assertEquals(completion, mergePendingPlayback(completion, laterProgress))
    }

    @Test
    fun explicitSeekSurvivesProgressCoalescingUntilAcknowledged() {
        val seek = request(
            episodeId = 9,
            positionSeconds = 10,
            didSeek = true
        )
        val laterProgress = request(
            episodeId = 9,
            positionSeconds = 22,
            clientUpdatedAt = "2026-07-16T12:01:00Z"
        )

        val merged = mergePendingPlayback(seek, laterProgress)

        assertEquals(22, merged.positionSeconds)
        assertEquals("2026-07-16T12:01:00Z", merged.clientUpdatedAt)
        assertTrue(merged.didSeek)
    }

    @Test
    fun latestActiveAndSpeedValuesReplaceOlderPendingValues() {
        val store = InMemoryPendingPlaybackSyncStore()

        store.putActive(1)
        store.putActive(2)
        store.putSpeed("Speed 1x")
        store.putSpeed("Speed 1.5x")

        assertEquals(2, store.snapshot().activeEpisodeId)
        assertEquals("Speed 1.5x", store.snapshot().speedLabel)
    }

    @Test
    fun onlySemanticClientErrorsArePermanent() {
        assertTrue(isPermanentPlaybackSyncFailure(400))
        assertTrue(isPermanentPlaybackSyncFailure(404))
        assertFalse(isPermanentPlaybackSyncFailure(401))
        assertFalse(isPermanentPlaybackSyncFailure(408))
        assertFalse(isPermanentPlaybackSyncFailure(429))
        assertFalse(isPermanentPlaybackSyncFailure(503))
    }

    private fun request(
        episodeId: Int,
        positionSeconds: Int,
        completed: Boolean = false,
        didSeek: Boolean = false,
        clientUpdatedAt: String = "2026-07-16T12:00:00Z"
    ) = PlaybackUpdateRequest(
        episodeId = episodeId,
        positionSeconds = positionSeconds,
        durationSeconds = 120,
        completed = completed,
        didSeek = didSeek,
        clientUpdatedAt = clientUpdatedAt
    )

    private fun responseFor(
        episodeId: Int,
        positionSeconds: Int,
        nextEpisodeId: Int? = null
    ) = PlaybackUpdateResponse(
        playback = PlaybackStateDto(
            episodeId = episodeId,
            positionSeconds = positionSeconds,
            lastUpdated = "2026-07-16T12:02:00Z"
        ),
        nextEpisodeId = nextEpisodeId
    )
}

private class InMemoryPendingPlaybackSyncStore : PendingPlaybackSyncStore {
    private var activeEpisodeId: Int? = null
    private val playbackUpdates = mutableMapOf<Int, PlaybackUpdateRequest>()
    private var speedLabel: String? = null

    override fun snapshot() = PendingPlaybackSync(
        activeEpisodeId = activeEpisodeId,
        playbackUpdates = playbackUpdates.values.sortedBy { it.episodeId },
        speedLabel = speedLabel
    )

    override fun putActive(episodeId: Int) {
        activeEpisodeId = episodeId
    }

    override fun clearActiveIf(episodeId: Int) {
        if (activeEpisodeId == episodeId) activeEpisodeId = null
    }

    override fun putPlayback(request: PlaybackUpdateRequest) {
        playbackUpdates[request.episodeId] = mergePendingPlayback(
            playbackUpdates[request.episodeId],
            request
        )
    }

    override fun clearPlaybackIf(episodeId: Int, clientUpdatedAt: String) {
        if (playbackUpdates[episodeId]?.clientUpdatedAt == clientUpdatedAt) {
            playbackUpdates.remove(episodeId)
        }
    }

    override fun putSpeed(label: String) {
        speedLabel = label
    }

    override fun clearSpeedIf(label: String) {
        if (speedLabel == label) speedLabel = null
    }
}

private class FakePlaybackSyncTransport : PlaybackSyncTransport {
    val playbackResults = ArrayDeque<SyncAttempt<PlaybackUpdateResponse>>()
    val playbackRequests = mutableListOf<PlaybackUpdateRequest>()
    val activeResults = ArrayDeque<SyncAttempt<Unit>>()
    val activeEpisodeIds = mutableListOf<Int>()
    val speedResults = ArrayDeque<SyncAttempt<Unit>>()
    val speedLabels = mutableListOf<String>()

    override suspend fun setActive(episodeId: Int): SyncAttempt<Unit> {
        activeEpisodeIds += episodeId
        return activeResults.removeFirstOrNull() ?: SyncAttempt.Success(Unit)
    }

    override suspend fun updatePlayback(
        request: PlaybackUpdateRequest
    ): SyncAttempt<PlaybackUpdateResponse> {
        playbackRequests += request
        return playbackResults.removeFirst()
    }

    override suspend fun updateSpeed(label: String): SyncAttempt<Unit> {
        speedLabels += label
        return speedResults.removeFirstOrNull() ?: SyncAttempt.Success(Unit)
    }
}
