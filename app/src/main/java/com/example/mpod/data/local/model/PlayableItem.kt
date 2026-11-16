package com.example.mpod.data.local.model

import com.example.mpod.data.local.entity.AudiobookEntity
import com.example.mpod.data.local.entity.AudiobookTrackEntity
import com.example.mpod.data.local.entity.EpisodeEntity

sealed interface PlayableItem {
    val playlistItemId: Long
    val position: Int
    val queueItemId: String

    data class EpisodeItem(
        override val playlistItemId: Long,
        override val position: Int,
        val episode: EpisodeEntity,
        val podcastTitle: String,
        val podcastArtworkUrl: String
    ) : PlayableItem {
        override val queueItemId: String get() = "episode:${episode.id}"
    }

    data class AudiobookItem(
        override val playlistItemId: Long,
        override val position: Int,
        val audiobook: AudiobookEntity,
        val selectedTracks: List<AudiobookTrackEntity>,
        val currentTrack: AudiobookTrackEntity?
    ) : PlayableItem {
        override val queueItemId: String get() = "audiobook:${audiobook.id}"

        val currentChapterIndex: Int
            get() {
                val curId = currentTrack?.id ?: return 0
                val idx = selectedTracks.indexOfFirst { it.id == curId }
                return if (idx >= 0) idx + 1 else 1
            }

        val totalChaptersCount: Int
            get() = selectedTracks.size
    }
}
