package com.example.mpod.data.repository

import com.example.mpod.BuildConfig
import com.example.mpod.data.local.dao.EpisodeDao
import com.example.mpod.data.local.dao.PodcastDao
import com.example.mpod.data.local.entity.EpisodeEntity
import com.example.mpod.data.local.entity.PodcastEntity
import com.example.mpod.data.local.model.EpisodeWithPodcast
import com.example.mpod.data.local.preferences.AppSettingsStore
import com.example.mpod.data.network.ProxyHttpClientFactory
import com.example.mpod.data.rss.OpmlParser
import com.example.mpod.data.rss.ParsedPodcastFeed
import com.example.mpod.data.rss.RssFeedParser
import com.example.mpod.playback.CleanupResult
import com.example.mpod.playback.PlaybackQueueInvalidator
import com.example.mpod.playback.SmartListeningManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class EpisodeCleanupFailure(
    val episodeId: Long,
    val path: String?,
    val error: String? = null
)

data class MarkAllListenedResult(
    val targetEpisodeIds: List<Long>,
    val listenedEpisodeIds: List<Long>,
    val removedPlaylistEpisodeIds: List<Long>,
    val cleanupFailures: List<EpisodeCleanupFailure>
)

@Singleton
class PodcastRepository @Inject constructor(
    private val podcastDao: PodcastDao,
    private val episodeDao: EpisodeDao,
    private val appSettingsDataStore: AppSettingsStore,
    private val proxyHttpClientFactory: ProxyHttpClientFactory,
    private val smartListeningManager: SmartListeningManager,
    private val queueInvalidator: PlaybackQueueInvalidator
) {
    // Serialize mark/retry through filesystem completion; unsubscribe cannot destroy its Room
    // links in the meantime. Additive refresh needs no network lock: Room linearizes its inserts.
    private val markActionMutex = Mutex()

    fun getAllPodcastsFlow(): Flow<List<PodcastEntity>> = podcastDao.getAllPodcastsFlow()

    fun getEpisodesByPodcastIdFlow(podcastId: Long): Flow<List<EpisodeEntity>> =
        episodeDao.getEpisodesByPodcastIdFlow(podcastId)

    fun getAllEpisodesWithPodcastFlow(): Flow<List<EpisodeWithPodcast>> =
        episodeDao.getAllEpisodesWithPodcastFlow()

    suspend fun getPodcastById(id: Long): PodcastEntity? = podcastDao.getPodcastById(id)

    suspend fun getEpisodeById(id: Long): EpisodeEntity? = episodeDao.getEpisodeById(id)

    suspend fun getEpisodeWithPodcastById(id: Long): EpisodeWithPodcast? =
        episodeDao.getEpisodeWithPodcastById(id)

    suspend fun addPodcastByFeedUrl(feedUrl: String): Result<PodcastEntity> = withContext(Dispatchers.IO) {
        try {
            val normalizedUrl = normalizeFeedUrl(feedUrl)
            val existing = podcastDao.getPodcastByFeedUrl(normalizedUrl)
            if (existing != null) {
                return@withContext Result.failure(
                    IllegalArgumentException("This podcast is already in your library.")
                )
            }

            val parsedFeed = fetchAndParseFeed(normalizedUrl)
                ?: throw IllegalStateException("Unexpected 304 on new podcast subscription")
            val podcastEntity = PodcastEntity(
                feedUrl = normalizedUrl,
                title = if (parsedFeed.title.isNotBlank()) parsedFeed.title else normalizedUrl,
                description = parsedFeed.description,
                author = parsedFeed.author,
                artworkUrl = parsedFeed.artworkUrl,
                link = parsedFeed.link,
                lastBuildDate = parsedFeed.lastBuildDate,
                lastRefreshedAt = System.currentTimeMillis()
            )
            val podcastId = podcastDao.insert(podcastEntity)
            val savedPodcast = podcastEntity.copy(id = podcastId)

            val episodeEntities = parsedFeed.episodes.map { ep ->
                EpisodeEntity(
                    podcastId = podcastId,
                    guid = ep.guid,
                    title = ep.title,
                    description = ep.description,
                    audioUrl = ep.audioUrl,
                    durationSeconds = ep.durationSeconds,
                    publishedAt = ep.publishedAt,
                    publishedAtString = ep.publishedAtString,
                    isListened = false,
                    playbackPositionMs = 0,
                    isDownloaded = false,
                    localFilePath = null
                )
            }
            episodeDao.insertEpisodes(episodeEntities)
            Result.success(savedPodcast)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun refreshPodcast(podcastId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val podcast = podcastDao.getPodcastById(podcastId)
                ?: return@withContext Result.failure(IllegalArgumentException("Podcast not found: $podcastId"))

            val parsedFeed = fetchAndParseFeed(podcast.feedUrl, podcast.lastBuildDate)
            if (parsedFeed == null) {
                podcastDao.update(podcast.copy(lastRefreshedAt = System.currentTimeMillis()))
                return@withContext Result.success(Unit)
            }
            val updatedPodcast = podcast.copy(
                title = if (parsedFeed.title.isNotBlank()) parsedFeed.title else podcast.title,
                description = if (parsedFeed.description.isNotBlank()) parsedFeed.description else podcast.description,
                author = if (parsedFeed.author.isNotBlank()) parsedFeed.author else podcast.author,
                artworkUrl = if (parsedFeed.artworkUrl.isNotBlank()) parsedFeed.artworkUrl else podcast.artworkUrl,
                link = if (parsedFeed.link.isNotBlank()) parsedFeed.link else podcast.link,
                lastBuildDate = if (parsedFeed.lastBuildDate.isNotBlank()) parsedFeed.lastBuildDate else podcast.lastBuildDate,
                lastRefreshedAt = System.currentTimeMillis()
            )
            podcastDao.update(updatedPodcast)

            val existingEpisodes = episodeDao.getEpisodesByPodcastId(podcastId)
            val existingGuids = existingEpisodes.map { it.guid }.toSet()

            val newEpisodes = parsedFeed.episodes
                .filter { it.guid !in existingGuids }
                .map { ep ->
                    EpisodeEntity(
                        podcastId = podcastId,
                        guid = ep.guid,
                        title = ep.title,
                        description = ep.description,
                        audioUrl = ep.audioUrl,
                        durationSeconds = ep.durationSeconds,
                        publishedAt = ep.publishedAt,
                        publishedAtString = ep.publishedAtString,
                        isListened = false,
                        playbackPositionMs = 0,
                        isDownloaded = false,
                        localFilePath = null
                    )
                }

            if (newEpisodes.isNotEmpty()) {
                episodeDao.insertEpisodes(newEpisodes)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun refreshAllPodcasts(): Result<Unit> = withContext(Dispatchers.IO) {
        val podcasts = podcastDao.getAllPodcasts()
        val semaphore = Semaphore(4)
        val failures = Collections.synchronizedList(mutableListOf<String>())
        coroutineScope {
            podcasts.map { pod ->
                async {
                    semaphore.withPermit {
                        refreshPodcast(pod.id).onFailure { e ->
                            failures.add("${pod.title}: ${e.message ?: "refresh failed"}")
                        }
                    }
                }
            }.awaitAll()
        }
        if (failures.isNotEmpty()) {
            Result.failure(
                Exception("Failed to refresh ${failures.size} podcast(s):\n" + failures.joinToString("\n"))
            )
        } else {
            val formatter = SimpleDateFormat("dd.MM HH:mm", Locale.getDefault())
            appSettingsDataStore.setLastRefreshTime("Last refresh today at ${formatter.format(Date())}")
            Result.success(Unit)
        }
    }

    suspend fun unsubscribe(podcastId: Long) = withContext(Dispatchers.IO) {
        markActionMutex.withLock {
            smartListeningManager.withPodcastDownloadsPaused(podcastId) {
                val episodes = episodeDao.getEpisodesByPodcastId(podcastId)
                for (episode in episodes) {
                    when (smartListeningManager.cleanupEpisodeFile(episode.id)) {
                        CleanupResult.Success -> Unit
                        is CleanupResult.DeleteFailed -> throw IllegalStateException(
                            "Could not delete downloaded audio. Please try unsubscribing again."
                        )
                    }
                }
                val activeEpisodeId = appSettingsDataStore.getActiveEpisodeId()
                ensureActive()
                withContext(NonCancellable) {
                    podcastDao.deleteById(podcastId) // Room FK cascades episodes and playlist atomically.
                    try {
                        if (activeEpisodeId != null && episodeDao.getEpisodeById(activeEpisodeId) == null) {
                            appSettingsDataStore.setActiveEpisodeId(null)
                        }
                    } finally {
                        queueInvalidator.invalidate()
                    }
                }
            }
        }
    }

    suspend fun setEpisodeListened(episodeId: Long, isListened: Boolean) = withContext(Dispatchers.IO) {
        episodeDao.setListened(episodeId, isListened)
    }

    suspend fun markAllEpisodesListened(podcastId: Long): MarkAllListenedResult = withContext(Dispatchers.IO) {
        markActionMutex.withLock {
            val snapshot = episodeDao.markAllListenedAndRemoveFromPlaylist(podcastId)
            val failures = mutableListOf<EpisodeCleanupFailure>()
            try {
                for (episode in snapshot.episodes) {
                    try {
                        when (val result = smartListeningManager.cleanupEpisodeFile(episode.id)) {
                            CleanupResult.Success -> Unit
                            is CleanupResult.DeleteFailed -> failures.add(EpisodeCleanupFailure(episode.id, result.path))
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        // Keep failed cleanup observable. Room still owns the path for a retry;
                        // if deletion succeeded but reset failed, missing-file cleanup can retry it.
                        failures.add(EpisodeCleanupFailure(episode.id, episode.localFilePath, error.message))
                    }
                }
            } finally {
                if (snapshot.removedPlaylistEpisodeIds.isNotEmpty()) queueInvalidator.invalidate()
            }
            MarkAllListenedResult(
                targetEpisodeIds = snapshot.episodes.map { it.id },
                listenedEpisodeIds = snapshot.listenedEpisodeIds,
                removedPlaylistEpisodeIds = snapshot.removedPlaylistEpisodeIds,
                cleanupFailures = failures
            )
        }
    }

    suspend fun updatePlaybackPosition(episodeId: Long, positionMs: Long) = withContext(Dispatchers.IO) {
        episodeDao.updatePlaybackPosition(episodeId, positionMs)
    }

    suspend fun importOpml(inputStream: InputStream): Result<OpmlImportSummary> = withContext(Dispatchers.IO) {
        try {
            val bytes = readLimited(inputStream, MAX_OPML_SIZE_BYTES + 1)
            if (bytes.size > MAX_OPML_SIZE_BYTES) {
                return@withContext Result.failure(
                    IllegalStateException("OPML file too large (max 5 MB).")
                )
            }
            val items = OpmlParser.parse(bytes.inputStream())
            var imported = 0
            val errors = mutableListOf<String>()
            for (item in items) {
                addPodcastByFeedUrl(item.xmlUrl)
                    .onSuccess { imported++ }
                    .onFailure { e ->
                        errors.add("${item.title}: ${e.message ?: "failed to import"}")
                    }
            }
            Result.success(
                OpmlImportSummary(
                    imported = imported,
                    skipped = items.size - imported,
                    errors = errors
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun exportOpml(): String = withContext(Dispatchers.IO) {
        val podcasts = podcastDao.getAllPodcasts()
        OpmlParser.generateOpml(podcasts)
    }

    private suspend fun fetchAndParseFeed(
        url: String,
        lastBuildDate: String? = null
    ): ParsedPodcastFeed? {
        val client = proxyHttpClientFactory.createClient()
        val requestBuilder = Request.Builder()
            .url(url)
            .header(
                "User-Agent",
                "mpoddy/${BuildConfig.VERSION_NAME} (Android; Linux) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
            )
            .header("Accept", "application/rss+xml, application/xml, application/atom+xml, text/xml, */*")

        if (!lastBuildDate.isNullOrBlank()) {
            if (lastBuildDate.startsWith("\"") || lastBuildDate.startsWith("W/\"")) {
                requestBuilder.header("If-None-Match", lastBuildDate)
            } else {
                requestBuilder.header("If-Modified-Since", lastBuildDate)
            }
        }

        val request = requestBuilder.build()

        client.newCall(request).execute().use { response ->
            if (response.code == 304) {
                return null
            }
            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP ${response.code} fetching feed: ${response.message}")
            }
            val httpCacheTag = response.header("ETag") ?: response.header("Last-Modified")
            val body = response.body?.byteStream() ?: throw IllegalStateException("Empty response body")
            val parsed = RssFeedParser.parse(body.buffered())
            return if (parsed.lastBuildDate.isBlank() && !httpCacheTag.isNullOrBlank()) {
                parsed.copy(lastBuildDate = httpCacheTag)
            } else {
                parsed
            }
        }
    }

    data class OpmlImportSummary(
        val imported: Int,
        val skipped: Int,
        val errors: List<String> = emptyList()
    )

    private fun normalizeFeedUrl(url: String): String {
        val trimmed = url.trim()
        return try {
            val u = java.net.URI(trimmed)
            val scheme = u.scheme?.lowercase() ?: return trimmed
            val host = u.host?.lowercase() ?: return trimmed
            val defaultPort = if (scheme == "https") 443 else 80
            val port = if (u.port != -1 && u.port == defaultPort) -1 else u.port
            var path = u.path?.trimEnd('/') ?: ""
            if (path.isEmpty()) path = "/"
            val query = if (!u.query.isNullOrBlank()) "?${u.query}" else ""
            "$scheme://$host${if (port != -1) ":$port" else ""}$path$query"
        } catch (_: Exception) {
            trimmed
        }
    }

    companion object {
        const val MAX_OPML_SIZE_BYTES = 5_000_000

        internal fun readLimited(inputStream: InputStream, maxBytes: Int): ByteArray {
            val buffer = ByteArray(maxBytes)
            var totalRead = 0
            while (totalRead < maxBytes) {
                val remaining = maxBytes - totalRead
                val bytesRead = inputStream.read(buffer, totalRead, remaining)
                if (bytesRead == -1) break
                totalRead += bytesRead
            }
            return if (totalRead == buffer.size) buffer else buffer.copyOf(totalRead)
        }
    }
}
