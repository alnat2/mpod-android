package com.example.mpod.ui.screens.settings

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
}
