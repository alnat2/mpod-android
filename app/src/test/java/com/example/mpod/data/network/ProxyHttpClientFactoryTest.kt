package com.example.mpod.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.net.Proxy

class ProxyHttpClientFactoryTest {

    @Test
    fun directClientHasNoProxy() {
        val client = ProxyHttpClientFactory.createDirectOkHttpClient()
        assertNull(client.proxy)
    }

    @Test
    fun socksProxyClientConfiguresSocksProxy() {
        val client = ProxyHttpClientFactory.createOkHttpClient(
            proxyEnabled = true,
            proxyHost = "127.0.0.1",
            proxyPort = 1080,
            proxyType = "SOCKS5"
        )
        assertNotNull(client.proxy)
        assertEquals(Proxy.Type.SOCKS, client.proxy?.type())
    }

    @Test
    fun disabledProxyReturnsDirectClient() {
        val client = ProxyHttpClientFactory.createOkHttpClient(
            proxyEnabled = false,
            proxyHost = "127.0.0.1",
            proxyPort = 1080,
            proxyType = "SOCKS5"
        )
        assertNull(client.proxy)
    }
}
