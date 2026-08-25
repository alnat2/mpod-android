package com.example.mpod.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.mpod.data.local.dao.EpisodeDao
import com.example.mpod.data.local.dao.PlaylistDao
import com.example.mpod.data.local.dao.PodcastDao
import com.example.mpod.data.local.entity.EpisodeEntity
import com.example.mpod.data.local.entity.PlaylistItemEntity
import com.example.mpod.data.local.entity.PodcastEntity

@Database(
    entities = [
        PodcastEntity::class,
        EpisodeEntity::class,
        PlaylistItemEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class MpodDatabase : RoomDatabase() {
    abstract fun podcastDao(): PodcastDao
    abstract fun episodeDao(): EpisodeDao
    abstract fun playlistDao(): PlaylistDao
}
