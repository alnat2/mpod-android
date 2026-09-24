package com.example.mpod.ui.screens.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class DailyRefreshTimeTest {
    @Test
    fun validSavedTimeIsParsed() {
        assertEquals(4 to 7, parseDailyRefreshTime("04:07"))
    }

    @Test
    fun invalidSavedTimeFallsBackToThreeAm() {
        assertEquals(3 to 0, parseDailyRefreshTime("25:90"))
    }

    @Test
    fun selectedTimeUsesStoredHourMinuteFormat() {
        assertEquals("04:07", formatDailyRefreshTime(hour = 4, minute = 7))
    }
}
