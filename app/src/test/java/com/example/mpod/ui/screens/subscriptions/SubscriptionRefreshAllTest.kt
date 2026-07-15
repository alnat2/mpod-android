package com.example.mpod.ui.screens.subscriptions

import com.example.mpod.data.network.model.SchedulerStatusDto
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class SubscriptionRefreshAllTest {
    @Test
    fun waitsThroughRunningStateUntilCompleted() = runBlocking {
        val statuses = ArrayDeque(
            listOf(
                scheduler(state = "running"),
                scheduler(state = "completed")
            )
        )
        var calls = 0

        val result = awaitRefreshAllCompletion(pollIntervalMs = 0) {
            calls += 1
            statuses.removeFirst()
        }

        assertEquals(RefreshAllCompletion.Completed, result)
        assertEquals(2, calls)
    }

    @Test
    fun retriesTransientStatusFailure() = runBlocking {
        var calls = 0

        val result = awaitRefreshAllCompletion(pollIntervalMs = 0) {
            calls += 1
            if (calls == 1) error("Temporary network failure")
            scheduler(state = "completed")
        }

        assertEquals(RefreshAllCompletion.Completed, result)
        assertEquals(2, calls)
    }

    @Test
    fun returnsBackendFailureMessage() = runBlocking {
        val result = awaitRefreshAllCompletion(pollIntervalMs = 0) {
            scheduler(state = "failed", lastError = "Planet Money refresh failed")
        }

        assertEquals(
            RefreshAllCompletion.Failed("Planet Money refresh failed"),
            result
        )
    }

    @Test
    fun usesFallbackForFailedStateWithoutMessage() = runBlocking {
        val result = awaitRefreshAllCompletion(pollIntervalMs = 0) {
            scheduler(state = "failed")
        }

        assertEquals(
            RefreshAllCompletion.Failed("Failed to refresh podcasts."),
            result
        )
    }

    @Test
    fun rejectsUnknownTerminalState() = runBlocking {
        val result = awaitRefreshAllCompletion(pollIntervalMs = 0) {
            scheduler(state = "unexpected")
        }

        assertEquals(
            RefreshAllCompletion.Failed("Backend returned an unknown refresh status."),
            result
        )
    }

    private fun scheduler(
        state: String,
        lastError: String? = null
    ) = SchedulerStatusDto(
        state = state,
        lastRunAt = null,
        lastSuccessAt = null,
        lastFailureAt = null,
        lastError = lastError
    )
}
