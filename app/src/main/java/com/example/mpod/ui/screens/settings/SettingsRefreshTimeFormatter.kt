package com.example.mpod.ui.screens.settings

import java.time.Instant
import java.time.LocalDate
import java.time.Month
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

object SettingsRefreshTimeFormatter {

    private val legacyWithDateRegex = Regex("""(\d{1,2})\.(\d{1,2})(?:\.(\d{2,4}))?\s+(\d{1,2}):(\d{2})""")
    private val legacyMonthNameRegex = Regex("""(\d{1,2})\s+([A-Za-z]{3})\s+at\s+(\d{1,2}):(\d{2})""")
    private val legacyTimeOnlyRegex = Regex("""(\d{1,2}):(\d{2})""")

    fun format(
        raw: String?,
        nowMillis: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): String {
        if (raw.isNullOrBlank() || raw.equals("Last refresh never", ignoreCase = true) || raw.equals("never", ignoreCase = true)) {
            return "Last updated: never"
        }

        val trimmed = raw.trim()

        // 1. Try parsing as epoch millis
        val epochMillis = trimmed.toLongOrNull()
        if (epochMillis != null && epochMillis > 0L) {
            val zdt = Instant.ofEpochMilli(epochMillis).atZone(zoneId)
            return formatDateTime(zdt.toLocalDate(), zdt.hour, zdt.minute, nowMillis, zoneId)
        }

        // 2. Try parsing ISO-8601
        try {
            val instant = Instant.parse(trimmed)
            val zdt = instant.atZone(zoneId)
            return formatDateTime(zdt.toLocalDate(), zdt.hour, zdt.minute, nowMillis, zoneId)
        } catch (_: Exception) {
            // Not a standard ISO-8601 instant
        }

        try {
            val zdt = ZonedDateTime.parse(trimmed)
            val localZdt = zdt.withZoneSameInstant(zoneId)
            return formatDateTime(localZdt.toLocalDate(), localZdt.hour, localZdt.minute, nowMillis, zoneId)
        } catch (_: Exception) {
            // Not a standard ISO-8601 zoned date time
        }

        // 3. Try parsing legacy "Last refresh today at dd.MM HH:mm" or "dd.MM HH:mm"
        val matchWithDate = legacyWithDateRegex.find(trimmed)
        if (matchWithDate != null) {
            val day = matchWithDate.groupValues[1].toInt()
            val month = matchWithDate.groupValues[2].toInt()
            val yearGroup = matchWithDate.groupValues[3]
            val hour = matchWithDate.groupValues[4].toInt()
            val minute = matchWithDate.groupValues[5].toInt()

            val nowZdt = Instant.ofEpochMilli(nowMillis).atZone(zoneId)
            val year = when {
                yearGroup.isNotBlank() -> {
                    val parsedYear = yearGroup.toInt()
                    if (parsedYear < 100) 2000 + parsedYear else parsedYear
                }
                month > nowZdt.monthValue -> nowZdt.year - 1
                else -> nowZdt.year
            }

            return try {
                val targetDate = LocalDate.of(year, month, day)
                formatDateTime(targetDate, hour, minute, nowMillis, zoneId)
            } catch (_: Exception) {
                "Last updated $trimmed"
            }
        }

        // 4. Try parsing "d MMM at HH:mm" (e.g. "27 Oct at 10:00")
        val matchMonthName = legacyMonthNameRegex.find(trimmed)
        if (matchMonthName != null) {
            val day = matchMonthName.groupValues[1].toInt()
            val monthStr = matchMonthName.groupValues[2]
            val hour = matchMonthName.groupValues[3].toInt()
            val minute = matchMonthName.groupValues[4].toInt()

            val parsedMonth = Month.values().firstOrNull {
                it.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).equals(monthStr, ignoreCase = true)
            }

            if (parsedMonth != null) {
                val nowZdt = Instant.ofEpochMilli(nowMillis).atZone(zoneId)
                val year = if (parsedMonth.value > nowZdt.monthValue) nowZdt.year - 1 else nowZdt.year
                return try {
                    val targetDate = LocalDate.of(year, parsedMonth, day)
                    formatDateTime(targetDate, hour, minute, nowMillis, zoneId)
                } catch (_: Exception) {
                    "Last updated $trimmed"
                }
            }
        }

        // 5. Try parsing time-only "HH:mm" (e.g. "Last refresh today at 10:00")
        val matchTimeOnly = legacyTimeOnlyRegex.find(trimmed)
        if (matchTimeOnly != null) {
            val hour = matchTimeOnly.groupValues[1].toInt()
            val minute = matchTimeOnly.groupValues[2].toInt()
            val timeStr = String.format(Locale.US, "%02d:%02d", hour, minute)
            return "Last updated today at $timeStr"
        }

        return if (trimmed.startsWith("Last updated", ignoreCase = true)) {
            trimmed
        } else {
            "Last updated $trimmed"
        }
    }

    private fun formatDateTime(
        date: LocalDate,
        hour: Int,
        minute: Int,
        nowMillis: Long,
        zoneId: ZoneId
    ): String {
        val nowZdt = Instant.ofEpochMilli(nowMillis).atZone(zoneId)
        val isToday = date.isEqual(nowZdt.toLocalDate())
        val timeStr = String.format(Locale.US, "%02d:%02d", hour, minute)

        return if (isToday) {
            "Last updated today at $timeStr"
        } else {
            val day = date.dayOfMonth
            val monthName = date.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
            "Last updated $day $monthName at $timeStr"
        }
    }
}
