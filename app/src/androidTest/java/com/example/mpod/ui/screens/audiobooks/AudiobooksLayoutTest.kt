package com.example.mpod.ui.screens.audiobooks

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.example.mpod.R
import com.example.mpod.data.local.entity.AudiobookTrackEntity
import com.example.mpod.ui.components.ChaptersModal
import com.example.mpod.ui.components.ChaptersModalMode
import com.example.mpod.ui.components.FilemanagerItem
import com.example.mpod.ui.components.FilemanagerItemType
import com.example.mpod.ui.theme.MpodTheme
import com.example.mpod.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AudiobooksLayoutTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun filemanagerItem_wrapsTitleToTwoLinesWithoutVerticalClipping() {
        val longTitle = "Folder with audiobook chapters and long title"
        composeRule.setContent {
            MpodTheme(themeMode = ThemeMode.Dark) {
                FilemanagerItem(
                    title = longTitle,
                    type = FilemanagerItemType.AudiobookDirectory,
                    durationText = "43h 12m",
                    actionIconRes = R.drawable.ic_huge_playlist_add,
                    actionContentDescription = "Add to playlist",
                    onActionClick = {},
                    modifier = Modifier.width(320.dp),
                    onClick = {}
                )
            }
        }

        composeRule.onNodeWithText(longTitle).assertIsDisplayed()
        composeRule.onNodeWithText("43h 12m").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Add to playlist").assertIsDisplayed()

        val textBounds = composeRule.onNodeWithText(longTitle).getUnclippedBoundsInRoot()
        assertTrue(
            "Expected 2-line wrapped height (> 24dp), got ${textBounds.bottom - textBounds.top}",
            (textBounds.bottom - textBounds.top) > 24.dp
        )
    }

    @Test
    fun audiobooksScreen_uses16dpHeaderGapAnd4dpItemGap() {
        val items = listOf(
            FilemanagerItemUi(
                id = "dir-1",
                title = "Folder with abooks",
                type = FilemanagerItemType.Directory
            ),
            FilemanagerItemUi(
                id = "book-1",
                title = "A story.mp3",
                type = FilemanagerItemType.AudioFile,
                durationText = "1h 24m",
                audiobookId = 1L,
                isInPlaylist = true
            )
        )
        composeRule.setContent {
            MpodTheme(themeMode = ThemeMode.Dark) {
                AudiobooksScreen(
                    state = AudiobooksUiState(
                        audiobooksFolderUri = "content://abooks",
                        items = items,
                        totalBooksCount = 1
                    )
                )
            }
        }

        val headerBounds = composeRule.onNodeWithTag("audiobooks_header").getUnclippedBoundsInRoot()
        val breadcrumbsBounds = composeRule.onNodeWithTag("audiobooks_breadcrumbs").getUnclippedBoundsInRoot()
        val firstCardBounds = composeRule.onNodeWithTag("filemanager_item_Folder with abooks").getUnclippedBoundsInRoot()
        val secondCardBounds = composeRule.onNodeWithTag("filemanager_item_A story.mp3").getUnclippedBoundsInRoot()

        // PageHeader has bottom = 12dp inside modifier, so PageHeader container bottom to breadcrumbs top is 4dp
        // while visual header content bottom to breadcrumbs top is 16dp.
        assertEquals(16f, (breadcrumbsBounds.top - (headerBounds.bottom - 12.dp)).value, 1.5f)
        assertEquals(4f, (firstCardBounds.top - breadcrumbsBounds.bottom).value, 1.5f)
        assertEquals(4f, (secondCardBounds.top - firstCardBounds.bottom).value, 1.5f)
    }

    @Test
    fun chaptersModal_rendersHeaderAndItemsWith4dpSpacing() {
        val tracks = listOf(
            AudiobookTrackEntity(
                id = 1L,
                audiobookId = 10L,
                title = "Chapter1.mp3",
                fileUri = "file:///1.mp3",
                trackNumber = 1,
                durationSeconds = 900L,
                isListened = true
            ),
            AudiobookTrackEntity(
                id = 2L,
                audiobookId = 10L,
                title = "Chapter2.mp3",
                fileUri = "file:///2.mp3",
                trackNumber = 2,
                durationSeconds = 1440L,
                playbackPositionMs = 720_000L
            )
        )

        composeRule.setContent {
            MpodTheme(themeMode = ThemeMode.Dark) {
                ChaptersModal(
                    title = "Abook title",
                    author = "Author",
                    coverUri = null,
                    tracks = tracks,
                    currentTrackId = 2L,
                    isPlaying = true,
                    mode = ChaptersModalMode.Player,
                    onDismiss = {}
                )
            }
        }

        composeRule.onNodeWithText("Abook title").assertIsDisplayed()
        composeRule.onNodeWithText("Author").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Close").assertIsDisplayed()

        val firstCard = composeRule.onNodeWithTag("filemanager_item_Chapter1.mp3").getUnclippedBoundsInRoot()
        val secondCard = composeRule.onNodeWithTag("filemanager_item_Chapter2.mp3").getUnclippedBoundsInRoot()
        assertEquals(4f, (secondCard.top - firstCard.bottom).value, 1.5f)
    }
}
