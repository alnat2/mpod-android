package com.example.mpod.ui.screens.auth

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.mpod.ui.theme.MpodTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class BackendUnavailableScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun displaysApprovedWebParityStateAndDispatchesRetry() {
        var retryCount = 0
        composeRule.setContent {
            MpodTheme {
                BackendUnavailableScreen(onRetry = { retryCount += 1 })
            }
        }

        composeRule.onNodeWithContentDescription("mpod").assertIsDisplayed()
        composeRule.onNodeWithText("mpod is not reachable").assertIsDisplayed()
        composeRule.onNodeWithText("Retry").performClick()

        composeRule.runOnIdle { assertEquals(1, retryCount) }
    }
}
