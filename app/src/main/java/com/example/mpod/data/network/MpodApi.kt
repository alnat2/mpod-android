package com.example.mpod.data.network

import com.example.mpod.data.network.model.CreatePodcastRequest
import com.example.mpod.data.network.model.EpisodeListenedRequest
import com.example.mpod.data.network.model.EpisodeResponse
import com.example.mpod.data.network.model.EpisodesResponse
import com.example.mpod.data.network.model.LoginRequest
import com.example.mpod.data.network.model.PlaybackSyncRequest
import com.example.mpod.data.network.model.PlaybackUpdateRequest
import com.example.mpod.data.network.model.PlaylistAddRequest
import com.example.mpod.data.network.model.PlaylistResponse
import com.example.mpod.data.network.model.PodcastsResponse
import com.example.mpod.data.network.model.ProxyStatusResponse
import com.example.mpod.data.network.model.SchedulerStatusResponse
import com.example.mpod.data.network.model.SessionDto
import com.example.mpod.data.network.model.SettingsResponse
import com.example.mpod.data.network.model.SettingsUpdateRequest
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.Path
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part

interface MpodApi {

    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<Unit>

    @POST("api/auth/register")
    suspend fun register(@Body request: LoginRequest): Response<Unit>

    @POST("api/auth/logout")
    suspend fun logout(): Response<Unit>

    @GET("api/auth/session")
    suspend fun getSession(): Response<SessionDto>

    @GET("api/podcasts")
    suspend fun getPodcasts(): Response<PodcastsResponse>

    @GET("api/podcasts/export-opml")
    suspend fun exportOpml(): Response<ResponseBody>

    @POST("api/podcasts")
    suspend fun createPodcast(@Body request: CreatePodcastRequest): Response<Unit>

    @Multipart
    @POST("api/podcasts/import-opml")
    suspend fun importOpml(@Part file: MultipartBody.Part): Response<Unit>

    @POST("api/podcasts/refresh-all")
    suspend fun refreshAllPodcasts(): Response<Unit>

    @POST("api/podcasts/{podcastId}/refresh")
    suspend fun refreshPodcast(@Path("podcastId") podcastId: Int): Response<Unit>

    @DELETE("api/podcasts/{podcastId}")
    suspend fun removePodcast(@Path("podcastId") podcastId: Int): Response<Unit>

    @GET("api/podcasts/{podcastId}/episodes")
    suspend fun getPodcastEpisodes(@Path("podcastId") podcastId: Int): Response<EpisodesResponse>

    @GET("api/episodes/{episodeId}")
    suspend fun getEpisode(@Path("episodeId") episodeId: Int): Response<EpisodeResponse>

    @PATCH("api/episodes/{episodeId}")
    suspend fun setEpisodeListened(
        @Path("episodeId") episodeId: Int,
        @Body request: EpisodeListenedRequest
    ): Response<Unit>

    @POST("api/episodes/{episodeId}/download")
    suspend fun downloadEpisode(@Path("episodeId") episodeId: Int): Response<Unit>

    @GET("api/playlist")
    suspend fun getPlaylist(): Response<PlaylistResponse>

    @POST("api/playlist")
    suspend fun addToPlaylist(@Body request: PlaylistAddRequest): Response<Unit>

    @DELETE("api/playlist/{episodeId}")
    suspend fun removeFromPlaylist(@Path("episodeId") episodeId: Int): Response<Unit>

    @POST("api/playback/sync")
    suspend fun syncPlayback(@Body request: PlaybackSyncRequest): Response<Unit>

    @POST("api/playback")
    suspend fun updatePlayback(@Body request: PlaybackUpdateRequest): Response<Unit>

    @GET("api/settings")
    suspend fun getSettings(): Response<SettingsResponse>

    @PATCH("api/settings")
    suspend fun updateSettings(@Body request: SettingsUpdateRequest): Response<SettingsResponse>

    @GET("api/proxy/status")
    suspend fun getProxyStatus(): Response<ProxyStatusResponse>

    @GET("api/jobs/status")
    suspend fun getJobsStatus(): Response<SchedulerStatusResponse>

}
