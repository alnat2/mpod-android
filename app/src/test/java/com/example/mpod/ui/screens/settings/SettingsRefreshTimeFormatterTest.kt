package com.example.mpod.ui.screens.settings

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class SettingsRefreshTimeFormatterTest {

    private val zoneId = ZoneId.of("Europe/Amsterdam")
    // Reference date: 2026-10-30 at 12:00:00 Europe/Amsterdam
    private val nowMillis = ZonedDateTime.of(2026, 10, 30, 12, 0, 0, 0, zoneId).toInstant().toEpochMilli()

    @Test
    fun `empty, null, or never raw string returns Last updated never`() {
        assertEquals("Last updated: never", SettingsRefreshTimeFormatter.format(null, nowMillis, zoneId))
        assertEquals("Last updated: never", SettingsRefreshTimeFormatter.format("", nowMillis, zoneId))
        assertEquals("Last updated: never", SettingsRefreshTimeFormatter.format("   ", nowMillis, zoneId))
        assertEquals("Last updated: never", SettingsRefreshTimeFormatter.format("Last refresh never", nowMillis, zoneId))
    }

    @Test
    fun `legacy phone string from past day formats as Last updated 27 Oct at 10_00`() {
        val legacy = "Last refresh today at 27.10 10:00"
        val result = SettingsRefreshTimeFormatter.format(legacy, nowMillis, zoneId)
        assertEquals("Last updated 27 Oct at 10:00", result)
    }

    @Test
    fun `legacy phone string from today formats as Last updated today at 10_00`() {
        val legacy = "Last refresh today at 30.10 10:00"
        val result = SettingsRefreshTimeFormatter.format(legacy, nowMillis, zoneId)
        assertEquals("Last updated today at 10:00", result)
    }

    @Test
    fun `legacy string with single digit day formats correctly`() {
        val legacy = "Last refresh today at 07.09 22:39"
        val result = SettingsRefreshTimeFormatter.format(legacy, nowMillis, zoneId)
        assertEquals("Last updated 7 Sep at 22:39", result)
    }

    @Test
    fun `epoch millis from today formats as today`() {
        val today10am = ZonedDateTime.of(2026, 10, 30, 10, 0, 0, 0, zoneId).toInstant().toEpochMilli()
        val result = SettingsRefreshTimeFormatter.format(today10am.toString(), nowMillis, zoneId)
        assertEquals("Last updated today at 10:00", result)
    }

    @Test
    fun `epoch millis from past day formats as day month`() {
        val pastDay = ZonedDateTime.of(2026, 10, 27, 10, 0, 0, 0, zoneId).toInstant().toEpochMilli()
        val result = SettingsRefreshTimeFormatter.format(pastDay.toString(), nowMillis, zoneId)
        assertEquals("Last updated 27 Oct at 10:00", result)
    }

    @Test
    fun `ISO-8601 instant string formats correctly`() {
        val pastInstant = ZonedDateTime.of(2026, 10, 27, 10, 0, 0, 0, zoneId).toInstant().toString()
        val result = SettingsRefreshTimeFormatter.format(pastInstant, nowMillis, zoneId)
        assertEquals("Last updated 27 Oct at 10:00", result)
    }

    @Test
    fun `already formatted with month name changes to today when evaluated on the same date`() {
        val formattedPast = "Last updated 27 Oct at 10:00"
        // On Oct 30, it stays 27 Oct
        assertEquals("Last updated 27 Oct at 10:00", SettingsRefreshTimeFormatter.format(formattedPast, nowMillis, zoneId))

        // But evaluated on Oct 27, it becomes today
        val onOct27Millis = ZonedDateTime.of(2026, 10, 27, 14, 0, 0, 0, zoneId).toInstant().toEpochMilli()
        assertEquals("Last updated today at 10:00", SettingsRefreshTimeFormatter.format(formattedPast, onOct27Millis, zoneId))
    }
}
