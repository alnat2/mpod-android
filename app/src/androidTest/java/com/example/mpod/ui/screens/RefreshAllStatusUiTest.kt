package com.example.mpod.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.mpod.ui.screens.settings.SettingsScreen
import com.example.mpod.ui.screens.settings.SettingsUiState
import com.example.mpod.ui.screens.subscriptions.SubscriptionPodcastUi
import com.example.mpod.ui.screens.subscriptions.SubscriptionsScreen
import com.example.mpod.ui.screens.subscriptions.SubscriptionsUiState
import com.example.mpod.ui.theme.MpodTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class RefreshAllStatusUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun failedRefreshShowsActualErrorAndKeepsRetryAvailable() {
        var retryCount = 0
        composeRule.setContent {
            MpodTheme {
                SubscriptionsScreen(
                    state = SubscriptionsUiState(
                        actionErrorMessage = REFRESH_ERROR,
                        podcasts = listOf(
                            SubscriptionPodcastUi(
                                id = 1L,
                                title = "Available podcast",
                                description = "Previously stored podcast",
                                imageUrl = null,
                                totalEpisodeCount = 0,
                                unlistenedEpisodeCount = 0,
                                episodes = emptyList()
                            )
                        )
                    ),
                    onRetryRefresh = { retryCount += 1 }
                )
            }
        }

        composeRule.onNodeWithText(REFRESH_ERROR).assertIsDisplayed()
        composeRule.onNodeWithText("Try again").performClick()
        composeRule.runOnIdle { assertEquals(1, retryCount) }
    }

    @Test
    fun settingsKeepsShowingPreviousSuccessfulRefreshText() {
        composeRule.setContent {
            MpodTheme {
                SettingsScreen(
                    state = SettingsUiState(lastRefreshHeaderText = PREVIOUS_LAST_REFRESH)
                )
            }
        }

        composeRule.onNodeWithText(PREVIOUS_LAST_REFRESH).assertIsDisplayed()
    }

    private companion object {
        const val PREVIOUS_LAST_REFRESH = "Last refresh today at 07.09 22:39"
        const val REFRESH_ERROR = "Failed to refresh 1 podcast(s):\nUnavailable podcast: HTTP 500"
    }
}
