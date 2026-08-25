package com.example.mpod.ui.screens.home

import androidx.compose.geometry.Offset
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import com.example.mpod.ui.theme.MpodTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class HomeScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun sampleState(): HomeUiState {
        return HomeUiState(
            hasPodcasts = true,
            queue = listOf(
                HomeEpisodeUi(
                    id = 1L,
                    title = "Why store loyalty cards became a UX minefield",
                    podcastTitle = "Decoder Ring",
                    durationSeconds = 54 * 60,
                    playbackPositionSeconds = 0,
                    isListened = false,
                    downloaded = false,
                    summary = "A story about loyalty cards, UX traps, and the tiny design decisions that become habits."
                ),
                HomeEpisodeUi(
                    id = 2L,
                    title = "How public transit maps teach invisible habits",
                    podcastTitle = "Decoder Ring",
                    durationSeconds = 36 * 60,
                    playbackPositionSeconds = 0,
                    isListened = false,
                    downloaded = false,
                    summary = "Transit maps look simple, but the choices behind them shape how people move through cities."
                )
            )
        )
    }

    @Test
    fun playerPlaylistUsesInlineActions() {
        var playToggleCount = 0
        composeRule.setContent {
            MpodTheme {
                HomeScreen(
                    state = sampleState(),
                    onPlayToggle = { playToggleCount += 1 }
                )
            }
        }

        composeRule.onAllNodesWithContentDescription("Refresh").assertCountEquals(0)
        composeRule.onAllNodesWithContentDescription("View").assertCountEquals(0)
        composeRule.onNodeWithContentDescription("Play Why store loyalty cards became a UX minefield")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Remove Why store loyalty cards became a UX minefield from playlist")
            .assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Play Why store loyalty cards became a UX minefield").performClick()
        composeRule.runOnIdle { assertEquals(1, playToggleCount) }
    }

    @Test
    fun playerDispatchesPlaybackControlsAndOpensNotes() {
        var playCount = 0
        var seekTotal = 0
        var absoluteSeek = 0f
        var absoluteSeekCount = 0
        var speed: String? = null
        composeRule.setContent {
            MpodTheme {
                HomeScreen(
                    state = sampleState(),
                    playbackStateProvider = {
                        HomePlaybackUiState(
                            currentEpisodeId = 1L,
                            positionSeconds = 23 * 60 + 14,
                            durationSeconds = 37 * 60 + 17,
                            speedLabel = "1.5"
                        )
                    },
                    onPlayToggle = { playCount += 1 },
                    onSeekBy = { seekTotal += it },
                    onSeekTo = {
                        absoluteSeek = it
                        absoluteSeekCount += 1
                    },
                    onSpeedChange = { speed = it }
                )
            }
        }

        composeRule.onNodeWithContentDescription("Play").performClick()
        composeRule.onNodeWithContentDescription("Rewind 15 seconds").performClick()
        composeRule.onNodeWithContentDescription("Forward 30 seconds").performClick()
        composeRule.onNodeWithText("-15").assertIsDisplayed()
        composeRule.onNodeWithText("+30").assertIsDisplayed()
        composeRule.onNodeWithText("14:03").assertIsDisplayed()
        composeRule.onNodeWithText("1.5").performClick()
        composeRule.onNodeWithText("2.0x").performClick()
        composeRule.onNodeWithContentDescription("Show notes").performClick()
        composeRule.onNodeWithTag("player_seek_bar").performTouchInput {
            click(Offset(width * 0.75f, height / 2f))
        }

        composeRule.onNodeWithContentDescription("Close show notes").assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(1, playCount)
            assertEquals(15, seekTotal)
            assertEquals(0.75f, absoluteSeek, 0.02f)
            assertEquals(1, absoluteSeekCount)
            assertEquals("2.0", speed)
        }
    }

    @Test
    fun homeMenuDispatchesPlaylistRemoval() {
        var removedEpisodeId: Long? = null
        composeRule.setContent {
            MpodTheme {
                HomeScreen(
                    state = sampleState(),
                    onRemoveEpisodeFromPlaylist = { removedEpisodeId = it }
                )
            }
        }

        composeRule.onNodeWithContentDescription(
            "Remove Why store loyalty cards became a UX minefield from playlist"
        ).performClick()

        composeRule.runOnIdle { assertEquals(1L, removedEpisodeId) }
    }

    @Test
    fun homeEmptyAndFailureStatesRemainActionableAndVisible() {
        var rssAdds = 0
        var opmlImports = 0
        composeRule.setContent {
            MpodTheme {
                HomeScreen(
                    state = HomeUiState(hasPodcasts = false),
                    onAddRssFeed = { rssAdds += 1 },
                    onImportOpml = { opmlImports += 1 }
                )
            }
        }
        composeRule.onNodeWithText("Add RSS feed").performClick()
        composeRule.onNodeWithText("Import OPML").performClick()
        composeRule.runOnIdle {
            assertEquals(1, rssAdds)
            assertEquals(1, opmlImports)
        }
    }

    @Test
    fun homeLoadFailureOffersRetry() {
        var retryCount = 0
        composeRule.setContent {
            MpodTheme {
                HomeScreen(
                    state = HomeUiState(errorMessage = "Could not load playlist."),
                    onRetryLoad = { retryCount += 1 }
                )
            }
        }

        composeRule.onNodeWithText("Could not load playlist.").assertIsDisplayed()
        composeRule.onNodeWithText("Retry").performClick()
        composeRule.runOnIdle { assertEquals(1, retryCount) }
    }

    @Test
    fun homeLoadingStateIsVisibleWithoutInventedQueueData() {
        composeRule.setContent {
            MpodTheme {
                HomeScreen(state = HomeUiState(isLoading = true))
            }
        }

        composeRule.onNodeWithText("Loading playlist").assertIsDisplayed()
        composeRule.onAllNodesWithText("Playlist is empty").assertCountEquals(0)
    }

    @Test
    fun emptyPlaylistIsDistinctFromNoSubscriptions() {
        composeRule.setContent {
            MpodTheme {
                HomeScreen(state = HomeUiState(hasPodcasts = true, queue = emptyList()))
            }
        }

        composeRule.onNodeWithText("Playlist is empty").assertIsDisplayed()
        composeRule.onNodeWithText("Add episodes from Subscriptions to start listening.").assertIsDisplayed()
        composeRule.onAllNodesWithText("Add RSS feed").assertCountEquals(0)
    }

    @Test
    fun tappingQueueRowStartsThatEpisode() {
        var playedEpisodeId: Long? = null
        composeRule.setContent {
            MpodTheme {
                HomeScreen(
                    state = sampleState(),
                    onPlayEpisode = { playedEpisodeId = it }
                )
            }
        }

        composeRule.onNodeWithText("How public transit maps teach invisible habits")
            .performClick()
        composeRule.runOnIdle { assertEquals(2L, playedEpisodeId) }
    }

    @Test
    fun missingShowNotesUseTruthfulEmptyState() {
        composeRule.setContent {
            MpodTheme {
                HomeScreen(
                    state = HomeUiState(
                        hasPodcasts = true,
                        queue = listOf(
                            HomeEpisodeUi(
                                id = 1L,
                                title = "Episode without notes",
                                podcastTitle = "Podcast",
                                durationSeconds = 60,
                                playbackPositionSeconds = 0,
                                isListened = false,
                                downloaded = false,
                                summary = null
                            )
                        )
                    )
                )
            }
        }

        composeRule.onNodeWithContentDescription("Show notes").performClick()
        composeRule.onNodeWithText("No show notes for this episode.").assertIsDisplayed()
    }
}
