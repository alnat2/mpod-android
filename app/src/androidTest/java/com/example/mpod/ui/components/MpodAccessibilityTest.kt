package com.example.mpod.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTouchHeightIsEqualTo
import androidx.compose.ui.test.assertTouchWidthIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.mpod.ui.navigation.Screen
import com.example.mpod.ui.theme.MpodTheme
import org.junit.Rule
import org.junit.Test

class MpodAccessibilityTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun compactControlsExposeAccessibleTouchTargetsAndLabels() {
        composeRule.setContent {
            MpodTheme {
                Column(modifier = androidx.compose.ui.Modifier.width(360.dp)) {
                    SquareIconButton(
                        iconRes = com.example.mpod.R.drawable.ic_refresh_dot,
                        contentDescription = "Refresh subscriptions",
                        onClick = {}
                    )
                    MpodSwitch(
                        checked = true,
                        onCheckedChange = {},
                        contentDescription = "Use dark theme"
                    )
                    EpisodeRow(
                        title = "Accessible episode",
                        podcastName = "Podcast",
                        duration = "20m",
                        onAction = {}
                    )
                }
            }
        }

        composeRule.onNodeWithContentDescription("Refresh subscriptions")
            .assertHasClickAction()
            .assertTouchWidthIsEqualTo(48.dp)
            .assertTouchHeightIsEqualTo(48.dp)
        composeRule.onNodeWithContentDescription("Use dark theme")
            .assertIsOn()
            .assertTouchWidthIsEqualTo(48.dp)
            .assertTouchHeightIsEqualTo(48.dp)
        composeRule.onNodeWithContentDescription("Options for Accessible episode")
            .assertHasClickAction()
            .assertTouchWidthIsEqualTo(48.dp)
            .assertTouchHeightIsEqualTo(48.dp)
    }

    @Test
    fun bottomNavigationReportsTabSelection() {
        composeRule.setContent {
            MpodTheme {
                MpodBottomNav(currentRoute = Screen.Home.route, onNavigate = {})
            }
        }

        composeRule.onNodeWithText("Home").assertIsSelected()
        composeRule.onNodeWithText("Settings").assertIsNotSelected()
    }

    @Test
    fun playerAndEpisodeRemainReadableAtOnePointFiveFontScale() {
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = 1.5f)) {
                MpodTheme {
                    Column(modifier = androidx.compose.ui.Modifier.width(360.dp)) {
                        PlayerView(
                            title = "A long episode title that occupies two lines",
                            podcastTitle = "A podcast with a long title"
                        )
                        EpisodeRow(
                            title = "A long episode title that also occupies two lines",
                            podcastName = "A podcast with a long title",
                            duration = "120m",
                            onAction = {}
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithContentDescription("Playback speed 1.5x").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Show notes").assertIsDisplayed()
        composeRule.onNodeWithText("A long episode title that also occupies two lines").assertIsDisplayed()
    }
}
