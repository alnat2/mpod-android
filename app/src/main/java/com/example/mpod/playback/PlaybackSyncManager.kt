package com.example.mpod.playback

import com.example.mpod.data.network.MpodApi
import com.example.mpod.data.network.model.ActivePlaybackRequest
import com.example.mpod.data.network.model.PlaybackUpdateRequest
import com.example.mpod.data.network.model.PlaybackUpdateResponse
import com.example.mpod.data.network.model.SettingsUpdateRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import retrofit2.Response
import java.time.Instant

internal sealed interface SyncAttempt<out T> {
    data class Success<T>(val value: T) : SyncAttempt<T>
    data object RetryableFailure : SyncAttempt<Nothing>
    data object PermanentFailure : SyncAttempt<Nothing>
}

internal interface PlaybackSyncTransport {
    suspend fun setActive(episodeId: Int): SyncAttempt<Unit>
    suspend fun updatePlayback(request: PlaybackUpdateRequest): SyncAttempt<PlaybackUpdateResponse>
    suspend fun updateSpeed(label: String): SyncAttempt<Unit>
}

internal class ApiPlaybackSyncTransport(private val api: MpodApi) : PlaybackSyncTransport {
    override suspend fun setActive(episodeId: Int): SyncAttempt<Unit> {
        return runCatching { api.setActivePlayback(ActivePlaybackRequest(episodeId)) }
            .fold(
                onSuccess = { it.toUnitSyncAttempt() },
                onFailure = { SyncAttempt.RetryableFailure }
            )
    }

    override suspend fun updatePlayback(
        request: PlaybackUpdateRequest
    ): SyncAttempt<PlaybackUpdateResponse> {
        return runCatching { api.updatePlayback(request) }
            .fold(
                onSuccess = { response ->
                    when {
                        response.isSuccessful && response.body() != null -> {
                            SyncAttempt.Success(response.body()!!)
                        }
                        isPermanentPlaybackSyncFailure(response.code()) -> SyncAttempt.PermanentFailure
                        else -> SyncAttempt.RetryableFailure
                    }
                },
                onFailure = { SyncAttempt.RetryableFailure }
            )
    }

    override suspend fun updateSpeed(label: String): SyncAttempt<Unit> {
        return runCatching {
            api.updateSettings(SettingsUpdateRequest(playbackSpeed = label))
        }.fold(
            onSuccess = { it.toUnitSyncAttempt() },
            onFailure = { SyncAttempt.RetryableFailure }
        )
    }

    private fun Response<*>.toUnitSyncAttempt(): SyncAttempt<Unit> {
        return when {
            isSuccessful -> SyncAttempt.Success(Unit)
            isPermanentPlaybackSyncFailure(code()) -> SyncAttempt.PermanentFailure
            else -> SyncAttempt.RetryableFailure
        }
    }
}

