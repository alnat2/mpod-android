package com.example.mpod.ui.screens.home

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.mpod.ui.theme.MpodTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class HomeScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun homeHasNoHeaderActionsAndEpisodeMenuMatchesWeb() {
        var playToggleCount = 0
        composeRule.setContent {
            MpodTheme {
                HomeScreen(onPlayToggle = { playToggleCount += 1 })
            }
        }

        composeRule.onAllNodesWithContentDescription("Refresh").assertCountEquals(0)
        composeRule.onAllNodesWithContentDescription("View").assertCountEquals(0)
        composeRule.onAllNodesWithContentDescription("Options")[0]
            .performScrollTo()
            .performClick()

        composeRule.onNodeWithTag("home_episode_play_action").assertIsDisplayed()
        composeRule.onNodeWithTag("home_episode_playlist_action").assertIsDisplayed()
        composeRule.onAllNodesWithText("Show notes").assertCountEquals(0)
        composeRule.onAllNodesWithText("Download").assertCountEquals(0)
        composeRule.onAllNodesWithText("Mark as listened").assertCountEquals(0)
        composeRule.onAllNodesWithText("Move down").assertCountEquals(0)

        composeRule.onNodeWithTag("home_episode_play_action").performClick()
        composeRule.runOnIdle { assertEquals(1, playToggleCount) }
    }

    @Test
    fun playerDispatchesPlaybackControlsAndOpensNotes() {
        var playCount = 0
        var seekTotal = 0
        var speed: String? = null
        composeRule.setContent {
            MpodTheme {
                HomeScreen(
                    onPlayToggle = { playCount += 1 },
                    onSeekBy = { seekTotal += it },
                    onSpeedChange = { speed = it }
                )
            }
        }

        composeRule.onNodeWithContentDescription("Play").performClick()
        composeRule.onNodeWithContentDescription("Rewind 10s").performClick()
        composeRule.onNodeWithContentDescription("Forward 15s").performClick()
        composeRule.onNodeWithText("1.5").performClick()
        composeRule.onNodeWithText("2.0x").performClick()
        composeRule.onNodeWithContentDescription("Notes").performClick()

        composeRule.onNodeWithContentDescription("Close show notes").assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(1, playCount)
            assertEquals(5, seekTotal)
            assertEquals("2.0", speed)
        }
    }

    @Test
    fun homeMenuDispatchesPlaylistRemoval() {
        var removedEpisodeId: Int? = null
        composeRule.setContent {
            MpodTheme {
                HomeScreen(onRemoveEpisodeFromPlaylist = { removedEpisodeId = it })
            }
        }

        composeRule.onAllNodesWithContentDescription("Options")[0].performClick()
        composeRule.onNodeWithTag("home_episode_playlist_action").performClick()

        composeRule.runOnIdle { assertEquals(1, removedEpisodeId) }
    }

    @Test
    fun homeEmptyAndFailureStatesRemainActionableAndVisible() {
        var rssAdds = 0
        composeRule.setContent {
            MpodTheme {
                HomeScreen(
                    state = HomeUiState(hasPodcasts = false),
                    onAddRssFeed = { rssAdds += 1 }
                )
            }
        }
        composeRule.onNodeWithText("Add RSS feed").performClick()
        composeRule.runOnIdle { assertEquals(1, rssAdds) }
    }
}
