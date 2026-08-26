package com.example.mpod.data.repository

import com.example.mpod.data.local.dao.PlaylistDao
import com.example.mpod.data.local.entity.PlaylistItemEntity
import com.example.mpod.data.local.model.PlaylistItemWithEpisode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistRepository @Inject constructor(
    private val playlistDao: PlaylistDao
) {
    fun getPlaylistFlow(): Flow<List<PlaylistItemWithEpisode>> =
        playlistDao.getPlaylistItemsWithEpisodesFlow()

    suspend fun getPlaylistItems(): List<PlaylistItemWithEpisode> = withContext(Dispatchers.IO) {
        playlistDao.getPlaylistItemsWithEpisodes()
    }

    suspend fun addEpisodeToPlaylist(episodeId: Long) = withContext(Dispatchers.IO) {
        if (playlistDao.isEpisodeInPlaylist(episodeId)) return@withContext
        val nextPos = (playlistDao.getMaxPosition() ?: -1) + 1
        playlistDao.insertPlaylistItem(PlaylistItemEntity(episodeId = episodeId, position = nextPos))
    }

    suspend fun removeFromPlaylist(episodeId: Long) = withContext(Dispatchers.IO) {
        playlistDao.removeFromPlaylist(episodeId)
    }

    suspend fun reorderPlaylist(reorderedEpisodeIds: List<Long>) = withContext(Dispatchers.IO) {
        val items = reorderedEpisodeIds.mapIndexed { index, epId ->
            PlaylistItemEntity(episodeId = epId, position = index)
        }
        if (items.isNotEmpty()) {
            playlistDao.reorderPlaylist(items)
        } else {
            playlistDao.clearPlaylist()
        }
    }

    suspend fun clearPlaylist() = withContext(Dispatchers.IO) {
        playlistDao.clearPlaylist()
    }
}
