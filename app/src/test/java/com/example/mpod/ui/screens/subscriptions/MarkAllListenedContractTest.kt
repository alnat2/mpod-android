package com.example.mpod.ui.screens.subscriptions

import com.example.mpod.data.network.model.MarkAllListenedResponse
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class MarkAllListenedContractTest {
    @Test
    fun successUsesBackendMarkedEpisodeCount() = runBlocking {
        val outcome = executeMarkAllListened {
            Response.success(MarkAllListenedResponse(success = true, markedEpisodes = 12))
        }

        assertEquals(MarkAllListenedOutcome.Success(markedEpisodes = 12), outcome)
    }

    @Test
    fun idempotentZeroCountIsStillSuccess() = runBlocking {
        val outcome = executeMarkAllListened {
            Response.success(MarkAllListenedResponse(success = true, markedEpisodes = 0))
        }

        assertEquals(MarkAllListenedOutcome.Success(markedEpisodes = 0), outcome)
    }

    @Test
    fun podcastNotFoundUsesBackendErrorMessage() = runBlocking {
        val body = """
            {"error":{"code":"PODCAST_NOT_FOUND","message":"Podcast was not found"}}
        """.trimIndent().toResponseBody("application/json".toMediaType())

        val outcome = executeMarkAllListened {
            Response.error(404, body)
        }

        assertEquals(MarkAllListenedOutcome.Failed("Podcast was not found"), outcome)
    }

    @Test
    fun transportFailureIsRetryableThroughSameImmediateAction() = runBlocking {
        val outcome = executeMarkAllListened {
            error("offline")
        }

        assertEquals(MarkAllListenedOutcome.Failed("Could not reach mpod backend."), outcome)
    }

    @Test
    fun failedOperationRestoresOnlyTargetPodcast() {
        val target = podcast(id = 1, unlistened = 2)
        val otherChanged = podcast(id = 2, unlistened = 7)
        val optimisticTarget = target.copy(unlistenedEpisodeCount = 0)

        val restored = listOf(optimisticTarget, otherChanged).restorePodcast(target)

        assertEquals(target, restored[0])
        assertEquals(otherChanged, restored[1])
        assertTrue(restored[1].unlistenedEpisodeCount == 7)
    }

    private fun podcast(id: Int, unlistened: Int): SubscriptionPodcastUi {
        return SubscriptionPodcastUi(
            id = id,
            title = "Podcast $id",
            description = "Description",
            imageUrl = null,
            totalEpisodeCount = unlistened,
            unlistenedEpisodeCount = unlistened,
            episodes = emptyList()
        )
    }
}
