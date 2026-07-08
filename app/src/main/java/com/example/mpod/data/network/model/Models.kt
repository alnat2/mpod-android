package com.example.mpod.data.network.model

import com.google.gson.annotations.SerializedName

data class LoginRequest(
    @SerializedName("username") val username: String,
    @SerializedName("password") val password: String
)

data class PodcastDto(
    @SerializedName("id") val id: Int,
    @SerializedName("title") val title: String,
    @SerializedName("rss_url") val rssUrl: String,
    @SerializedName("last_checked") val lastChecked: String?,
    @SerializedName("update_time") val updateTime: String?
)

data class EpisodeDto(
    @SerializedName("id") val id: Int,
    @SerializedName("podcast_id") val podcastId: Int,
    @SerializedName("title") val title: String,
    @SerializedName("audio_url") val audioUrl: String,
    @SerializedName("duration") val duration: Int?,
    @SerializedName("is_listened") val isListened: Boolean,
    @SerializedName("published_at") val publishedAt: String?
)

data class PlaylistItemDto(
    @SerializedName("id") val id: Int,
    @SerializedName("episode_id") val episodeId: Int,
    @SerializedName("position") val position: Int
)

data class PlaybackSyncRequest(
    @SerializedName("episode_id") val episodeId: Int,
    @SerializedName("position_seconds") val positionSeconds: Int
)
