package com.example.mpod.data.repository

import com.example.mpod.BuildConfig
import com.example.mpod.data.local.dao.EpisodeDao
import com.example.mpod.data.local.dao.PodcastDao
import com.example.mpod.data.local.entity.EpisodeEntity
import com.example.mpod.data.local.entity.PodcastEntity
import com.example.mpod.data.local.model.EpisodeWithPodcast
import com.example.mpod.data.local.preferences.AppSettingsDataStore
import com.example.mpod.data.network.ProxyHttpClientFactory
import com.example.mpod.data.rss.OpmlParser
import com.example.mpod.data.rss.ParsedPodcastFeed
import com.example.mpod.data.rss.RssFeedParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PodcastRepository @Inject constructor(
    private val podcastDao: PodcastDao,
    private val episodeDao: EpisodeDao,
    private val appSettingsDataStore: AppSettingsDataStore,
    private val proxyHttpClientFactory: ProxyHttpClientFactory
) {
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
                return@withContext Result.success(existing)
            }

            val parsedFeed = fetchAndParseFeed(normalizedUrl)
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

            val parsedFeed = fetchAndParseFeed(podcast.feedUrl)
            val updatedPodcast = podcast.copy(
                title = if (parsedFeed.title.isNotBlank()) parsedFeed.title else podcast.title,
                description = if (parsedFeed.description.isNotBlank()) parsedFeed.description else podcast.description,
                author = if (parsedFeed.author.isNotBlank()) parsedFeed.author else podcast.author,
                artworkUrl = if (parsedFeed.artworkUrl.isNotBlank()) parsedFeed.artworkUrl else podcast.artworkUrl,
                link = if (parsedFeed.link.isNotBlank()) parsedFeed.link else podcast.link,
                lastBuildDate = parsedFeed.lastBuildDate,
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
        val failures = mutableListOf<String>()
        for (pod in podcasts) {
            refreshPodcast(pod.id).onFailure { e ->
                failures.add("${pod.title}: ${e.message ?: "refresh failed"}")
            }
        }
        val formatter = SimpleDateFormat("dd.MM HH:mm", Locale.getDefault())
        appSettingsDataStore.setLastRefreshTime("Last refresh today at ${formatter.format(Date())}")
        if (failures.isNotEmpty()) {
            Result.failure(
                Exception("Failed to refresh ${failures.size} podcast(s):\n" + failures.joinToString("\n"))
            )
        } else {
            Result.success(Unit)
        }
    }

    suspend fun unsubscribe(podcastId: Long) = withContext(Dispatchers.IO) {
        val episodes = episodeDao.getEpisodesByPodcastId(podcastId)
        for (ep in episodes) {
            if (!ep.localFilePath.isNullOrBlank()) {
                val f = File(ep.localFilePath)
                if (f.exists()) f.delete()
            }
        }
        val activeEpisodeId = appSettingsDataStore.getActiveEpisodeId()
        podcastDao.deleteById(podcastId)
        if (activeEpisodeId != null && episodeDao.getEpisodeById(activeEpisodeId) == null) {
            appSettingsDataStore.setActiveEpisodeId(null)
        }
    }

    suspend fun setEpisodeListened(episodeId: Long, isListened: Boolean) = withContext(Dispatchers.IO) {
        episodeDao.setListened(episodeId, isListened)
    }

    suspend fun markAllEpisodesListened(podcastId: Long, isListened: Boolean) = withContext(Dispatchers.IO) {
        episodeDao.setAllListenedForPodcast(podcastId, isListened)
    }

    suspend fun updatePlaybackPosition(episodeId: Long, positionMs: Long) = withContext(Dispatchers.IO) {
        episodeDao.updatePlaybackPosition(episodeId, positionMs)
    }

    suspend fun importOpml(inputStream: InputStream): Result<OpmlImportSummary> = withContext(Dispatchers.IO) {
        try {
            val items = OpmlParser.parse(inputStream)
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

    private suspend fun fetchAndParseFeed(url: String): ParsedPodcastFeed {
        val settings = appSettingsDataStore.settingsFlow.first()
        val client = proxyHttpClientFactory.createClient(settings)
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "mpoddy/${BuildConfig.VERSION_NAME} (Android Podcast Player)")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP ${response.code} fetching feed: ${response.message}")
            }
            val body = response.body?.byteStream() ?: throw IllegalStateException("Empty response body")
            return RssFeedParser.parse(body)
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
}
