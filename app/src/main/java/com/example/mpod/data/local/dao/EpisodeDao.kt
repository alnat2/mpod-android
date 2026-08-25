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

    @Query("UPDATE episodes SET playbackPositionMs = :positionMs WHERE id = :episodeId")
    fun updatePlaybackPosition(episodeId: Long, positionMs: Long)

    @Query("UPDATE episodes SET isDownloaded = :isDownloaded, localFilePath = :localFilePath WHERE id = :episodeId")
    fun updateDownloadState(episodeId: Long, isDownloaded: Boolean, localFilePath: String?)

    @Query("SELECT * FROM episodes WHERE isDownloaded = 1")
    fun getDownloadedEpisodes(): List<EpisodeEntity>
}
