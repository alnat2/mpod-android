package com.example.mpod.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.mpod.ui.theme.MpodTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MiniPlayerTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun miniPlayerRendersControlsAndLabels() {
        composeRule.setContent {
            MpodTheme {
                MiniPlayer(
                    isPlaying = false,
                    onPlayToggle = {},
                    onSeekBackward = {},
                    onSeekForward = {}
                )
            }
        }

        composeRule.onNodeWithTag("mini_player").assertIsDisplayed()
        composeRule.onNodeWithTag("mini_player_seek_backward").assertIsDisplayed()
        composeRule.onNodeWithTag("mini_player_play_pause").assertIsDisplayed()
        composeRule.onNodeWithTag("mini_player_seek_forward").assertIsDisplayed()

        composeRule.onNodeWithText("-15").assertIsDisplayed()
        composeRule.onNodeWithText("+30").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Play").assertIsDisplayed()
    }

    @Test
    fun miniPlayerClickingSeekBackwardCallsCallback() {
        var backwardClicked = false
        composeRule.setContent {
            MpodTheme {
                MiniPlayer(
                    isPlaying = false,
                    onPlayToggle = {},
                    onSeekBackward = { backwardClicked = true },
                    onSeekForward = {}
                )
            }
        }

        composeRule.onNodeWithTag("mini_player_seek_backward").performClick()
        composeRule.runOnIdle {
            assertTrue(backwardClicked)
        }
    }

    @Test
    fun miniPlayerClickingSeekForwardCallsCallback() {
        var forwardClicked = false
        composeRule.setContent {
            MpodTheme {
                MiniPlayer(
                    isPlaying = false,
                    onPlayToggle = {},
                    onSeekBackward = {},
                    onSeekForward = { forwardClicked = true }
                )
            }
        }

        composeRule.onNodeWithTag("mini_player_seek_forward").performClick()
        composeRule.runOnIdle {
            assertTrue(forwardClicked)
        }
    }

    @Test
    fun miniPlayerClickingPlayPauseCallsCallback() {
        var playToggleCount = 0
        composeRule.setContent {
            MpodTheme {
                MiniPlayer(
                    isPlaying = false,
                    onPlayToggle = { playToggleCount++ },
                    onSeekBackward = {},
                    onSeekForward = {}
                )
            }
        }

        composeRule.onNodeWithTag("mini_player_play_pause").performClick()
        composeRule.runOnIdle {
            assertEquals(1, playToggleCount)
        }
    }

    @Test
    fun miniPlayerPlayingStateShowsPauseContentDescription() {
        composeRule.setContent {
            MpodTheme {
                MiniPlayer(
                    isPlaying = true,
                    onPlayToggle = {},
                    onSeekBackward = {},
                    onSeekForward = {}
                )
            }
        }

        composeRule.onNodeWithContentDescription("Pause").assertIsDisplayed()
    }
}
