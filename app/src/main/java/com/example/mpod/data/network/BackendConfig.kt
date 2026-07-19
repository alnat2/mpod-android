package com.example.mpod.data.network

import com.example.mpod.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackendConfig @Inject constructor() {
    val address: String
        get() = BuildConfig.BACKEND_ADDRESS

    val baseUrl: String
        get() = "http://$address/"
}
