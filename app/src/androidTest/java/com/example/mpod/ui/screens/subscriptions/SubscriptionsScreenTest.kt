package com.example.mpod.ui.screens.subscriptions

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import com.example.mpod.ui.theme.MpodTheme
import org.junit.Rule
import org.junit.Assert.assertEquals
import org.junit.Test

class SubscriptionsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun swipingCarouselChangesSelectedPodcastEpisodesAndCanReturn() {
        composeRule.setContent {
            MpodTheme {
                SubscriptionsScreen(state = populatedState())
            }
        }

        composeRule.onNodeWithText("First episode").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("First podcast fallback cover").assertIsDisplayed()

        composeRule.onNodeWithTag("subscriptions_podcast_pager").performTouchInput { swipeLeft() }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Second episode").assertIsDisplayed()
        composeRule.onAllNodesWithText("First episode").assertCountEquals(0)

        composeRule.onNodeWithTag("subscriptions_podcast_pager").performTouchInput { swipeRight() }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("First episode").assertIsDisplayed()
        composeRule.onAllNodesWithText("Second episode").assertCountEquals(0)
    }

    @Test
    fun subscriptionEpisodeListNeverExposesDragControls() {
        composeRule.setContent {
            MpodTheme {
                SubscriptionsScreen(state = populatedState())
            }
        }

        composeRule.onAllNodesWithContentDescription("Drag").assertCountEquals(0)

        composeRule.onNodeWithTag("subscriptions_podcast_pager").performTouchInput { swipeLeft() }
        composeRule.waitForIdle()

        composeRule.onAllNodesWithContentDescription("Drag").assertCountEquals(0)
    }

    @Test
    fun markAllListenedDispatchesSelectedPodcastId() {
        var selectedPodcastId: Int? = null
        composeRule.setContent {
            MpodTheme {
                SubscriptionsScreen(
                    state = populatedState(),
                    onMarkAllListened = { selectedPodcastId = it }
                )
            }
        }

        composeRule.onNodeWithText("Mark all listened").performClick()
        composeRule.runOnIdle { assertEquals(1, selectedPodcastId) }
    }

    @Test
    fun addToPlaylistDispatchesSelectedEpisodeId() {
        var selectedEpisodeId: Int? = null
        composeRule.setContent {
            MpodTheme {
                SubscriptionsScreen(
                    state = populatedState(),
                    onAddEpisodeToPlaylist = { selectedEpisodeId = it }
                )
            }
        }

        composeRule.onNodeWithContentDescription("Options").performClick()
        composeRule.onNodeWithText("Add to playlist").performClick()

        composeRule.runOnIdle { assertEquals(1, selectedEpisodeId) }
    }

    @Test
    fun visibilityActionChangesFromShowAllToShowUnlistened() {
        composeRule.setContent {
            MpodTheme {
                SubscriptionsScreen(state = populatedState())
            }
        }

        composeRule.onNodeWithContentDescription("Show all").performClick()
        composeRule.onNodeWithContentDescription("Show unlistened").assertIsDisplayed()
    }

    @Test
    fun globalRefreshShowsRefreshingStateOnPodcastCard() {
        composeRule.setContent {
            MpodTheme {
                SubscriptionsScreen(
                    state = populatedState().copy(isRefreshingAll = true)
                )
            }
        }

        composeRule.onNodeWithText("Refreshing").assertIsDisplayed()
    }

    @Test
    fun podcastRefreshShowsRefreshingStateOnlyForSelectedPodcast() {
        composeRule.setContent {
            MpodTheme {
                SubscriptionsScreen(
                    state = populatedState().copy(refreshingPodcastIds = setOf(1))
                )
            }
        }

        composeRule.onNodeWithText("Refreshing").assertIsDisplayed()
        composeRule.onNodeWithTag("subscriptions_podcast_pager").performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Refresh").assertIsDisplayed()
    }

    private fun populatedState(): SubscriptionsUiState {
        return SubscriptionsUiState(
            podcasts = listOf(
                podcast(id = 1, title = "First podcast", episodeTitle = "First episode"),
                podcast(id = 2, title = "Second podcast", episodeTitle = "Second episode")
            )
        )
    }

    private fun podcast(
        id: Int,
        title: String,
        episodeTitle: String
    ): SubscriptionPodcastUi {
        return SubscriptionPodcastUi(
            id = id,
            title = title,
            description = "Podcast description",
            imageUrl = null,
            totalEpisodeCount = 1,
            unlistenedEpisodeCount = 1,
            episodes = listOf(
                SubscriptionEpisodeUi(
                    id = id,
                    title = episodeTitle,
                    durationSeconds = 60,
                    publishedAt = "2026-07-14T10:00:00Z",
                    isListened = false,
                    downloaded = false,
                    summary = null,
                    inPlaylist = false
                )
            )
        )
    }
}
