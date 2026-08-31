package com.example.mpod.data.network

import com.example.mpod.data.local.preferences.AppSettingsDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(
        factory: ProxyHttpClientFactory,
        appSettingsDataStore: AppSettingsDataStore
    ): OkHttpClient {
        val settings = runBlocking { appSettingsDataStore.settingsFlow.first() }
        return factory.createClient(settings)
    }
}
