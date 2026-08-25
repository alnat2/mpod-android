package com.example.mpod.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.mpod.data.local.entity.PodcastEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PodcastDao {
    @Query("SELECT * FROM podcasts ORDER BY title ASC")
    fun getAllPodcastsFlow(): Flow<List<PodcastEntity>>

    @Query("SELECT * FROM podcasts ORDER BY title ASC")
    suspend fun getAllPodcasts(): List<PodcastEntity>

    @Query("SELECT * FROM podcasts WHERE id = :id LIMIT 1")
    suspend fun getPodcastById(id: Long): PodcastEntity?

    @Query("SELECT * FROM podcasts WHERE feedUrl = :feedUrl LIMIT 1")
    suspend fun getPodcastByFeedUrl(feedUrl: String): PodcastEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(podcast: PodcastEntity): Long

    @Update
    suspend fun update(podcast: PodcastEntity)

    @Query("DELETE FROM podcasts WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM podcasts")
    suspend fun deleteAll()
}
