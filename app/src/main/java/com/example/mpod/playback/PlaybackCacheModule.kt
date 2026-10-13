package com.example.mpod.playback

import android.content.Context
import androidx.annotation.OptIn
import androidx.annotation.VisibleForTesting
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.example.mpod.data.network.NetworkModule
import com.example.mpod.data.network.ProxyHttpClientFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Qualifier
import javax.inject.Singleton

@Retention(AnnotationRetention.BINARY)
@Qualifier
annotation class MediaCacheDataSourceFactory

@Module
@InstallIn(SingletonComponent::class)
object PlaybackCacheModule {

    const val MAX_CACHE_SIZE_BYTES = 2L * 1024 * 1024 * 1024 // 2 GB

    @OptIn(UnstableApi::class)
    @Volatile
    private var simpleCacheInstance: SimpleCache? = null

    @OptIn(UnstableApi::class)
    @Provides
    @Singleton
    fun provideSimpleCache(
        @ApplicationContext context: Context
    ): SimpleCache {
        return simpleCacheInstance ?: synchronized(this) {
            simpleCacheInstance ?: run {
                val cacheDir = File(context.cacheDir, "media_cache")
                val evictor = LeastRecentlyUsedCacheEvictor(MAX_CACHE_SIZE_BYTES)
                val databaseProvider = StandaloneDatabaseProvider(context)
                SimpleCache(cacheDir, evictor, databaseProvider).also {
                    simpleCacheInstance = it
                }
            }
        }
    }

    @OptIn(UnstableApi::class)
    @VisibleForTesting
    internal fun resetForTesting() {
        synchronized(this) {
            try {
                simpleCacheInstance?.release()
            } catch (_: Exception) {}
            simpleCacheInstance = null
        }
    }

    @OptIn(UnstableApi::class)
    @Provides
    @Singleton
    @MediaCacheDataSourceFactory
    fun provideCacheDataSourceFactory(
        @ApplicationContext context: Context,
        proxyHttpClientFactory: ProxyHttpClientFactory,
        simpleCache: SimpleCache
    ): DataSource.Factory {
        val okHttpClient = proxyHttpClientFactory.createClient()
        val okHttpDataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
            .setUserAgent(NetworkModule.USER_AGENT)
        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(simpleCache)
            .setUpstreamDataSourceFactory(okHttpDataSourceFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        return DefaultDataSource.Factory(context, cacheDataSourceFactory)
    }
}
