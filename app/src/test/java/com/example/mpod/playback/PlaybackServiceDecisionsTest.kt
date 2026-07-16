package com.example.mpod.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun `backend completion window starts exactly fifteen seconds before duration`() {
        assertEquals(false, countsAsBackendCompletion(positionSeconds = 84, durationSeconds = 100))
        assertEquals(true, countsAsBackendCompletion(positionSeconds = 85, durationSeconds = 100))
        assertEquals(true, countsAsBackendCompletion(positionSeconds = 4, durationSeconds = 6))
        assertEquals(false, countsAsBackendCompletion(positionSeconds = 4, durationSeconds = 0))
    }

    @Test
    fun `paused threshold completion requires queue reconciliation`() {
        assertEquals(
            true,
            shouldReconcilePausedThresholdCompletion(
                completedByPosition = true,
                isPlaying = false,
                completedEpisodeId = 7,
                currentEpisodeId = 7
            )
        )
    }

    @Test
    fun `threshold completion does not hijack continuing or newer playback`() {
        assertFalse(
            shouldReconcilePausedThresholdCompletion(
                completedByPosition = true,
                isPlaying = true,
                completedEpisodeId = 7,
                currentEpisodeId = 7
            )
        )
        assertFalse(
            shouldReconcilePausedThresholdCompletion(
                completedByPosition = true,
                isPlaying = false,
                completedEpisodeId = 7,
                currentEpisodeId = 9
            )
        )
    }
}
