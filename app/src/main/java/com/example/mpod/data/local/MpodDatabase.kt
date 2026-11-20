package com.example.mpod.data.local

import androidx.room.Database
import androidx.room.TypeConverters
import androidx.room.RoomDatabase
import com.example.mpod.data.local.dao.AudiobookDao
import com.example.mpod.data.local.dao.EpisodeDao
import com.example.mpod.data.local.dao.PendingShareImportDao
import com.example.mpod.data.local.dao.PlaylistDao
import com.example.mpod.data.local.dao.PodcastDao
import com.example.mpod.data.local.entity.AudiobookEntity
import com.example.mpod.data.local.entity.AudiobookPlaylistTrackEntity
import com.example.mpod.data.local.entity.AudiobookTrackEntity
import com.example.mpod.data.local.entity.EpisodeEntity
import com.example.mpod.data.local.entity.PendingShareImportEntity
import com.example.mpod.data.local.entity.PlaylistItemEntity
import com.example.mpod.data.local.entity.PodcastEntity

@Database(
    entities = [
        PodcastEntity::class,
        EpisodeEntity::class,
        PlaylistItemEntity::class,
        PendingShareImportEntity::class,
        AudiobookEntity::class,
        AudiobookTrackEntity::class,
        AudiobookPlaylistTrackEntity::class
    ],
    version = 5,
    exportSchema = true
)
@TypeConverters(ShareImportStatusConverter::class)
abstract class MpodDatabase : RoomDatabase() {
    abstract fun podcastDao(): PodcastDao
    abstract fun episodeDao(): EpisodeDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun pendingShareImportDao(): PendingShareImportDao
    abstract fun audiobookDao(): AudiobookDao
}
