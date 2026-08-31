package com.example.mpod.playback

import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class AutoRefreshSchedulerTest {

    @Test
    fun computeDelayToNextRun_returnsPositiveDelay() {
        val zoneId = ZoneId.of("UTC")
        val now = ZonedDateTime.of(2026, 8, 31, 10, 0, 0, 0, zoneId)
        val delay = AutoRefreshScheduler.computeDelayToNextRun("03:00", zoneId, now)
        assertTrue("Delay should be positive but was $delay", delay > 0)
    }

    @Test
    fun computeDelayToNextRun_whenTimeAlreadyPassed_returnsNextDay() {
        val zoneId = ZoneId.of("UTC")
        val now = ZonedDateTime.of(2026, 8, 31, 10, 0, 0, 0, zoneId)
        val delay = AutoRefreshScheduler.computeDelayToNextRun("03:00", zoneId, now)
        // Should be ~17 hours from now (10:00 to 03:00 next day)
        assertTrue("Delay should be > 15h but was ${delay / 3_600_000}h", delay > 15 * 3_600_000)
        assertTrue("Delay should be < 18h but was ${delay / 3_600_000}h", delay < 18 * 3_600_000)
    }

    @Test
    fun computeDelayToNextRun_whenTimeIsFuture_returnsToday() {
        val zoneId = ZoneId.of("UTC")
        val now = ZonedDateTime.of(2026, 8, 31, 10, 0, 0, 0, zoneId)
        val delay = AutoRefreshScheduler.computeDelayToNextRun("15:00", zoneId, now)
        // Should be ~5 hours from now (10:00 to 15:00)
        assertTrue("Delay should be > 4h but was ${delay / 3_600_000}h", delay > 4 * 3_600_000)
        assertTrue("Delay should be < 6h but was ${delay / 3_600_000}h", delay < 6 * 3_600_000)
    }

    @Test
    fun computeDelayToNextRun_minimumIsOneMinute() {
        val zoneId = ZoneId.of("UTC")
        val now = ZonedDateTime.of(2026, 8, 31, 2, 59, 0, 0, zoneId)
        val delay = AutoRefreshScheduler.computeDelayToNextRun("03:00", zoneId, now)
        assertTrue("Delay should be at least 60000ms but was $delay", delay >= 60_000)
    }
}
