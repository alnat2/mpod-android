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

    @Test
    fun validateProxyCredentials_returnsErrorWhenOneFieldMissing() {
        assertEquals(
            "Password is required when username is provided.",
            validateProxyCredentials("user", "")
        )
        assertEquals(
            "Password is required when username is provided.",
            validateProxyCredentials("  user  ", "   ")
        )
        assertEquals(
            "Username is required when password is provided.",
            validateProxyCredentials("", "secret")
        )
        assertEquals(
            "Username is required when password is provided.",
            validateProxyCredentials("   ", "secret")
        )
    }

    @Test
    fun validateProxyCredentials_returnsNullWhenBothOrNeitherProvided() {
        assertNull(validateProxyCredentials("", ""))
        assertNull(validateProxyCredentials("   ", ""))
        assertNull(validateProxyCredentials("admin", "secret123"))
        assertNull(validateProxyCredentials("  admin  ", "secret123"))
    }

    @Test
    fun proxyCardDescription_reflectsEnabledAndDisabledStates() {
        // When proxy is disabled, description is default guidance regardless of IP/Geo text
        assertEquals(
            "Turn on if direct connection update fails.",
            proxyCardDescription(isProxyEnabled = false, currentIpGeoText = null)
        )
        assertEquals(
            "Turn on if direct connection update fails.",
            proxyCardDescription(isProxyEnabled = false, currentIpGeoText = "Current IP: 1.2.3.4\nGeo: US")
        )

        // When proxy is enabled but IP/Geo is not yet available
        assertEquals(
            "Turn on if direct connection update fails.",
            proxyCardDescription(isProxyEnabled = true, currentIpGeoText = null)
        )
        assertEquals(
            "Turn on if direct connection update fails.",
            proxyCardDescription(isProxyEnabled = true, currentIpGeoText = "  ")
        )

        // When proxy is enabled and IP/Geo is available
        val ipGeo = "Current IP: 43.32.112.45\nGeo: UK"
        assertEquals(
            ipGeo,
            proxyCardDescription(isProxyEnabled = true, currentIpGeoText = ipGeo)
        )
    }
}
