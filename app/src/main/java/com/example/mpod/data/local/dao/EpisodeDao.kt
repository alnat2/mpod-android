package com.example.mpod.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.mpod.data.local.entity.EpisodeEntity
import com.example.mpod.data.local.model.EpisodeWithPodcast
import kotlinx.coroutines.flow.Flow

data class MarkAllListenedSnapshot(
    val episodes: List<EpisodeEntity>,
    val listenedEpisodeIds: List<Long>,
    val removedPlaylistEpisodeIds: List<Long>
)

@Dao
interface EpisodeDao {
    @Query("SELECT * FROM episodes WHERE podcastId = :podcastId ORDER BY publishedAt DESC")
    fun getEpisodesByPodcastIdFlow(podcastId: Long): Flow<List<EpisodeEntity>>

    @Transaction
    @Query("SELECT * FROM episodes ORDER BY publishedAt DESC")
    fun getAllEpisodesWithPodcastFlow(): Flow<List<EpisodeWithPodcast>>

    @Query("SELECT * FROM episodes WHERE id = :id LIMIT 1")
    fun getEpisodeById(id: Long): EpisodeEntity?

    @Transaction
    @Query("SELECT * FROM episodes WHERE id = :id LIMIT 1")
    fun getEpisodeWithPodcastById(id: Long): EpisodeWithPodcast?

    @Query("SELECT * FROM episodes WHERE podcastId = :podcastId AND guid = :guid LIMIT 1")
    fun getEpisodeByPodcastIdAndGuid(podcastId: Long, guid: String): EpisodeEntity?

    @Query("SELECT * FROM episodes WHERE podcastId = :podcastId ORDER BY publishedAt DESC")
    fun getEpisodesByPodcastId(podcastId: Long): List<EpisodeEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertEpisode(episode: EpisodeEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertEpisodes(episodes: List<EpisodeEntity>): List<Long>

    @Update
    fun update(episode: EpisodeEntity)

    @Query("UPDATE episodes SET isListened = :listened WHERE id = :episodeId")
    fun setListened(episodeId: Long, listened: Boolean)

    @Query("UPDATE episodes SET isListened = :listened WHERE podcastId = :podcastId")
    fun setAllListenedForPodcast(podcastId: Long, listened: Boolean)

    @Query("SELECT p.episodeId FROM playlist_items p INNER JOIN episodes e ON e.id = p.episodeId WHERE e.podcastId = :podcastId ORDER BY p.episodeId")
    fun getPlaylistEpisodeIdsForPodcast(podcastId: Long): List<Long>

    @Query("UPDATE episodes SET isListened = 1 WHERE id IN (:episodeIds) AND isListened = 0")
    fun setEpisodesListened(episodeIds: List<Long>): Int

    @Query("DELETE FROM playlist_items WHERE episodeId IN (:episodeIds)")
    fun deletePlaylistEpisodes(episodeIds: List<Long>): Int

    /**
     * Room owns the snapshot and both table mutations. Refresh inserts either precede this
     * transaction and belong to its snapshot, or follow it and remain unlistened.
     * Filesystem work must happen after commit, using these exact operation-owned episodes.
     */
    @Transaction
    fun markAllListenedAndRemoveFromPlaylist(podcastId: Long): MarkAllListenedSnapshot {
        val episodes = getEpisodesByPodcastId(podcastId)
        val playlistIds = getPlaylistEpisodeIdsForPodcast(podcastId)
        val queuedIds = playlistIds.toSet()
        val targets = episodes.filter {
            !it.isListened || it.id in queuedIds || it.isDownloaded || !it.localFilePath.isNullOrBlank()
        }
        val targetIds = targets.map { it.id }
        val listenedIds = targets.filter { !it.isListened }.map { it.id }
        val unlistenedIds = listenedIds.toSet()
        // Older supported Android SQLite builds cap bound parameters at 999. Chunking keeps
        // large feeds within that limit while the outer transaction still owns one snapshot.
        for (ids in targetIds.chunked(900)) {
            if (ids.any { it in unlistenedIds }) setEpisodesListened(ids)
            if (ids.any { it in queuedIds }) deletePlaylistEpisodes(ids)
        }
        return MarkAllListenedSnapshot(targets, listenedIds, playlistIds)
    }

    @Query("UPDATE episodes SET playbackPositionMs = :positionMs WHERE id = :episodeId")
    fun updatePlaybackPosition(episodeId: Long, positionMs: Long)

    @Query("UPDATE episodes SET isDownloaded = :isDownloaded, localFilePath = :localFilePath WHERE id = :episodeId")
    fun updateDownloadState(episodeId: Long, isDownloaded: Boolean, localFilePath: String?)

    @Query(
        """UPDATE episodes SET isDownloaded = 0, localFilePath = NULL
           WHERE id = :episodeId
             AND isDownloaded = :expectedIsDownloaded
             AND ((localFilePath = :expectedLocalFilePath)
                  OR (localFilePath IS NULL AND :expectedLocalFilePath IS NULL))"""
    )
    fun clearDownloadStateIfMatches(
        episodeId: Long,
        expectedIsDownloaded: Boolean,
        expectedLocalFilePath: String?
    ): Int

    @Query("SELECT * FROM episodes WHERE isDownloaded = 1")
    fun getDownloadedEpisodes(): List<EpisodeEntity>
}
