package com.example.mpod.ui.screens.settings

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.mpod.ui.theme.MpodTheme
import com.example.mpod.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SettingsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun settingsShowsHeaderStatusAndSections() {
        composeRule.setContent {
            MpodTheme {
                SettingsScreen(
                    state = SettingsUiState(
                        lastRefreshHeaderText = "Last refresh today at 03:04"
                    )
                )
            }
        }

        composeRule.onNodeWithText("Last refresh today at 03:04").assertIsDisplayed()
        composeRule.onNodeWithText("Auto refresh").assertIsDisplayed()
        composeRule.onNodeWithText("Use SOCKS5 proxy").assertIsDisplayed()
        composeRule.onNodeWithText("Turn on if direct connection update fails.").assertIsDisplayed()
        composeRule.onNodeWithText("Appearance").assertIsDisplayed()
        composeRule.onAllNodesWithText("Export OPML").get(0).assertIsDisplayed()
    }

    @Test
    fun themeSelectorShowsAllThreeModes() {
        composeRule.setContent {
            MpodTheme {
                SettingsScreen(
                    state = SettingsUiState(),
                    themeMode = ThemeMode.Light
                )
            }
        }

        composeRule.onNodeWithText("System").assertIsDisplayed()
        composeRule.onNodeWithText("Light").assertIsDisplayed()
        composeRule.onNodeWithText("Dark").assertIsDisplayed()
    }

    @Test
    fun darkThemeSelectorSetsDarkMode() {
        var selectedMode: ThemeMode? = null
        composeRule.setContent {
            MpodTheme {
                SettingsScreen(
                    state = SettingsUiState(),
                    themeMode = ThemeMode.Light,
                    onThemeModeChange = { selectedMode = it }
                )
            }
        }

        composeRule.onNodeWithText("Dark").performClick()
        composeRule.runOnIdle { assertEquals(ThemeMode.Dark, selectedMode) }
    }

    @Test
    fun systemThemeSelectorSetsSystemMode() {
        var selectedMode: ThemeMode? = null
        composeRule.setContent {
            MpodTheme {
                SettingsScreen(
                    state = SettingsUiState(),
                    themeMode = ThemeMode.Dark,
                    onThemeModeChange = { selectedMode = it }
                )
            }
        }

        composeRule.onNodeWithText("System").performClick()
        composeRule.runOnIdle { assertEquals(ThemeMode.System, selectedMode) }
    }

    @Test
    fun autoRefreshAccordionRevealsDailyRefreshTime() {
        composeRule.setContent {
            MpodTheme {
                SettingsScreen(
                    state = SettingsUiState(
                        isAutoRefreshEnabled = true,
                        dailyRefreshTime = "04:00"
                    )
                )
            }
        }

        composeRule.onNodeWithText("Feed daily refresh").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Daily refresh time").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Daily refresh time").performClick()
        composeRule.onNodeWithText("Cancel").assertIsDisplayed()
        composeRule.onNodeWithText("OK").assertIsDisplayed()
    }

    @Test
    fun socksProxyAccordionRevealsProxyInputs() {
        composeRule.setContent {
            MpodTheme {
                SettingsScreen(
                    state = SettingsUiState(
                        isProxyEnabled = true,
                        proxyHost = "127.0.0.1",
                        proxyPort = 1080
                    )
                )
            }
        }

        composeRule.onNodeWithText("Proxy settings").assertIsDisplayed()
        composeRule.onNodeWithText("Save settings").assertIsDisplayed()
    }

    @Test
    fun settingsDispatchesExportOpml() {
        var exports = 0
        composeRule.setContent {
            MpodTheme {
                SettingsScreen(
                    state = SettingsUiState(),
                    onExportOpml = { exports += 1 }
                )
            }
        }

        composeRule.onNode(hasText("Export OPML") and hasClickAction()).performClick()
        composeRule.runOnIdle { assertEquals(1, exports) }
    }

    @Test
    fun settingsShowsAppBuildInfo() {
        composeRule.setContent {
            MpodTheme {
                SettingsScreen(
                    state = SettingsUiState(appBuild = "mpoddy v1.0.17")
                )
            }
        }

        composeRule.onNodeWithText("Current app build: mpoddy v1.0.17").assertIsDisplayed()
    }
}
