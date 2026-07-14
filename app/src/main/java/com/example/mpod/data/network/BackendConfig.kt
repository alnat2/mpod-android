package com.example.mpod.data.network

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackendConfig @Inject constructor() {
    val address: String
        get() = DEFAULT_ADDRESS

    val baseUrl: String
        get() = DEFAULT_BASE_URL

    companion object {
        const val DEFAULT_ADDRESS = "192.168.0.222:5051"
        private const val DEFAULT_BASE_URL = "http://$DEFAULT_ADDRESS/"
    }
}
