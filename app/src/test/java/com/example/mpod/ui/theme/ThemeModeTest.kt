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
    fun allThreeModesAreDistinct() {
        val modes = listOf(ThemeMode.System, ThemeMode.Light, ThemeMode.Dark)
        assertEquals(3, modes.size)
        assertEquals(3, modes.map { it.storageValue }.toSet().size)
    }

    @Test
    fun storageRoundTripForAllModes() {
        for (mode in ThemeMode.values()) {
            assertEquals(mode, ThemeMode.fromStorage(mode.storageValue))
        }
    }

    @Test
    fun storageValuesAreLowerCase() {
        for (mode in ThemeMode.values()) {
            assertEquals(mode.storageValue, mode.storageValue.lowercase())
        }
    }
}
