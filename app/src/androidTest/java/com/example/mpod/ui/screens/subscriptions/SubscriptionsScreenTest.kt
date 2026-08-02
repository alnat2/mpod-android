package com.example.mpod.ui.screens.subscriptions

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
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
import org.junit.Rule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
                                id = 1,
                                title = "Active podcast",
                                episodeTitle = "Fresh episode",
                                isListened = false
                            ),
                            podcast(
                                id = 2,
                                title = "Caught up podcast",
                                episodeTitle = "Old episode",
                                isListened = true
                            )
                        )
                    )
                )
            }
        }

        composeRule.onNodeWithText("2 podcasts · 1 with unlistened").assertIsDisplayed()
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
    fun subscriptionEpisodeListNeverExposesDragControls() {
        composeRule.setContent {
            MpodTheme {
                SubscriptionsScreen(state = populatedState())
            }
        }

        composeRule.onAllNodesWithContentDescription("Drag").assertCountEquals(0)

        composeRule.onNodeWithTag("subscriptions_podcast_pager").performTouchInput {
            swipeToNextPodcast()
        }
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

        composeRule.onNodeWithContentDescription("Options for First episode").performClick()
        composeRule.onNodeWithTag("episode_actions_sheet").assertIsDisplayed()
        composeRule.onNodeWithTag("episode_action_icon_playlist", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("episode_action_icon_notes", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("episode_action_icon_download", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("episode_action_icon_listened", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Add to playlist").performClick()

        composeRule.runOnIdle { assertEquals(1, selectedEpisodeId) }
    }

    @Test
    fun showNotesOpensSelectedEpisodeNotes() {
        composeRule.setContent {
            MpodTheme {
                SubscriptionsScreen(state = populatedState())
            }
        }

        composeRule.onNodeWithContentDescription("Options for First episode").performClick()
        composeRule.onNodeWithText("Show notes").performClick()

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
        val caughtUpPodcast = podcast(
            id = 1,
            title = "Caught up podcast",
            episodeTitle = "Listened episode"
        ).copy(
            unlistenedEpisodeCount = 0,
            episodes = listOf(
                podcast(1, "Caught up podcast", "Listened episode")
                    .episodes.single().copy(isListened = true)
            )
        )
        composeRule.setContent {
            MpodTheme {
                SubscriptionsScreen(
                    state = SubscriptionsUiState(podcasts = listOf(caughtUpPodcast))
                )
            }
        }

        composeRule.onNodeWithText("All caught up").assertIsDisplayed()
        composeRule.onAllNodesWithText("No podcasts yet").assertCountEquals(0)
        composeRule.onNodeWithText("Show all").performClick()
        composeRule.onNodeWithText("Listened episode").assertIsDisplayed()
    }

    @Test
    fun episodeLoadFailureStaysScopedWhileOtherPodcastRemainsUsable() {
        val failedPodcast = podcast(1, "Failed podcast", "Missing episode").copy(
            episodes = emptyList(),
            totalEpisodeCount = 0,
            unlistenedEpisodeCount = 0,
            errorMessage = "Episodes unavailable. Refresh this podcast to try again.",
            episodesUnavailable = true
        )
        val healthyPodcast = podcast(2, "Healthy podcast", "Healthy episode")
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
                    state = populatedState().copy(refreshingPodcastIds = setOf(1))
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
    fun episodeMenuDispatchesDownloadAndListenedActions() {
        var downloadedEpisodeId: Int? = null
        var listenedChange: Pair<Int, Boolean>? = null
        composeRule.setContent {
            MpodTheme {
                SubscriptionsScreen(
                    state = populatedState(),
                    onDownloadEpisode = { downloadedEpisodeId = it },
                    onSetEpisodeListened = { id, listened -> listenedChange = id to listened }
                )
            }
        }

        composeRule.onNodeWithContentDescription("Options for First episode").performClick()
        composeRule.onNodeWithText("Download").performClick()
        composeRule.onNodeWithContentDescription("Options for First episode").performClick()
        composeRule.onNodeWithText("Mark as listened").performClick()

        composeRule.runOnIdle {
            assertEquals(1, downloadedEpisodeId)
            assertEquals(1 to true, listenedChange)
        }
    }

    @Test
    fun playlistAndListenedStateExposeInverseActions() {
        var removedEpisodeId: Int? = null
        var listenedChange: Pair<Int, Boolean>? = null
        val podcast = podcast(id = 1, title = "First podcast", episodeTitle = "First episode")
            .copy(episodes = listOf(
                podcast(id = 1, title = "First podcast", episodeTitle = "First episode")
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
        composeRule.onNodeWithContentDescription("Options for First episode").performClick()
        composeRule.onNodeWithText("Remove from playlist").performClick()
        composeRule.onNodeWithContentDescription("Options for First episode").performClick()
        composeRule.onNodeWithText("Mark as unlistened").performClick()

        composeRule.runOnIdle {
            assertEquals(1, removedEpisodeId)
            assertEquals(1 to false, listenedChange)
        }
    }

    @Test
    fun pendingUnsubscribeUndoDispatchesSelectedPodcast() {
        var undoId: Int? = null
        composeRule.setContent {
            MpodTheme {
                SubscriptionsScreen(
                    state = populatedState().copy(
                        pendingUnsubscribe = PendingUnsubscribeUi(1, "First podcast", 15)
                    ),
                    onUndoPodcastUnsubscribe = { undoId = it }
                )
            }
        }

        composeRule.onNodeWithText("Undo").performClick()
        composeRule.runOnIdle { assertEquals(1, undoId) }
    }

    @Test
    fun unsubscribeDispatchesSelectedPodcast() {
        var unsubscribeId: Int? = null
        composeRule.setContent {
            MpodTheme {
                SubscriptionsScreen(
                    state = populatedState(),
                    onUnsubscribePodcast = { unsubscribeId = it }
                )
            }
        }
        composeRule.onNodeWithText("Unsubscribe").performClick()
        composeRule.runOnIdle { assertEquals(1, unsubscribeId) }
    }

    @Test
    fun loadErrorRetryAndDownloadFailureDismissAreActionable() {
        var retries = 0
        var dismisses = 0
        composeRule.setContent {
            MpodTheme {
                SubscriptionsScreen(
                    state = SubscriptionsUiState(
                        errorMessage = "Subscriptions unavailable",
                        downloadFailure = SubscriptionDownloadFailureUi(1, "Download failed")
                    ),
                    onRetryLoad = { retries += 1 },
                    onDismissDownloadFailure = { dismisses += 1 }
                )
            }
        }

        composeRule.onNodeWithText("Try again").performClick()
        composeRule.onNodeWithContentDescription("Dismiss download error").performClick()
        composeRule.runOnIdle {
            assertEquals(1, retries)
            assertEquals(1, dismisses)
        }
    }

    @Test
    fun actionFailureTryAgainDispatchesTheViewModelRetryRoute() {
        var retries = 0
        composeRule.setContent {
            MpodTheme {
                SubscriptionsScreen(
                    state = populatedState().copy(
                        actionErrorMessage = "Could not unsubscribe from this podcast.",
                        failedUnsubscribePodcastId = 1
                    ),
                    onRetryRefresh = { retries += 1 }
                )
            }
        }

        composeRule.onNodeWithText("Try again").performClick()
        composeRule.runOnIdle { assertEquals(1, retries) }
    }

    @Test
    fun emptySubscriptionsDispatchBothAddPaths() {
        var rssAdds = 0
        var opmlImports = 0
        composeRule.setContent {
            MpodTheme {
                SubscriptionsScreen(
                    state = SubscriptionsUiState(),
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
    fun longEpisodeListKeepsTheLastEpisodeActionBoundToItsId() {
        val longTitle =
            "Episode 80 with an intentionally long title that must not hide its actions"
        val episodes = (1..80).map { index ->
            SubscriptionEpisodeUi(
                id = 1_000 + index,
                title = if (index == 80) longTitle else "Episode $index",
                durationSeconds = 60,
                publishedAt = "2026-07-14T10:00:00Z",
                isListened = false,
                downloaded = false,
                summary = null,
                inPlaylist = false
            )
        }
        val longPodcast = podcast(1, "Long podcast title", "Unused").copy(
            totalEpisodeCount = episodes.size,
            unlistenedEpisodeCount = episodes.size,
            episodes = episodes
        )
        var selectedEpisodeId: Int? = null
        composeRule.setContent {
            MpodTheme {
                SubscriptionsScreen(
                    state = SubscriptionsUiState(podcasts = listOf(longPodcast)),
                    onAddEpisodeToPlaylist = { selectedEpisodeId = it }
                )
            }
        }

        composeRule.onNodeWithTag("subscriptions_episode_list")
            .performScrollToNode(hasContentDescription("Options for $longTitle"))
        composeRule.onNodeWithContentDescription("Options for $longTitle")
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithText("Add to playlist").performClick()

        composeRule.runOnIdle { assertEquals(1_080, selectedEpisodeId) }
    }

    private fun populatedState(): SubscriptionsUiState {
        return SubscriptionsUiState(
            podcasts = listOf(
                podcast(id = 1, title = "First podcast", episodeTitle = "First episode"),
                podcast(id = 2, title = "Second podcast", episodeTitle = "Second episode")
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

    private fun TouchInjectionScope.swipeToPreviousPodcast() {
        swipe(
            start = Offset(width * 0.2f, height / 2f),
            end = Offset(width * 0.8f, height / 2f),
            durationMillis = 600L
        )
    }

    private fun podcast(
        id: Int,
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
