package com.example.mpod.ui.screens.audiobooks

import android.net.Uri
import com.example.mpod.data.audiobook.ScanSummary
import com.example.mpod.data.local.dao.PlaylistDao
import com.example.mpod.data.local.entity.AudiobookEntity
import com.example.mpod.data.local.entity.AudiobookTrackEntity
import com.example.mpod.data.local.preferences.AppSettings
import com.example.mpod.data.local.preferences.AppSettingsStore
import com.example.mpod.data.repository.AudiobookRepository
import com.example.mpod.data.repository.PlaylistRepository
import com.example.mpod.playback.PlaybackQueueInvalidator
import com.example.mpod.ui.components.FilemanagerItemType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify

@OptIn(ExperimentalCoroutinesApi::class)
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

    @Test
    fun currentChapterIndex_whenCurrentTrackNull_defaultsToOne() {
        val book = AudiobookEntity(
            id = 1L,
            folderUri = "uri://book1",
            title = "Book",
            author = "Author",
            isSingleFile = false
        )
        val item = com.example.mpod.data.local.model.PlayableItem.AudiobookItem(
            playlistItemId = 10L,
            position = 0,
            audiobook = book,
            selectedTracks = emptyList(),
            currentTrack = null
        )
        assertEquals(1, item.currentChapterIndex)
    }

    @Test
    fun buildFilemanagerItems_withNestedSeries_showsSeriesFolderUnderAuthor() {
        val books = listOf(
            AudiobookEntity(
                id = 1L,
                folderUri = "uri://inhabited_island",
                title = "Обитаемый остров",
                author = "Стругацкие",
                isSingleFile = false,
                durationSeconds = 12000L,
                relativePath = "Стругацкие/Мир Полудня/Обитаемый остров"
            ),
            AudiobookEntity(
                id = 2L,
                folderUri = "uri://hard_to_be_a_god",
                title = "Трудно быть богом",
                author = "Стругацкие",
                isSingleFile = false,
                durationSeconds = 10000L,
                relativePath = "Стругацкие/Трудно быть богом"
            )
        )

        // 1. Root level should only contain 1 folder for "Стругацкие"
        val rootItems = AudiobooksViewModel.buildFilemanagerItems(books, emptySet(), emptyList())
        assertEquals(1, rootItems.size)
        assertEquals("Стругацкие", rootItems[0].title)
        assertEquals(FilemanagerItemType.Directory, rootItems[0].type)

        // 2. Inside "Стругацкие" should show series folder "Мир Полудня" and book folder "Трудно быть богом"
        val authorItems = AudiobooksViewModel.buildFilemanagerItems(books, emptySet(), listOf("Стругацкие"))
        assertEquals(2, authorItems.size)
        // Folders sorted naturally: "Мир Полудня", then "Трудно быть богом"
        assertEquals("Мир Полудня", authorItems[0].title)
        assertEquals(FilemanagerItemType.Directory, authorItems[0].type)
        assertEquals("Трудно быть богом", authorItems[1].title)
        assertEquals(FilemanagerItemType.AudiobookDirectory, authorItems[1].type)

        // 3. Inside "Стругацкие/Мир Полудня" should show book folder "Обитаемый остров"
        val seriesItems = AudiobooksViewModel.buildFilemanagerItems(books, emptySet(), listOf("Стругацкие", "Мир Полудня"))
        assertEquals(1, seriesItems.size)
        assertEquals("Обитаемый остров", seriesItems[0].title)
        assertEquals(FilemanagerItemType.AudiobookDirectory, seriesItems[0].type)
    }

    @Test
    fun buildFilemanagerItems_sorting_foldersFirstThenFilesNaturally() {
        val books = listOf(
            AudiobookEntity(
                id = 1L,
                folderUri = "uri://file2",
                title = "02 Track",
                isSingleFile = true,
                durationSeconds = 100L,
                relativePath = "02 Track.mp3"
            ),
            AudiobookEntity(
                id = 2L,
                folderUri = "uri://file1",
                title = "01 Track",
                isSingleFile = true,
                durationSeconds = 100L,
                relativePath = "01 Track.mp3"
            ),
            AudiobookEntity(
                id = 3L,
                folderUri = "uri://book2",
                title = "Book 10",
                isSingleFile = false,
                durationSeconds = 100L,
                relativePath = "Book 10"
            ),
            AudiobookEntity(
                id = 4L,
                folderUri = "uri://book1",
                title = "Book 2",
                isSingleFile = false,
                durationSeconds = 100L,
                relativePath = "Book 2"
            )
        )

        val items = AudiobooksViewModel.buildFilemanagerItems(books, emptySet(), emptyList())
        assertEquals(4, items.size)
        // Folders first in natural order: "Book 2", then "Book 10"
        assertEquals("Book 2", items[0].title)
        assertEquals(FilemanagerItemType.AudiobookDirectory, items[0].type)
        assertEquals("Book 10", items[1].title)
        assertEquals(FilemanagerItemType.AudiobookDirectory, items[1].type)

        // Followed by files in natural order: "01 Track", then "02 Track"
        assertEquals("01 Track", items[2].title)
        assertEquals(FilemanagerItemType.AudioFile, items[2].type)
        assertEquals("02 Track", items[3].title)
        assertEquals(FilemanagerItemType.AudioFile, items[3].type)
    }

    @Test
    fun onRescanLibrary_whenScannerReturnsError_setsErrorMessageInState() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            val audiobookRepository = mock(AudiobookRepository::class.java)
            val playlistRepository = mock(PlaylistRepository::class.java)
            val playlistDao = mock(PlaylistDao::class.java)
            val appSettingsStore = mock(AppSettingsStore::class.java)
            val queueInvalidator = mock(PlaybackQueueInvalidator::class.java)

            `when`(appSettingsStore.settingsFlow).thenReturn(flowOf(AppSettings(audiobooksFolderUri = "content://tree/root")))
            `when`(audiobookRepository.audiobooksFlow).thenReturn(flowOf(emptyList()))
            `when`(playlistDao.getPlaylistItemsWithAudiobooksFlow()).thenReturn(flowOf(emptyList()))
            `when`(audiobookRepository.rescanLibrary()).thenReturn(
                ScanSummary(booksScanned = 0, tracksScanned = 0, errorMessage = "Access denied")
            )

            val viewModel = AudiobooksViewModel(
                audiobookRepository = audiobookRepository,
                playlistRepository = playlistRepository,
                playlistDao = playlistDao,
                appSettingsStore = appSettingsStore,
                queueInvalidator = queueInvalidator
            ).apply {
                ioDispatcher = testDispatcher
            }
            advanceUntilIdle()

            viewModel.onRescanLibrary()
            advanceUntilIdle()

            assertEquals("Rescan failed: Access denied", viewModel.state.value.errorMessage)
            assertNull(viewModel.state.value.statusMessage)
            assertEquals(false, viewModel.state.value.isScanning)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun onScanFolder_whenScannerReturnsError_setsErrorMessageInState() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            val audiobookRepository = mock(AudiobookRepository::class.java)
            val playlistRepository = mock(PlaylistRepository::class.java)
            val playlistDao = mock(PlaylistDao::class.java)
            val appSettingsStore = mock(AppSettingsStore::class.java)
            val queueInvalidator = mock(PlaybackQueueInvalidator::class.java)
            val uri = mock(Uri::class.java)

            `when`(appSettingsStore.settingsFlow).thenReturn(flowOf(AppSettings()))
            `when`(audiobookRepository.audiobooksFlow).thenReturn(flowOf(emptyList()))
            `when`(playlistDao.getPlaylistItemsWithAudiobooksFlow()).thenReturn(flowOf(emptyList()))
            `when`(audiobookRepository.scanDirectory(uri)).thenReturn(
                ScanSummary(booksScanned = 0, tracksScanned = 0, errorMessage = "SAF permission revoked")
            )

            val viewModel = AudiobooksViewModel(
                audiobookRepository = audiobookRepository,
                playlistRepository = playlistRepository,
                playlistDao = playlistDao,
                appSettingsStore = appSettingsStore,
                queueInvalidator = queueInvalidator
            ).apply {
                ioDispatcher = testDispatcher
            }
            advanceUntilIdle()

            viewModel.onScanFolder(uri)
            advanceUntilIdle()

            assertEquals("Scan failed: SAF permission revoked", viewModel.state.value.errorMessage)
            assertNull(viewModel.state.value.statusMessage)
            assertEquals(false, viewModel.state.value.isScanning)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun openBookModal_whenBookNotInPlaylist_showsEmptySelectedTracks() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            val audiobookRepository = mock(AudiobookRepository::class.java)
            val playlistRepository = mock(PlaylistRepository::class.java)
            val playlistDao = mock(PlaylistDao::class.java)
            val appSettingsStore = mock(AppSettingsStore::class.java)
            val queueInvalidator = mock(PlaybackQueueInvalidator::class.java)

            `when`(appSettingsStore.settingsFlow).thenReturn(flowOf(AppSettings()))
            `when`(audiobookRepository.audiobooksFlow).thenReturn(flowOf(emptyList()))
            `when`(playlistDao.getPlaylistItemsWithAudiobooksFlow()).thenReturn(flowOf(emptyList()))

            val book = AudiobookEntity(id = 10L, folderUri = "uri://book10", title = "Book 10")
            val tracks = listOf(
                AudiobookTrackEntity(id = 101L, audiobookId = 10L, title = "Ch 1", fileUri = "uri://1", trackNumber = 1),
                AudiobookTrackEntity(id = 102L, audiobookId = 10L, title = "Ch 2", fileUri = "uri://2", trackNumber = 2)
            )
            `when`(playlistDao.isAudiobookInPlaylist(10L)).thenReturn(false)
            `when`(audiobookRepository.getTracksForAudiobook(10L)).thenReturn(tracks)

            val viewModel = AudiobooksViewModel(
                audiobookRepository, playlistRepository, playlistDao, appSettingsStore, queueInvalidator
            ).apply { ioDispatcher = testDispatcher }
            advanceUntilIdle()

            viewModel.openBookModal(book)
            advanceUntilIdle()

            assertEquals(book, viewModel.state.value.selectedBookForModal)
            assertEquals(tracks, viewModel.state.value.selectedBookTracks)
            assertTrue(viewModel.state.value.selectedBookPlaylistTrackIds.isEmpty())
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun openBookModal_whenBookInPlaylist_restoresSelectedTracks() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            val audiobookRepository = mock(AudiobookRepository::class.java)
            val playlistRepository = mock(PlaylistRepository::class.java)
            val playlistDao = mock(PlaylistDao::class.java)
            val appSettingsStore = mock(AppSettingsStore::class.java)
            val queueInvalidator = mock(PlaybackQueueInvalidator::class.java)

            `when`(appSettingsStore.settingsFlow).thenReturn(flowOf(AppSettings()))
            `when`(audiobookRepository.audiobooksFlow).thenReturn(flowOf(emptyList()))
            `when`(playlistDao.getPlaylistItemsWithAudiobooksFlow()).thenReturn(flowOf(emptyList()))

            val book = AudiobookEntity(id = 10L, folderUri = "uri://book10", title = "Book 10")
            val tracks = listOf(
                AudiobookTrackEntity(id = 101L, audiobookId = 10L, title = "Ch 1", fileUri = "uri://1", trackNumber = 1),
                AudiobookTrackEntity(id = 102L, audiobookId = 10L, title = "Ch 2", fileUri = "uri://2", trackNumber = 2)
            )
            `when`(playlistDao.isAudiobookInPlaylist(10L)).thenReturn(true)
            `when`(audiobookRepository.getTracksForAudiobook(10L)).thenReturn(tracks)
            `when`(audiobookRepository.getSelectedTracksForAudiobook(10L)).thenReturn(listOf(tracks[0]))

            val viewModel = AudiobooksViewModel(
                audiobookRepository, playlistRepository, playlistDao, appSettingsStore, queueInvalidator
            ).apply { ioDispatcher = testDispatcher }
            advanceUntilIdle()

            viewModel.openBookModal(book)
            advanceUntilIdle()

            assertEquals(setOf(101L), viewModel.state.value.selectedBookPlaylistTrackIds)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun toggleTrackSelection_syncsViaPlaylistRepositoryAndInvalidates() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            val audiobookRepository = mock(AudiobookRepository::class.java)
            val playlistRepository = mock(PlaylistRepository::class.java)
            val playlistDao = mock(PlaylistDao::class.java)
            val appSettingsStore = mock(AppSettingsStore::class.java)
            val queueInvalidator = mock(PlaybackQueueInvalidator::class.java)

            `when`(appSettingsStore.settingsFlow).thenReturn(flowOf(AppSettings()))
            `when`(audiobookRepository.audiobooksFlow).thenReturn(flowOf(emptyList()))
            `when`(playlistDao.getPlaylistItemsWithAudiobooksFlow()).thenReturn(flowOf(emptyList()))

            val book = AudiobookEntity(id = 10L, folderUri = "uri://book10", title = "Book 10")
            val track1 = AudiobookTrackEntity(id = 101L, audiobookId = 10L, title = "Ch 1", fileUri = "uri://1", trackNumber = 1)
            val tracks = listOf(track1)

            `when`(playlistDao.isAudiobookInPlaylist(10L)).thenReturn(false)
            `when`(audiobookRepository.getTracksForAudiobook(10L)).thenReturn(tracks)
            `when`(playlistRepository.syncAudiobookSelection(10L, listOf(101L))).thenReturn(true)

            val viewModel = AudiobooksViewModel(
                audiobookRepository, playlistRepository, playlistDao, appSettingsStore, queueInvalidator
            ).apply { ioDispatcher = testDispatcher }
            advanceUntilIdle()

            viewModel.openBookModal(book)
            advanceUntilIdle()

            // Intermediate assert: initially empty selection for book not in queue
            assertTrue(viewModel.state.value.selectedBookPlaylistTrackIds.isEmpty())

            viewModel.toggleTrackSelection(track1)

            // Intermediate assert: optimistic update immediately adds track
            assertEquals(setOf(101L), viewModel.state.value.selectedBookPlaylistTrackIds)

            advanceUntilIdle()

            assertEquals(setOf(101L), viewModel.state.value.selectedBookPlaylistTrackIds)
            verify(playlistRepository).syncAudiobookSelection(10L, listOf(101L))
            verify(queueInvalidator).invalidate()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun toggleTrackSelection_whenSyncFails_rollsBackUIState() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            val audiobookRepository = mock(AudiobookRepository::class.java)
            val playlistRepository = mock(PlaylistRepository::class.java)
            val playlistDao = mock(PlaylistDao::class.java)
            val appSettingsStore = mock(AppSettingsStore::class.java)
            val queueInvalidator = mock(PlaybackQueueInvalidator::class.java)

            `when`(appSettingsStore.settingsFlow).thenReturn(flowOf(AppSettings()))
            `when`(audiobookRepository.audiobooksFlow).thenReturn(flowOf(emptyList()))
            `when`(playlistDao.getPlaylistItemsWithAudiobooksFlow()).thenReturn(flowOf(emptyList()))

            val book = AudiobookEntity(id = 10L, folderUri = "uri://book10", title = "Book 10")
            val track1 = AudiobookTrackEntity(id = 101L, audiobookId = 10L, title = "Ch 1", fileUri = "uri://1", trackNumber = 1)
            val tracks = listOf(track1)

            `when`(playlistDao.isAudiobookInPlaylist(10L)).thenReturn(false)
            `when`(audiobookRepository.getTracksForAudiobook(10L)).thenReturn(tracks)
            `when`(playlistRepository.syncAudiobookSelection(10L, listOf(101L)))
                .thenThrow(RuntimeException("Database disk image is malformed"))

            val viewModel = AudiobooksViewModel(
                audiobookRepository, playlistRepository, playlistDao, appSettingsStore, queueInvalidator
            ).apply { ioDispatcher = testDispatcher }
            advanceUntilIdle()

            viewModel.openBookModal(book)
            advanceUntilIdle()

            // Intermediate assert: initially empty selection for book not in queue
            assertTrue(viewModel.state.value.selectedBookPlaylistTrackIds.isEmpty())

            viewModel.toggleTrackSelection(track1)

            // Intermediate assert: optimistic update updated state before worker failure
            assertEquals(setOf(101L), viewModel.state.value.selectedBookPlaylistTrackIds)

            advanceUntilIdle()

            // State must be rolled back to emptySet()
            assertTrue(viewModel.state.value.selectedBookPlaylistTrackIds.isEmpty())
            assertTrue(viewModel.state.value.errorMessage?.contains("Database disk image is malformed") == true)
        } finally {
            Dispatchers.resetMain()
        }
    }
}
