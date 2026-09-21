package com.example.mpod.data.repository

import android.content.ContextWrapper
import com.example.mpod.data.local.dao.EpisodeDao
import com.example.mpod.data.local.dao.PlaylistDao
import com.example.mpod.data.local.entity.PlaylistItemEntity
import com.example.mpod.data.local.model.PlaylistItemWithEpisode
import com.example.mpod.data.network.ProxyHttpClientFactory
import com.example.mpod.playback.SmartListeningManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** Mandatory concrete cleanup dependency for refresh/OPML fixtures, which never download. */
internal fun repositoryCleanupManager(episodes: EpisodeDao, client: ProxyHttpClientFactory) =
    SmartListeningManager(object : ContextWrapper(null) {}, object : PlaylistDao {
        override fun getPlaylistItemsWithEpisodesFlow(): Flow<List<PlaylistItemWithEpisode>> = emptyFlow()
        override fun getPlaylistItemsWithEpisodes(): List<PlaylistItemWithEpisode> = emptyList()
        override fun getAllPlaylistItems(): List<PlaylistItemEntity> = emptyList()
        override fun getMaxPosition(): Int? = null
        override fun isEpisodeInPlaylist(episodeId: Long) = false
        override fun insertPlaylistItem(item: PlaylistItemEntity): Long = error("Refresh/OPML does not mutate playlist")
        override fun insertPlaylistItems(items: List<PlaylistItemEntity>): List<Long> = error("Refresh/OPML does not mutate playlist")
        override fun removeFromPlaylist(episodeId: Long) = error("Refresh/OPML does not mutate playlist")
        override fun clearPlaylist() = error("Refresh/OPML does not mutate playlist")
    }, episodes, client)
