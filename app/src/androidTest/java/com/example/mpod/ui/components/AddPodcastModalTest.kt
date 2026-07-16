package com.example.mpod.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.example.mpod.ui.theme.MpodTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AddPodcastModalTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun invalidRssIsRejectedBeforeDispatch() {
        var submitted = false
        composeRule.setContent {
            MpodTheme {
                AddPodcastModal(onDismiss = {}, onAddUrl = { submitted = true }, onImportOpml = {})
            }
        }

        composeRule.onNodeWithText("https://feeds.example.com/podcast.xml")
            .performTextInput("ftp://example.com/feed")
        composeRule.onNodeWithText("Add Feed").performClick()

        composeRule.onNodeWithText("Enter a valid http or https RSS feed URL.").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(false, submitted) }
    }

    @Test
    fun validRssIsTrimmedAndDispatched() {
        var submitted: String? = null
        composeRule.setContent {
            MpodTheme {
                AddPodcastModal(onDismiss = {}, onAddUrl = { submitted = it }, onImportOpml = {})
            }
        }

        composeRule.onNodeWithText("https://feeds.example.com/podcast.xml")
            .performTextInput("  https://example.com/feed.xml  ")
        composeRule.onNodeWithText("Add Feed").performClick()

        composeRule.runOnIdle { assertEquals("https://example.com/feed.xml", submitted) }
    }

    @Test
    fun OPMLModeDispatchesBrowseAndCancelActions() {
        var imports = 0
        var dismisses = 0
        composeRule.setContent {
            MpodTheme {
                AddPodcastModal(
                    onDismiss = { dismisses += 1 },
                    onAddUrl = {},
                    onImportOpml = { imports += 1 }
                )
            }
        }

        composeRule.onNodeWithText("Import OPML File").performClick()
        composeRule.onNodeWithText("Import OPML").performClick()
        composeRule.onNodeWithText("Cancel").performClick()

        composeRule.runOnIdle {
            assertEquals(1, imports)
            assertEquals(1, dismisses)
        }
    }

    @Test
    fun submittingStateDisablesImportActionAndShowsBackendError() {
        composeRule.setContent {
            MpodTheme {
                AddPodcastModal(
                    onDismiss = {},
                    onAddUrl = {},
                    onImportOpml = {},
                    initialMode = AddPodcastMode.ImportOpmlFile,
                    isSubmitting = true,
                    errorMessage = "OPML file is too large. Maximum size is 5 MB."
                )
            }
        }

        composeRule.onNodeWithText("OPML file is too large. Maximum size is 5 MB.").assertIsDisplayed()
        composeRule.onNodeWithText("Please wait...").assertIsNotEnabled()
    }
}
