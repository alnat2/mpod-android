package com.example.mpod.ui.screens.settings

import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import com.example.mpod.data.network.MpodApi
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class SettingsViewModelTest {
    private lateinit var server: MockWebServer
    private lateinit var api: MpodApi
    private var settingsCode = 200
    private var schedulerFailureFromCall = Int.MAX_VALUE
    private var proxyCode = 200
    private var patchCode = 200
    private var exportCode = 200
    private var exportBody = "<opml><body><outline text=\"Planet Money\"/></body></opml>"
    private var exportDelayMillis = 0L
    private var dailyRefreshTime = "03:00"
    private var proxyEnabled = true
    private var proxyConfigured = true
    private var proxyStatus = "ok"
    private var proxyError: String? = null
    private val schedulerCalls = AtomicInteger()
    private val patchCalls = AtomicInteger()
    private val exportCalls = AtomicInteger()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                return when {
                    request.method == "GET" && request.path == "/api/settings" -> {
                        if (settingsCode != 200) errorResponse(settingsCode, "Settings unavailable")
                        else settingsResponse()
                    }
                    request.method == "GET" && request.path == "/api/jobs/status" -> {
                        val call = schedulerCalls.incrementAndGet()
                        if (call >= schedulerFailureFromCall) {
                            errorResponse(500, "Refresh status unavailable")
                        } else {
                            MockResponse().setResponseCode(200).setBody(
                                """{"scheduler":{"state":"completed","lastRunAt":"2026-07-19T08:30:00Z"}}"""
                            )
                        }
                    }
                    request.method == "GET" && request.path == "/api/proxy/status" -> {
                        if (proxyCode != 200) errorResponse(proxyCode, "Proxy status unavailable")
                        else proxyResponse()
                    }
                    request.method == "PATCH" && request.path == "/api/settings" -> {
                        patchCalls.incrementAndGet()
                        if (patchCode != 200) {
                            errorResponse(patchCode, "Save failed")
                        } else {
                            val body = request.body.readUtf8()
                            Regex(""""dailyRefreshTime":"([^"]+)"""")
                                .find(body)?.groupValues?.get(1)?.let { dailyRefreshTime = it }
                            Regex(""""proxyEnabled":(true|false)""")
                                .find(body)?.groupValues?.get(1)?.toBooleanStrict()
                                ?.let { proxyEnabled = it }
                            settingsResponse()
                        }
                    }
                    request.method == "GET" && request.path == "/api/podcasts/export-opml" -> {
                        exportCalls.incrementAndGet()
                        if (exportCode != 200) {
                            MockResponse().setResponseCode(exportCode).setBody(
                                """{"error":{"code":"EXPORT_FAILED","message":"Export unavailable"}}"""
                            )
                        } else {
                            MockResponse().setResponseCode(200)
                                .setBody(exportBody)
                                .setBodyDelay(exportDelayMillis, TimeUnit.MILLISECONDS)
                        }
                    }
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
        api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MpodApi::class.java)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun proxyFailureDoesNotHideRefreshOrLocalSettings() = runBlocking {
        proxyCode = 500
        val viewModel = newViewModel()

        val state = viewModel.awaitState { !it.isLoading }

        assertNull(state.refreshErrorMessage)
        assertEquals("Status: completed · last refresh 2026-07-19 08:30", state.schedulerStatusText)
        assertEquals("Proxy status unavailable", state.proxyErrorMessage)
        assertTrue(state.hasConfirmedSettings)
    }

    @Test
    fun schedulerFailureDoesNotHideProxyState() = runBlocking {
        schedulerFailureFromCall = 1
        val viewModel = newViewModel()

        val state = viewModel.awaitState { !it.isLoading }

        assertEquals("Refresh status unavailable", state.refreshErrorMessage)
        assertNull(state.proxyErrorMessage)
        assertEquals("Current IP: 88.216.223.105 · Geo: Sweden", state.proxyStatusText)
    }

    @Test
    fun settingsFailureScopesBothDependentSections() = runBlocking {
        settingsCode = 500
        val viewModel = newViewModel()

        val state = viewModel.awaitState { !it.isLoading }

        assertEquals("Settings unavailable", state.refreshErrorMessage)
        assertEquals("Settings unavailable", state.proxyErrorMessage)
        assertFalse(state.hasConfirmedSettings)
    }

    @Test
    fun unchangedRefreshTimeDoesNotWrite() = runBlocking {
        val viewModel = newViewModel()
        viewModel.awaitState { !it.isLoading }

        viewModel.saveDailyRefreshTime("03:00")
        delay(200)

        assertEquals(0, patchCalls.get())
    }

    @Test
    fun firstResumeDoesNotDuplicateInitialLoadAndLaterResumeReloads() = runBlocking {
        val viewModel = newViewModel()
        viewModel.awaitState { !it.isLoading }
        assertEquals(3, server.requestCount)

        viewModel.onResume()
        delay(200)
        assertEquals(3, server.requestCount)

        viewModel.onResume()
        viewModel.awaitState { schedulerCalls.get() == 2 && !it.isLoading }
        assertEquals(6, server.requestCount)
    }

    @Test
    fun failedRefreshSaveKeepsConfirmedValueAndCanRetryExactWrite() = runBlocking {
        val viewModel = newViewModel()
        viewModel.awaitState { !it.isLoading }
        patchCode = 500

        viewModel.saveDailyRefreshTime("04:30")
        val failed = viewModel.awaitState { it.refreshErrorMessage == "Save failed" }

        assertEquals("03:00", failed.dailyRefreshTime)
        patchCode = 200
        viewModel.saveDailyRefreshTime("04:30")
        val recovered = viewModel.awaitState { it.dailyRefreshTime == "04:30" }
        assertNull(recovered.refreshErrorMessage)
        assertEquals(2, patchCalls.get())
    }

    @Test
    fun confirmedRefreshTimeSurvivesStatusReloadFailure() = runBlocking {
        val viewModel = newViewModel()
        viewModel.awaitState { !it.isLoading }
        schedulerFailureFromCall = 2

        viewModel.saveDailyRefreshTime("04:30")
        val state = viewModel.awaitState {
            it.refreshErrorMessage == "Refresh time was saved, but status could not be refreshed."
        }

        assertEquals("04:30", state.dailyRefreshTime)
        assertFalse(state.isSavingRefreshTime)
    }

    @Test
    fun unconfiguredProxyCannotAppearEnabled() = runBlocking {
        proxyConfigured = false
        val state = newViewModel().awaitState { !it.isLoading }

        assertFalse(state.proxyConfigured)
        assertFalse(state.proxyEnabled)
        assertEquals("Proxy is not configured.", state.proxyStatusText)
    }

    @Test
    fun configuredProxyToggleUsesAuthoritativeSettingAndStatus() = runBlocking {
        val viewModel = newViewModel()
        viewModel.awaitState { !it.isLoading }

        viewModel.setProxyEnabled(false)
        val state = viewModel.awaitState { !it.isSavingProxy && !it.proxyEnabled }

        assertEquals("Proxy is off", state.proxyStatusText)
        assertEquals(1, patchCalls.get())
    }

    @Test
    fun proxyUnknownAndErrorStatesAreTruthful() = runBlocking {
        proxyStatus = "unknown"
        val viewModel = newViewModel()
        val unknown = viewModel.awaitState { !it.isLoading }
        assertEquals("Proxy status is unknown.", unknown.proxyStatusText)

        proxyStatus = "error"
        proxyError = "SOCKS5 handshake failed"
        viewModel.refresh()
        val failed = viewModel.awaitState { !it.isLoading && it.proxyStatusText != unknown.proxyStatusText }
        assertEquals("SOCKS5 handshake failed", failed.proxyStatusText)
    }

    @Test
    fun exportCancellationIsANoOp() = runBlocking {
        val viewModel = newViewModel()
        viewModel.awaitState { !it.isLoading }

        viewModel.exportOpml(null)
        delay(100)

        assertEquals(0, exportCalls.get())
        assertNull(viewModel.state.value.exportMessage)
        assertNull(viewModel.state.value.errorMessage)
    }

    @Test
    fun successfulExportWritesExactBackendDocument() = runBlocking {
        val viewModel = newViewModel()
        viewModel.awaitState { !it.isLoading }
        val destination = temporaryExportFile("successful-export.opml")

        viewModel.exportOpml(Uri.fromFile(destination))
        val state = viewModel.awaitState { it.exportMessage == "OPML export saved." }

        assertFalse(state.isExportingOpml)
        assertNull(state.errorMessage)
        assertEquals(exportBody, destination.readText())
        assertEquals(1, exportCalls.get())
    }

    @Test
    fun failedExportKeepsExistingDestinationAndShowsParsedError() = runBlocking {
        exportCode = 500
        val viewModel = newViewModel()
        viewModel.awaitState { !it.isLoading }
        val destination = temporaryExportFile("failed-export.opml").apply {
            writeText("existing content")
        }

        viewModel.exportOpml(Uri.fromFile(destination))
        val state = viewModel.awaitState { it.errorMessage == "Export unavailable" }

        assertFalse(state.isExportingOpml)
        assertNull(state.exportMessage)
        assertEquals("existing content", destination.readText())
    }

    @Test
    fun destinationWriteFailureDoesNotReportExportSuccess() = runBlocking {
        val viewModel = newViewModel()
        viewModel.awaitState { !it.isLoading }

        viewModel.exportOpml(Uri.parse("content://missing.mpod.provider/export.opml"))
        val state = viewModel.awaitState { !it.isExportingOpml && it.errorMessage != null }

        assertNull(state.exportMessage)
        assertEquals(1, exportCalls.get())
    }

    @Test
    fun duplicateExportWhileRequestIsPendingIsBlocked() = runBlocking {
        exportDelayMillis = 300
        val viewModel = newViewModel()
        viewModel.awaitState { !it.isLoading }
        val first = temporaryExportFile("first-export.opml")
        val second = temporaryExportFile("second-export.opml")

        viewModel.exportOpml(Uri.fromFile(first))
        viewModel.exportOpml(Uri.fromFile(second))
        viewModel.awaitState { it.exportMessage == "OPML export saved." }

        assertEquals(1, exportCalls.get())
        assertEquals(exportBody, first.readText())
        assertFalse(second.exists())
    }

    @Test
    fun exportCompletionSurvivesConcurrentResumeReload() = runBlocking {
        exportDelayMillis = 200
        val viewModel = newViewModel()
        viewModel.awaitState { !it.isLoading }
        viewModel.onResume()
        val destination = temporaryExportFile("resume-export.opml")

        viewModel.exportOpml(Uri.fromFile(destination))
        viewModel.onResume()
        val state = viewModel.awaitState {
            schedulerCalls.get() == 2 && !it.isLoading &&
                it.exportMessage == "OPML export saved."
        }

        assertFalse(state.isExportingOpml)
        assertNull(state.errorMessage)
        assertEquals(exportBody, destination.readText())
        assertEquals(1, exportCalls.get())
    }

    private fun newViewModel(): SettingsViewModel {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return SettingsViewModel(context, api)
    }

    private fun temporaryExportFile(name: String): File {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return File(context.cacheDir, name).also { it.delete() }
    }

    private suspend fun SettingsViewModel.awaitState(
        predicate: (SettingsUiState) -> Boolean
    ): SettingsUiState = withTimeout(5_000) {
        state.first(predicate)
    }

    private fun settingsResponse(): MockResponse = MockResponse().setResponseCode(200).setBody(
        """{"settings":{"dailyRefreshTime":"$dailyRefreshTime","playbackSpeed":"Speed 1x","proxyEnabled":$proxyEnabled,"proxyConfigured":$proxyConfigured,"appBuild":"test-build"}}"""
    )

    private fun proxyResponse(): MockResponse {
        val errorJson = proxyError?.let { "\"$it\"" } ?: "null"
        return MockResponse().setResponseCode(200).setBody(
            """{"proxy":{"status":"$proxyStatus","externalIp":"88.216.223.105","country":"Sweden","error":$errorJson,"proxyConfigured":$proxyConfigured}}"""
        )
    }

    private fun errorResponse(code: Int, message: String): MockResponse =
        MockResponse().setResponseCode(code).setBody(message)
}
