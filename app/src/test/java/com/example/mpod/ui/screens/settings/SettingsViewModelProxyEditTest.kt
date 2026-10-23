package com.example.mpod.ui.screens.settings

import android.content.Context
import androidx.lifecycle.viewModelScope
import com.example.mpod.data.local.preferences.AppSettings
import com.example.mpod.data.local.preferences.FakeAppSettingsStore
import com.example.mpod.data.local.preferences.ProxyServerConfig
import com.example.mpod.data.network.IpGeoService
import com.example.mpod.data.network.ProxyCheckResult
import com.example.mpod.data.network.ProxyHealthService
import com.example.mpod.data.network.ProxyHttpClientFactory
import com.example.mpod.data.repository.PodcastRepository
import com.example.mpod.playback.AutoRefreshScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito


/**
 * Regression tests for the proxy server name not being saved when editing.
 *
 * Bug: [SettingsViewModel.updateProxyServer] launched a coroutine to write to DataStore and
 * returned Result.success synchronously before the coroutine finished. EditProxyScreen called
 * popBackStack immediately on success. The in-memory state ([SettingsViewModel.state]) was only
 * updated once the DataStore Flow emitted — which could happen after the next screen opened,
 * causing the old name to appear.
 *
 * Fix: an optimistic update applies the new server to [SettingsUiState.proxyServers] synchronously
 * before the coroutine is launched, so the name is visible instantly on popBackStack.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelProxyEditTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var store: SlowFakeStore
    private lateinit var viewModel: SettingsViewModel

    private val existingServer = ProxyServerConfig(
        id = "server-1",
        name = "Original Name",
        host = "10.0.0.1",
        port = 1080
    )

    @Before
    fun setUp() {
        runBlocking {
            Dispatchers.setMain(testDispatcher)
            store = SlowFakeStore(
                AppSettings(
                    proxyServers = listOf(existingServer),
                    selectedProxyServerId = existingServer.id
                )
            )
            viewModel = buildViewModel(store)
            withTimeout(3_000) { viewModel.state.first { it.proxyServers.isNotEmpty() } }
        }
    }

    @After
    fun tearDown() {
        viewModel.viewModelScope.cancel()
        Dispatchers.resetMain()
    }

    // ----------------------------------------------------------------
    // Core regression: optimistic state update before DataStore write
    // ----------------------------------------------------------------

    /**
     * The primary regression test.
     *
     * The DataStore fake is configured to block its write indefinitely so we can inspect
     * [SettingsViewModel.state] synchronously — after [updateProxyServer] returns but before
     * the async coroutine finishes. The optimistic update must have already put the new name
     * into state at this point.
     *
     * Without the fix: state still shows "Original Name" here.
     * With the fix: state shows "New Proxy Name" immediately.
     */
    @Test
    fun updateProxyServer_nameIsVisibleInStateBeforeDataStoreCoroutineCompletes() {
        store.blockOnNextUpdate = true

        val result = viewModel.updateProxyServer(
            id = existingServer.id,
            name = "New Proxy Name",
            host = existingServer.host,
            port = existingServer.port
        )

        assertTrue("Expected success result", result.isSuccess)

        val nameInState = viewModel.state.value.proxyServers
            .firstOrNull { it.id == existingServer.id }
            ?.name

        assertEquals(
            "Name must be updated in state immediately (optimistic update), " +
                "even while DataStore write is still pending",
            "New Proxy Name",
            nameInState
        )
    }

    // ----------------------------------------------------------------
    // Name persisted after the DataStore coroutine completes
    // ----------------------------------------------------------------

    @Test
    fun updateProxyServer_nameIsPersisted_afterDataStoreCompletes() = runBlocking {
        viewModel.updateProxyServer(
            id = existingServer.id,
            name = "Persisted Name",
            host = existingServer.host,
            port = existingServer.port
        )

        withTimeout(3_000) {
            viewModel.state.first { state ->
                state.proxyServers.any { it.id == existingServer.id && it.name == "Persisted Name" }
            }
        }

        val storedName = store.stateFlow.value.proxyServers
            .firstOrNull { it.id == existingServer.id }
            ?.name
        assertEquals("Name must be persisted in store", "Persisted Name", storedName)
    }

    // ----------------------------------------------------------------
    // Validation: blank name must not mutate state
    // ----------------------------------------------------------------

    @Test
    fun updateProxyServer_blankName_returnsFailureAndDoesNotChangeState() {
        val result = viewModel.updateProxyServer(
            id = existingServer.id,
            name = "   ",
            host = existingServer.host,
            port = existingServer.port
        )

        assertTrue("Expected failure for blank name", result.isFailure)
        assertEquals(
            "Original name must remain unchanged after validation failure",
            "Original Name",
            viewModel.state.value.proxyServers.firstOrNull { it.id == existingServer.id }?.name
        )
    }

    // ----------------------------------------------------------------
    // Name trimming
    // ----------------------------------------------------------------

    @Test
    fun updateProxyServer_trimmedName_isSavedWithoutLeadingTrailingSpaces() = runBlocking {
        viewModel.updateProxyServer(
            id = existingServer.id,
            name = "  Trimmed Name  ",
            host = existingServer.host,
            port = existingServer.port
        )

        withTimeout(3_000) {
            viewModel.state.first { state ->
                state.proxyServers.any { it.id == existingServer.id && it.name == "Trimmed Name" }
            }
        }

        assertEquals(
            "Trimmed Name",
            viewModel.state.value.proxyServers.first { it.id == existingServer.id }.name
        )
    }

    // ----------------------------------------------------------------
    // Unknown id must not corrupt other servers
    // ----------------------------------------------------------------

    @Test
    fun updateProxyServer_unknownId_doesNotAffectExistingServers() {
        val result = viewModel.updateProxyServer(
            id = "non-existent-id",
            name = "Ghost Server",
            host = "1.2.3.4",
            port = 1080
        )

        assertTrue("Expected success result (id is not validated)", result.isSuccess)
        assertEquals(
            "Existing server name must not change when an unknown id is edited",
            "Original Name",
            viewModel.state.value.proxyServers.firstOrNull { it.id == existingServer.id }?.name
        )
    }

    // ----------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------

    /**
     * FakeAppSettingsStore with an optional suspend gate on [updateProxyServer].
     *
     * When [blockOnNextUpdate] is set to true before calling [SettingsViewModel.updateProxyServer],
     * the store's coroutine parks on [latch] so the test can inspect the ViewModel's in-memory
     * state while the "DataStore write" hasn't finished yet.
     */
    private class SlowFakeStore(initial: AppSettings) : FakeAppSettingsStore(initial) {

        @Volatile var blockOnNextUpdate = false
        private val latch = java.util.concurrent.CountDownLatch(1)

        fun unblockUpdate() = latch.countDown()

        override suspend fun updateProxyServer(server: ProxyServerConfig) {
            if (blockOnNextUpdate) {
                blockOnNextUpdate = false
                delay(50)      // let UnconfinedTestDispatcher yield
                latch.await()  // park until unblockUpdate() is called
            }
            super.updateProxyServer(server)
        }
    }

    private fun buildViewModel(appStore: FakeAppSettingsStore): SettingsViewModel {
        // Context is needed by ProxyHealthService and AutoRefreshScheduler.
        // Both classes guard against null/failed Android APIs gracefully, so a bare mock suffices.
        val mockContext = Mockito.mock(Context::class.java)

        val factory = ProxyHttpClientFactory()

        val noOpHealthService = object : ProxyHealthService(mockContext) {
            override fun isDeviceOnline() = true
            override suspend fun checkProxy(server: ProxyServerConfig): ProxyCheckResult =
                ProxyCheckResult.Success("0.0.0.0", "XX")
        }

        val noOpIpGeo = object : IpGeoService(factory) {
            override suspend fun fetchCurrentIpGeo(client: OkHttpClient?): Result<Pair<String, String>> =
                Result.success("0.0.0.0" to "XX")
        }

        // AutoRefreshScheduler and PodcastRepository are concrete/final classes — JavaProxy
        // only works with interfaces and would throw IllegalArgumentException here.
        // Mockito 5.x uses the inline mock maker by default, which handles concrete classes.
        val noOpScheduler = Mockito.mock(AutoRefreshScheduler::class.java)
        val noOpRepository = Mockito.mock(PodcastRepository::class.java)

        return SettingsViewModel(
            context = mockContext,
            appSettingsDataStore = appStore,
            podcastRepository = noOpRepository,
            autoRefreshScheduler = noOpScheduler,
            proxyHttpClientFactory = factory,
            ipGeoService = noOpIpGeo,
            proxyHealthService = noOpHealthService
        )
    }
}
