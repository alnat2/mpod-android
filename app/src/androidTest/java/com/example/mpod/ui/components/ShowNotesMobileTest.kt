package com.example.mpod.ui.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.mpod.ui.theme.MpodTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ShowNotesMobileTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun tappingShowNotesLinkDispatchesExternalUrl() {
        var openedUrl: String? = null
        composeRule.setContent {
            MpodTheme {
                ShowNotesMobile(
                    podcastTitle = "Podcast - Episode",
                    notes = "https://example.com/episode",
                    onOpenLink = { openedUrl = it }
                )
            }
        }

        composeRule.onNodeWithText("https://example.com/episode").performClick()

        composeRule.runOnIdle {
            assertEquals("https://example.com/episode", openedUrl)
        }
    }
}
