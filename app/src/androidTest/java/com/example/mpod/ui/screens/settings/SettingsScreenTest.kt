package com.example.mpod.ui.screens.settings

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
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
    fun darkThemeSwitchEnablesDarkMode() {
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

        composeRule.onNodeWithContentDescription("Use dark theme").performClick()

        composeRule.runOnIdle { assertEquals(ThemeMode.Dark, selectedMode) }
    }

    @Test
    fun darkThemeSwitchReturnsToLightMode() {
        var selectedMode: ThemeMode? = null
        composeRule.setContent {
            MpodTheme(themeMode = ThemeMode.Dark) {
                SettingsScreen(
                    state = SettingsUiState(),
                    themeMode = ThemeMode.Dark,
                    onThemeModeChange = { selectedMode = it }
                )
            }
        }

        composeRule.onNodeWithContentDescription("Use dark theme").performClick()

        composeRule.runOnIdle { assertEquals(ThemeMode.Light, selectedMode) }
    }

    @Test
    fun dailyRefreshTimeOpensMaterialTimePicker() {
        composeRule.setContent {
            MpodTheme {
                SettingsScreen(state = SettingsUiState(dailyRefreshTime = "04:00"))
            }
        }

        composeRule.onNodeWithContentDescription("Daily refresh time").performClick()

        composeRule.onNodeWithText("Cancel").assertIsDisplayed()
        composeRule.onNodeWithText("OK").assertIsDisplayed()
    }

    @Test
    fun settingsDispatchesProxyExportLogoutAndRefreshSave() {
        var proxyEnabled: Boolean? = null
        var exports = 0
        var logouts = 0
        var savedTime: String? = null
        composeRule.setContent {
            MpodTheme {
                SettingsScreen(
                    state = SettingsUiState(
                        dailyRefreshTime = "04:00",
                        proxyEnabled = false,
                        proxyConfigured = true
                    ),
                    onSaveDailyRefreshTime = { savedTime = it },
                    onProxyEnabledChange = { proxyEnabled = it },
                    onExportOpml = { exports += 1 },
                    onLogout = { logouts += 1 }
                )
            }
        }

        composeRule.onNodeWithText("Save time").performClick()
        composeRule.onNodeWithContentDescription("Use SOCKS5 proxy").performClick()
        composeRule.onNode(hasText("Export OPML") and hasClickAction()).performClick()
        composeRule.onNodeWithText("Log out").performClick()

        composeRule.runOnIdle {
            assertEquals("04:00", savedTime)
            assertEquals(true, proxyEnabled)
            assertEquals(1, exports)
            assertEquals(1, logouts)
        }
    }

    @Test
    fun settingsShowsLoadingState() {
        composeRule.setContent {
            MpodTheme {
                SettingsScreen(state = SettingsUiState(isLoading = true))
            }
        }
        composeRule.onNodeWithText("Loading settings").assertIsDisplayed()
    }

    @Test
    fun settingsShowsBackendFailureState() {
        composeRule.setContent {
            MpodTheme {
                SettingsScreen(state = SettingsUiState(errorMessage = "Settings unavailable"))
            }
        }
        composeRule.onNodeWithText("Settings unavailable").assertIsDisplayed()
    }
}
