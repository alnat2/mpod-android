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
                roomQueueEpisodeIds = listOf(1L, 2L, 3L),
                currentEpisodeId = 2L,
                targetEpisodeId = 2L,
                preferredEpisodeId = null
            )
        )
    }

    @Test
    fun changedQueueDoesNotRebuildPlayerWhenActiveEpisodeUnchanged() {
        assertFalse(
            requiresPlayerQueueRebuild(
                currentQueueEpisodeIds = listOf(1L, 2L, 3L),
                roomQueueEpisodeIds = listOf(1L, 3L),
                currentEpisodeId = 1L,
                targetEpisodeId = 1L,
                preferredEpisodeId = null
            )
        )
    }

    @Test
    fun removingActiveEpisodeFromQueueRebuildsPlayer() {
        assertTrue(
            requiresPlayerQueueRebuild(
                currentQueueEpisodeIds = listOf(1L, 2L, 3L),
                roomQueueEpisodeIds = listOf(2L, 3L),
                currentEpisodeId = 1L,
                targetEpisodeId = 2L,
                preferredEpisodeId = null
            )
        )
    }

    @Test
    fun explicitEpisodeSelectionRebuildsPlayerEvenWhenQueueMatches() {
        assertTrue(
            requiresPlayerQueueRebuild(
                currentQueueEpisodeIds = listOf(1L, 2L, 3L),
                roomQueueEpisodeIds = listOf(1L, 2L, 3L),
                currentEpisodeId = 2L,
                targetEpisodeId = 2L,
                preferredEpisodeId = 2L
            )
        )
    }

    @Test
    fun changedMediaUriRebuildsPlayerEvenWhenQueueAndEpisodeMatch() {
        assertTrue(
            requiresPlayerQueueRebuild(
                currentQueueEpisodeIds = listOf(1L, 2L, 3L),
                roomQueueEpisodeIds = listOf(1L, 2L, 3L),
                currentEpisodeId = 2L,
                targetEpisodeId = 2L,
                preferredEpisodeId = null,
                currentMediaUri = "https://cdn.example.com/audio.mp3",
                targetMediaUri = "file:///data/user/0/com.prod.mpod/files/podcasts/ep_2.mp3"
            )
        )
    }

    @Test
    fun matchingMediaUriDoesNotRebuildPlayer() {
        assertFalse(
            requiresPlayerQueueRebuild(
                currentQueueEpisodeIds = listOf(1L, 2L, 3L),
                roomQueueEpisodeIds = listOf(1L, 2L, 3L),
                currentEpisodeId = 2L,
                targetEpisodeId = 2L,
                preferredEpisodeId = null,
                currentMediaUri = "file:///data/user/0/com.prod.mpod/files/podcasts/ep_2.mp3",
                targetMediaUri = "file:///data/user/0/com.prod.mpod/files/podcasts/ep_2.mp3"
            )
        )
    }

    @Test
    fun nullVsNonNullMediaUriRebuildsPlayer() {
        assertTrue(
            requiresPlayerQueueRebuild(
                currentQueueEpisodeIds = listOf(1L, 2L, 3L),
                roomQueueEpisodeIds = listOf(1L, 2L, 3L),
                currentEpisodeId = 2L,
                targetEpisodeId = 2L,
                preferredEpisodeId = null,
                currentMediaUri = null,
                targetMediaUri = "file:///data/user/0/com.prod.mpod/files/podcasts/ep_2.mp3"
            )
        )
    }

    @Test
    fun emptyQueueHasNoPlaybackTarget() {
        assertNull(
            resolveQueuePlaybackTarget(
                queue = emptyList(),
                savedActiveEpisodeId = null,
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
            savedActiveEpisodeId = 1L,
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
            savedActiveEpisodeId = 1L,
            currentEpisodeId = 1L,
            currentPositionMs = 45_000,
            currentPlayWhenReady = true
        )

        assertEquals(1L, target?.episodeId)
        assertEquals(45_000L, target?.positionMs)
        assertTrue(target?.playWhenReady == true)
    }

    @Test
    fun removingActiveEpisodeSelectsSavedTargetWithoutAutoplay() {
        val target = resolveQueuePlaybackTarget(
            queue = queue(2L to 12_000L, 3L to 0L),
            savedActiveEpisodeId = 2L,
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
            savedActiveEpisodeId = 2L,
            currentEpisodeId = null,
            currentPositionMs = 0,
            currentPlayWhenReady = false
        )

        assertEquals(2L, target?.episodeId)
        assertEquals(32_000L, target?.positionMs)
        assertFalse(target?.playWhenReady == true)
    }

    @Test
    fun syncActions_emptyDiffWhenQueuesIdentical() {
        val actions = calculateQueueSyncActions(
            currentQueueIds = listOf(1L, 2L, 3L),
            roomQueueIds = listOf(1L, 2L, 3L),
            currentUris = listOf("uri1", "uri2", "uri3"),
            targetUris = listOf("uri1", "uri2", "uri3"),
            currentPlayingIndex = 0
        )
        assertTrue(actions.isEmpty())
    }

    @Test
    fun syncActions_removesItemsNotInRoomQueue() {
        val actions = calculateQueueSyncActions(
            currentQueueIds = listOf(1L, 2L, 3L),
            roomQueueIds = listOf(1L, 3L),
            currentPlayingIndex = 0
        )
        assertEquals(listOf(QueueAction.Remove(1)), actions)
    }

    @Test
    fun syncActions_movesReorderedItems() {
        val actions = calculateQueueSyncActions(
            currentQueueIds = listOf(1L, 2L, 3L),
            roomQueueIds = listOf(1L, 3L, 2L),
            currentPlayingIndex = 0
        )
        assertEquals(listOf(QueueAction.Move(fromIndex = 2, toIndex = 1)), actions)
    }

    @Test
    fun syncActions_addsNewItemAtEnd() {
        val actions = calculateQueueSyncActions(
            currentQueueIds = listOf(1L, 2L),
            roomQueueIds = listOf(1L, 2L, 3L),
            currentPlayingIndex = 0
        )
        assertEquals(listOf(QueueAction.Add(index = 2, episodeId = 3L)), actions)
    }

    @Test
    fun syncActions_addsNewItemInMiddle() {
        val actions = calculateQueueSyncActions(
            currentQueueIds = listOf(1L, 3L),
            roomQueueIds = listOf(1L, 2L, 3L),
            currentPlayingIndex = 0
        )
        assertEquals(listOf(QueueAction.Add(index = 1, episodeId = 2L)), actions)
    }

    @Test
    fun syncActions_replacesUriForUpcomingItem() {
        val actions = calculateQueueSyncActions(
            currentQueueIds = listOf(1L, 2L),
            roomQueueIds = listOf(1L, 2L),
            currentUris = listOf("uri1", "old_uri2"),
            targetUris = listOf("uri1", "new_uri2"),
            currentPlayingIndex = 0
        )
        assertEquals(listOf(QueueAction.ReplaceUri(index = 1, episodeId = 2L)), actions)
    }

    @Test
    fun syncActions_doesNotReplaceUriForActivePlayingItem() {
        val actions = calculateQueueSyncActions(
            currentQueueIds = listOf(1L, 2L),
            roomQueueIds = listOf(1L, 2L),
            currentUris = listOf("old_uri1", "uri2"),
            targetUris = listOf("new_uri1", "uri2"),
            currentPlayingIndex = 0
        )
        // Active item URI change is handled at rebuild level, not via hot replacement
        assertTrue(actions.isEmpty())
    }

    @Test
    fun syncActions_combinedRemoveMoveAndAdd() {
        // Current: [1, 2, 3, 4]
        // Target:  [4, 1, 5] (2 and 3 removed, 4 moved to front, 5 added at end)
        val actions = calculateQueueSyncActions(
            currentQueueIds = listOf(1L, 2L, 3L, 4L),
            roomQueueIds = listOf(4L, 1L, 5L),
            currentPlayingIndex = 0
        )
        // Removals from end: 3 (index 2), then 2 (index 1) -> leaves [1, 4]
        // Move 4 to index 0 -> leaves [4, 1]
        // Add 5 at index 2 -> leaves [4, 1, 5]
        assertEquals(
            listOf(
                QueueAction.Remove(2),
                QueueAction.Remove(1),
                QueueAction.Move(fromIndex = 1, toIndex = 0),
                QueueAction.Add(index = 2, episodeId = 5L)
            ),
            actions
        )
    }

    @Test
    fun syncActions_replacesUriWhenCurrentUriWasNull() {
        val actions = calculateQueueSyncActions(
            currentQueueIds = listOf(1L, 2L),
            roomQueueIds = listOf(1L, 2L),
            currentUris = listOf("uri1", null),
            targetUris = listOf("uri1", "new_uri2"),
            currentPlayingIndex = 0
        )
        assertEquals(listOf(QueueAction.ReplaceUri(index = 1, episodeId = 2L)), actions)
    }

    @Test
    fun syncActions_removesDuplicateOrTailItems() {
        // Player somehow has duplicate id 1: [1, 2, 1], while room queue is [1, 2]
        val actions = calculateQueueSyncActions(
            currentQueueIds = listOf(1L, 2L, 1L),
            roomQueueIds = listOf(1L, 2L),
            currentPlayingIndex = 0
        )
        assertEquals(listOf(QueueAction.Remove(2)), actions)
    }

    private fun queue(vararg entries: Pair<Long, Long>): List<QueueEpisodeState> {
        return entries.map { (episodeId, positionMs) ->
            QueueEpisodeState(episodeId = episodeId, savedPositionMs = positionMs)
        }
    }
}
