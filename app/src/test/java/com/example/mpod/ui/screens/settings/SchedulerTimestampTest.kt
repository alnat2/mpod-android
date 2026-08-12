package com.example.mpod.ui.screens.settings

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class SchedulerTimestampTest {
    @Test
    fun utcTimestampIsDisplayedInDeviceTimezone() {
        assertEquals(
            "2026-07-27 04:00",
            formatSchedulerTimestamp(
                rawTimestamp = "2026-07-27T01:00:00Z",
                zoneId = ZoneId.of("Europe/Moscow")
            )
        )
    }

    @Test
    fun malformedTimestampKeepsStableCompactFallback() {
        assertEquals(
            "2026-07-27 01:00",
            formatSchedulerTimestamp("2026-07-27T01:00")
        )
    }

    @Test
    fun settingsHeaderUsesRelativeDayWhenRefreshHappenedToday() {
        assertEquals(
            "Last refresh today at 04:00",
            formatSettingsLastRefreshText(
                rawTimestamp = "2026-07-27T01:00:00Z",
                zoneId = ZoneId.of("Europe/Moscow"),
                today = LocalDate.of(2026, 7, 27)
            )
        )
    }

    @Test
    fun settingsHeaderKeepsDateForOlderRefresh() {
        assertEquals(
            "Last refresh 2026-07-27 at 04:00",
            formatSettingsLastRefreshText(
                rawTimestamp = "2026-07-27T01:00:00Z",
                zoneId = ZoneId.of("Europe/Moscow"),
                today = LocalDate.of(2026, 7, 29)
            )
        )
    }
}
