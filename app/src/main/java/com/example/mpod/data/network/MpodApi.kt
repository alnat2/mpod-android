package com.example.mpod.data.network

import com.example.mpod.data.network.model.CreatePodcastRequest
import com.example.mpod.data.network.model.EpisodeResponse
import com.example.mpod.data.network.model.EpisodesResponse
import com.example.mpod.data.network.model.LoginRequest
import com.example.mpod.data.network.model.PlaybackSyncRequest
import com.example.mpod.data.network.model.PlaylistResponse
import com.example.mpod.data.network.model.PodcastsResponse
import com.example.mpod.data.network.model.SessionDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.POST

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

    @POST("api/podcasts")
    suspend fun createPodcast(@Body request: CreatePodcastRequest): Response<Unit>

    @GET("api/podcasts/{podcastId}/episodes")
    suspend fun getPodcastEpisodes(@Path("podcastId") podcastId: Int): Response<EpisodesResponse>

    @GET("api/episodes/{episodeId}")
    suspend fun getEpisode(@Path("episodeId") episodeId: Int): Response<EpisodeResponse>

    @GET("api/playlist")
    suspend fun getPlaylist(): Response<PlaylistResponse>

    @POST("api/playback/sync")
    suspend fun syncPlayback(@Body request: PlaybackSyncRequest): Response<Unit>

}
