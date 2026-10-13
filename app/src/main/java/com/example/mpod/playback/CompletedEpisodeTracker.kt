package com.example.mpod.playback

import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks completed episode IDs to prevent duplicate completion events during a single playback session,
 * while allowing replay and re-completion when an episode is restarted, seeked, or re-queued.
 */
internal class CompletedEpisodeTracker {
    private val completedIds = ConcurrentHashMap.newKeySet<Long>()

    /**
     * Attempts to mark the episode as completed.
     * @return true if the episode was newly marked completed, false if it was already marked.
     */
    fun markCompleted(episodeId: Long): Boolean = completedIds.add(episodeId)

    /**
     * Resets the completion state for an episode (e.g. when restarted, seeked, or selected).
     * @return true if the episode was previously marked completed, false otherwise.
     */
    fun reset(episodeId: Long): Boolean = completedIds.remove(episodeId)

    /**
     * Clears all completed episode records (e.g. when queue is cleared).
     */
    fun resetAll() {
        completedIds.clear()
    }

    /**
     * Checks if the episode is currently marked completed.
     */
    fun isCompleted(episodeId: Long): Boolean = completedIds.contains(episodeId)

    /**
     * Current number of tracked completed episodes.
     */
    val size: Int get() = completedIds.size
}
