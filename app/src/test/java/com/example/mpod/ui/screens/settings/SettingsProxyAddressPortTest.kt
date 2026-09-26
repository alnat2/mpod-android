package com.example.mpod.ui.screens.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsProxyAddressPortTest {

    @Test
    fun parseProxyAddressPort_validInputs() {
        val result1 = parseProxyAddressPort("192.168.1.1:1080")
        assertNotNull(result1)
        assertEquals("192.168.1.1" to 1080, result1)

        val result2 = parseProxyAddressPort("  proxy.example.com:8080  ")
        assertNotNull(result2)
        assertEquals("proxy.example.com" to 8080, result2)
    }

    @Test
    fun parseProxyAddressPort_invalidInputs() {
        assertNull(parseProxyAddressPort(""))
        assertNull(parseProxyAddressPort("   "))
        assertNull(parseProxyAddressPort("localhost"))
        assertNull(parseProxyAddressPort("localhost:"))
        assertNull(parseProxyAddressPort(":1080"))
        assertNull(parseProxyAddressPort("localhost:0"))
        assertNull(parseProxyAddressPort("localhost:65536"))
        assertNull(parseProxyAddressPort("localhost:abc"))
    }

    @Test
    fun formatProxyAddressPort_formatsCorrectly() {
        assertEquals("127.0.0.1:1080", formatProxyAddressPort("127.0.0.1", 1080))
        assertEquals("", formatProxyAddressPort("", 1080))
        assertEquals("", formatProxyAddressPort("   ", 1080))
    }
}
