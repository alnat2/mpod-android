package com.example.mpod.data.local.preferences

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxyServerConfigTest {

    @Test
    fun serializeAndParse_singleServer() {
        val server = ProxyServerConfig(
            id = "test-id-1",
            name = "Home Proxy",
            host = "192.168.1.100",
            port = 1080,
            username = "user",
            password = "pwd"
        )
        val json = serializeProxyServers(listOf(server))
        val parsed = parseProxyServersJson(json)

        assertEquals(1, parsed.size)
        assertEquals(server, parsed[0])
    }

    @Test
    fun serializeAndParse_multipleServers() {
        val servers = listOf(
            ProxyServerConfig(
                id = "id-1",
                name = "Server 1",
                host = "proxy1.internal",
                port = 1080,
                username = "alice",
                password = "secret\"password\\"
            ),
            ProxyServerConfig(
                id = "id-2",
                name = "Server 2",
                host = "proxy2.internal",
                port = 9050,
                username = "",
                password = ""
            )
        )
        val json = serializeProxyServers(servers)
        val parsed = parseProxyServersJson(json)

        assertEquals(2, parsed.size)
        assertEquals(servers[0], parsed[0])
        assertEquals(servers[1], parsed[1])
    }

    @Test
    fun parseInvalidOrEmptyJson_returnsEmptyList() {
        assertEquals(emptyList<ProxyServerConfig>(), parseProxyServersJson(""))
        assertEquals(emptyList<ProxyServerConfig>(), parseProxyServersJson("   "))
        assertEquals(emptyList<ProxyServerConfig>(), parseProxyServersJson("[]"))
        assertEquals(emptyList<ProxyServerConfig>(), parseProxyServersJson("[  ]"))
        assertEquals(emptyList<ProxyServerConfig>(), parseProxyServersJson("invalid json"))
        assertEquals(emptyList<ProxyServerConfig>(), parseProxyServersJson("{}"))
    }

    @Test
    fun addressPortDisplay_formatsCorrectly() {
        val server = ProxyServerConfig(
            name = "Test",
            host = "10.0.0.1",
            port = 1080
        )
        assertEquals("10.0.0.1:1080", server.addressPortDisplay)

        val emptyHost = ProxyServerConfig(
            name = "Empty",
            host = "",
            port = 1080
        )
        assertEquals("", emptyHost.addressPortDisplay)
    }

    @Test
    fun appSettings_activeProxyServerResolution() {
        val server1 = ProxyServerConfig(id = "srv-1", name = "S1", host = "10.0.0.1", port = 1080)
        val server2 = ProxyServerConfig(id = "srv-2", name = "S2", host = "10.0.0.2", port = 1080)

        // When proxy enabled, with selected ID matching server2
        val settingsWithSelection = AppSettings(
            isProxyEnabled = true,
            proxyServers = listOf(server1, server2),
            selectedProxyServerId = "srv-2"
        )
        assertEquals(server2, settingsWithSelection.activeProxyServer)

        // When proxy enabled, selected ID empty or not matching -> falls back to first server
        val settingsFallbackFirst = AppSettings(
            isProxyEnabled = true,
            proxyServers = listOf(server1, server2),
            selectedProxyServerId = "non-existent"
        )
        assertEquals(server1, settingsFallbackFirst.activeProxyServer)

        // When proxy enabled, proxyServers empty -> activeProxyServer is null
        val settingsEmpty = AppSettings(
            isProxyEnabled = true,
            proxyServers = emptyList()
        )
        org.junit.Assert.assertNull(settingsEmpty.activeProxyServer)

        // When proxy is disabled -> activeProxyServer still points to the configured server
        val settingsDisabled = AppSettings(
            isProxyEnabled = false,
            proxyServers = listOf(server1),
            selectedProxyServerId = "srv-1"
        )
        assertEquals(server1, settingsDisabled.activeProxyServer)
    }
}
