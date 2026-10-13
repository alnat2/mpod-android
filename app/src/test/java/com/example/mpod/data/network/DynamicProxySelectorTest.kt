package com.example.mpod.data.network

import com.example.mpod.data.local.preferences.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI

class DynamicProxySelectorTest {

    @Test
    fun directSettingsReturnNoProxy() {
        val settings = AppSettings(
            isProxyEnabled = false,
            proxyHost = "",
            proxyPort = 1080,
            proxyType = "SOCKS5"
        )
        val proxy = DynamicProxySelector.resolveProxy(settings)
        assertEquals(Proxy.NO_PROXY, proxy)
    }

    @Test
    fun socks5SettingsReturnSocksProxy() {
        val settings = AppSettings(
            isProxyEnabled = true,
            proxyHost = "127.0.0.1",
            proxyPort = 1080,
            proxyType = "SOCKS5"
        )
        val proxy = DynamicProxySelector.resolveProxy(settings)
        assertEquals(Proxy.Type.SOCKS, proxy.type())
        val addr = proxy.address() as InetSocketAddress
        assertEquals(1080, addr.port)
        assertTrue(addr.hostName == "127.0.0.1" || addr.hostName == "localhost")
    }

    @Test
    fun httpSettingsReturnHttpProxy() {
        val settings = AppSettings(
            isProxyEnabled = true,
            proxyHost = "proxy.example.com",
            proxyPort = 8080,
            proxyType = "HTTP"
        )
        val proxy = DynamicProxySelector.resolveProxy(settings)
        assertEquals(Proxy.Type.HTTP, proxy.type())
        val addr = proxy.address() as InetSocketAddress
        assertEquals("proxy.example.com", addr.hostName)
        assertEquals(8080, addr.port)
    }

    @Test
    fun enabledProxyWithBlankHost_throwsException_andFallsBackToDirectInSelector() {
        val settings = AppSettings(
            isProxyEnabled = true,
            proxyHost = "   ",
            proxyPort = 1080,
            proxyType = "SOCKS5"
        )
        try {
            DynamicProxySelector.resolveProxy(settings)
            fail("Expected IllegalArgumentException for enabled proxy with blank host")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Proxy host cannot be blank") == true)
        }

        // Test in selector: select() must gracefully fall back to direct, never throw
        val selector = DynamicProxySelector()
        selector.updateProxy(settings)
        val proxies = selector.select(URI.create("https://example.com/feed.xml"))
        assertEquals(listOf(Proxy.NO_PROXY), proxies)
        assertNotNull(selector.lastConfigError)
    }

    @Test
    fun enabledProxyWithInvalidPort_throwsException_andFallsBackToDirectInSelector() {
        for (invalidPort in listOf(0, -1, 65536, 70000)) {
            val settings = AppSettings(
                isProxyEnabled = true,
                proxyHost = "127.0.0.1",
                proxyPort = invalidPort,
                proxyType = "SOCKS5"
            )
            try {
                DynamicProxySelector.resolveProxy(settings)
                fail("Expected IllegalArgumentException for invalid port $invalidPort")
            } catch (e: IllegalArgumentException) {
                assertTrue(e.message?.contains("Invalid proxy port") == true)
            }

            val selector = DynamicProxySelector()
            selector.updateProxy(settings)
            val proxies = selector.select(URI.create("https://example.com/feed.xml"))
            assertEquals(listOf(Proxy.NO_PROXY), proxies)
            assertNotNull(selector.lastConfigError)
        }
    }

    @Test
    fun enabledProxyWithUnsupportedType_throwsException_andFallsBackToDirectInSelector() {
        val settings = AppSettings(
            isProxyEnabled = true,
            proxyHost = "127.0.0.1",
            proxyPort = 1080,
            proxyType = "FTP"
        )
        try {
            DynamicProxySelector.resolveProxy(settings)
            fail("Expected IllegalArgumentException for unsupported type FTP")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Unsupported proxy type") == true)
        }

        val selector = DynamicProxySelector()
        selector.updateProxy(settings)
        val proxies = selector.select(URI.create("https://example.com/feed.xml"))
        assertEquals(listOf(Proxy.NO_PROXY), proxies)
        assertNotNull(selector.lastConfigError)
    }

    @Test
    fun switchingFromSocksToDirectReturnsNoProxy() {
        val socksSettings = AppSettings(
            isProxyEnabled = true,
            proxyHost = "127.0.0.1",
            proxyPort = 1080,
            proxyType = "SOCKS5"
        )
        val directSettings = AppSettings(
            isProxyEnabled = false,
            proxyHost = "",
            proxyPort = 1080,
            proxyType = "SOCKS5"
        )
        val socksProxy = DynamicProxySelector.resolveProxy(socksSettings)
        val directProxy = DynamicProxySelector.resolveProxy(directSettings)

        assertEquals(Proxy.Type.SOCKS, socksProxy.type())
        assertEquals(Proxy.NO_PROXY, directProxy)
    }

