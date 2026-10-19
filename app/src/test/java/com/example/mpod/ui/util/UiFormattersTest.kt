package com.example.mpod.ui.util

import org.junit.Assert.assertEquals
import org.junit.Test

class UiFormattersTest {
    @Test
    fun progressTimeFormatsHoursWhenOverAnHour() {
        assertEquals("1:25:39", formatProgressTime(85 * 60 + 39))
        assertEquals("23:14", formatProgressTime(23 * 60 + 14))
        assertEquals("00:00", formatProgressTime(0))
    }

    @Test
    fun episodeDurationHandlesZeroAndVariousDurations() {
        assertEquals("", formatEpisodeDuration(null))
        assertEquals("", formatEpisodeDuration(0))
        assertEquals("00:45", formatEpisodeDuration(45))
        assertEquals("01:00", formatEpisodeDuration(60))
        assertEquals("45:00", formatEpisodeDuration(45 * 60))
        assertEquals("1:30:00", formatEpisodeDuration(90 * 60))
        assertEquals("2:00:00", formatEpisodeDuration(120 * 60))
    }

    @Test
    fun totalDurationOmitsEmptyWhenZero() {
        assertEquals("", formatTotalDuration(0))
        assertEquals("45:00", formatTotalDuration(45 * 60))
        assertEquals("2:13:00", formatTotalDuration(2 * 3600 + 13 * 60))
    }

    @Test
    fun remainingTimeSubtractsElapsedPositionAndClampsAtZero() {
        assertEquals(
            "30:49",
            formatRemainingTime(
                durationSeconds = 54 * 60 + 3,
                positionSeconds = 23 * 60 + 14
            )
        )
        assertEquals(
            "1:03:00",
            formatRemainingTime(
                durationSeconds = 148 * 60 + 39,
                positionSeconds = 85 * 60 + 39
            )
        )
        assertEquals("00:00", formatRemainingTime(durationSeconds = 60, positionSeconds = 75))
    }

    @Test
    fun cleanFeedTextRemovesTagsAndDecodesCommonEntities() {
        assertEquals(
            "Planet Money & The Indicator: what's next?",
            cleanFeedText("<p>Planet&nbsp;Money &amp; The Indicator:<br>what&apos;s next?</p>")
        )
    }

    @Test
    fun cleanFeedTextDecodesNumericEntitiesAndCollapsesWhitespace() {
        assertEquals(
            "Decoder Ring UX minefield",
            cleanFeedText(" Decoder&#32;Ring\n\n<span>UX</span> &#x6d;inefield ")
        )
    }

    @Test
    fun cleanFeedTextRemovesTagsDecodedFromEntities() {
        assertEquals(
            "Encoded tag",
            cleanFeedText("&lt;strong&gt;Encoded&lt;/strong&gt; tag")
        )
    }

    @Test
    fun formatPublishedDateFormatsIsoAndRfcDates() {
        assertEquals("26.08.26", formatPublishedDate("2026-08-26T14:30:00Z"))
        assertEquals("12.08.26", formatPublishedDate("Wed, 12 Aug 2026 13:11:45 +0000"))
        assertEquals("12.08.26", formatPublishedDate("12 Aug 2026 13:11:45 GMT"))
        assertEquals(null, formatPublishedDate(null))
        assertEquals(null, formatPublishedDate(""))
    }
}
