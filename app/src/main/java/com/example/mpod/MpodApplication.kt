package com.example.mpod

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import dagger.hilt.android.HiltAndroidApp
import okhttp3.OkHttpClient
import javax.inject.Inject

@HiltAndroidApp
class MpodApplication : Application(), ImageLoaderFactory {
    @Inject lateinit var okHttpClient: OkHttpClient
    @Inject lateinit var smartListeningManager: com.example.mpod.playback.SmartListeningManager

    override fun onCreate() {
        super.onCreate()
        smartListeningManager.startObserving()
    }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .okHttpClient(okHttpClient)
            .build()
}
