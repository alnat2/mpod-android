package com.example.mpod.data.network

import com.example.mpod.data.network.model.EpisodeDto
import com.example.mpod.data.network.model.LoginRequest
import com.example.mpod.data.network.model.PlaybackSyncRequest
import com.example.mpod.data.network.model.PlaylistItemDto
import com.example.mpod.data.network.model.PodcastDto
import com.example.mpod.data.network.model.SessionDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface MpodApi {

    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<Unit>

    @POST("api/auth/logout")
    suspend fun logout(): Response<Unit>

    @GET("api/auth/session")
    suspend fun getSession(): Response<SessionDto>

    @GET("api/podcasts")
    suspend fun getPodcasts(): Response<List<PodcastDto>>

    @GET("api/episodes")
    suspend fun getEpisodes(): Response<List<EpisodeDto>>

    @GET("api/playlist")
    suspend fun getPlaylist(): Response<List<PlaylistItemDto>>

    @POST("api/playback/sync")
    suspend fun syncPlayback(@Body request: PlaybackSyncRequest): Response<Unit>

}
