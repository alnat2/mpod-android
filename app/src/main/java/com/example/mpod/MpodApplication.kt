package com.example.mpod

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.example.mpod.playback.AutoRefreshScheduler
import dagger.hilt.android.HiltAndroidApp
import okhttp3.OkHttpClient
import javax.inject.Inject

@HiltAndroidApp
class MpodApplication : Application(), ImageLoaderFactory, Configuration.Provider {
    @Inject lateinit var okHttpClient: OkHttpClient
    @Inject lateinit var smartListeningManager: com.example.mpod.playback.SmartListeningManager
    @Inject lateinit var autoRefreshScheduler: AutoRefreshScheduler
    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        smartListeningManager.startObserving()
    }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .okHttpClient(okHttpClient)
            .build()
}
