package com.example.mpod.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackQueueReconciliationTest {
    @Test
    fun emptyQueueHasNoPlaybackTarget() {
        assertNull(
            resolveQueuePlaybackTarget(
                queue = emptyList(),
                backendActiveEpisodeId = null,
                currentEpisodeId = 1,
                currentPositionMs = 10_000,
                currentPlayWhenReady = true
            )
        )
    }

    @Test
    fun reorderPreservesCurrentEpisodePositionAndPlayingState() {
        val target = resolveQueuePlaybackTarget(
            queue = queue(3 to 0, 1 to 20_000, 2 to 0),
            backendActiveEpisodeId = 1,
            currentEpisodeId = 1,
            currentPositionMs = 42_000,
            currentPlayWhenReady = true
        )

        assertEquals(1, target?.episodeId)
        assertEquals(42_000L, target?.positionMs)
        assertTrue(target?.playWhenReady == true)
    }

    @Test
    fun removingInactiveEpisodeDoesNotInterruptPlayback() {
        val target = resolveQueuePlaybackTarget(
            queue = queue(1 to 20_000, 3 to 0),
            backendActiveEpisodeId = 1,
            currentEpisodeId = 1,
            currentPositionMs = 45_000,
            currentPlayWhenReady = true
        )

        assertEquals(1, target?.episodeId)
        assertEquals(45_000L, target?.positionMs)
        assertTrue(target?.playWhenReady == true)
    }

    @Test
    fun removingActiveEpisodeSelectsBackendTargetWithoutAutoplay() {
        val target = resolveQueuePlaybackTarget(
            queue = queue(2 to 12_000, 3 to 0),
            backendActiveEpisodeId = 2,
            currentEpisodeId = 1,
            currentPositionMs = 45_000,
            currentPlayWhenReady = true
        )

        assertEquals(2, target?.episodeId)
        assertEquals(12_000L, target?.positionMs)
        assertFalse(target?.playWhenReady == true)
    }

    @Test
    fun completionFallbackStartsPreferredEpisodeFromBeginning() {
        val target = resolveQueuePlaybackTarget(
            queue = queue(2 to 12_000, 3 to 8_000),
            backendActiveEpisodeId = null,
            currentEpisodeId = 1,
            currentPositionMs = 45_000,
            currentPlayWhenReady = false,
            preferredEpisodeId = 3,
            forcePlayPreferred = true
        )

        assertEquals(3, target?.episodeId)
        assertEquals(0L, target?.positionMs)
        assertTrue(target?.playWhenReady == true)
    }

    @Test
    fun initialLoadUsesSavedActivePositionWithoutAutoplay() {
        val target = resolveQueuePlaybackTarget(
            queue = queue(1 to 2_000, 2 to 32_000),
            backendActiveEpisodeId = 2,
            currentEpisodeId = null,
            currentPositionMs = 0,
            currentPlayWhenReady = false
        )

        assertEquals(2, target?.episodeId)
        assertEquals(32_000L, target?.positionMs)
        assertFalse(target?.playWhenReady == true)
    }

    private fun queue(vararg entries: Pair<Int, Int>): List<QueueEpisodeState> {
        return entries.map { (episodeId, positionMs) ->
            QueueEpisodeState(episodeId = episodeId, savedPositionMs = positionMs.toLong())
        }
    }
}
