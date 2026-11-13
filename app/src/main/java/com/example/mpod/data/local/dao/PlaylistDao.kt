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
    fun getPlaylistItemsWithEpisodes(): List<PlaylistItemWithEpisode>

    @Query("SELECT * FROM playlist_items ORDER BY position ASC")
    fun getAllPlaylistItems(): List<PlaylistItemEntity>

    @Query("SELECT MAX(position) FROM playlist_items")
    fun getMaxPosition(): Int?

    @Query("SELECT EXISTS(SELECT 1 FROM playlist_items WHERE episodeId = :episodeId)")
    fun isEpisodeInPlaylist(episodeId: Long): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM playlist_items WHERE audiobookId = :audiobookId)")
    fun isAudiobookInPlaylist(audiobookId: Long): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertPlaylistItem(item: PlaylistItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertPlaylistItems(items: List<PlaylistItemEntity>): List<Long>

    @Query("DELETE FROM playlist_items WHERE episodeId = :episodeId")
    fun removeFromPlaylist(episodeId: Long)

    @Query("DELETE FROM playlist_items WHERE audiobookId = :audiobookId")
    fun removeAudiobookFromPlaylist(audiobookId: Long)

    @Query("DELETE FROM playlist_items")
    fun clearPlaylist()

    @Query("UPDATE playlist_items SET position = :position WHERE episodeId = :episodeId")
    fun updatePosition(episodeId: Long, position: Int): Int

    @Query("UPDATE playlist_items SET position = :position WHERE audiobookId = :audiobookId")
    fun updateAudiobookPosition(audiobookId: Long, position: Int): Int

    @Deprecated("Use deleteEpisodesNotIn instead. deleteNotIn is kept for backward compatibility.")
    @Query("DELETE FROM playlist_items WHERE episodeId IS NOT NULL AND episodeId NOT IN (:episodeIds)")
    fun deleteNotIn(episodeIds: List<Long>)

    @Query("DELETE FROM playlist_items WHERE episodeId IS NOT NULL AND episodeId NOT IN (:episodeIds)")
    fun deleteEpisodesNotIn(episodeIds: List<Long>)

    @Query("DELETE FROM playlist_items WHERE episodeId IS NOT NULL")
    fun deleteEpisodeItems()

    @Query("DELETE FROM playlist_items WHERE audiobookId IS NOT NULL AND audiobookId NOT IN (:audiobookIds)")
    fun deleteAudiobooksNotIn(audiobookIds: List<Long>)

    @Query("DELETE FROM playlist_items WHERE audiobookId IS NOT NULL")
    fun deleteAudiobookItems()

    @Transaction
    fun appendEpisodeIfMissing(episodeId: Long): Boolean {
        if (isEpisodeInPlaylist(episodeId)) return false
        val nextPos = (getMaxPosition() ?: -1) + 1
        insertPlaylistItem(PlaylistItemEntity(episodeId = episodeId, position = nextPos))
        return true
    }

    @Transaction
    fun appendAudiobookIfMissing(audiobookId: Long): Boolean {
        if (isAudiobookInPlaylist(audiobookId)) return false
        val nextPos = (getMaxPosition() ?: -1) + 1
        insertPlaylistItem(PlaylistItemEntity(audiobookId = audiobookId, position = nextPos))
        return true
    }

    @Transaction
    fun reorderPlaylist(items: List<PlaylistItemEntity>) {
        if (items.isEmpty()) {
            clearPlaylist()
            return
        }
        val targetEpisodeIds = items.mapNotNull { it.episodeId }
        val targetAudiobookIds = items.mapNotNull { it.audiobookId }

        // Only prune unreferenced entries for types present in the reorder batch,
        // preventing accidental deletion of episodes when reordering audiobooks (and vice versa).
        if (targetEpisodeIds.isNotEmpty()) {
            deleteEpisodesNotIn(targetEpisodeIds)
        }
        if (targetAudiobookIds.isNotEmpty()) {
            deleteAudiobooksNotIn(targetAudiobookIds)
        }

        for (item in items) {
            val updated = when {
                item.episodeId != null -> updatePosition(item.episodeId, item.position)
                item.audiobookId != null -> updateAudiobookPosition(item.audiobookId, item.position)
                else -> 0
            }
            if (updated == 0) {
                insertPlaylistItem(item)
            }
        }
    }
}
