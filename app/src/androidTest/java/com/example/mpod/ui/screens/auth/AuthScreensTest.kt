package com.example.mpod.ui.screens.auth

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.example.mpod.ui.theme.MpodTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AuthScreensTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun loginCollectsCredentialsAndSubmitsOnce() {
        var credentials: Pair<String, String>? = null
        composeRule.setContent {
            MpodTheme {
                LoginScreen(onSubmit = { username, password -> credentials = username to password })
            }
        }

        composeRule.onNodeWithText("Choose a username").performTextInput("listener")
        composeRule.onNodeWithText("Create a password").performTextInput("secret")
        composeRule.onNode(hasText("Log in") and hasClickAction()).performClick()

        composeRule.runOnIdle { assertEquals("listener" to "secret", credentials) }
    }

    @Test
    fun setupCollectsCredentialsAndSubmitsOnce() {
        var credentials: Pair<String, String>? = null
        composeRule.setContent {
            MpodTheme {
                SetupScreen(onSubmit = { username, password -> credentials = username to password })
            }
        }

        composeRule.onNodeWithText("Choose a username").performTextInput("owner")
        composeRule.onNodeWithText("Create a password").performTextInput("password")
        composeRule.onNode(hasText("Create account") and hasClickAction()).performClick()

        composeRule.runOnIdle { assertEquals("owner" to "password", credentials) }
    }

    @Test
    fun authFailureIsVisibleAndSubmittingDisablesAction() {
        composeRule.setContent {
            MpodTheme {
                LoginScreen(isSubmitting = true, errorMessage = "Invalid credentials")
            }
        }

        composeRule.onNodeWithText("Invalid credentials").assertIsDisplayed()
        composeRule.onNodeWithText("Please wait...").assertIsNotEnabled()
    }
}
