package com.example.mpod.data.local.preferences

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSettingsTest {
    @Test
    fun defaultSettingsHaveSmartListeningEnabled() {
        val settings = AppSettings()
        assertTrue(settings.smartListeningEnabled)
        assertEquals(3, settings.maxDownloadsPerPodcast)
        assertFalse(settings.wifiOnlyDownloads)
    }

    @Test
    fun defaultSettingsHaveAutoRefreshDisabled() {
        val settings = AppSettings()
        assertFalse(settings.isAutoRefreshEnabled)
        assertEquals("03:00", settings.dailyRefreshTime)
    }

    @Test
    fun defaultThemeModeIsSystem() {
        val settings = AppSettings()
        assertEquals("System", settings.themeMode)
    }
}
