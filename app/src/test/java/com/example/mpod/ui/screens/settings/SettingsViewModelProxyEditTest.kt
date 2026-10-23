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
 * Fix: an optimistic update applies the new server to [SettingsUiState.proxyServers] and
 * [SettingsUiState.activeProxyServer] synchronously before the coroutine is launched,
 * so the name is visible instantly on popBackStack and SettingsScreen. If the DataStore write
 * fails, the state is rolled back.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelProxyEditTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var store: FakeAppSettingsStore
    private lateinit var viewModel: SettingsViewModel

    private val existingServer = ProxyServerConfig(
        id = "server-1",
        name = "Original Name",
        host = "10.0.0.1",
        port = 1080
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        store = FakeAppSettingsStore(
            AppSettings(
                proxyServers = listOf(existingServer),
                selectedProxyServerId = existingServer.id
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
    // Core regression: optimistic state update before DataStore write
    // ----------------------------------------------------------------

    /**
     * The primary regression test.
     *
     * With StandardTestDispatcher, coroutines launched on viewModelScope are queued on
     * the scheduler. Calling [updateProxyServer] returns synchronously before the background
     * coroutine executes. The optimistic update must already be present in both proxyServers
     * and activeProxyServer in [SettingsViewModel.state].
     */
    @Test
    fun updateProxyServer_nameIsVisibleInStateBeforeDataStoreCoroutineCompletes() {
        val result = viewModel.updateProxyServer(
            id = existingServer.id,
            name = "New Proxy Name",
            host = existingServer.host,
            port = existingServer.port
        )

        assertTrue("Expected success result", result.isSuccess)

        // Optimistic update in proxyServers list
        val nameInState = viewModel.state.value.proxyServers
            .firstOrNull { it.id == existingServer.id }
            ?.name

        assertEquals(
            "Name must be updated in proxyServers immediately (optimistic update)",
            "New Proxy Name",
            nameInState
        )

        // Optimistic update in activeProxyServer
        assertEquals(
            "activeProxyServer must be updated optimistically when the active server is edited",
            "New Proxy Name",
            viewModel.state.value.activeProxyServer?.name
        )

        // DataStore has not updated yet
        assertEquals(
            "DataStore must still have old name before scheduler runs",
            "Original Name",
            store.stateFlow.value.proxyServers.first { it.id == existingServer.id }.name
        )

        // Advance scheduler so the background DataStore coroutine runs
        testDispatcher.scheduler.runCurrent()

        // Persisted in store
        assertEquals(
            "Name must be persisted in store after scheduler runs",
            "New Proxy Name",
            store.stateFlow.value.proxyServers.first { it.id == existingServer.id }.name
        )
    }

    // ----------------------------------------------------------------
    // Name persisted after the DataStore coroutine completes
    // ----------------------------------------------------------------

    @Test
    fun updateProxyServer_nameIsPersisted_afterDataStoreCompletes() {
        viewModel.updateProxyServer(
            id = existingServer.id,
            name = "Persisted Name",
            host = existingServer.host,
            port = existingServer.port
        )

        testDispatcher.scheduler.runCurrent()

        val storedName = store.stateFlow.value.proxyServers
            .firstOrNull { it.id == existingServer.id }
            ?.name
        assertEquals("Name must be persisted in store", "Persisted Name", storedName)
        assertEquals(
            "Name in state must match persisted name",
            "Persisted Name",
            viewModel.state.value.proxyServers.first { it.id == existingServer.id }.name
        )
    }

    // ----------------------------------------------------------------
    // Rollback when DataStore write fails
    // ----------------------------------------------------------------

    @Test
    fun updateProxyServer_rollsBackState_whenDataStoreWriteFails() {
        val failingStore = object : FakeAppSettingsStore(
            AppSettings(
                proxyServers = listOf(existingServer),
                selectedProxyServerId = existingServer.id
            )
        ) {
            override suspend fun updateProxyServer(server: ProxyServerConfig) {
                throw java.io.IOException("Disk I/O error")
            }
        }
        val failingVm = buildViewModel(failingStore)
        testDispatcher.scheduler.runCurrent()

        val result = failingVm.updateProxyServer(
            id = existingServer.id,
            name = "Failed Name",
            host = existingServer.host,
            port = existingServer.port
        )
        assertTrue(result.isSuccess)

        // Optimistic state before scheduler runs
        assertEquals("Failed Name", failingVm.state.value.proxyServers.first { it.id == existingServer.id }.name)
        assertEquals("Failed Name", failingVm.state.value.activeProxyServer?.name)

        // Run scheduler -> DataStore throws IOException -> catches and rolls back
        testDispatcher.scheduler.runCurrent()

        assertEquals(
            "proxyServers must roll back to original state on failure",
            "Original Name",
            failingVm.state.value.proxyServers.first { it.id == existingServer.id }.name
        )
        assertEquals(
            "activeProxyServer must roll back to original state on failure",
            "Original Name",
            failingVm.state.value.activeProxyServer?.name
        )
        assertTrue(
            "proxyMessage must report the failure",
            failingVm.state.value.proxyMessage?.contains("Failed to update proxy server") == true
        )

        failingVm.viewModelScope.cancel()
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
    fun updateProxyServer_trimmedName_isSavedWithoutLeadingTrailingSpaces() {
        viewModel.updateProxyServer(
            id = existingServer.id,
            name = "  Trimmed Name  ",
            host = existingServer.host,
            port = existingServer.port
        )

        testDispatcher.scheduler.runCurrent()

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
