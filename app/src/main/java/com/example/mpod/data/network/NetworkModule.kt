package com.example.mpod.data.network

import android.content.Context
import com.example.mpod.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideCookieJar(@ApplicationContext context: Context): PersistentCookieJar {
        return PersistentCookieJar(context)
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        cookieJar: PersistentCookieJar,
        sessionExpiryInterceptor: SessionExpiryInterceptor
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(CORE_NETWORK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(CORE_NETWORK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(CORE_NETWORK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(CORE_NETWORK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .configureConnectionPool()
            .addInterceptor(sessionExpiryInterceptor)
            .addDebugHttpLogging(BuildConfig.DEBUG)
            .cookieJar(cookieJar)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(
        okHttpClient: OkHttpClient,
        backendConfig: BackendConfig
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl(backendConfig.baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideMpodApi(retrofit: Retrofit): MpodApi {
        return retrofit.create(MpodApi::class.java)
    }
}

internal fun OkHttpClient.Builder.addDebugHttpLogging(enabled: Boolean): OkHttpClient.Builder = apply {
    if (enabled) {
        addInterceptor(
            HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
        )
    }
}

internal fun OkHttpClient.Builder.configureConnectionPool(): OkHttpClient.Builder = connectionPool(
    ConnectionPool(
        maxIdleConnections = CORE_NETWORK_MAX_IDLE_CONNECTIONS,
        keepAliveDuration = CORE_NETWORK_KEEP_ALIVE_MINUTES,
        TimeUnit.MINUTES
    )
)

internal const val CORE_NETWORK_TIMEOUT_SECONDS = 30L
internal const val CORE_NETWORK_MAX_IDLE_CONNECTIONS = 10
internal const val CORE_NETWORK_KEEP_ALIVE_MINUTES = 5L
