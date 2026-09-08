package com.example.mpod

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.example.mpod.data.local.preferences.AppSettingsDataStore
import com.example.mpod.data.network.ProxyHttpClientFactory
import com.example.mpod.playback.AutoRefreshScheduler
import com.example.mpod.playback.SmartListeningManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import javax.inject.Inject

@HiltAndroidApp
class MpodApplication : Application(), ImageLoaderFactory, Configuration.Provider {
    @Inject lateinit var okHttpClient: OkHttpClient
    @Inject lateinit var proxyHttpClientFactory: ProxyHttpClientFactory
    @Inject lateinit var appSettingsDataStore: AppSettingsDataStore
    @Inject lateinit var smartListeningManager: SmartListeningManager
    @Inject lateinit var autoRefreshScheduler: AutoRefreshScheduler
    @Inject lateinit var workerFactory: HiltWorkerFactory

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        proxyHttpClientFactory.configure(appSettingsDataStore, appScope)
        smartListeningManager.startObserving()
    }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .okHttpClient(okHttpClient)
            .build()
}
