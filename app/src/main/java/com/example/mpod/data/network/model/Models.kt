package com.example.mpod.data.network.model

import com.google.gson.annotations.SerializedName

data class LoginRequest(
    @SerializedName("username") val username: String,
    @SerializedName("password") val password: String
)

data class SessionDto(
    @SerializedName("authenticated") val authenticated: Boolean,
    @SerializedName("setupRequired") val setupRequired: Boolean,
    @SerializedName("user") val user: String?
)

data class PodcastsResponse(
    @SerializedName("podcasts") val podcasts: List<PodcastDto> = emptyList()
)

data class PodcastDto(
    @SerializedName("id") val id: Int,
    @SerializedName("title") val title: String?,
    @SerializedName("description") val description: String?,
    @SerializedName(value = "rssUrl", alternate = ["rss_url"]) val rssUrl: String?,
    @SerializedName("imageUrl") val imageUrl: String?,
    @SerializedName(value = "lastChecked", alternate = ["last_checked"]) val lastChecked: String?,
    @SerializedName(value = "updateTime", alternate = ["update_time"]) val updateTime: String?
)

data class EpisodesResponse(
    @SerializedName("episodes") val episodes: List<EpisodeDto> = emptyList()
)

data class EpisodeResponse(
    @SerializedName("episode") val episode: EpisodeDto?
)

data class EpisodeDto(
    @SerializedName("id") val id: Int,
    @SerializedName(value = "podcastId", alternate = ["podcast_id"]) val podcastId: Int,
    @SerializedName("title") val title: String?,
    @SerializedName(value = "audioUrl", alternate = ["audio_url"]) val audioUrl: String?,
    @SerializedName("duration") val duration: Double?,
    @SerializedName(value = "isListened", alternate = ["is_listened"]) val isListened: Boolean,
    @SerializedName("downloaded") val downloaded: Boolean?,
    @SerializedName("summary") val summary: String?,
    @SerializedName(value = "publishedAt", alternate = ["published_at"]) val publishedAt: String?
)

data class PlaylistResponse(
    @SerializedName("items") val items: List<PlaylistItemDto> = emptyList()
)

data class PlaylistItemDto(
    @SerializedName("id") val id: Int,
    @SerializedName(value = "episodeId", alternate = ["episode_id"]) val episodeId: Int,
    @SerializedName("position") val position: Int
)

data class PlaybackSyncRequest(
    @SerializedName("episode_id") val episodeId: Int,
    @SerializedName("position_seconds") val positionSeconds: Int
)
