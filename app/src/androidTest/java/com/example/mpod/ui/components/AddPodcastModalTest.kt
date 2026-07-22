package com.example.mpod.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.espresso.Espresso.closeSoftKeyboard
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

        composeRule.onNodeWithTag("add_podcast_rss_url")
            .performTextReplacement("ftp://example.com/feed")
        composeRule.onNodeWithTag("add_podcast_rss_url")
            .assertTextEquals("ftp://example.com/feed")
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

        composeRule.onNodeWithTag("add_podcast_rss_url")
            .performTextReplacement("  https://example.com/feed.xml  ")
        composeRule.onNodeWithTag("add_podcast_rss_url")
            .assertTextEquals("  https://example.com/feed.xml  ")
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
    fun switchingModesDoesNotSubmitAndCanReturnToRss() {
        var imports = 0
        var feeds = 0
        composeRule.setContent {
            MpodTheme {
                AddPodcastModal(
                    onDismiss = {},
                    onAddUrl = { feeds += 1 },
                    onImportOpml = { imports += 1 }
                )
            }
        }

        composeRule.onNodeWithText("Import OPML File").performClick()
        composeRule.onNodeWithText("Choose OPML file").assertIsDisplayed()
        composeRule.onNodeWithText("RSS Feed URL").performClick()
        composeRule.onNodeWithText("Paste RSS feed URL").assertIsDisplayed()

        composeRule.runOnIdle {
            assertEquals(0, imports)
            assertEquals(0, feeds)
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
        composeRule.onNodeWithText("Cancel").assertIsNotEnabled()
        composeRule.onNodeWithText("RSS Feed URL").assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("Close").assertIsNotEnabled()
        composeRule.onNodeWithText("Choose file").assertIsNotEnabled()
    }

    @Test
    fun importResultReplacesFormAndDoneDismissesModal() {
        var dismisses = 0
        composeRule.setContent {
            MpodTheme {
                AddPodcastModal(
                    onDismiss = { dismisses += 1 },
                    onAddUrl = {},
                    onImportOpml = {},
                    initialMode = AddPodcastMode.ImportOpmlFile,
                    importResult = OpmlImportResultUi(imported = 8, skipped = 2)
                )
            }
        }

        composeRule.onNodeWithText("Import completed").assertIsDisplayed()
        composeRule.onNodeWithText("Imported: 8").assertIsDisplayed()
        composeRule.onNodeWithText("Skipped: 2").assertIsDisplayed()
        composeRule.onNodeWithText("Done").performClick()

        composeRule.runOnIdle { assertEquals(1, dismisses) }
    }

    @Test
    fun rssDraftSurvivesStateRestorationWithoutSubmitting() {
        var submissions = 0
        val restorationTester = StateRestorationTester(composeRule)
        restorationTester.setContent {
            MpodTheme {
                AddPodcastModal(
                    onDismiss = {},
                    onAddUrl = { submissions += 1 },
                    onImportOpml = {}
                )
            }
        }

        composeRule.onNodeWithTag("add_podcast_rss_url")
            .performTextReplacement("https://example.com/draft.xml")
        restorationTester.emulateSavedInstanceStateRestore()

        composeRule.onNodeWithTag("add_podcast_rss_url")
            .assertTextEquals("https://example.com/draft.xml")
        composeRule.runOnIdle { assertEquals(0, submissions) }
        closeSoftKeyboard()
    }

    @Test
    fun opmlModeSurvivesStateRestorationWithoutOpeningPicker() {
        var imports = 0
        val restorationTester = StateRestorationTester(composeRule)
        restorationTester.setContent {
            MpodTheme {
                AddPodcastModal(
                    onDismiss = {},
                    onAddUrl = {},
                    onImportOpml = { imports += 1 }
                )
            }
        }

        composeRule.onNodeWithText("Import OPML File").performClick()
        restorationTester.emulateSavedInstanceStateRestore()

        composeRule.onNodeWithText("Choose OPML file").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(0, imports) }
    }
}
