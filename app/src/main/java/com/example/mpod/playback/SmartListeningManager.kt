package com.example.mpod.playback

import android.content.Context
import com.example.mpod.data.local.dao.EpisodeDao
import com.example.mpod.data.local.dao.PlaylistDao
import com.example.mpod.data.local.preferences.AppSettingsDataStore
import com.example.mpod.data.network.ProxyHttpClientFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SmartListeningManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playlistDao: PlaylistDao,
    private val episodeDao: EpisodeDao,
    private val appSettingsDataStore: AppSettingsDataStore,
    private val proxyHttpClientFactory: ProxyHttpClientFactory
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pendingDownloadJobs = ConcurrentHashMap<Long, Job>()
    private var observationJob: Job? = null

    fun startObserving() {
        if (observationJob != null) return
        observationJob = scope.launch {
            playlistDao.getPlaylistItemsWithEpisodesFlow().collectLatest { items ->
                val currentPlaylistEpisodeIds = items.map { it.episode.id }.toSet()

                // Cancel pending downloads for episodes no longer in playlist
                val cancelledIds = pendingDownloadJobs.keys.filter { it !in currentPlaylistEpisodeIds }
                for (id in cancelledIds) {
                    pendingDownloadJobs.remove(id)?.cancel()
                }

                // Schedule 15s debounce download for new playlist items not yet downloaded
                for (item in items) {
                    val ep = item.episode
                    if (!ep.isDownloaded && ep.localFilePath.isNullOrBlank() && !pendingDownloadJobs.containsKey(ep.id)) {
                        scheduleDebouncedDownload(ep.id, ep.audioUrl)
                    }
                }
            }
        }
    }

    private fun scheduleDebouncedDownload(episodeId: Long, audioUrl: String) {
        val job = scope.launch {
            delay(15_000) // 15 seconds Smart Listening requirement
            val inPlaylist = playlistDao.isEpisodeInPlaylist(episodeId)
            if (!inPlaylist) {
                pendingDownloadJobs.remove(episodeId)
                return@launch
            }

            val episode = episodeDao.getEpisodeById(episodeId)
            if (episode == null || episode.isDownloaded || audioUrl.isBlank()) {
                pendingDownloadJobs.remove(episodeId)
                return@launch
            }

            downloadAudioFile(episodeId, audioUrl)
            pendingDownloadJobs.remove(episodeId)
        }
        pendingDownloadJobs[episodeId] = job
    }

    suspend fun downloadAudioFile(episodeId: Long, audioUrl: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val podcastsDir = File(context.filesDir, "podcasts").apply { if (!exists()) mkdirs() }
            val fileName = "ep_${episodeId}_${System.currentTimeMillis()}.mp3"
            val targetFile = File(podcastsDir, fileName)

            val settings = appSettingsDataStore.settingsFlow.first()
            val client = proxyHttpClientFactory.createClient(settings)
            val request = Request.Builder()
                .url(audioUrl)
                .header("User-Agent", "mpoddy/1.0.17 (Android Podcast Player)")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext false
                val body = response.body ?: return@withContext false

                body.byteStream().use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }

            if (targetFile.exists() && targetFile.length() > 0) {
                episodeDao.updateDownloadState(
                    episodeId = episodeId,
                    isDownloaded = true,
                    localFilePath = targetFile.absolutePath
                )
                true
            } else {
                targetFile.delete()
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    suspend fun cleanupEpisodeFile(episodeId: Long) = withContext(Dispatchers.IO) {
        pendingDownloadJobs.remove(episodeId)?.cancel()
        val episode = episodeDao.getEpisodeById(episodeId) ?: return@withContext
        if (!episode.localFilePath.isNullOrBlank()) {
            val file = File(episode.localFilePath)
            if (file.exists()) {
                file.delete()
            }
            episodeDao.updateDownloadState(episodeId, isDownloaded = false, localFilePath = null)
        }
    }
}
