package com.example.mpod.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackQueueReconciliationTest {
    @Test
    fun identicalQueueAndCurrentEpisodeDoNotRebuildPlayer() {
        assertFalse(
            requiresPlayerQueueRebuild(
                currentQueueEpisodeIds = listOf(1L, 2L, 3L),
                backendQueueEpisodeIds = listOf(1L, 2L, 3L),
                currentEpisodeId = 2L,
                targetEpisodeId = 2L,
                preferredEpisodeId = null
            )
        )
    }

    @Test
    fun changedQueueRebuildsPlayer() {
        assertTrue(
            requiresPlayerQueueRebuild(
                currentQueueEpisodeIds = listOf(1L, 2L, 3L),
                backendQueueEpisodeIds = listOf(1L, 3L),
                currentEpisodeId = 1L,
                targetEpisodeId = 1L,
                preferredEpisodeId = null
            )
        )
    }

    @Test
    fun explicitEpisodeSelectionRebuildsPlayerEvenWhenQueueMatches() {
        assertTrue(
            requiresPlayerQueueRebuild(
                currentQueueEpisodeIds = listOf(1L, 2L, 3L),
                backendQueueEpisodeIds = listOf(1L, 2L, 3L),
                currentEpisodeId = 2L,
                targetEpisodeId = 2L,
                preferredEpisodeId = 2L
            )
        )
    }

    @Test
    fun emptyQueueHasNoPlaybackTarget() {
        assertNull(
            resolveQueuePlaybackTarget(
                queue = emptyList(),
                backendActiveEpisodeId = null,
                currentEpisodeId = 1L,
                currentPositionMs = 10_000,
                currentPlayWhenReady = true
            )
        )
    }

    @Test
    fun reorderPreservesCurrentEpisodePositionAndPlayingState() {
        val target = resolveQueuePlaybackTarget(
            queue = queue(3L to 0L, 1L to 20_000L, 2L to 0L),
            backendActiveEpisodeId = 1L,
            currentEpisodeId = 1L,
            currentPositionMs = 42_000,
            currentPlayWhenReady = true
        )

        assertEquals(1L, target?.episodeId)
        assertEquals(42_000L, target?.positionMs)
        assertTrue(target?.playWhenReady == true)
    }

    @Test
    fun removingInactiveEpisodeDoesNotInterruptPlayback() {
        val target = resolveQueuePlaybackTarget(
            queue = queue(1L to 20_000L, 3L to 0L),
            backendActiveEpisodeId = 1L,
            currentEpisodeId = 1L,
            currentPositionMs = 45_000,
            currentPlayWhenReady = true
        )

        assertEquals(1L, target?.episodeId)
        assertEquals(45_000L, target?.positionMs)
        assertTrue(target?.playWhenReady == true)
    }

    @Test
    fun removingActiveEpisodeSelectsBackendTargetWithoutAutoplay() {
        val target = resolveQueuePlaybackTarget(
            queue = queue(2L to 12_000L, 3L to 0L),
            backendActiveEpisodeId = 2L,
            currentEpisodeId = 1L,
            currentPositionMs = 45_000,
            currentPlayWhenReady = true
        )

        assertEquals(2L, target?.episodeId)
        assertEquals(12_000L, target?.positionMs)
        assertFalse(target?.playWhenReady == true)
    }

    @Test
    fun initialLoadUsesSavedActivePositionWithoutAutoplay() {
        val target = resolveQueuePlaybackTarget(
            queue = queue(1L to 2_000L, 2L to 32_000L),
            backendActiveEpisodeId = 2L,
            currentEpisodeId = null,
            currentPositionMs = 0,
            currentPlayWhenReady = false
        )

        assertEquals(2L, target?.episodeId)
        assertEquals(32_000L, target?.positionMs)
        assertFalse(target?.playWhenReady == true)
    }

    private fun queue(vararg entries: Pair<Long, Long>): List<QueueEpisodeState> {
        return entries.map { (episodeId, positionMs) ->
            QueueEpisodeState(episodeId = episodeId, savedPositionMs = positionMs)
        }
    }
}
