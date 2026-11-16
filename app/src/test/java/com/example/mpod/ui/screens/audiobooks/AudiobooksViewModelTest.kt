package com.example.mpod.ui.screens.audiobooks

import com.example.mpod.data.local.entity.AudiobookEntity
import com.example.mpod.ui.components.FilemanagerItemType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudiobooksViewModelTest {

    @Test
    fun formatFmDuration_variousValues() {
        assertEquals("", AudiobooksViewModel.formatFmDuration(0))
        assertEquals("45m", AudiobooksViewModel.formatFmDuration(45 * 60))
        assertEquals("1h 24m", AudiobooksViewModel.formatFmDuration(3600 + 24 * 60))
        assertEquals("43h 12m", AudiobooksViewModel.formatFmDuration(43 * 3600 + 12 * 60))
    }

    @Test
    fun buildFilemanagerItems_rootLevel_groupsAuthorsAndRootBooks() {
        val books = listOf(
            AudiobookEntity(
                id = 1L,
                folderUri = "uri://book1",
                title = "The Fellowship",
                author = "J.R.R. Tolkien",
                isSingleFile = false,
                durationSeconds = 7200L
            ),
            AudiobookEntity(
                id = 2L,
                folderUri = "uri://book2",
                title = "The Two Towers",
                author = "J.R.R. Tolkien",
                isSingleFile = false,
                durationSeconds = 7500L
            ),
            AudiobookEntity(
                id = 3L,
                folderUri = "uri://book3",
                title = "A Single Story",
                author = "Unknown Author",
                isSingleFile = true,
                durationSeconds = 3600L
            )
        )

        val playlistIds = setOf(1L)
        val items = AudiobooksViewModel.buildFilemanagerItems(
            audiobooks = books,
            playlistIds = playlistIds,
            currentPath = emptyList()
        )

        // At root level, J.R.R. Tolkien is an author Directory; "A Single Story" is at root as an AudioFile
        assertEquals(2, items.size)

        val authorItem = items[0]
        assertEquals("J.R.R. Tolkien", authorItem.title)
        assertEquals(FilemanagerItemType.Directory, authorItem.type)

        val rootBookItem = items[1]
        assertEquals("A Single Story", rootBookItem.title)
        assertEquals(FilemanagerItemType.AudioFile, rootBookItem.type)
        assertEquals("1h 0m", rootBookItem.durationText)
        assertEquals(false, rootBookItem.isInPlaylist)
    }

    @Test
    fun buildFilemanagerItems_subfolderLevel_showsAuthorBooks() {
        val books = listOf(
            AudiobookEntity(
                id = 1L,
                folderUri = "uri://book1",
                title = "The Fellowship",
                author = "J.R.R. Tolkien",
                isSingleFile = false,
                durationSeconds = 7200L
            ),
            AudiobookEntity(
                id = 2L,
                folderUri = "uri://book2",
                title = "The Two Towers",
                author = "J.R.R. Tolkien",
                isSingleFile = false,
                durationSeconds = 7500L
            ),
            AudiobookEntity(
                id = 3L,
                folderUri = "uri://book3",
                title = "Foundation",
                author = "Isaac Asimov",
                isSingleFile = false,
                durationSeconds = 5000L
            )
        )

        val playlistIds = setOf(1L)
        val items = AudiobooksViewModel.buildFilemanagerItems(
            audiobooks = books,
            playlistIds = playlistIds,
            currentPath = listOf("J.R.R. Tolkien")
        )

        assertEquals(2, items.size)
        assertEquals("The Fellowship", items[0].title)
        assertEquals(FilemanagerItemType.AudiobookDirectory, items[0].type)
        assertTrue(items[0].isInPlaylist)

        assertEquals("The Two Towers", items[1].title)
        assertEquals(FilemanagerItemType.AudiobookDirectory, items[1].type)
        assertEquals(false, items[1].isInPlaylist)
    }
}
