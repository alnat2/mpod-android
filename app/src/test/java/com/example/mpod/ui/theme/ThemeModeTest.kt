package com.example.mpod.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeModeTest {
    @Test
    fun unknownOrMissingPreferenceFallsBackToSystem() {
        assertEquals(ThemeMode.System, ThemeMode.fromStorage(null))
        assertEquals(ThemeMode.System, ThemeMode.fromStorage("unexpected"))
        assertEquals(ThemeMode.System, ThemeMode.fromStorage("system"))
    }

    @Test
    fun restoresExplicitThemePreference() {
        assertEquals(ThemeMode.Light, ThemeMode.fromStorage("light"))
        assertEquals(ThemeMode.Dark, ThemeMode.fromStorage("dark"))
    }

    @Test
    fun systemModeFollowsTheDeviceTheme() {
        assertEquals(false, ThemeMode.System.isDark(systemDark = false))
        assertEquals(true, ThemeMode.System.isDark(systemDark = true))
        assertEquals(false, ThemeMode.Light.isDark(systemDark = true))
        assertEquals(true, ThemeMode.Dark.isDark(systemDark = false))
    }
}
