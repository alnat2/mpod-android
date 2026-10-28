package com.example.mpod.data.network

import com.example.mpod.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    const val USER_AGENT =
        "mpoddy/${BuildConfig.VERSION_NAME} (Android; Linux) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    @Provides
    @Singleton
    fun provideOkHttpClient(
        factory: ProxyHttpClientFactory
    ): OkHttpClient {
        return factory.createClient()
    }

    @Provides
    @Singleton
    fun provideAudioLinkResolver(
        resolver: com.example.mpod.data.network.resolver.DefaultAudioLinkResolver
    ): com.example.mpod.data.network.resolver.AudioLinkResolver {
        return resolver
    }
}
