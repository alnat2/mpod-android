package com.example.mpod.ui.screens.settings

import android.content.Context
import com.example.mpod.data.local.preferences.AppSettings
import com.example.mpod.data.local.preferences.FakeAppSettingsStore
import com.example.mpod.data.network.IpGeoService
import com.example.mpod.data.network.ProxyHealthService
import com.example.mpod.data.network.ProxyHttpClientFactory
import com.example.mpod.data.repository.PodcastRepository
import com.example.mpod.playback.AutoRefreshScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelMiniPlayerTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var store: FakeAppSettingsStore
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        store = FakeAppSettingsStore(AppSettings(isMiniPlayerEnabled = true))
        val context = Mockito.mock(Context::class.java)
        val podcastRepo = Mockito.mock(PodcastRepository::class.java)
        val scheduler = Mockito.mock(AutoRefreshScheduler::class.java)
        val proxyFactory = ProxyHttpClientFactory()
        val noOpIpGeo = object : IpGeoService(proxyFactory) {
            override suspend fun fetchCurrentIpGeo(client: OkHttpClient?): Result<Pair<String, String>> =
                Result.success("0.0.0.0" to "XX")
        }
        val health = Mockito.mock(ProxyHealthService::class.java)

        viewModel = SettingsViewModel(
            context = context,
            appSettingsDataStore = store,
            podcastRepository = podcastRepo,
            autoRefreshScheduler = scheduler,
            proxyHttpClientFactory = proxyFactory,
            ipGeoService = noOpIpGeo,
            proxyHealthService = health
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialMiniPlayerState_isEnabledByDefault() = runTest(testDispatcher) {
        advanceUntilIdle()
        assertTrue(viewModel.state.value.isMiniPlayerEnabled)
    }

    @Test
    fun setMiniPlayerEnabled_updatesStateAndStore() = runTest(testDispatcher) {
        advanceUntilIdle()
        assertTrue(viewModel.state.value.isMiniPlayerEnabled)

        viewModel.setMiniPlayerEnabled(false)
        advanceUntilIdle()
        assertFalse(viewModel.state.value.isMiniPlayerEnabled)
        assertFalse(store.stateFlow.value.isMiniPlayerEnabled)

        viewModel.setMiniPlayerEnabled(true)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.isMiniPlayerEnabled)
        assertTrue(store.stateFlow.value.isMiniPlayerEnabled)
    }
}
