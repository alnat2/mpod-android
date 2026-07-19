package com.example.mpod.ui.screens.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class InstalledAppBuildInfoTest {
    @Test
    fun testPackageIsIdentifiedAsTestEnvironment() {
        assertEquals("Test", installedEnvironment("com.prod.mpod.test"))
    }

    @Test
    fun productionPackageIsIdentifiedAsProductionEnvironment() {
        assertEquals("Production", installedEnvironment("com.prod.mpod"))
    }
}
