package com.example.mpod.data.network

import android.content.Context
import android.content.ContextWrapper
import com.example.mpod.data.local.preferences.ProxyServerConfig
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI

class ProxyHealthServiceTest {

    private class MockContext : ContextWrapper(null)

    @Test
    fun proxyCheckResult_offlineDeviceReturnsNoInternet() = runBlocking {
        val offlineHealthService = object : ProxyHealthService(MockContext()) {
            override fun isDeviceOnline(): Boolean = false
        }

        val server = ProxyServerConfig(name = "Test Proxy", host = "127.0.0.1", port = 1080)
        val result = offlineHealthService.checkProxy(server)

        assertEquals(ProxyCheckResult.NoInternet, result)
    }

    @Test
    fun dynamicProxySelector_onProxyConnectFailedIsInvoked() {
        val selector = DynamicProxySelector()
        var failedProxy: Proxy? = null
        var failedException: IOException? = null

        selector.onProxyConnectFailed = { proxy, ioe ->
            failedProxy = proxy
            failedException = ioe
        }

        val testProxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress.createUnresolved("127.0.0.1", 1080))
        val settings = com.example.mpod.data.local.preferences.AppSettings(
            isProxyEnabled = true,
            proxyHost = "127.0.0.1",
            proxyPort = 1080
        )
        selector.updateProxy(settings)

        val expectedException = IOException("Connection refused")
        selector.connectFailed(URI.create("https://example.com"), testProxy.address(), expectedException)

        assertEquals(testProxy, failedProxy)
        assertEquals(expectedException, failedException)
    }
}
