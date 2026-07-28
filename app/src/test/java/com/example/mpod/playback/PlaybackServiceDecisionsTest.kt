package com.example.mpod.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackServiceDecisionsTest {

    @Test
    fun `all supported backend speed labels round trip`() {
        val speeds = listOf(0.5f, 0.75f, 1f, 1.3f, 1.5f, 2f)

        speeds.forEach { speed ->
            val label = speed.toPlaybackSpeedLabel()
            assertEquals(speed, label.toPlaybackSpeedOrNull())
        }
    }

    @Test
    fun `unsupported speed values and labels are ignored`() {
        assertNull(1.25f.toPlaybackSpeedLabel())
        assertNull("Speed 1.25x".toPlaybackSpeedOrNull())
        assertNull(null.toPlaybackSpeedOrNull())
    }

    @Test
    fun `foreground reconciliation applies authoritative backend speed`() {
        assertEquals(
            1.5f,
            resolvePlaybackSpeedForReconciliation(
                backendSpeedLabel = "Speed 1.5x",
                pendingSpeedLabel = null
            )
        )
    }

    @Test
    fun `foreground reconciliation does not overwrite pending local speed`() {
        assertNull(
            resolvePlaybackSpeedForReconciliation(
                backendSpeedLabel = "Speed 0.5x",
                pendingSpeedLabel = "Speed 2x"
            )
        )
    }

    @Test
    fun `delayed completion resumes backend next episode only at same ended item`() {
        assertEquals(
            8,
            resolveRetriedCompletionNextEpisode(
                playbackEnded = true,
                completedEpisodeId = 7,
                currentEpisodeId = 7,
                backendNextEpisodeId = 8
            )
        )
    }

    @Test
    fun `delayed completion does not hijack newer playback`() {
        assertNull(
            resolveRetriedCompletionNextEpisode(
                playbackEnded = true,
                completedEpisodeId = 7,
                currentEpisodeId = 9,
                backendNextEpisodeId = 8
            )
        )
        assertNull(
            resolveRetriedCompletionNextEpisode(
                playbackEnded = false,
                completedEpisodeId = 7,
                currentEpisodeId = 7,
                backendNextEpisodeId = 8
            )
        )
    }

    @Test
    fun `completion without backend next episode reconciles without forced target`() {
        assertNull(
            resolveRetriedCompletionNextEpisode(
                playbackEnded = true,
                completedEpisodeId = 7,
                currentEpisodeId = 7,
                backendNextEpisodeId = null
            )
        )
    }

    @Test
    fun `queue reconciliation syncs only a currently playing queued episode`() {
        assertEquals(
            true,
            shouldSyncCurrentBeforeQueueReconciliation(
                currentEpisodeId = 7,
                queuedEpisodeIds = setOf(7, 8),
                isPlaying = true
            )
        )
        assertEquals(
            false,
            shouldSyncCurrentBeforeQueueReconciliation(
                currentEpisodeId = 7,
                queuedEpisodeIds = setOf(7, 8),
                isPlaying = false
            )
        )
        assertEquals(
            false,
            shouldSyncCurrentBeforeQueueReconciliation(
                currentEpisodeId = 7,
                queuedEpisodeIds = setOf(8),
                isPlaying = true
            )
        )
    }

    @Test
    fun `ordinary progress at duration remains an explicit non completion`() {
        val request = playbackRequest(
            episodeId = 7,
            positionSeconds = 100,
            durationSeconds = 100,
            clientUpdatedAt = "2026-07-28T20:00:00Z"
        )

        assertEquals(100, request.positionSeconds)
        assertEquals(false, request.completed)
    }

    @Test
    fun `near end seek remains progress without completion side effects`() {
        val request = playbackRequest(
            episodeId = 7,
            positionSeconds = 99,
            durationSeconds = 100,
            didSeek = true,
            clientUpdatedAt = "2026-07-28T20:00:00Z"
        )

        assertTrue(request.didSeek)
        assertEquals(false, request.completed)
    }

    @Test
    fun `natural audio completion creates explicit completion request`() {
        val request = playbackRequest(
            episodeId = 7,
            positionSeconds = 99,
            durationSeconds = 100,
            completed = true,
            clientUpdatedAt = "2026-07-28T20:00:00Z"
        )

        assertTrue(request.completed)
        assertEquals(99, request.positionSeconds)
        assertEquals(100, request.durationSeconds)
    }

}
