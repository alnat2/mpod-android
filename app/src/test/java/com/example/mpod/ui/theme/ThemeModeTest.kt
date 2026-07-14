package com.example.mpod.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeModeTest {
    @Test
    fun unknownOrMissingPreferenceFallsBackToLight() {
        assertEquals(ThemeMode.Light, ThemeMode.fromStorage(null))
        assertEquals(ThemeMode.Light, ThemeMode.fromStorage("unexpected"))
        assertEquals(ThemeMode.Light, ThemeMode.fromStorage("system"))
    }

    @Test
    fun restoresExplicitThemePreference() {
        assertEquals(ThemeMode.Light, ThemeMode.fromStorage("light"))
        assertEquals(ThemeMode.Dark, ThemeMode.fromStorage("dark"))
    }
}
