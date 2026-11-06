package com.example.mpod.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackPositionPersistenceTest {
    @Test
    fun technicalZeroPreservesLastSignificantPosition() {
        assertEquals(42_000L, resolveMeaningfulPlaybackPosition(0L, 42_000L, allowExplicitZero = false))
    }

    @Test
    fun explicitRestartPersistsZero() {
        assertEquals(0L, resolveMeaningfulPlaybackPosition(0L, 42_000L, allowExplicitZero = true))
    }

    @Test
    fun positiveCurrentPositionSupersedesEarlierSignificantPosition() {
        assertEquals(45_000L, resolveMeaningfulPlaybackPosition(45_000L, 42_000L, allowExplicitZero = false))
    }

    @Test
    fun noKnownPositionDoesNotWriteTechnicalZero() {
        assertNull(resolveMeaningfulPlaybackPosition(0L, null, allowExplicitZero = false))
    }
}
