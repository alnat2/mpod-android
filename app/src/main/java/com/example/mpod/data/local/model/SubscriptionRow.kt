package com.example.mpod.data.local.model

import androidx.room.Embedded

/** Only fields rendered by Subscriptions; playback position and RSS bookkeeping are excluded. */
data class SubscriptionPodcastData(
    val id: Long,
    val title: String,
    val description: String,
    val feedUrl: String,
    val artworkUrl: String
)

data class SubscriptionEpisodeData(
    val id: Long,
    val title: String,
    val description: String,
    val durationSeconds: Long,
    val publishedAtString: String,
    val isListened: Boolean,
    val isDownloaded: Boolean
)

data class SubscriptionRow(
    @Embedded(prefix = "podcast_") val podcast: SubscriptionPodcastData,
    // LEFT JOIN retains empty podcasts. Room maps an all-NULL episode to null.
    @Embedded(prefix = "episode_") val episode: SubscriptionEpisodeData?,
    val inPlaylist: Boolean
)