    @Test
    fun switchingFromHttpToDirectReturnsNoProxy() {
        val httpSettings = AppSettings(
            isProxyEnabled = true,
            proxyHost = "proxy.example.com",
            proxyPort = 8080,
            proxyType = "HTTP"
        )
        val directSettings = AppSettings(
            isProxyEnabled = false,
            proxyHost = "",
            proxyPort = 1080,
            proxyType = "SOCKS5"
        )
        val httpProxy = DynamicProxySelector.resolveProxy(httpSettings)
        val directProxy = DynamicProxySelector.resolveProxy(directSettings)

        assertEquals(Proxy.Type.HTTP, httpProxy.type())
        assertEquals(Proxy.NO_PROXY, directProxy)
    }

    @Test
    fun changingHostPortDoesNotReturnCachedRoute() {
        val settings1 = AppSettings(
            isProxyEnabled = true,
            proxyHost = "proxy1.example.com",
            proxyPort = 1080,
            proxyType = "SOCKS5"
        )
        val settings2 = AppSettings(
            isProxyEnabled = true,
            proxyHost = "proxy2.example.com",
            proxyPort = 9050,
            proxyType = "SOCKS5"
        )
        val proxy1 = DynamicProxySelector.resolveProxy(settings1)
        val proxy2 = DynamicProxySelector.resolveProxy(settings2)

        val addr1 = proxy1.address() as InetSocketAddress
        val addr2 = proxy2.address() as InetSocketAddress

        assertEquals("proxy1.example.com", addr1.hostName)
        assertEquals(1080, addr1.port)
        assertEquals("proxy2.example.com", addr2.hostName)
        assertEquals(9050, addr2.port)
    }

    @Test
    fun changingTypeDoesNotReturnCachedRoute() {
        val socksSettings = AppSettings(
            isProxyEnabled = true,
            proxyHost = "127.0.0.1",
            proxyPort = 1080,
            proxyType = "SOCKS5"
        )
        val httpSettings = AppSettings(
            isProxyEnabled = true,
            proxyHost = "127.0.0.1",
            proxyPort = 1080,
            proxyType = "HTTP"
        )
        val socksProxy = DynamicProxySelector.resolveProxy(socksSettings)
        val httpProxy = DynamicProxySelector.resolveProxy(httpSettings)

        assertEquals(Proxy.Type.SOCKS, socksProxy.type())
        assertEquals(Proxy.Type.HTTP, httpProxy.type())
    }

    @Test
    fun selectBeforeInitialization_waitsForInitialProxy_includesDirectFallback() = runBlocking {
        val selector = DynamicProxySelector()

        val selectJob = async(Dispatchers.IO) {
            selector.select(URI.create("https://example.com/feed.xml"))
        }

        delay(50)
        // Ensure proxy is configured asynchronously
        selector.updateProxy(AppSettings(
            isProxyEnabled = true,
            proxyHost = "10.0.0.1",
            proxyPort = 1080,
            proxyType = "SOCKS5"
        ))

        val proxies = selectJob.await()
        assertEquals(2, proxies.size)
        assertEquals(Proxy.Type.SOCKS, proxies[0].type())
        assertEquals(Proxy.NO_PROXY, proxies[1])
        val addr = proxies[0].address() as InetSocketAddress
        assertEquals("10.0.0.1", addr.hostName)
        assertEquals(1080, addr.port)
    }

    @Test
    fun configuredProxyIncludesDirectFallbackInRouteList() {
        val selector = DynamicProxySelector()
        selector.updateProxy(AppSettings(
            isProxyEnabled = true,
            proxyHost = "127.0.0.1",
            proxyPort = 1080,
            proxyType = "SOCKS5"
        ))
        val routes = selector.select(URI.create("https://example.com/audio.mp3"))
        assertEquals(2, routes.size)
        assertEquals(Proxy.Type.SOCKS, routes[0].type())
        assertEquals(Proxy.NO_PROXY, routes[1])
    }

    @Test
    fun initialStateNotArrived_awaitTimesOut_gracefullyFallsBackToDirect() {
        val selector = DynamicProxySelector()

        // Await with short timeout
        val initialized = selector.awaitInitialization(50)
        assertFalse(initialized)
        val proxies = selector.select(URI.create("https://example.com/feed.xml"))
        assertEquals(1, proxies.size)
        assertEquals(Proxy.NO_PROXY, proxies[0])
    }

    @Test
    fun waitingThreadInterrupted_gracefullyFallsBackToDirect() {
        val selector = DynamicProxySelector()

        var selectedProxies: List<Proxy>? = null
        val thread = Thread {
            try {
                selectedProxies = selector.select(URI.create("https://example.com/feed.xml"))
            } catch (_: Exception) {
            }
        }

        thread.start()
        Thread.sleep(50)
        thread.interrupt()
        thread.join(2000)

        assertNotNull("Expected graceful fallback to direct connection", selectedProxies)
        assertEquals(1, selectedProxies!!.size)
        assertEquals(Proxy.NO_PROXY, selectedProxies!![0])
    }

    @Test
    fun explicitDirectSettings_returnsNoProxyWithoutWaiting() {
        val selector = DynamicProxySelector()
        selector.updateProxy(AppSettings(isProxyEnabled = false))

        assertTrue(selector.isInitialized)
        val proxies = selector.select(URI.create("https://example.com/feed.xml"))
        assertEquals(1, proxies.size)
        assertEquals(Proxy.NO_PROXY, proxies[0])
    }
}
