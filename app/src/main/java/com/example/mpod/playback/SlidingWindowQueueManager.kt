package com.example.mpod.playback

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.example.mpod.data.local.entity.AudiobookEntity
import com.example.mpod.data.local.entity.AudiobookTrackEntity
import com.example.mpod.data.local.entity.EpisodeEntity
import com.example.mpod.data.local.model.PlayableItem
import java.io.File

sealed class MediaQueueId {
    data class Episode(val episodeId: Long) : MediaQueueId()
    data class Audiobook(val audiobookId: Long, val trackId: Long) : MediaQueueId()

    fun toMediaId(): String = when (this) {
        is Episode -> "episode:$episodeId"
        is Audiobook -> "audiobook:$audiobookId:$trackId"
    }

    companion object {
        fun fromMediaId(mediaId: String?): MediaQueueId? {
            if (mediaId == null) return null
            if (mediaId.startsWith("episode:")) {
                return mediaId.removePrefix("episode:").toLongOrNull()?.let { Episode(it) }
            }
            if (mediaId.startsWith("audiobook:")) {
                val parts = mediaId.removePrefix("audiobook:").split(":")
                if (parts.size == 2) {
                    val abkId = parts[0].toLongOrNull()
                    val trkId = parts[1].toLongOrNull()
                    if (abkId != null && trkId != null) {
                        return Audiobook(abkId, trkId)
                    }
                }
            }
            // Backward compatibility for raw numeric string (episodes)
            return mediaId.toLongOrNull()?.let { Episode(it) }
        }
    }
}

sealed class PlaybackTrack {
    abstract val mediaUri: Uri
    abstract val title: String
    abstract val subtitle: String
    abstract val artworkUri: Uri?
    abstract val durationSeconds: Long
    abstract val initialPositionMs: Long
    abstract val customCacheKey: String
    abstract val isAudiobook: Boolean
    abstract fun toMediaId(): String

    data class EpisodeTrack(
        val episode: EpisodeEntity,
        val podcastTitle: String,
        val podcastArtworkUrl: String
    ) : PlaybackTrack() {
        override val mediaUri: Uri get() = resolveEpisodeUri(episode)
        override val title: String = episode.title
        override val subtitle: String = podcastTitle
        override val artworkUri: Uri? get() = podcastArtworkUrl.takeIf { it.isNotBlank() }?.let(Uri::parse)
        override val durationSeconds: Long = episode.durationSeconds
        override val initialPositionMs: Long = episode.playbackPositionMs
        override val customCacheKey: String = "episode_${episode.id}"
        override val isAudiobook: Boolean = false
        override fun toMediaId(): String = "episode:${episode.id}"

        companion object {
            fun resolveEpisodeUri(ep: EpisodeEntity): Uri {
                return if (ep.isDownloaded && !ep.localFilePath.isNullOrBlank() && File(ep.localFilePath).exists()) {
                    Uri.fromFile(File(ep.localFilePath))
                } else {
                    Uri.parse(ep.audioUrl)
                }
            }
        }
    }

    data class AudiobookChapterTrack(
        val audiobook: AudiobookEntity,
        val track: AudiobookTrackEntity,
        val chapterIndex: Int,
        val totalChapters: Int
    ) : PlaybackTrack() {
        override val mediaUri: Uri get() = Uri.parse(track.fileUri)
        override val title: String = track.title
        override val subtitle: String = audiobook.title
        override val artworkUri: Uri? get() = audiobook.coverUri?.let(Uri::parse)
        override val durationSeconds: Long = track.durationSeconds
        override val initialPositionMs: Long = track.playbackPositionMs
        override val customCacheKey: String = "audiobook_${audiobook.id}_${track.id}"
        override val isAudiobook: Boolean = true
        override fun toMediaId(): String = "audiobook:${audiobook.id}:${track.id}"
    }

    fun toMediaItem(): MediaItem {
        val extras = Bundle().apply {
            putLong(EXTRA_DURATION_SECONDS, durationSeconds)
            putBoolean(EXTRA_IS_AUDIOBOOK, isAudiobook)
            when (this@PlaybackTrack) {
                is EpisodeTrack -> {
                    putLong(EXTRA_EPISODE_ID, episode.id)
                }
                is AudiobookChapterTrack -> {
                    putLong(EXTRA_AUDIOBOOK_ID, audiobook.id)
                    putLong(EXTRA_TRACK_ID, track.id)
                    putInt(EXTRA_CHAPTER_INDEX, chapterIndex)
                    putInt(EXTRA_TOTAL_CHAPTERS, totalChapters)
                }
            }
        }

        return MediaItem.Builder()
            .setMediaId(toMediaId())
            .setCustomCacheKey(customCacheKey)
            .setUri(mediaUri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(subtitle)
                    .setArtworkUri(artworkUri)
                    .setExtras(extras)
                    .build()
            )
            .build()
    }

    companion object {
        const val EXTRA_DURATION_SECONDS = "com.prod.mpod.duration_seconds"
        const val EXTRA_IS_AUDIOBOOK = "com.prod.mpod.is_audiobook"
        const val EXTRA_EPISODE_ID = "com.prod.mpod.episode_id"
        const val EXTRA_AUDIOBOOK_ID = "com.prod.mpod.audiobook_id"
        const val EXTRA_TRACK_ID = "com.prod.mpod.track_id"
        const val EXTRA_CHAPTER_INDEX = "com.prod.mpod.chapter_index"
        const val EXTRA_TOTAL_CHAPTERS = "com.prod.mpod.total_chapters"
    }
}

