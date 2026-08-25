package com.example.mpod.data.local.model

import androidx.room.Embedded
import androidx.room.Relation
import com.example.mpod.data.local.entity.EpisodeEntity
import com.example.mpod.data.local.entity.PodcastEntity

data class EpisodeWithPodcast(
    @Embedded val episode: EpisodeEntity,
    @Relation(
        parentColumn = "podcastId",
        entityColumn = "id"
    )
    val podcast: PodcastEntity?
)

data class PlaylistItemWithEpisode(
    val playlistItemId: Long,
    val position: Int,
    @Embedded val episode: EpisodeEntity,
    val podcastTitle: String,
    val podcastArtworkUrl: String
)
