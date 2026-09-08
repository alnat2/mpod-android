package com.example.mpod.data.network

import com.example.mpod.data.local.preferences.AppSettings
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class DynamicProxySelector : ProxySelector() {

    private val currentProxy = AtomicReference<Proxy?>(null)
    private val configError = AtomicReference<Throwable?>(null)
    private val initLatch = CountDownLatch(1)

    fun updateProxy(settings: AppSettings) {
        try {
            currentProxy.set(resolveProxy(settings))
            configError.set(null)
        } catch (t: Throwable) {
            currentProxy.set(null)
            configError.set(t)
        } finally {
            initLatch.countDown()
        }
    }

    /**
     * Waits for initial proxy configuration to be loaded, up to [timeoutMs].
     * Synchronously called on background/network threads during route selection.
     */
    fun awaitInitialization(timeoutMs: Long = 5000): Boolean {
        return try {
            initLatch.await(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }
    }

    val isInitialized: Boolean
        get() = initLatch.count == 0L

    override fun select(uri: URI?): List<Proxy> {
        val initialized = if (initLatch.count > 0L) {
            awaitInitialization(5000)
        } else {
            true
        }

        if (!initialized) {
            throw IllegalStateException("Proxy configuration initialization timed out or was interrupted; direct fallback is disallowed")
        }

        configError.get()?.let { err ->
            throw IllegalStateException("Proxy configuration error: ${err.message}", err)
        }

        val proxy = currentProxy.get()
            ?: throw IllegalStateException("Proxy configuration not available; direct fallback is disallowed")
        return listOf(proxy)
    }

    override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) {
        // No-op: connection failures are handled by OkHttp caller
    }

    companion object {
        fun createNoop(): DynamicProxySelector {
            val selector = DynamicProxySelector()
            selector.updateProxy(AppSettings(isProxyEnabled = false))
            return selector
        }

        fun resolveProxy(settings: AppSettings): Proxy {
            if (!settings.isProxyEnabled) {
                return Proxy.NO_PROXY
            }
            if (settings.proxyHost.isBlank()) {
                throw IllegalArgumentException("Proxy host cannot be blank when proxy is enabled")
            }
            if (settings.proxyPort !in 1..65535) {
                throw IllegalArgumentException("Invalid proxy port: ${settings.proxyPort}")
            }
            val type = when (settings.proxyType.trim().uppercase()) {
                "SOCKS", "SOCKS5" -> Proxy.Type.SOCKS
                "HTTP", "HTTPS" -> Proxy.Type.HTTP
                else -> throw IllegalArgumentException("Unsupported proxy type: ${settings.proxyType}")
            }
            // Use createUnresolved to prevent synchronous DNS resolution on the calling thread.
            val address = InetSocketAddress.createUnresolved(settings.proxyHost.trim(), settings.proxyPort)
            return Proxy(type, address)
        }
    }
}