internal class PlaybackSyncManager(
    private val transport: PlaybackSyncTransport,
    private val store: PendingPlaybackSyncStore,
    private val scope: CoroutineScope,
    private val retryDelaysMs: List<Long> = DEFAULT_RETRY_DELAYS_MS,
    private val nowIso: () -> String = { Instant.now().toString() },
    private val onRetriedCompletion: suspend (
        PlaybackUpdateRequest,
        PlaybackUpdateResponse
    ) -> Unit = { _, _ -> }
) {
    private val syncMutex = Mutex()
    private val wakeups = Channel<Unit>(capacity = Channel.CONFLATED)
    private var retryJob: Job? = null

    fun start() {
        if (retryJob?.isActive == true) return
        retryJob = scope.launch {
            wakeups.trySend(Unit)
            while (isActive) {
                wakeups.receive()
                var delayIndex = 0
                while (isActive && !store.snapshot().isEmpty) {
                    val retryDelay = retryDelaysMs[delayIndex.coerceAtMost(retryDelaysMs.lastIndex)]
                    val wokeForNewMutation = withTimeoutOrNull(retryDelay) {
                        wakeups.receive()
                        true
                    } == true
                    if (wokeForNewMutation) {
                        delayIndex = 0
                        continue
                    }

                    val hadRetryableFailure = flushPendingOnce(notifyRetriedCompletion = true)
                    if (store.snapshot().isEmpty) break
                    delayIndex = if (hadRetryableFailure) {
                        (delayIndex + 1).coerceAtMost(retryDelaysMs.lastIndex)
                    } else {
                        0
                    }
                }
            }
        }
    }

    fun pendingSnapshot(): PendingPlaybackSync = store.snapshot()

    suspend fun submitActive(episodeId: Int): Boolean {
        val result = syncMutex.withLock {
            store.putActive(episodeId)
            val attempt = transport.setActive(episodeId)
            if (attempt is SyncAttempt.Success || attempt is SyncAttempt.PermanentFailure) {
                store.clearActiveIf(episodeId)
            }
            attempt is SyncAttempt.Success
        }
        signalIfPending()
        return result
    }

    suspend fun submitPlayback(request: PlaybackUpdateRequest): PlaybackUpdateResponse? {
        val result = syncMutex.withLock {
            store.putPlayback(request)
            val pending = store.snapshot().playbackFor(request.episodeId)!!
            when (val attempt = transport.updatePlayback(pending)) {
                is SyncAttempt.Success -> {
                    store.clearPlaybackIf(pending.episodeId, pending.clientUpdatedAt)
                    attempt.value
                }
                SyncAttempt.PermanentFailure -> {
                    store.clearPlaybackIf(pending.episodeId, pending.clientUpdatedAt)
                    null
                }
                SyncAttempt.RetryableFailure -> null
            }
        }
        signalIfPending()
        return result
    }

    suspend fun submitSpeed(label: String): Boolean {
        val result = syncMutex.withLock {
            store.putSpeed(label)
            val attempt = transport.updateSpeed(label)
            if (attempt is SyncAttempt.Success || attempt is SyncAttempt.PermanentFailure) {
                store.clearSpeedIf(label)
            }
            attempt is SyncAttempt.Success
        }
        signalIfPending()
        return result
    }

    internal suspend fun flushPendingOnce(notifyRetriedCompletion: Boolean = true): Boolean {
        val outcome = syncMutex.withLock {
            val snapshot = store.snapshot()
            var retryableFailure = false
            val completed = mutableListOf<Pair<PlaybackUpdateRequest, PlaybackUpdateResponse>>()

            snapshot.activeEpisodeId?.let { episodeId ->
                when (transport.setActive(episodeId)) {
                    is SyncAttempt.Success, SyncAttempt.PermanentFailure -> store.clearActiveIf(episodeId)
                    SyncAttempt.RetryableFailure -> retryableFailure = true
                }
            }

            snapshot.playbackUpdates.forEach { request ->
                val outboundRequest = if (request.completed) {
                    request.copy(clientUpdatedAt = nowIso())
                } else {
                    request
                }
                when (val attempt = transport.updatePlayback(outboundRequest)) {
                    is SyncAttempt.Success -> {
                        store.clearPlaybackIf(request.episodeId, request.clientUpdatedAt)
                        if (request.completed) completed += request to attempt.value
                    }
                    SyncAttempt.PermanentFailure -> {
                        store.clearPlaybackIf(request.episodeId, request.clientUpdatedAt)
                    }
                    SyncAttempt.RetryableFailure -> retryableFailure = true
                }
            }

            snapshot.speedLabel?.let { label ->
                when (transport.updateSpeed(label)) {
                    is SyncAttempt.Success, SyncAttempt.PermanentFailure -> store.clearSpeedIf(label)
                    SyncAttempt.RetryableFailure -> retryableFailure = true
                }
            }

            FlushOutcome(retryableFailure = retryableFailure, completed = completed)
        }

        if (notifyRetriedCompletion) {
            outcome.completed.forEach { (request, response) ->
                onRetriedCompletion(request, response)
            }
        }
        return outcome.retryableFailure
    }

    private fun signalIfPending() {
        if (!store.snapshot().isEmpty) wakeups.trySend(Unit)
    }

    private data class FlushOutcome(
        val retryableFailure: Boolean,
        val completed: List<Pair<PlaybackUpdateRequest, PlaybackUpdateResponse>>
    )

    companion object {
        private val DEFAULT_RETRY_DELAYS_MS = listOf(1_000L, 2_000L, 5_000L, 15_000L, 30_000L)
    }
}

private fun PendingPlaybackSync.playbackFor(episodeId: Int): PlaybackUpdateRequest? {
    return playbackUpdates.firstOrNull { it.episodeId == episodeId }
}

internal fun isPermanentPlaybackSyncFailure(code: Int): Boolean {
    return code in 400..499 && code !in setOf(401, 408, 409, 425, 429)
}
