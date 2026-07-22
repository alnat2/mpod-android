package com.example.mpod.playback

import com.example.mpod.data.network.MpodApi
import com.example.mpod.data.network.model.PlaybackUpdateRequest
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class ApiPlaybackSyncTransportTest {
    private lateinit var server: MockWebServer
    private lateinit var transport: ApiPlaybackSyncTransport

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MpodApi::class.java)
        transport = ApiPlaybackSyncTransport(api)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun malformedSuccessfulActiveResponseRemainsRetryable() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        assertEquals(SyncAttempt.RetryableFailure, transport.setActive(7))
    }

    @Test
    fun malformedSuccessfulPlaybackResponseRemainsRetryable() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        assertEquals(
            SyncAttempt.RetryableFailure,
            transport.updatePlayback(playbackRequest())
        )
    }

    @Test
    fun malformedOrStaleSpeedConfirmationRemainsRetryable() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"settings":{"playbackSpeed":"Speed 1x"}}"""
            )
        )

        assertEquals(SyncAttempt.RetryableFailure, transport.updateSpeed("Speed 1.5x"))
        assertEquals(SyncAttempt.RetryableFailure, transport.updateSpeed("Speed 1.5x"))
    }

    private fun playbackRequest() = PlaybackUpdateRequest(
        episodeId = 7,
        positionSeconds = 12,
        durationSeconds = 120,
        clientUpdatedAt = "2026-07-22T12:00:00Z"
    )
}
