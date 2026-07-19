package com.example.mpod.ui.components

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.mpod.data.network.MpodApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit

class AddPodcastViewModelTest {
    private lateinit var server: MockWebServer
    private lateinit var context: Context
    private lateinit var viewModel: AddPodcastViewModel

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        context = ApplicationProvider.getApplicationContext()
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MpodApi::class.java)
        viewModel = AddPodcastViewModel(context, api)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun importResultKeepsExactCountsAndRefreshesOnce() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"success":true,"imported":3,"skipped":2}""")
        )
        var refreshes = 0

        viewModel.importOpml(opmlUri()) { refreshes += 1 }
        val result = awaitState { it.importResult != null }.importResult

        assertEquals(OpmlImportResultUi(imported = 3, skipped = 2), result)
        assertEquals(1, refreshes)
        assertEquals(1, server.requestCount)
        assertEquals("/api/podcasts/import-opml", server.takeRequest().path)
    }

    @Test
    fun pendingImportIgnoresDuplicateSubmission() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"success":true,"imported":1,"skipped":0}""")
                .setBodyDelay(750, TimeUnit.MILLISECONDS)
        )
        val uri = opmlUri()

        viewModel.importOpml(uri) {}
        awaitState { it.isSubmitting }
        viewModel.importOpml(uri) {}
        awaitState { it.importResult != null }

        assertEquals(1, server.requestCount)
    }

    private fun opmlUri(): Uri {
        val file = File(context.cacheDir, "subscriptions-${System.nanoTime()}.opml")
        file.writeText("""<?xml version="1.0"?><opml version="2.0"><body/></opml>""")
        return Uri.fromFile(file)
    }

    private suspend fun awaitState(
        predicate: (AddPodcastUiState) -> Boolean
    ): AddPodcastUiState = withTimeout(5_000) {
        viewModel.state.first(predicate)
    }
}
