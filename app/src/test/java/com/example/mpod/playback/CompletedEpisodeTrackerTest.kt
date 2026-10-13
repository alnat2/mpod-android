package com.example.mpod.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class CompletedEpisodeTrackerTest {

    private lateinit var tracker: CompletedEpisodeTracker

    @Before
    fun setUp() {
        tracker = CompletedEpisodeTracker()
    }

    @Test
    fun markCompleted_marksEpisodeAndReturnsTrueOnFirstCall() {
        assertFalse(tracker.isCompleted(101L))
        assertTrue(tracker.markCompleted(101L))
        assertTrue(tracker.isCompleted(101L))
        assertEquals(1, tracker.size)
    }

    @Test
    fun markCompleted_duplicateCallsReturnFalseToPreventDuplicateHandling() {
        assertTrue(tracker.markCompleted(101L))
        assertFalse("Second completion event for the same playback must return false", tracker.markCompleted(101L))
        assertFalse("Third completion event for the same playback must return false", tracker.markCompleted(101L))
        assertEquals(1, tracker.size)
    }

    @Test
    fun reset_allowsEpisodeToBeCompletedAgainOnReplay() {
        assertTrue(tracker.markCompleted(101L))
        assertTrue(tracker.isCompleted(101L))

        // User replays, seeks back, or restarts the episode
        assertTrue("reset should return true when removing an existing entry", tracker.reset(101L))
        assertFalse(tracker.isCompleted(101L))
        assertEquals(0, tracker.size)

        // Episode plays to the end again in the new listening session
        assertTrue("Replayed episode must be allowed to complete again", tracker.markCompleted(101L))
        assertTrue(tracker.isCompleted(101L))
    }

    @Test
    fun reset_onUntrackedEpisodeReturnsFalse() {
        assertFalse(tracker.reset(999L))
    }

    @Test
    fun resetAll_clearsAllTrackedEpisodesWhenQueueEmptiedOrReset() {
        tracker.markCompleted(101L)
        tracker.markCompleted(102L)
        tracker.markCompleted(103L)
        assertEquals(3, tracker.size)

        tracker.resetAll()

        assertEquals(0, tracker.size)
        assertFalse(tracker.isCompleted(101L))
        assertFalse(tracker.isCompleted(102L))
        assertFalse(tracker.isCompleted(103L))
    }

    @Test
    fun concurrentAccess_isThreadSafeUnderParallelCalls() {
        val threadCount = 8
        val iterationsPerThread = 500
        val executor = Executors.newFixedThreadPool(threadCount)
        val latch = CountDownLatch(threadCount)

        for (t in 0 until threadCount) {
            executor.submit {
                try {
                    for (i in 0 until iterationsPerThread) {
                        val id = (i % 20).toLong()
                        tracker.markCompleted(id)
                        if (i % 2 == 0) {
                            tracker.reset(id)
                        }
                    }
                } finally {
                    latch.countDown()
                }
            }
        }

        assertTrue("Concurrent operations should complete cleanly", latch.await(5, TimeUnit.SECONDS))
        executor.shutdown()
    }
}
