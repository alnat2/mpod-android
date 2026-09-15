package com.example.mpod.ui.screens.subscriptions

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import com.example.mpod.ui.theme.MpodTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SubscriptionsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun carouselShowsBothNeighborsAndAlignsEpisodesWithSelectedCard() {
        composeRule.setContent {
            MpodTheme {
                SubscriptionsScreen(state = populatedState())
            }
        }

        val pagerBounds = composeRule.onNodeWithTag("subscriptions_podcast_pager")
            .fetchSemanticsNode()
            .boundsInRoot
        val selectedCardBounds = composeRule.onNodeWithTag("subscription_podcast_card_selected")
            .fetchSemanticsNode()
            .boundsInRoot
        val previousCardBounds = composeRule.onNodeWithTag("subscription_podcast_card_previous")
            .fetchSemanticsNode()
            .boundsInRoot
        val nextCardBounds = composeRule.onNodeWithTag("subscription_podcast_card_next")
            .fetchSemanticsNode()
            .boundsInRoot
        val episodeHeaderBounds = composeRule.onNodeWithTag("subscriptions_episode_header")
            .fetchSemanticsNode()
            .boundsInRoot
        val episodeRowBounds = composeRule.onNodeWithTag("subscription_episode_row_1")
            .fetchSemanticsNode()
            .boundsInRoot

        val leftInset = selectedCardBounds.left - pagerBounds.left
        val rightInset = pagerBounds.right - selectedCardBounds.right
        assertEquals(0f, pagerBounds.left, 1f)
        assertTrue(leftInset > 0f)
        assertEquals(leftInset, rightInset, 1f)
        assertTrue(previousCardBounds.right > pagerBounds.left)
        assertTrue(previousCardBounds.width > 0f)
        assertTrue(nextCardBounds.left < pagerBounds.right)
        assertTrue(nextCardBounds.width > 0f)
        assertEquals(selectedCardBounds.left, episodeHeaderBounds.left, 1f)
        assertEquals(selectedCardBounds.right, episodeHeaderBounds.right, 1f)
        assertEquals(selectedCardBounds.left, episodeRowBounds.left, 1f)
        assertEquals(selectedCardBounds.right, episodeRowBounds.right, 1f)
        composeRule.onAllNodesWithText("Unsubscribe").assertCountEquals(1)
    }

    @Test
    fun headerSubtitleShowsPodcastsWithUnlistenedCount() {
        composeRule.setContent {
            MpodTheme {
                SubscriptionsScreen(
                    state = SubscriptionsUiState(
                        podcasts = listOf(
                            podcast(
                                id = 1L,
                                title = "Active podcast",
                                episodeTitle = "Fresh episode",
                                isListened = false
                            ),
                            podcast(
                                id = 2L,
                                title = "Caught up podcast",
                                episodeTitle = "Old episode",
                                isListened = true
                            )
                        )
                    )
                )
            }
        }

        composeRule.onNodeWithText("2 podcasts · 1 unlistened").assertIsDisplayed()
    }

    @Test
    fun swipingCarouselChangesSelectedPodcastEpisodesAndCanReturn() {
        composeRule.setContent {
            MpodTheme {
                SubscriptionsScreen(state = populatedState())
            }
        }

        composeRule.onNodeWithText("First episode").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("First podcast fallback cover").assertIsDisplayed()

        composeRule.onNodeWithTag("subscriptions_podcast_pager").performTouchInput {
            swipeToNextPodcast()
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Second episode").assertIsDisplayed()
        composeRule.onAllNodesWithText("First episode").assertCountEquals(0)

        composeRule.onNodeWithTag("subscriptions_podcast_pager").performTouchInput {
            swipeToPreviousPodcast()
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("First episode").assertIsDisplayed()
        composeRule.onAllNodesWithText("Second episode").assertCountEquals(0)
    }

    @Test
    fun selectedPodcastAndEpisodesRemainSynchronizedAcrossGesturesAndFilters() {
        var refreshedPodcastId: Long? = null
        var unsubscribedPodcastId: Long? = null
        var markedAllListenedPodcastId: Long? = null
        var addedEpisodeId: Long? = null

        val alphaPodcast = SubscriptionPodcastUi(
            id = 101L,
            title = "Alpha Podcast",
            description = "Alpha Description",
            imageUrl = null,
            totalEpisodeCount = 2,
            unlistenedEpisodeCount = 1,
            episodes = listOf(
                SubscriptionEpisodeUi(
                    id = 1001L,
                    title = "Alpha Unlistened Episode",
                    durationSeconds = 120,
                    publishedAt = "2026-09-01T10:00:00Z",
                    isListened = false,
                    downloaded = false,
                    summary = "Alpha unlistened notes",
                    inPlaylist = false
                ),
                SubscriptionEpisodeUi(
                    id = 1002L,
                    title = "Alpha Listened Episode",
                    durationSeconds = 180,
                    publishedAt = "2026-08-25T10:00:00Z",
                    isListened = true,
                    downloaded = false,
                    summary = "Alpha listened notes",
                    inPlaylist = false
                )
            )
        )
        val betaPodcast = SubscriptionPodcastUi(
            id = 202L,
            title = "Beta Podcast",
            description = "Beta Description",
            imageUrl = null,
            totalEpisodeCount = 3,
            unlistenedEpisodeCount = 2,
            episodes = listOf(
                SubscriptionEpisodeUi(
                    id = 2001L,
                    title = "Beta Episode",
                    durationSeconds = 240,
                    publishedAt = "2026-09-02T10:00:00Z",
                    isListened = false,
                    downloaded = false,
                    summary = "Beta notes",
                    inPlaylist = false
                )
            )
        )
        val gammaPodcast = SubscriptionPodcastUi(
            id = 303L,
            title = "Gamma Podcast",
            description = "Gamma Description",
            imageUrl = null,
            totalEpisodeCount = 4,
            unlistenedEpisodeCount = 3,
            episodes = listOf(
                SubscriptionEpisodeUi(
                    id = 3001L,
                    title = "Gamma Episode",
                    durationSeconds = 300,
                    publishedAt = "2026-09-03T10:00:00Z",
                    isListened = false,
                    downloaded = false,
                    summary = "Gamma notes",
                    inPlaylist = false
                )
            )
        )

        composeRule.setContent {
            MpodTheme {
                SubscriptionsScreen(
                    state = SubscriptionsUiState(
                        podcasts = listOf(alphaPodcast, betaPodcast, gammaPodcast)
                    ),
                    onRefreshPodcast = { refreshedPodcastId = it },
                    onUnsubscribePodcast = { unsubscribedPodcastId = it },
                    onMarkAllListened = { markedAllListenedPodcastId = it },
                    onAddEpisodeToPlaylist = { addedEpisodeId = it }
                )
            }
        }

        // 1. Initial settled state: Alpha Podcast is selected (page 1)
        composeRule.onNodeWithTag("subscription_podcast_card_selected").assertIsDisplayed()
        assertSelectedPodcast("Alpha Podcast")
        composeRule.onNodeWithText("2 / 1 episodes").assertIsDisplayed()
        composeRule.onNodeWithText("Alpha Unlistened Episode").assertIsDisplayed()
        composeRule.onAllNodesWithText("Beta Episode").assertCountEquals(0)
        composeRule.onAllNodesWithText("Gamma Episode").assertCountEquals(0)

        // 2. Swipe towards next podcast (Beta):
        // Control Compose clock explicitly without automatic advance
        try {
            composeRule.mainClock.autoAdvance = false
            // Single gesture scope with complete swipe
            composeRule.onNodeWithTag("subscriptions_podcast_pager").performTouchInput {
                swipe(
                    start = Offset(width * 0.8f, height / 2f),
                    end = Offset(width * 0.2f, height / 2f),
                    durationMillis = 200L
                )
            }
            // Advance one frame so composition renders the post-touch state while settle animation is stopped
            composeRule.mainClock.advanceTimeByFrame()

            // Intermediate state: settle animation is stopped (clock autoAdvance is false).
            // Visually selected card is Beta Podcast.
            // Contract: visible selected card, summary, and episode list MUST be consistent.
            assertSelectedPodcast("Beta Podcast")
            composeRule.onNodeWithText("3 / 2 episodes").assertIsDisplayed()
            composeRule.onNodeWithText("Beta Episode").assertIsDisplayed()
            composeRule.onAllNodesWithText("Alpha Unlistened Episode").assertCountEquals(0)

            // Drive the virtual Compose clock long enough for the pager settle animation.
            // Advancing one frame at a time keeps the test deterministic without real-time waits.
            repeat(120) {
                composeRule.mainClock.advanceTimeByFrame()
            }
        } finally {
            composeRule.mainClock.autoAdvance = true
        }
        composeRule.waitForIdle()

        // 3. Settle on Beta Podcast: callbacks receive Beta's ID (202L) and episode ID (2001L)
        assertSelectedPodcast("Beta Podcast")
        composeRule.onNodeWithText("3 / 2 episodes").assertIsDisplayed()
        composeRule.onNodeWithText("Beta Episode").assertIsDisplayed()
        composeRule.onNodeWithText("Refresh").performClick()
        composeRule.runOnIdle { assertEquals(202L, refreshedPodcastId) }

        composeRule.onNodeWithText("Mark all listened").performClick()
        composeRule.runOnIdle { assertEquals(202L, markedAllListenedPodcastId) }

        composeRule.onNodeWithContentDescription("Add Beta Episode to playlist").performClick()
        composeRule.runOnIdle { assertEquals(2001L, addedEpisodeId) }

        composeRule.onNodeWithText("Unsubscribe").performClick()
        composeRule.runOnIdle { assertEquals(202L, unsubscribedPodcastId) }

        // 4. Swipe next to Gamma Podcast
        composeRule.onNodeWithTag("subscriptions_podcast_pager").performTouchInput {
            swipeToNextPodcast()
        }
        composeRule.waitForIdle()
        assertSelectedPodcast("Gamma Podcast")
        composeRule.onNodeWithText("4 / 3 episodes").assertIsDisplayed()
        composeRule.onNodeWithText("Gamma Episode").assertIsDisplayed()
        composeRule.onAllNodesWithText("Beta Episode").assertCountEquals(0)

        // 5. Right wrap-around: from Gamma (last), swipe next wraps around to Alpha (first)
        composeRule.onNodeWithTag("subscriptions_podcast_pager").performTouchInput {
            swipeToNextPodcast()
        }
        composeRule.waitForIdle()
        assertSelectedPodcast("Alpha Podcast")
        composeRule.onNodeWithText("2 / 1 episodes").assertIsDisplayed()
        composeRule.onNodeWithText("Alpha Unlistened Episode").assertIsDisplayed()
        composeRule.onAllNodesWithText("Gamma Episode").assertCountEquals(0)

        // 6. Left wrap-around: from Alpha (first), swipe previous wraps around to Gamma (last)
        composeRule.onNodeWithTag("subscriptions_podcast_pager").performTouchInput {
            swipeToPreviousPodcast()
        }
        composeRule.waitForIdle()
        assertSelectedPodcast("Gamma Podcast")
        composeRule.onNodeWithText("4 / 3 episodes").assertIsDisplayed()
        composeRule.onNodeWithText("Gamma Episode").assertIsDisplayed()
        composeRule.onAllNodesWithText("Alpha Unlistened Episode").assertCountEquals(0)

        // 7. Filter switching (Show all / Show unlistened)
        // Return to Alpha
        composeRule.onNodeWithTag("subscriptions_podcast_pager").performTouchInput {
            swipeToNextPodcast()
        }
        composeRule.waitForIdle()
        assertSelectedPodcast("Alpha Podcast")
        composeRule.onNodeWithText("2 / 1 episodes").assertIsDisplayed()
        // Switch to Show all
        composeRule.onNodeWithContentDescription("Show all").performClick()
        composeRule.waitForIdle()
        // Selected podcast is still Alpha Podcast; both unlistened and listened episodes are visible
        assertSelectedPodcast("Alpha Podcast")
        composeRule.onNodeWithText("2 / 1 episodes").assertIsDisplayed()
        composeRule.onNodeWithText("Alpha Unlistened Episode").assertIsDisplayed()
        composeRule.onNodeWithText("Alpha Listened Episode").assertIsDisplayed()
        composeRule.onAllNodesWithText("Gamma Episode").assertCountEquals(0)

        // Switch back to Show unlistened
        composeRule.onNodeWithContentDescription("Show unlistened").performClick()
        composeRule.waitForIdle()
        assertSelectedPodcast("Alpha Podcast")
        composeRule.onNodeWithText("2 / 1 episodes").assertIsDisplayed()
        composeRule.onNodeWithText("Alpha Unlistened Episode").assertIsDisplayed()
        composeRule.onAllNodesWithText("Alpha Listened Episode").assertCountEquals(0)
    }

    @Test
    fun swipingBackwardFromFirstPodcastWrapsToLastAndCanReturn() {
        composeRule.setContent {
            MpodTheme {
                SubscriptionsScreen(state = populatedState())
            }
        }

        composeRule.onNodeWithText("First episode").assertIsDisplayed()

        composeRule.onNodeWithTag("subscriptions_podcast_pager").performTouchInput {
            swipeToPreviousPodcast()
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Second episode").assertIsDisplayed()

        composeRule.onNodeWithTag("subscriptions_podcast_pager").performTouchInput {
            swipeToNextPodcast()
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("First episode").assertIsDisplayed()
    }

    @Test
    fun markAllListenedDispatchesSelectedPodcastId() {
        var selectedPodcastId: Long? = null
        composeRule.setContent {
            MpodTheme {
                SubscriptionsScreen(
                    state = populatedState(),
                    onMarkAllListened = { selectedPodcastId = it }
                )
            }
        }

        composeRule.onNodeWithText("Mark all listened").performClick()
        composeRule.runOnIdle { assertEquals(1L, selectedPodcastId) }
    }

    @Test
    fun addToPlaylistDispatchesSelectedEpisodeId() {
        var selectedEpisodeId: Long? = null
        composeRule.setContent {
            MpodTheme {
                SubscriptionsScreen(
                    state = populatedState(),
                    onAddEpisodeToPlaylist = { selectedEpisodeId = it }
                )
            }
        }

        composeRule.onNodeWithContentDescription("Add First episode to playlist").assertIsDisplayed()
        composeRule.onNodeWithTag("episode_action_icon_playlist", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("episode_action_icon_notes", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onAllNodesWithText("Download").assertCountEquals(0)
        composeRule.onNodeWithTag("episode_action_icon_listened", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Add First episode to playlist").performClick()

        composeRule.runOnIdle { assertEquals(1L, selectedEpisodeId) }
    }

    @Test
    fun showNotesOpensSelectedEpisodeNotes() {
        composeRule.setContent {
            MpodTheme {
                SubscriptionsScreen(state = populatedState())
            }
        }

        composeRule.onNodeWithContentDescription("Show notes for First episode").performClick()

        composeRule.onNodeWithContentDescription("Close show notes").assertIsDisplayed()
        composeRule.onNodeWithText("First episode notes").assertIsDisplayed()
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
        composeRule.onNodeWithContentDescription("Refresh").assertHasNoClickAction()
    }

    @Test
    fun loadingStateDoesNotExposeSubscriptionMutationActions() {
        composeRule.setContent {
            MpodTheme {
                SubscriptionsScreen(state = SubscriptionsUiState(isLoading = true))
            }
        }

        composeRule.onNodeWithText("Loading subscriptions").assertIsDisplayed()
        composeRule.onAllNodesWithText("Refresh").assertCountEquals(0)
        composeRule.onAllNodesWithText("Unsubscribe").assertCountEquals(0)
        composeRule.onAllNodesWithText("Mark all listened").assertCountEquals(0)
    }

    @Test
    fun caughtUpStateIsDistinctFromEmptyLibraryAndCanShowListenedEpisodes() {
        val firstCaughtUpPodcast = podcast(
            id = 1L,
            title = "First caught up podcast",
            episodeTitle = "First listened episode"
        ).copy(
            unlistenedEpisodeCount = 0,
            episodes = listOf(
                podcast(1L, "First caught up podcast", "First listened episode")
                    .episodes.single().copy(isListened = true)
            )
        )
        val secondCaughtUpPodcast = podcast(
            id = 2L,
            title = "Second caught up podcast",
            episodeTitle = "Second listened episode"
        ).copy(
            unlistenedEpisodeCount = 0,
            episodes = listOf(
                podcast(2L, "Second caught up podcast", "Second listened episode")
                    .episodes.single().copy(isListened = true)
            )
        )
        composeRule.setContent {
            MpodTheme {
                SubscriptionsScreen(
                    state = SubscriptionsUiState(
                        podcasts = listOf(firstCaughtUpPodcast, secondCaughtUpPodcast)
                    )
                )
            }
        }

        composeRule.onNodeWithText("All caught up").assertIsDisplayed()
        composeRule.onAllNodesWithText("No podcasts yet").assertCountEquals(0)
        composeRule.onNodeWithText("Show all").performClick()
        composeRule.onNodeWithTag("subscriptions_podcast_pager").assertIsDisplayed()
        composeRule.onNodeWithText("First listened episode").assertIsDisplayed()
    }

    @Test
    fun episodeLoadFailureStaysScopedWhileOtherPodcastRemainsUsable() {
        val failedPodcast = podcast(1L, "Failed podcast", "Missing episode").copy(
            episodes = emptyList(),
            totalEpisodeCount = 0,
            unlistenedEpisodeCount = 0,
            errorMessage = "Episodes unavailable. Refresh this podcast to try again.",
            episodesUnavailable = true
        )
        val healthyPodcast = podcast(2L, "Healthy podcast", "Healthy episode")
        composeRule.setContent {
            MpodTheme {
                SubscriptionsScreen(
                    state = SubscriptionsUiState(
                        actionErrorMessage = "Some podcast episodes could not be loaded.",
                        podcasts = listOf(failedPodcast, healthyPodcast)
                    )
                )
            }
        }

        composeRule.onNodeWithText(
            "Episodes could not be loaded. Use Refresh on the podcast card to try again."
        ).assertIsDisplayed()
        composeRule.onNodeWithTag("subscriptions_podcast_pager").performTouchInput {
            swipeToNextPodcast()
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Healthy episode").assertIsDisplayed()
    }

    @Test
    fun podcastRefreshShowsRefreshingStateOnlyForSelectedPodcast() {
        composeRule.setContent {
            MpodTheme {
                SubscriptionsScreen(
                    state = populatedState().copy(refreshingPodcastIds = setOf(1L))
                )
            }
        }

        composeRule.onNodeWithText("Refreshing").assertIsDisplayed()
        composeRule.onNodeWithTag("subscriptions_podcast_pager").performTouchInput {
            swipeToNextPodcast()
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Refresh").assertIsDisplayed()
    }

    @Test
    fun episodeMenuOmitsManualDownloadAndDispatchesListenedAction() {
        var listenedChange: Pair<Long, Boolean>? = null
        composeRule.setContent {
            MpodTheme {
                SubscriptionsScreen(
                    state = populatedState(),
                    onSetEpisodeListened = { id, listened -> listenedChange = id to listened }
                )
            }
        }

        composeRule.onNodeWithContentDescription("Mark First episode as listened").assertIsDisplayed()
        composeRule.onAllNodesWithText("Download").assertCountEquals(0)
        composeRule.onNodeWithContentDescription("Mark First episode as listened").performClick()

        composeRule.runOnIdle {
            assertEquals(1L to true, listenedChange)
        }
    }

    @Test
    fun playlistAndListenedStateExposeInverseActions() {
        var removedEpisodeId: Long? = null
        var listenedChange: Pair<Long, Boolean>? = null
        val podcast = podcast(id = 1L, title = "First podcast", episodeTitle = "First episode")
            .copy(episodes = listOf(
                podcast(id = 1L, title = "First podcast", episodeTitle = "First episode")
                    .episodes.single().copy(inPlaylist = true, isListened = true)
            ), unlistenedEpisodeCount = 0)
        composeRule.setContent {
            MpodTheme {
                SubscriptionsScreen(
                    state = SubscriptionsUiState(podcasts = listOf(podcast)),
                    onRemoveEpisodeFromPlaylist = { removedEpisodeId = it },
                    onSetEpisodeListened = { id, listened -> listenedChange = id to listened }
                )
            }
        }

        composeRule.onNodeWithContentDescription("Show all").performClick()
        composeRule.onNodeWithContentDescription("Remove First episode from playlist").performClick()
        composeRule.onNodeWithContentDescription("Mark First episode as unlistened").performClick()

        composeRule.runOnIdle {
            assertEquals(1L, removedEpisodeId)
            assertEquals(1L to false, listenedChange)
        }
    }

    @Test
    fun pendingUnsubscribeUndoDispatchesSelectedPodcast() {
        var undoId: Long? = null
        composeRule.setContent {
            MpodTheme {
                SubscriptionsScreen(
                    state = populatedState().copy(
                        pendingUnsubscribe = PendingUnsubscribeUi(1L, "First podcast", 15)
                    ),
                    onUndoPodcastUnsubscribe = { undoId = it }
                )
            }
        }

        composeRule.onNodeWithText("Undo").performClick()
        composeRule.runOnIdle { assertEquals(1L, undoId) }
    }

    @Test
    fun unsubscribeDispatchesSelectedPodcast() {
        var unsubscribeId: Long? = null
        composeRule.setContent {
            MpodTheme {
                SubscriptionsScreen(
                    state = populatedState(),
                    onUnsubscribePodcast = { unsubscribeId = it }
                )
            }
        }
        composeRule.onNodeWithText("Unsubscribe").performClick()
        composeRule.runOnIdle { assertEquals(1L, unsubscribeId) }
    }

    @Test
    fun loadErrorRetryIsActionable() {
        var retries = 0
        composeRule.setContent {
            MpodTheme {
                SubscriptionsScreen(
                    state = SubscriptionsUiState(
                        errorMessage = "Subscriptions unavailable"
                    ),
                    onRetryLoad = { retries += 1 }
                )
            }
        }

        composeRule.onNodeWithText("Try again").performClick()
        composeRule.runOnIdle {
            assertEquals(1, retries)
        }
    }

    @Test
    fun emptySubscriptionsDispatchBothAddPaths() {
        var rssAdds = 0
        var opmlImports = 0
        composeRule.setContent {
            MpodTheme {
                SubscriptionsScreen(
                    state = SubscriptionsUiState(podcasts = emptyList()),
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

    private fun populatedState(): SubscriptionsUiState {
        return SubscriptionsUiState(
            podcasts = listOf(
                podcast(id = 1L, title = "First podcast", episodeTitle = "First episode"),
                podcast(id = 2L, title = "Second podcast", episodeTitle = "Second episode")
            )
        )
    }

    private fun TouchInjectionScope.swipeToNextPodcast() {
        swipe(
            start = Offset(width * 0.8f, height / 2f),
            end = Offset(width * 0.2f, height / 2f),
            durationMillis = 600L
        )
    }

    private fun assertSelectedPodcast(title: String) {
        composeRule.onNode(
            hasText(title) and hasAnyAncestor(hasTestTag("subscription_podcast_card_selected"))
        ).assertIsDisplayed()
    }

    private fun TouchInjectionScope.swipeToPreviousPodcast() {
        swipe(
            start = Offset(width * 0.2f, height / 2f),
            end = Offset(width * 0.8f, height / 2f),
            durationMillis = 600L
        )
    }

    private fun podcast(
        id: Long,
        title: String,
        episodeTitle: String,
        isListened: Boolean = false
    ): SubscriptionPodcastUi {
        return SubscriptionPodcastUi(
            id = id,
            title = title,
            description = "Podcast description",
            imageUrl = null,
            totalEpisodeCount = 1,
            unlistenedEpisodeCount = if (isListened) 0 else 1,
            episodes = listOf(
                SubscriptionEpisodeUi(
                    id = id,
                    title = episodeTitle,
                    durationSeconds = 60,
                    publishedAt = "2026-07-14T10:00:00Z",
                    isListened = isListened,
                    downloaded = false,
                    summary = "$episodeTitle notes",
                    inPlaylist = false
                )
            )
        )
    }
}