data class SlidingWindow(
    val current: PlaybackTrack?,
    val next: PlaybackTrack?
)

object SlidingWindowResolver {

    fun resolveWindow(
        queue: List<PlayableItem>,
        targetQueueItemId: String? = null,
        preferredTrackId: Long? = null,
        currentMediaQueueId: MediaQueueId? = null
    ): SlidingWindow {
        if (queue.isEmpty()) return SlidingWindow(null, null)

        // 1. Identify starting PlayableItem index
        val targetItemIndex = when {
            targetQueueItemId != null -> queue.indexOfFirst { it.queueItemId == targetQueueItemId }
            currentMediaQueueId != null -> queue.indexOfFirst { item ->
                when (currentMediaQueueId) {
                    is MediaQueueId.Episode -> item is PlayableItem.EpisodeItem && item.episode.id == currentMediaQueueId.episodeId
                    is MediaQueueId.Audiobook -> item is PlayableItem.AudiobookItem && item.audiobook.id == currentMediaQueueId.audiobookId
                }
            }
            else -> 0
        }.let { if (it >= 0) it else 0 }

        // 2. Resolve current PlaybackTrack, advancing to next item if target item has no playable tracks
        var activeItemIndex = targetItemIndex
        var currentTrack: PlaybackTrack? = null

        for (i in targetItemIndex until queue.size) {
            val candidate = resolveTrackForItem(
                item = queue[i],
                preferredTrackId = if (i == targetItemIndex) preferredTrackId else null,
                currentMediaQueueId = if (i == targetItemIndex) currentMediaQueueId else null
            )
            if (candidate != null) {
                activeItemIndex = i
                currentTrack = candidate
                break
            }
        }

        // 3. Resolve next PlaybackTrack
        val nextTrack: PlaybackTrack? = when (currentTrack) {
            null -> null
            is PlaybackTrack.EpisodeTrack -> {
                resolveFirstTrackOfItem(queue, activeItemIndex + 1)
            }
            is PlaybackTrack.AudiobookChapterTrack -> {
                val currentAbkItem = queue[activeItemIndex] as PlayableItem.AudiobookItem
                val selectedTracks = currentAbkItem.selectedTracks
                val currentTrackIdx = selectedTracks.indexOfFirst { it.id == currentTrack.track.id }

                val nextChapter = if (currentTrackIdx >= 0 && currentTrackIdx + 1 < selectedTracks.size) {
                    selectedTracks.subList(currentTrackIdx + 1, selectedTracks.size)
                        .firstOrNull { it.isAvailable }
                } else null

                if (nextChapter != null) {
                    val nextChapterIdx = selectedTracks.indexOfFirst { it.id == nextChapter.id } + 1
                    PlaybackTrack.AudiobookChapterTrack(
                        audiobook = currentAbkItem.audiobook,
                        track = nextChapter,
                        chapterIndex = nextChapterIdx,
                        totalChapters = selectedTracks.size
                    )
                } else {
                    resolveFirstTrackOfItem(queue, activeItemIndex + 1)
                }
            }
        }

        return SlidingWindow(current = currentTrack, next = nextTrack)
    }

    private fun resolveTrackForItem(
        item: PlayableItem,
        preferredTrackId: Long?,
        currentMediaQueueId: MediaQueueId?
    ): PlaybackTrack? {
        return when (item) {
            is PlayableItem.EpisodeItem -> {
                PlaybackTrack.EpisodeTrack(
                    episode = item.episode,
                    podcastTitle = item.podcastTitle,
                    podcastArtworkUrl = item.podcastArtworkUrl
                )
            }
            is PlayableItem.AudiobookItem -> {
                val availableTracks = item.selectedTracks.filter { it.isAvailable }
                if (availableTracks.isEmpty()) null
                else {
                    val track = when {
                        preferredTrackId != null -> availableTracks.firstOrNull { it.id == preferredTrackId }
                        currentMediaQueueId is MediaQueueId.Audiobook && currentMediaQueueId.audiobookId == item.audiobook.id ->
                            availableTracks.firstOrNull { it.id == currentMediaQueueId.trackId }
                        else -> null
                    } ?: item.currentTrack?.takeIf { it.isAvailable }
                      ?: availableTracks.firstOrNull { !it.isListened }
                      ?: availableTracks.first()

                    val chapterIdx = item.selectedTracks.indexOfFirst { it.id == track.id }
                        .let { if (it >= 0) it + 1 else 1 }

                    PlaybackTrack.AudiobookChapterTrack(
                        audiobook = item.audiobook,
                        track = track,
                        chapterIndex = chapterIdx,
                        totalChapters = item.selectedTracks.size
                    )
                }
            }
        }
    }

    private fun resolveFirstTrackOfItem(queue: List<PlayableItem>, startIndex: Int): PlaybackTrack? {
        for (i in startIndex until queue.size) {
            val candidate = resolveTrackForItem(queue[i], preferredTrackId = null, currentMediaQueueId = null)
            if (candidate != null) return candidate
        }
        return null
    }
}
