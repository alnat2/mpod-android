package com.example.mpod.data.network

import com.example.mpod.data.local.preferences.AppSettings
import com.example.mpod.data.local.preferences.AppSettingsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Test
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI

class ProxyHttpClientFactoryDynamicTest {

    @Test
    fun createClientReturnsSameSingletonInstance() {
        val factory = ProxyHttpClientFactory()
        val client1 = factory.createClient()
        val client2 = factory.createClient()
        assertSame(client1, client2)
    }

    @Test
    fun singleDynamicClientPicksUpProxyChangesAcrossAllProtocols() {
        val factory = ProxyHttpClientFactory()
        val client = factory.createClient()

        // 1. Initial Direct
        factory.updateProxy(
            AppSettings(
                isProxyEnabled = false,
                proxyHost = "",
                proxyPort = 1080,
                proxyType = "SOCKS5"
            )
        )
        val directRoute = client.proxySelector.select(URI.create("https://example.com/feed.xml"))
        assertEquals(listOf(Proxy.NO_PROXY), directRoute)

        // 2. Switch to SOCKS5
        factory.updateProxy(
            AppSettings(
                isProxyEnabled = true,
                proxyHost = "127.0.0.1",
                proxyPort = 1080,
                proxyType = "SOCKS5"
            )
        )
        val socksRoute = client.proxySelector.select(URI.create("https://example.com/feed.xml"))
        assertEquals(1, socksRoute.size)
        assertEquals(Proxy.Type.SOCKS, socksRoute[0].type())
        assertEquals(1080, (socksRoute[0].address() as InetSocketAddress).port)

        // 3. Switch to HTTP
        factory.updateProxy(
            AppSettings(
                isProxyEnabled = true,
                proxyHost = "proxy.example.com",
                proxyPort = 8080,
                proxyType = "HTTP"
            )
        )
        val httpRoute = client.proxySelector.select(URI.create("https://example.com/feed.xml"))
        assertEquals(1, httpRoute.size)
        assertEquals(Proxy.Type.HTTP, httpRoute[0].type())
        assertEquals("proxy.example.com", (httpRoute[0].address() as InetSocketAddress).hostName)
        assertEquals(8080, (httpRoute[0].address() as InetSocketAddress).port)

        // 4. Switch back to Direct
        factory.updateProxy(
            AppSettings(
                isProxyEnabled = false,
                proxyHost = "",
                proxyPort = 1080,
                proxyType = "SOCKS5"
            )
        )
        val backToDirectRoute = client.proxySelector.select(URI.create("https://example.com/feed.xml"))
        assertEquals(listOf(Proxy.NO_PROXY), backToDirectRoute)
    }

    @Test
    fun productionConfigurePath_subscribesToDataStoreAndAppliesInitialAndSubsequentChanges() = runBlocking {
        val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val fakeDataStore = FakeAppSettingsDataStore(
            AppSettings(
                isProxyEnabled = true,
                proxyHost = "10.0.0.1",
                proxyPort = 9050,
                proxyType = "SOCKS5"
            )
        )

        val factory = ProxyHttpClientFactory()
        factory.configure(fakeDataStore, testScope)

        val client = factory.createClient()

        // Immediate first select must see initial DataStore proxy setting
        val initialSelected = client.proxySelector.select(URI.create("https://example.com/feed.xml"))
        assertEquals(Proxy.Type.SOCKS, initialSelected[0].type())
        assertEquals(9050, (initialSelected[0].address() as InetSocketAddress).port)

        // Update settings in flow and allow flow collector to process
        fakeDataStore.stateFlow.value = AppSettings(
            isProxyEnabled = true,
            proxyHost = "http-proxy.org",
            proxyPort = 3128,
            proxyType = "HTTP"
        )
        delay(100)

        // Subsequent select sees the updated proxy configuration
        val updatedSelected = client.proxySelector.select(URI.create("https://example.com/feed.xml"))
        assertEquals(Proxy.Type.HTTP, updatedSelected[0].type())
        assertEquals("http-proxy.org", (updatedSelected[0].address() as InetSocketAddress).hostName)
        assertEquals(3128, (updatedSelected[0].address() as InetSocketAddress).port)
    }

    @Test
    fun directSaveTakesEffectImmediatelyWithoutDelay() {
        val factory = ProxyHttpClientFactory()
        val client = factory.createClient()

        factory.updateProxy(
            AppSettings(
                isProxyEnabled = true,
                proxyHost = "fast-proxy.lan",
                proxyPort = 8888,
                proxyType = "HTTP"
            )
        )

        // Next immediate request sees updated proxy synchronously without delay
        val selected = client.proxySelector.select(URI.create("https://example.com/stream.mp3"))
        assertEquals(Proxy.Type.HTTP, selected[0].type())
        assertEquals("fast-proxy.lan", (selected[0].address() as InetSocketAddress).hostName)
        assertEquals(8888, (selected[0].address() as InetSocketAddress).port)
    }

    @Test
    fun staticFactoryHelpers_workAsExpected() {
        val direct = ProxyHttpClientFactory.createDirectOkHttpClient()
        val socks = ProxyHttpClientFactory.createOkHttpClient(
            proxyEnabled = true,
            proxyHost = "127.0.0.1",
            proxyPort = 1080,
            proxyType = "SOCKS5"
        )
        val http = ProxyHttpClientFactory.createOkHttpClient(
            proxyEnabled = true,
            proxyHost = "127.0.0.1",
            proxyPort = 8080,
            proxyType = "HTTP"
        )

        assertNotNull(direct)
        assertEquals(Proxy.Type.SOCKS, socks.proxy?.type())
        assertEquals(Proxy.Type.HTTP, http.proxy?.type())
    }

    class FakeAppSettingsDataStore(
        initial: AppSettings = AppSettings()
    ) : AppSettingsDataStore() {
        val stateFlow = MutableStateFlow(initial)
        override val settingsFlow: Flow<AppSettings> = stateFlow
    }
}
