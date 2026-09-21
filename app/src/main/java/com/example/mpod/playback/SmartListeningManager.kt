package com.example.mpod.playback

import android.content.Context
import com.example.mpod.BuildConfig
import com.example.mpod.data.local.dao.EpisodeDao
import com.example.mpod.data.local.dao.PlaylistDao
import com.example.mpod.data.network.ProxyHttpClientFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
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
    private var observationJob: Job? = null

    internal var fileOps: FileOperations = DefaultFileOperations
    internal var debounceMs: Long = 15_000L
    internal var preDaoHook: (suspend () -> Unit)? = null
    internal var postDaoHook: (suspend () -> Unit)? = null

    fun startObserving() {
        if (observationJob != null) return
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

    fun stopObserving() {
        observationJob?.cancel()
        observationJob = null
        for ((_, job) in pendingDownloadJobs) {
            job.cancel()
        }
        pendingDownloadJobs.clear()
    }

    private fun scheduleDebouncedDownload(episodeId: Long, audioUrl: String) {
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
        if (previous == null) {
            job.start()
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

                val result: DownloadFinalizer.Result = suspendCancellableCoroutine { continuation ->
                    continuation.invokeOnCancellation { call.cancel() }

                    call.enqueue(object : Callback {
                        override fun onFailure(call: Call, e: IOException) {
                            if (continuation.isCancelled) return
                            continuation.resumeWith(kotlin.Result.failure(e))
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
                            }
                        }
                    })
                }

                if (!result.success) {
                    android.util.Log.e("SmartListening", "Download failed for episode $episodeId (cleanupFailed=${result.cleanupFailed}): ${result.errorMessage}")
                    return@withContext DownloadOutcome.Failure(cleanupFailed = result.cleanupFailed, message = result.errorMessage)
                }

                ensureActive()
                preDaoHook?.invoke()

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
        pendingDownloadJobs[episodeId]?.cancelAndJoin()

        val episode = episodeDao.getEpisodeById(episodeId)
            ?: return@withContext CleanupResult.Success

        if (!episode.localFilePath.isNullOrBlank()) {
            val file = File(episode.localFilePath)
            val deleted = fileOps.delete(file)
            if (!deleted) {
                android.util.Log.e("SmartListening", "Failed to delete audio file: ${file.absolutePath} for episode $episodeId. Skipping Room state reset to prevent desync.")
                return@withContext CleanupResult.DeleteFailed(episode.localFilePath)
            }
            // Only clear the state that owned the deleted file. A newer download may have
            // committed a different path while this cleanup was deleting the old one.
            episodeDao.clearDownloadStateIfMatches(
                episodeId = episodeId,
                expectedIsDownloaded = episode.isDownloaded,
                expectedLocalFilePath = episode.localFilePath
            )
        } else if (episode.isDownloaded) {
            // No file is linked, so there is nothing to delete before clearing stale metadata.
            episodeDao.clearDownloadStateIfMatches(
                episodeId = episodeId,
                expectedIsDownloaded = true,
                expectedLocalFilePath = null
            )
        }
        CleanupResult.Success
    }

    internal fun getPendingDownloadJob(episodeId: Long): Job? = pendingDownloadJobs[episodeId]
    internal fun getPendingDownloadCount(): Int = pendingDownloadJobs.size
}
