package com.example.mpod.ui.util

import org.junit.Assert.assertEquals
import org.junit.Test

class UiFormattersTest {
    @Test
    fun remainingTimeSubtractsElapsedPositionAndClampsAtZero() {
        assertEquals(
            "30:49",
            formatRemainingTime(
                durationSeconds = 54 * 60 + 3,
                positionSeconds = 23 * 60 + 14
            )
        )
        assertEquals("0:00", formatRemainingTime(durationSeconds = 60, positionSeconds = 75))
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
