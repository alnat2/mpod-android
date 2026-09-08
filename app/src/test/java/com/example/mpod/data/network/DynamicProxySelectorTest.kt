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
    fun enabledProxyWithBlankHost_throwsException_andDisallowsDirect() {
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

        // Test in selector: select() must throw IllegalStateException
        val selector = DynamicProxySelector()
        selector.updateProxy(settings)
        try {
            selector.select(URI.create("https://example.com/feed.xml"))
            fail("Expected IllegalStateException from select() for enabled proxy with blank host")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("Proxy configuration error") == true)
        }
    }

    @Test
    fun enabledProxyWithInvalidPort_throwsException_andDisallowsDirect() {
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
            try {
                selector.select(URI.create("https://example.com/feed.xml"))
                fail("Expected IllegalStateException for invalid port $invalidPort")
            } catch (e: IllegalStateException) {
                assertTrue(e.message?.contains("Proxy configuration error") == true)
            }
        }
    }

    @Test
    fun enabledProxyWithUnsupportedType_throwsException_andDisallowsDirect() {
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
        try {
            selector.select(URI.create("https://example.com/feed.xml"))
            fail("Expected IllegalStateException for unsupported type FTP")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("Proxy configuration error") == true)
        }
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
    fun selectBeforeInitialization_waitsForInitialProxy_doesNotFallBackToDirect() = runBlocking {
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
        assertEquals(1, proxies.size)
        assertEquals(Proxy.Type.SOCKS, proxies[0].type())
        val addr = proxies[0].address() as InetSocketAddress
        assertEquals("10.0.0.1", addr.hostName)
        assertEquals(1080, addr.port)
    }

    @Test
    fun initialStateNotArrived_awaitTimesOut_throwsIllegalStateException_noDirectFallback() {
        val selector = DynamicProxySelector()

        var caughtException: Exception? = null
        try {
            // Await with short timeout
            val initialized = selector.awaitInitialization(50)
            assertFalse(initialized)
            selector.select(URI.create("https://example.com/feed.xml"))
        } catch (e: Exception) {
            caughtException = e
        }

        assertNotNull("Expected IllegalStateException on timeout", caughtException)
        assertTrue(caughtException is IllegalStateException)
        assertTrue(caughtException?.message?.contains("direct fallback is disallowed") == true)
    }

    @Test
    fun waitingThreadInterrupted_throwsIllegalStateException_noDirectFallback() {
        val selector = DynamicProxySelector()

        var caughtException: Exception? = null
        val thread = Thread {
            try {
                selector.select(URI.create("https://example.com/feed.xml"))
            } catch (e: Exception) {
                caughtException = e
            }
        }

        thread.start()
        Thread.sleep(50)
        thread.interrupt()
        thread.join(2000)

        assertNotNull("Expected IllegalStateException on interruption", caughtException)
        assertTrue(caughtException is IllegalStateException)
        assertTrue(caughtException?.message?.contains("direct fallback is disallowed") == true)
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
