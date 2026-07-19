package com.example.mpod.data.network

import com.example.mpod.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class BackendConfigVariantTest {
    @Test
    fun debugUsesTheTestPackageAndBackend() {
        assertEquals("com.prod.mpod.test", BuildConfig.APPLICATION_ID)
        assertEquals("192.168.0.222:5051", BackendConfig().address)
        assertEquals("http://192.168.0.222:5051/", BackendConfig().baseUrl)
    }
}
