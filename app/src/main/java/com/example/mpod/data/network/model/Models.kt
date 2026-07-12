package com.example.mpod.data.network.model

import com.google.gson.annotations.SerializedName

data class LoginRequest(
    @SerializedName("username") val username: String,
    @SerializedName("password") val password: String
)

data class CreatePodcastRequest(
    @SerializedName("rssUrl") val rssUrl: String
)

data class PlaylistAddRequest(
    @SerializedName("episodeId") val episodeId: Int
)

data class PlaylistReorderRequest(
    @SerializedName("episodeIds") val episodeIds: List<Int>
)

data class EpisodeListenedRequest(
    @SerializedName("isListened") val isListened: Boolean
)

data class SessionDto(
    @SerializedName("authenticated") val authenticated: Boolean,
    @SerializedName("setupRequired") val setupRequired: Boolean,
    @SerializedName("user") val user: SessionUserDto?
)

data class SessionUserDto(
    @SerializedName("id") val id: Int,
    @SerializedName("username") val username: String?
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

data class PlaybackUpdateRequest(
    @SerializedName("episodeId") val episodeId: Int,
    @SerializedName("positionSeconds") val positionSeconds: Int,
    @SerializedName("durationSeconds") val durationSeconds: Int,
    @SerializedName("completed") val completed: Boolean = false,
    @SerializedName("didSeek") val didSeek: Boolean = false,
    @SerializedName("clientUpdatedAt") val clientUpdatedAt: String
)

data class SettingsResponse(
    @SerializedName("settings") val settings: SettingsDto
)

data class SettingsDto(
    @SerializedName("dailyRefreshTime") val dailyRefreshTime: String?,
    @SerializedName("proxyEnabled") val proxyEnabled: Boolean?,
    @SerializedName("proxyConfigured") val proxyConfigured: Boolean?,
    @SerializedName("appBuild") val appBuild: String?
)

data class SettingsUpdateRequest(
    @SerializedName("dailyRefreshTime") val dailyRefreshTime: String? = null,
    @SerializedName("proxyEnabled") val proxyEnabled: Boolean? = null
)

data class SchedulerStatusResponse(
    @SerializedName("scheduler") val scheduler: SchedulerStatusDto?
)

data class SchedulerStatusDto(
    @SerializedName("state") val state: String?,
    @SerializedName("lastRunAt") val lastRunAt: String?,
    @SerializedName("lastSuccessAt") val lastSuccessAt: String?,
    @SerializedName("lastFailureAt") val lastFailureAt: String?
)

data class ProxyStatusResponse(
    @SerializedName("proxy") val proxy: ProxyStatusDto?
)

data class ProxyStatusDto(
    @SerializedName("status") val status: String?,
    @SerializedName("externalIp") val externalIp: String?,
    @SerializedName("country") val country: String?,
    @SerializedName("error") val error: String?,
    @SerializedName("proxyConfigured") val proxyConfigured: Boolean?
)
