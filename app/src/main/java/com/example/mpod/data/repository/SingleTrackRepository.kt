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
        val finalTitle = title?.takeIf { it.isNotBlank() } ?: resolved.title
        val showTitle = resolved.podcastTitle ?: resolved.author ?: SINGLE_TRACKS_PODCAST_TITLE
        val artworkUrl = resolved.artworkUrl ?: ""
        val author = resolved.author ?: "mpod"
        val duration = resolved.durationSeconds?.toLong() ?: 0L
        val description = resolved.description ?: ""

        val feedUrl = if (showTitle != SINGLE_TRACKS_PODCAST_TITLE) {
            "mpod://single_tracks/${showTitle.lowercase().replace(Regex("[^a-z0-9_-]+"), "_")}"
        } else {
            SINGLE_TRACKS_FEED_URL
        }

        val existingPodcast = podcastDao.getPodcastByFeedUrl(feedUrl)
        val podcastId = if (existingPodcast != null) {
            if (artworkUrl.isNotBlank() && existingPodcast.artworkUrl.isBlank()) {
                podcastDao.update(existingPodcast.copy(artworkUrl = artworkUrl))
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
