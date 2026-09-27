package com.example.mpod.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.mpod.data.local.entity.PodcastEntity
import com.example.mpod.data.local.model.SubscriptionRow
import kotlinx.coroutines.flow.Flow

@Dao
interface PodcastDao {
    // One snapshot/query regardless of podcast count. The unique playlist episode index
    // guarantees the membership join cannot duplicate episodes.
    @Query("""
        SELECT pod.id AS podcast_id, pod.title AS podcast_title,
            pod.description AS podcast_description, pod.feedUrl AS podcast_feedUrl,
            pod.artworkUrl AS podcast_artworkUrl,
            e.id AS episode_id, e.title AS episode_title,
            e.description AS episode_description, e.durationSeconds AS episode_durationSeconds,
            e.publishedAtString AS episode_publishedAtString,
            e.isListened AS episode_isListened, e.isDownloaded AS episode_isDownloaded,
            CASE WHEN p.episodeId IS NULL THEN 0 ELSE 1 END AS inPlaylist
        FROM podcasts pod
        LEFT JOIN episodes e ON e.podcastId = pod.id
        LEFT JOIN playlist_items p ON p.episodeId = e.id
        ORDER BY pod.title ASC, pod.id ASC, e.publishedAt DESC
    """)
    fun getSubscriptionRowsFlow(): Flow<List<SubscriptionRow>>

    @Query("SELECT * FROM podcasts ORDER BY title ASC")
    fun getAllPodcastsFlow(): Flow<List<PodcastEntity>>

    @Query("SELECT * FROM podcasts ORDER BY title ASC")
    fun getAllPodcasts(): List<PodcastEntity>

    @Query("SELECT * FROM podcasts WHERE id = :id LIMIT 1")
    fun getPodcastById(id: Long): PodcastEntity?

    @Query("SELECT * FROM podcasts WHERE feedUrl = :feedUrl LIMIT 1")
    fun getPodcastByFeedUrl(feedUrl: String): PodcastEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(podcast: PodcastEntity): Long

    @Update
    fun update(podcast: PodcastEntity)

    @Query("DELETE FROM podcasts WHERE id = :id")
    fun deleteById(id: Long)

    @Query("DELETE FROM podcasts")
    fun deleteAll()
}
