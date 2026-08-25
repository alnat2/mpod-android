package com.example.mpod.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.mpod.data.local.entity.PlaylistItemEntity
import com.example.mpod.data.local.model.PlaylistItemWithEpisode
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Query("""
        SELECT 
            p.id AS playlistItemId,
            p.position AS position,
            e.id AS id,
            e.podcastId AS podcastId,
            e.guid AS guid,
            e.title AS title,
            e.description AS description,
            e.audioUrl AS audioUrl,
            e.durationSeconds AS durationSeconds,
            e.publishedAt AS publishedAt,
            e.publishedAtString AS publishedAtString,
            e.isListened AS isListened,
            e.playbackPositionMs AS playbackPositionMs,
            e.isDownloaded AS isDownloaded,
            e.localFilePath AS localFilePath,
            pod.title AS podcastTitle,
            pod.artworkUrl AS podcastArtworkUrl
        FROM playlist_items p
        INNER JOIN episodes e ON p.episodeId = e.id
        INNER JOIN podcasts pod ON e.podcastId = pod.id
        ORDER BY p.position ASC
    """)
    fun getPlaylistItemsWithEpisodesFlow(): Flow<List<PlaylistItemWithEpisode>>

    @Query("""
        SELECT 
            p.id AS playlistItemId,
            p.position AS position,
            e.id AS id,
            e.podcastId AS podcastId,
            e.guid AS guid,
            e.title AS title,
            e.description AS description,
            e.audioUrl AS audioUrl,
            e.durationSeconds AS durationSeconds,
            e.publishedAt AS publishedAt,
            e.publishedAtString AS publishedAtString,
            e.isListened AS isListened,
            e.playbackPositionMs AS playbackPositionMs,
            e.isDownloaded AS isDownloaded,
            e.localFilePath AS localFilePath,
            pod.title AS podcastTitle,
            pod.artworkUrl AS podcastArtworkUrl
        FROM playlist_items p
        INNER JOIN episodes e ON p.episodeId = e.id
        INNER JOIN podcasts pod ON e.podcastId = pod.id
        ORDER BY p.position ASC
    """)
    suspend fun getPlaylistItemsWithEpisodes(): List<PlaylistItemWithEpisode>

    @Query("SELECT * FROM playlist_items ORDER BY position ASC")
    suspend fun getAllPlaylistItems(): List<PlaylistItemEntity>

    @Query("SELECT MAX(position) FROM playlist_items")
    suspend fun getMaxPosition(): Int?

    @Query("SELECT EXISTS(SELECT 1 FROM playlist_items WHERE episodeId = :episodeId)")
    suspend fun isEpisodeInPlaylist(episodeId: Long): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistItem(item: PlaylistItemEntity): Long

    @Query("DELETE FROM playlist_items WHERE episodeId = :episodeId")
    suspend fun removeFromPlaylist(episodeId: Long)

    @Query("DELETE FROM playlist_items")
    suspend fun clearPlaylist()

    @Transaction
    suspend fun addEpisodeToPlaylist(episodeId: Long) {
        if (isEpisodeInPlaylist(episodeId)) return
        val nextPos = (getMaxPosition() ?: -1) + 1
        insertPlaylistItem(PlaylistItemEntity(episodeId = episodeId, position = nextPos))
    }

    @Transaction
    suspend fun reorderPlaylist(reorderedEpisodeIds: List<Long>) {
        clearPlaylist()
        reorderedEpisodeIds.forEachIndexed { index, epId ->
            insertPlaylistItem(PlaylistItemEntity(episodeId = epId, position = index))
        }
    }
}
