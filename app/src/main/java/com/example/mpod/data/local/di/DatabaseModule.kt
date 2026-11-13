package com.example.mpod.data.local.di

import android.content.Context
import androidx.room.Room
import com.example.mpod.data.local.MpodDatabase
import com.example.mpod.data.local.MpodDatabaseMigrations
import com.example.mpod.data.local.dao.AudiobookDao
import com.example.mpod.data.local.dao.EpisodeDao
import com.example.mpod.data.local.dao.PlaylistDao
import com.example.mpod.data.local.dao.PodcastDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MpodDatabase {
        return Room.databaseBuilder(
            context,
            MpodDatabase::class.java,
            "mpoddy.db"
        )
        .addMigrations(
            MpodDatabaseMigrations.MIGRATION_1_2,
            MpodDatabaseMigrations.MIGRATION_2_3
        )
        .build()
    }

    @Provides
    fun providePodcastDao(database: MpodDatabase): PodcastDao = database.podcastDao()

    @Provides
    fun provideEpisodeDao(database: MpodDatabase): EpisodeDao = database.episodeDao()

    @Provides
    fun providePlaylistDao(database: MpodDatabase): PlaylistDao = database.playlistDao()

    @Provides
    fun providePendingShareImportDao(database: MpodDatabase) = database.pendingShareImportDao()

    @Provides
    fun provideAudiobookDao(database: MpodDatabase): AudiobookDao = database.audiobookDao()
}
