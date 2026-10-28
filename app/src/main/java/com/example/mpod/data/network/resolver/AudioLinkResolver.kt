package com.example.mpod.data.network.resolver

data class ResolvedAudioMetadata(
    val directAudioUrl: String,
    val title: String,
    val author: String? = null,
    val artworkUrl: String? = null,
    val durationSeconds: Int? = null,
    val description: String? = null,
    val podcastTitle: String? = null
)

interface AudioLinkResolver {
    suspend fun resolve(rawUrl: String): Result<ResolvedAudioMetadata>
}
