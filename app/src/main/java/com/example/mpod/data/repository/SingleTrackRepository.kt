package com.example.mpod.data.repository

import com.example.mpod.data.local.dao.EpisodeDao
import com.example.mpod.data.local.dao.PlaylistDao
import com.example.mpod.data.local.dao.PodcastDao
import com.example.mpod.data.local.entity.EpisodeEntity
import com.example.mpod.data.local.entity.PlaylistItemEntity
import com.example.mpod.data.local.entity.PodcastEntity
import com.example.mpod.data.local.preferences.AppSettingsStore
import com.example.mpod.playback.PlaybackQueueInvalidator
import com.example.mpod.data.network.resolver.AudioLinkResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI
import java.net.URLEncoder
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class SingleTrackRepository @Inject constructor(
    private val podcastDao: PodcastDao,
    private val episodeDao: EpisodeDao,
    private val playlistDao: PlaylistDao,
    private val appSettingsDataStore: AppSettingsStore,
    private val queueInvalidator: PlaybackQueueInvalidator,
    private val audioLinkResolver: AudioLinkResolver
) {
    companion object {
        const val SINGLE_TRACKS_FEED_URL = "mpod://single_tracks"
        const val SINGLE_TRACKS_PODCAST_TITLE = "Audio"
    }

    suspend fun getOrCreateSingleTracksPodcastId(): Long = withContext(Dispatchers.IO) {
        val existing = podcastDao.getPodcastByFeedUrl(SINGLE_TRACKS_FEED_URL)
        if (existing != null) return@withContext existing.id

        podcastDao.insert(
            PodcastEntity(
                feedUrl = SINGLE_TRACKS_FEED_URL,
                title = SINGLE_TRACKS_PODCAST_TITLE,
                description = "Single and shared audio tracks",
                author = "mpod"
            )
        )
    }

    open suspend fun playSingleTrack(urlOrUri: String, title: String? = null): Long = withContext(Dispatchers.IO) {
        val cleanUrl = urlOrUri.trim()

        val resolved = audioLinkResolver.resolve(cleanUrl).getOrElse { error ->
            throw IllegalArgumentException(error.message ?: "Could not extract audio from link: $cleanUrl", error)
        }

        val directUrl = resolved.directAudioUrl
        if (directUrl.contains("podcasts.apple.com") || directUrl.contains("itunes.apple.com")) {
            throw IllegalArgumentException("Resolved audio URL cannot be an Apple Podcasts web page: $directUrl")
        }

        val finalTitle = title?.takeIf { it.isNotBlank() } ?: resolved.title
        val showTitle = resolved.podcastTitle ?: resolved.author ?: SINGLE_TRACKS_PODCAST_TITLE
        val artworkUrl = resolved.artworkUrl ?: ""
        val author = resolved.author ?: "mpod"
        val duration = resolved.durationSeconds?.toLong() ?: 0L
        val description = resolved.description ?: ""

        val feedUrl = if (showTitle != SINGLE_TRACKS_PODCAST_TITLE) {
            val slug = showTitle.lowercase(Locale.ROOT)
                .replace(Regex("""[^\p{L}\p{N}_-]+"""), "_")
                .trim('_')
            val identifier = if (slug.isNotBlank()) slug else {
                runCatching { URLEncoder.encode(showTitle, "UTF-8") }.getOrDefault("track")
            }
            "mpod://single_tracks/$identifier"
        } else {
            SINGLE_TRACKS_FEED_URL
        }

        val existingPodcast = podcastDao.getPodcastByFeedUrl(feedUrl)
        val podcastId = if (existingPodcast != null) {
            var updated = existingPodcast
            var needsUpdate = false
            if (artworkUrl.isNotBlank() && existingPodcast.artworkUrl.isBlank()) {
                updated = updated.copy(artworkUrl = artworkUrl)
                needsUpdate = true
            }
            if (existingPodcast.title != showTitle && showTitle != SINGLE_TRACKS_PODCAST_TITLE) {
                updated = updated.copy(title = showTitle)
                needsUpdate = true
            }
            if (needsUpdate) {
                podcastDao.update(updated)
            }
            existingPodcast.id
        } else {
            podcastDao.insert(
                PodcastEntity(
                    feedUrl = feedUrl,
                    title = showTitle,
                    description = "Single and shared audio tracks from $showTitle",
                    author = author,
                    artworkUrl = artworkUrl
                )
            )
        }

        val existingEpisode = episodeDao.getEpisodeByPodcastIdAndGuid(podcastId, directUrl)
            ?: episodeDao.getEpisodeByPodcastIdAndGuid(podcastId, cleanUrl)

        val episodeId = if (existingEpisode != null) {
            if (existingEpisode.audioUrl != directUrl || (existingEpisode.title.isBlank() && finalTitle.isNotBlank())) {
                episodeDao.update(
                    existingEpisode.copy(
                        title = finalTitle,
                        audioUrl = directUrl,
                        durationSeconds = if (duration > 0) duration else existingEpisode.durationSeconds,
                        description = if (description.isNotBlank()) description else existingEpisode.description
                    )
                )
            }
            existingEpisode.id
        } else {
            episodeDao.insertEpisode(
                EpisodeEntity(
                    podcastId = podcastId,
                    guid = directUrl,
                    title = finalTitle,
                    description = description,
                    audioUrl = directUrl,
                    durationSeconds = duration,
                    publishedAt = System.currentTimeMillis()
                )
            )
        }

        // Prepend to playlist at position 0 so it plays immediately
        val currentItems = playlistDao.getPlaylistItemsWithEpisodes()
        val otherItems = currentItems.filter { it.episode.id != episodeId }
        val newPlaylistItems = listOf(
            PlaylistItemEntity(episodeId = episodeId, position = 0)
        ) + otherItems.mapIndexed { idx, item ->
            PlaylistItemEntity(id = item.playlistItemId, episodeId = item.episode.id, position = idx + 1)
        }
        playlistDao.reorderPlaylist(newPlaylistItems)

        // Set as active episode and notify queue invalidator
        appSettingsDataStore.setActiveEpisodeId(episodeId)
        queueInvalidator.invalidate()

        episodeId
    }

    private fun deriveTitle(url: String): String {
        return try {
            val uri = URI.create(url)
            val path = uri.path ?: ""
            val filename = path.substringAfterLast('/')
            val nameWithoutExt = filename.substringBeforeLast('.')
            if (nameWithoutExt.isNotBlank()) {
                nameWithoutExt.replace('-', ' ').replace('_', ' ').replace("%20", " ")
            } else {
                uri.host ?: "Audio Track"
            }
        } catch (_: Exception) {
            "Audio Track"
        }
    }
}
