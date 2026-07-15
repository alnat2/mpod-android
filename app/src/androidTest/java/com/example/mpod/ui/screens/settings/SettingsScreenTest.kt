package com.example.mpod.ui.screens.settings

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertIsDisplayed
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
}
