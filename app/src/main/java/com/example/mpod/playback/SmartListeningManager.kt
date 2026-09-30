package com.example.mpod.playback

import android.content.Context
import com.example.mpod.BuildConfig
import com.example.mpod.data.local.dao.EpisodeDao
import com.example.mpod.data.local.dao.PlaylistDao
import com.example.mpod.data.network.ProxyHttpClientFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

sealed class DownloadOutcome {
    object Success : DownloadOutcome()
    data class Failure(val cleanupFailed: Boolean = false, val message: String?) : DownloadOutcome()
}

sealed interface CleanupResult {
    object Success : CleanupResult
    data class DeleteFailed(val path: String) : CleanupResult
}

@Singleton
class SmartListeningManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playlistDao: PlaylistDao,
    private val episodeDao: EpisodeDao,
    private val proxyHttpClientFactory: ProxyHttpClientFactory
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pendingDownloadJobs = ConcurrentHashMap<Long, Job>()
    private val lifecycleMutex = Mutex()
    private var observationJob: Job? = null
    // Scheduling and suppression share one lock, so even a previously emitted Room snapshot
    // cannot register an owner after cleanup has captured the jobs it must join.
    private val ownershipLock = Any()
    private val pausedPodcasts = mutableMapOf<Long, Int>()
    private val pausedEpisodes = mutableMapOf<Long, Int>()
    private val rescheduleAfterCleanup = mutableSetOf<Long>()
    private var stopping = false
    private var lifecycleGeneration = 0L

    internal val activeObservationJobForTest: Job?
        get() = observationJob

    internal var fileOps: FileOperations = DefaultFileOperations
    internal var debounceMs: Long = 15_000L
    internal var preDaoHook: (suspend () -> Unit)? = null
    internal var postDaoHook: (suspend () -> Unit)? = null

    suspend fun startObserving() = lifecycleMutex.withLock {
        if (observationJob != null) return@withLock
        synchronized(ownershipLock) {
            check(!stopping) { "SmartListeningManager is stopping" }
        }
        observationJob = scope.launch {
            playlistDao.getPlaylistItemsWithEpisodesFlow().collectLatest { items ->
                val currentPlaylistEpisodeIds = items.map { it.episode.id }.toSet()

                val cancelledIds = pendingDownloadJobs.keys.filter { it !in currentPlaylistEpisodeIds }
                for (id in cancelledIds) {
                    // Keep the job discoverable until its finally block removes it. Cleanup
                    // must still be able to join a download already cancelled by this observer.
                    pendingDownloadJobs[id]?.cancelAndJoin()
                    android.util.Log.d("SmartListening", "Cancelled download for removed episode $id")
                }

                for (item in items) {
                    val ep = item.episode
                    // A removed episode may be re-added while cancellation is finishing.
                    // Wait for that old owner before allowing a replacement download.
                    pendingDownloadJobs[ep.id]?.takeIf { it.isCancelled }?.join()
                    if (!ep.isDownloaded && ep.localFilePath.isNullOrBlank()) {
                        if (!ep.audioUrl.isNullOrBlank() && !pendingDownloadJobs.containsKey(ep.id)) {
                            scheduleDebouncedDownload(ep.id, ep.audioUrl)
                        }
                    }
                }
            }
        }
    }

    suspend fun stopObserving() = lifecycleMutex.withLock {
        // Once shutdown owns the mutex, caller cancellation must not release the lifecycle
        // or hide owners whose OkHttp callbacks are still finishing filesystem writes.
        withContext(NonCancellable) {
            synchronized(ownershipLock) {
                stopping = true
                lifecycleGeneration++
            }
            observationJob?.cancelAndJoin()

            while (true) {
                val jobs = pendingDownloadJobs.values.toList()
                if (jobs.isEmpty()) break
                jobs.forEach { it.cancel() }
                jobs.forEach { it.join() }
            }
            check(pendingDownloadJobs.isEmpty()) { "Download owners must finish before stopObserving returns" }
            observationJob = null
            synchronized(ownershipLock) {
                stopping = false
            }
        }
    }

    private fun scheduleDebouncedDownload(
        episodeId: Long,
        audioUrl: String,
        expectedGeneration: Long? = null
    ) = synchronized(ownershipLock) {
        if (stopping || (expectedGeneration != null && expectedGeneration != lifecycleGeneration)) return@synchronized
        val episode = episodeDao.getEpisodeById(episodeId) ?: return@synchronized
        if (episode.podcastId in pausedPodcasts) return@synchronized
        if (episodeId in pausedEpisodes) {
            rescheduleAfterCleanup.add(episodeId)
            return@synchronized
        }
        if (episode.isDownloaded || !episode.localFilePath.isNullOrBlank()) return@synchronized
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                android.util.Log.d("SmartListening", "Scheduling debounced download (${debounceMs}ms) for episode $episodeId ($audioUrl)")
                delay(debounceMs)

                val inPlaylist = playlistDao.isEpisodeInPlaylist(episodeId)
                if (!inPlaylist) {
                    android.util.Log.d("SmartListening", "Episode $episodeId is no longer in playlist, skipping download")
                    return@launch
                }

                val episode = episodeDao.getEpisodeById(episodeId)
                if (episode == null || episode.isDownloaded || audioUrl.isBlank()) {
                    android.util.Log.d("SmartListening", "Episode $episodeId already downloaded or empty URL, skipping")
                    return@launch
                }

                android.util.Log.d("SmartListening", "Starting background audio download for episode $episodeId: $audioUrl")
                val outcome = downloadAudioFile(episodeId, audioUrl)
                android.util.Log.d("SmartListening", "Download finished for episode $episodeId, outcome=$outcome")
            } finally {
                pendingDownloadJobs.remove(episodeId, coroutineContext.job)
            }
        }

        val previous = pendingDownloadJobs.putIfAbsent(episodeId, job)
        if (previous == null) job.start() else job.cancel()
    }

    /** Holds suppression through the caller's Room deletion, without a network/filesystem transaction. */
    suspend fun <T> withPodcastDownloadsPaused(podcastId: Long, block: suspend () -> T): T {
        val jobs = synchronized(ownershipLock) {
            val owners = pendingDownloadJobs.filterKeys { episodeDao.getEpisodeById(it)?.podcastId == podcastId }.values.toList()
            pausedPodcasts[podcastId] = (pausedPodcasts[podcastId] ?: 0) + 1
            owners
        }
        try {
            // The podcast pause was installed before releasing ownershipLock. Scheduling
            // checks it under the same lock, so no new owner can register before cancellation;
            // cancelling and joining this captured set outside the lock is sufficient.
            jobs.forEach { it.cancel() }
            jobs.forEach { it.join() }
            return block()
        } finally {
            synchronized(ownershipLock) {
                val remaining = pausedPodcasts.getValue(podcastId) - 1
                if (remaining == 0) pausedPodcasts.remove(podcastId) else pausedPodcasts[podcastId] = remaining
            }
        }
    }

    internal suspend fun downloadAudioFile(episodeId: Long, audioUrl: String): DownloadOutcome =
        withContext(Dispatchers.IO) {
            val podcastsDir = File(context.filesDir, "podcasts").apply { if (!exists()) mkdirs() }
            val ext = audioUrl.substringBefore('?').substringAfterLast('.', "")
                .takeIf { it.length in 2..4 && it.all { c -> c.isLetterOrDigit() } } ?: "mp3"
            val fileName = "ep_${episodeId}_${System.currentTimeMillis()}.$ext"
            val targetFile = File(podcastsDir, fileName)
            val tempFile = File(podcastsDir, "${fileName}.tmp")
            var roomCommitted = false

            try {
                val client = proxyHttpClientFactory.createClient()
                val request = Request.Builder()
                    .url(audioUrl)
                    .header("User-Agent", "mpoddy/${BuildConfig.VERSION_NAME} (Android Podcast Player)")
                    .build()

                val call = client.newCall(request)

                val callbackFinished = CompletableDeferred<Unit>()
                val result: DownloadFinalizer.Result = try {
                    suspendCancellableCoroutine { continuation ->
                        continuation.invokeOnCancellation { call.cancel() }

                        try {
                            call.enqueue(object : Callback {
                                override fun onFailure(call: Call, e: IOException) {
                                    try {
                                        if (continuation.isActive) continuation.resumeWith(kotlin.Result.failure(e))
                                    } finally {
                                        callbackFinished.complete(Unit)
                                    }
                                }

                                override fun onResponse(call: Call, response: Response) {
                                    try {
                                        response.use { resp ->
                                            if (!resp.isSuccessful) {
                                                android.util.Log.w("SmartListening", "Download HTTP failed with code ${resp.code} for $audioUrl")
                                                val clean = DownloadFinalizer.cleanupTempFile(tempFile, fileOps)
                                                if (continuation.isActive) {
                                                    continuation.resume(
                                                        DownloadFinalizer.Result(success = false, cleanupFailed = !clean, errorMessage = "HTTP ${resp.code}: ${resp.message}")
                                                    )
                                                }
                                                return
                                            }
                                            val body = resp.body
                                            if (body == null) {
                                                val clean = DownloadFinalizer.cleanupTempFile(tempFile, fileOps)
                                                if (continuation.isActive) {
                                                    continuation.resume(
                                                        DownloadFinalizer.Result(success = false, cleanupFailed = !clean, errorMessage = "Empty HTTP response body")
                                                    )
                                                }
                                                return
                                            }
                                            val finalizerResult = body.byteStream().use { input ->
                                                DownloadFinalizer.downloadAndFinalize(
                                                    tempFile = tempFile,
                                                    targetFile = targetFile,
                                                    dataStream = input,
                                                    isCancelled = { continuation.isCancelled },
                                                    fileOps = fileOps
                                                )
                                            }
                                            if (continuation.isActive) {
                                                continuation.resume(finalizerResult)
                                            }
                                        }
                                    } catch (e: Throwable) {
                                        if (continuation.isActive) {
                                            continuation.resumeWith(kotlin.Result.failure(e))
                                        }
                                    } finally {
                                        callbackFinished.complete(Unit)
                                    }
                                }
                            })
                        } catch (error: Exception) {
                            callbackFinished.complete(Unit)
                            if (continuation.isActive) continuation.resumeWith(kotlin.Result.failure(error))
                        }
                    }
                } finally {
                    // Coroutine cancellation resumes immediately; OkHttp may still be writing.
                    // Keep the owner alive until its callback closes the body and output stream.
                    withContext(NonCancellable) { callbackFinished.await() }
                }

                if (!result.success) {
                    android.util.Log.e("SmartListening", "Download failed for episode $episodeId (cleanupFailed=${result.cleanupFailed}): ${result.errorMessage}")
                    return@withContext DownloadOutcome.Failure(cleanupFailed = result.cleanupFailed, message = result.errorMessage)
                }

                ensureActive()
                preDaoHook?.invoke()
                ensureActive()

                episodeDao.updateDownloadState(episodeId = episodeId, isDownloaded = true, localFilePath = result.finalFile!!.absolutePath)
                roomCommitted = true

                postDaoHook?.invoke()

                android.util.Log.i("SmartListening", "Saved episode $episodeId to ${targetFile.absolutePath} (${targetFile.length()} bytes)")
                DownloadOutcome.Success
            } catch (e: CancellationException) {
                if (!roomCommitted) {
                    val tempClean = DownloadFinalizer.cleanupTempFile(tempFile, fileOps)
                    val targetClean = DownloadFinalizer.cleanupTargetFile(targetFile, fileOps)
                    if (!tempClean || !targetClean) {
                        android.util.Log.e("SmartListening", "CRITICAL: Cleanup failed during cancellation for episode $episodeId: tempClean=$tempClean, targetClean=$targetClean")
                    }
                }
                throw e
            } catch (e: Exception) {
                android.util.Log.e("SmartListening", "Error downloading episode $episodeId: ${e.message}", e)
                val tempClean = DownloadFinalizer.cleanupTempFile(tempFile, fileOps)
                val targetClean = DownloadFinalizer.cleanupTargetFile(targetFile, fileOps)
                val cleanupFailed = !tempClean || !targetClean
                if (cleanupFailed) {
                    android.util.Log.e("SmartListening", "CRITICAL: Cleanup failed during error handling for episode $episodeId: tempClean=$tempClean, targetClean=$targetClean")
                }
                DownloadOutcome.Failure(cleanupFailed = cleanupFailed, message = e.message)
            }
        }

    suspend fun cleanupEpisodeFile(episodeId: Long): CleanupResult = withContext(Dispatchers.IO) {
        val owner = synchronized(ownershipLock) {
            pausedEpisodes[episodeId] = (pausedEpisodes[episodeId] ?: 0) + 1
            CleanupOwner(pendingDownloadJobs[episodeId], lifecycleGeneration)
        }
        var cleaned = false
        try {
            owner.job?.cancelAndJoin()
            val episode = episodeDao.getEpisodeById(episodeId)
            val directory = File(context.filesDir, "podcasts")
            // A cancelled/failed download can leave a file before its path reaches Room.
            // The ID prefix is durable ownership information, also available after process restart.
            val ownedFiles = if (directory.exists()) {
                directory.listFiles { _, name -> name.startsWith("ep_${episodeId}_") }
                    ?.toList() ?: throw IOException("Could not list downloaded episode files")
            } else emptyList()
            val linkedFile = episode?.localFilePath?.takeIf { it.isNotBlank() }?.let(::File)
            for (file in (ownedFiles + listOfNotNull(linkedFile)).distinctBy { it.absolutePath }) {
                if (fileOps.exists(file) && !fileOps.delete(file)) {
                    return@withContext CleanupResult.DeleteFailed(file.absolutePath)
                }
            }
            if (episode != null && (episode.isDownloaded || !episode.localFilePath.isNullOrBlank())) {
                episodeDao.clearDownloadStateIfMatches(
                    episodeId, episode.isDownloaded, episode.localFilePath
                )
            }
            cleaned = true
            CleanupResult.Success
        } finally {
            val reschedule = synchronized(ownershipLock) {
                val remaining = pausedEpisodes.getValue(episodeId) - 1
                if (remaining == 0) {
                    pausedEpisodes.remove(episodeId)
                    rescheduleAfterCleanup.remove(episodeId)
                } else {
                    pausedEpisodes[episodeId] = remaining
                    false
                }
            }
            // A re-add emission may have been suppressed while cleanup was running. Recheck
            // current Room state after success rather than replaying the observer's old snapshot.
            if (cleaned && reschedule) scope.launch {
                val current = episodeDao.getEpisodeById(episodeId)
                if (current != null && playlistDao.isEpisodeInPlaylist(episodeId) && !current.audioUrl.isNullOrBlank()) {
                    scheduleDebouncedDownload(episodeId, current.audioUrl, owner.generation)
                }
            }
        }
    }

    private data class CleanupOwner(val job: Job?, val generation: Long)

    internal fun getPendingDownloadJob(episodeId: Long): Job? = pendingDownloadJobs[episodeId]
    internal fun getPendingDownloadCount(): Int = pendingDownloadJobs.size
}
