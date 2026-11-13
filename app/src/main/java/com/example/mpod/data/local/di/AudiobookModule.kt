package com.example.mpod.data.local.di

import com.example.mpod.data.audiobook.AudioMetadataExtractor
import com.example.mpod.data.audiobook.DefaultAudioMetadataExtractor
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AudiobookModule {

    @Binds
    @Singleton
    abstract fun bindAudioMetadataExtractor(
        impl: DefaultAudioMetadataExtractor
    ): AudioMetadataExtractor
}
