package com.example.mpod.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
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
    fun getPlaylistItemsWithEpisodes(): List<PlaylistItemWithEpisode>

    @Query("SELECT * FROM playlist_items ORDER BY position ASC")
    fun getAllPlaylistItems(): List<PlaylistItemEntity>

    @Query("SELECT MAX(position) FROM playlist_items")
    fun getMaxPosition(): Int?

    @Query("SELECT EXISTS(SELECT 1 FROM playlist_items WHERE episodeId = :episodeId)")
    fun isEpisodeInPlaylist(episodeId: Long): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertPlaylistItem(item: PlaylistItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertPlaylistItems(items: List<PlaylistItemEntity>): List<Long>

    @Query("DELETE FROM playlist_items WHERE episodeId = :episodeId")
    fun removeFromPlaylist(episodeId: Long)

    @Query("DELETE FROM playlist_items")
    fun clearPlaylist()
}
