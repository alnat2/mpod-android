package com.example.mpod.data.network

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkLoggingTest {
    @Test
    fun releaseClientDoesNotContainHttpLoggingInterceptor() {
        val client = OkHttpClient.Builder()
            .addDebugHttpLogging(enabled = false)
            .build()

        assertTrue(client.interceptors.none { it is HttpLoggingInterceptor })
    }

    @Test
    fun debugClientUsesBasicHttpLogging() {
        val client = OkHttpClient.Builder()
            .addDebugHttpLogging(enabled = true)
            .build()

        val logging = client.interceptors.filterIsInstance<HttpLoggingInterceptor>()
        assertEquals(1, logging.size)
        assertEquals(HttpLoggingInterceptor.Level.BASIC, logging.single().level)
    }
}
