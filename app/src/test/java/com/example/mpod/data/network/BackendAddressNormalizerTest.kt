package com.example.mpod.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackendAddressNormalizerTest {
    @Test
    fun normalizesDisplayAddressFromHostPort() {
        assertEquals(
            "192.168.0.222:5051",
            BackendAddressNormalizer.toDisplayAddressOrNull(" 192.168.0.222:5051 ")
        )
    }

    @Test
    fun stripsPathQueryAndDefaultHttpPort() {
        assertEquals(
            "example.com",
            BackendAddressNormalizer.toDisplayAddressOrNull("http://example.com:80/api?debug=true")
        )
        assertEquals(
            "https://example.com/",
            BackendAddressNormalizer.toBaseUrlOrNull("https://example.com/settings")
        )
    }

    @Test
    fun rejectsBlankOrInvalidAddress() {
        assertNull(BackendAddressNormalizer.toDisplayAddressOrNull(""))
        assertNull(BackendAddressNormalizer.toDisplayAddressOrNull("not a host"))
    }
}
