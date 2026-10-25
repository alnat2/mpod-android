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
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito

/**
 * Tests for adding proxy servers in [SettingsViewModel.addProxyServer].
 *
 * Verifies:
 * 1. Existing servers are retained and not lost when adding a new server.
 * 2. Optimistic UI update: the new server is immediately visible in [SettingsViewModel.state]
 *    before the background DataStore coroutine finishes.
 * 3. Correct active server resolution (first server becomes active; existing active server retained).
 * 4. State rollback if the DataStore write fails.
 * 5. Input validation (blank name, blank host, invalid ports).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelProxyAddTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var store: FakeAppSettingsStore
    private lateinit var viewModel: SettingsViewModel

    private val server1 = ProxyServerConfig(
        id = "server-1",
        name = "First Server",
        host = "10.0.0.1",
        port = 1080
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        store = FakeAppSettingsStore(
            AppSettings(
                proxyServers = listOf(server1),
                selectedProxyServerId = server1.id
            )
        )
        viewModel = buildViewModel(store)
        testDispatcher.scheduler.runCurrent()
    }

    @After
    fun tearDown() {
        viewModel.viewModelScope.cancel()
        Dispatchers.resetMain()
    }

    // ----------------------------------------------------------------
    // Core preservation test: adding new server preserves existing ones
    // ----------------------------------------------------------------

    @Test
    fun addProxyServer_preservesExistingServers_inStateAndStore() {
        val result = viewModel.addProxyServer(
            name = "Second Server",
            host = "10.0.0.2",
            port = 8080
        )
        assertTrue("Expected addProxyServer to succeed", result.isSuccess)

        // Optimistically present in state
        val stateServers = viewModel.state.value.proxyServers
        assertEquals("Both existing and new server must be in state", 2, stateServers.size)
        assertEquals("First Server", stateServers[0].name)
        assertEquals("Second Server", stateServers[1].name)

        // Run scheduler so DataStore coroutine completes
        testDispatcher.scheduler.runCurrent()

        // Check store persistence
        val storedServers = store.stateFlow.value.proxyServers
        assertEquals("Both existing and new server must be in store", 2, storedServers.size)
        assertEquals("First Server", storedServers[0].name)
        assertEquals("Second Server", storedServers[1].name)
    }

    // ----------------------------------------------------------------
    // Optimistic update before DataStore write
    // ----------------------------------------------------------------

    @Test
    fun addProxyServer_optimisticallyUpdatesState_beforeDataStoreCompletes() {
        viewModel.addProxyServer(
            name = "Second Server",
            host = "10.0.0.2",
            port = 8080
        )

        // State has 2 servers immediately
        assertEquals(2, viewModel.state.value.proxyServers.size)
        assertEquals("Second Server", viewModel.state.value.proxyServers[1].name)

        // Store still only has 1 server before scheduler runs
        assertEquals(1, store.stateFlow.value.proxyServers.size)

        // Advance scheduler
        testDispatcher.scheduler.runCurrent()

        // Store now also has 2 servers
        assertEquals(2, store.stateFlow.value.proxyServers.size)
    }

    // ----------------------------------------------------------------
    // Active server resolution
    // ----------------------------------------------------------------

    @Test
    fun addProxyServer_whenEmpty_becomesActiveProxyServer() {
        val emptyStore = FakeAppSettingsStore(AppSettings(proxyServers = emptyList()))
        val emptyVm = buildViewModel(emptyStore)
        testDispatcher.scheduler.runCurrent()

        emptyVm.addProxyServer(
            name = "Sole Server",
            host = "192.168.1.50",
            port = 9050
        )

        // Sole server immediately becomes active
        assertNotNull(emptyVm.state.value.activeProxyServer)
        assertEquals("Sole Server", emptyVm.state.value.activeProxyServer?.name)

        testDispatcher.scheduler.runCurrent()
        assertEquals("Sole Server", emptyVm.state.value.activeProxyServer?.name)

        emptyVm.viewModelScope.cancel()
    }

    @Test
    fun addProxyServer_whenNonEmpty_retainsExistingActiveServer() {
        viewModel.addProxyServer(
            name = "Second Server",
            host = "10.0.0.2",
            port = 8080
        )

        // Active server remains server1
        assertEquals("First Server", viewModel.state.value.activeProxyServer?.name)

        testDispatcher.scheduler.runCurrent()
        assertEquals("First Server", viewModel.state.value.activeProxyServer?.name)
    }

    // ----------------------------------------------------------------
    // Rollback on DataStore failure
    // ----------------------------------------------------------------

    @Test
    fun addProxyServer_rollsBackState_whenDataStoreWriteFails() {
        val failingStore = object : FakeAppSettingsStore(
            AppSettings(
                proxyServers = listOf(server1),
                selectedProxyServerId = server1.id
            )
        ) {
            override suspend fun addProxyServer(server: ProxyServerConfig): ProxyServerConfig {
                throw java.io.IOException("Disk full")
            }
        }
        val failingVm = buildViewModel(failingStore)
        testDispatcher.scheduler.runCurrent()

        val result = failingVm.addProxyServer(
            name = "Doomed Server",
            host = "10.0.0.99",
            port = 1080
        )
        assertTrue(result.isSuccess)

        // Optimistic state has 2 servers
        assertEquals(2, failingVm.state.value.proxyServers.size)

        // Advance scheduler -> exception thrown -> rollback
        testDispatcher.scheduler.runCurrent()

        assertEquals("State must roll back to 1 server on failure", 1, failingVm.state.value.proxyServers.size)
        assertEquals("First Server", failingVm.state.value.proxyServers[0].name)
        assertTrue(
            "proxyMessage must report the failure",
            failingVm.state.value.proxyMessage?.contains("Failed to add proxy server") == true
        )

        failingVm.viewModelScope.cancel()
    }

    // ----------------------------------------------------------------
    // Input validation
    // ----------------------------------------------------------------

    @Test
    fun addProxyServer_blankName_returnsFailureAndDoesNotChangeState() {
        val result = viewModel.addProxyServer(
            name = "   ",
            host = "10.0.0.2",
            port = 1080
        )
        assertTrue(result.isFailure)
        assertEquals(1, viewModel.state.value.proxyServers.size)
    }

    @Test
    fun addProxyServer_blankHost_returnsFailureAndDoesNotChangeState() {
        val result = viewModel.addProxyServer(
            name = "Valid Name",
            host = "   ",
            port = 1080
        )
        assertTrue(result.isFailure)
        assertEquals(1, viewModel.state.value.proxyServers.size)
    }

    @Test
    fun addProxyServer_invalidPort_returnsFailureAndDoesNotChangeState() {
        val resultLow = viewModel.addProxyServer(name = "Valid", host = "10.0.0.2", port = 0)
        assertTrue(resultLow.isFailure)

        val resultHigh = viewModel.addProxyServer(name = "Valid", host = "10.0.0.2", port = 65536)
        assertTrue(resultHigh.isFailure)

        assertEquals(1, viewModel.state.value.proxyServers.size)
    }

    @Test
    fun addProxyServer_trimmedFields_savedWithoutSurroundingSpaces() {
        viewModel.addProxyServer(
            name = "  Padded Name  ",
            host = "  10.0.0.3  ",
            port = 1080
        )
        testDispatcher.scheduler.runCurrent()

        val added = store.stateFlow.value.proxyServers.last()
        assertEquals("Padded Name", added.name)
        assertEquals("10.0.0.3", added.host)
    }

    // ----------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------

    private fun buildViewModel(appStore: FakeAppSettingsStore): SettingsViewModel {
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
