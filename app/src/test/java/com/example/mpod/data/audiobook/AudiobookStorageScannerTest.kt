package com.example.mpod.data.audiobook

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.example.mpod.data.local.dao.AudiobookDao
import com.example.mpod.data.local.entity.AudiobookEntity
import com.example.mpod.data.local.entity.AudiobookPlaylistTrackEntity
import com.example.mpod.data.local.entity.AudiobookTrackEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import java.io.File

class AudiobookStorageScannerTest {

    private lateinit var context: Context
    private lateinit var contentResolver: ContentResolver
    private lateinit var fakeAudiobookDao: FakeAudiobookDao
    private lateinit var fakeMetadataExtractor: FakeAudioMetadataExtractor
    private lateinit var fakeTreeHelper: FakeDocumentTreeHelper
    private lateinit var scanner: AudiobookStorageScanner
    private lateinit var treeUri: Uri

    @Before
    fun setUp() {
        context = Mockito.mock(Context::class.java)
        contentResolver = Mockito.mock(ContentResolver::class.java)
        Mockito.`when`(context.contentResolver).thenReturn(contentResolver)
        Mockito.`when`(context.cacheDir).thenReturn(File(System.getProperty("java.io.tmpdir", "/tmp")))

        fakeAudiobookDao = FakeAudiobookDao()
        fakeMetadataExtractor = FakeAudioMetadataExtractor()
        fakeTreeHelper = FakeDocumentTreeHelper()

        scanner = AudiobookStorageScanner(context, fakeAudiobookDao, fakeMetadataExtractor)
        scanner.treeHelper = fakeTreeHelper

        treeUri = fakeTreeHelper.createMockUri("content://tree/root")
    }

    @Test
    fun processBookFolder_withDiscSubfolders_aggregatesTracksAcrossDiscsInNaturalOrder(): Unit = runBlocking {
        val bookDir = DocumentItem(
            docId = "book_war_and_peace",
            name = "War and Peace",
            mimeType = "vnd.android.document/directory",
            size = 0L,
            isDirectory = true
        )
        val directChildren = listOf(
            DocumentItem("cd1", "CD1", "vnd.android.document/directory", 0L, isDirectory = true),
            DocumentItem("cd2", "CD2", "vnd.android.document/directory", 0L, isDirectory = true)
        )

        fakeTreeHelper.childrenMap["cd1"] = listOf(
            DocumentItem("t1", "01-Chapter 1.mp3", "audio/mpeg", 1000L, isDirectory = false),
            DocumentItem("t2", "02-Chapter 2.mp3", "audio/mpeg", 1000L, isDirectory = false)
        )
        fakeTreeHelper.childrenMap["cd2"] = listOf(
            DocumentItem("t3", "03-Chapter 3.mp3", "audio/mpeg", 1000L, isDirectory = false),
            DocumentItem("t4", "10-Chapter 4.mp3", "audio/mpeg", 1000L, isDirectory = false)
        )

        val result = scanner.processBookFolder(
            contentResolver = contentResolver,
            treeUri = treeUri,
            bookDir = bookDir,
            directChildren = directChildren,
            authorFallback = "Leo Tolstoy",
            currentFileCount = 0
        )

        assertEquals(4, result.tracksProcessed)
        assertFalse(result.limitExceeded)

        // Verify book upserted
        val book = fakeAudiobookDao.upsertedBooks.first()
        assertEquals("War and Peace", book.title)
        assertEquals("Leo Tolstoy", book.author)

        // Verify tracks upserted in natural order across both discs
        val insertedTracks = fakeAudiobookDao.insertedTracks
        assertEquals(4, insertedTracks.size)
        assertEquals("01-Chapter 1", insertedTracks[0].title)
        assertEquals(1, insertedTracks[0].trackNumber)
        assertEquals("content://tree/t1", insertedTracks[0].fileUri)

        assertEquals("02-Chapter 2", insertedTracks[1].title)
        assertEquals(2, insertedTracks[1].trackNumber)
        assertEquals("content://tree/t2", insertedTracks[1].fileUri)

        assertEquals("03-Chapter 3", insertedTracks[2].title)
        assertEquals(3, insertedTracks[2].trackNumber)
        assertEquals("content://tree/t3", insertedTracks[2].fileUri)

        assertEquals("10-Chapter 4", insertedTracks[3].title)
        assertEquals(4, insertedTracks[3].trackNumber)
        assertEquals("content://tree/t4", insertedTracks[3].fileUri)
    }

    @Test
    fun processBookFolder_withDiscSubfolders_discoversCoverInsideDiscFolderWhenMissingInRoot(): Unit = runBlocking {
        val bookDir = DocumentItem(
            docId = "book_1",
            name = "Audiobook With Disc Cover",
            mimeType = "vnd.android.document/directory",
            size = 0L,
            isDirectory = true
        )
        val directChildren = listOf(
            DocumentItem("cd1", "CD 1", "vnd.android.document/directory", 0L, isDirectory = true),
            DocumentItem("cd2", "CD 2", "vnd.android.document/directory", 0L, isDirectory = true)
        )

        // cover.jpg placed inside CD 1, not in book root
        fakeTreeHelper.childrenMap["cd1"] = listOf(
            DocumentItem("cd1_cover", "cover.jpg", "image/jpeg", 5000L, isDirectory = false),
            DocumentItem("t1", "track01.mp3", "audio/mpeg", 1000L, isDirectory = false)
        )
        fakeTreeHelper.childrenMap["cd2"] = listOf(
            DocumentItem("t2", "track02.mp3", "audio/mpeg", 1000L, isDirectory = false)
        )

        scanner.processBookFolder(
            contentResolver = contentResolver,
            treeUri = treeUri,
            bookDir = bookDir,
            directChildren = directChildren,
            authorFallback = "Author",
            currentFileCount = 0
        )

        val book = fakeAudiobookDao.upsertedBooks.first()
        assertEquals("content://tree/cd1_cover", book.coverUri)
    }

    @Test
    fun processBookFolder_withCoverInBookRoot_prefersRootCoverOverDiscCover(): Unit = runBlocking {
        val bookDir = DocumentItem(
            docId = "book_1",
            name = "Audiobook With Root Cover",
            mimeType = "vnd.android.document/directory",
            size = 0L,
            isDirectory = true
        )
        val directChildren = listOf(
            DocumentItem("root_cover", "cover.png", "image/png", 8000L, isDirectory = false),
            DocumentItem("cd1", "disc 1", "vnd.android.document/directory", 0L, isDirectory = true)
        )

        // disc 1 also has cover.jpg, but root cover.png must take priority
        fakeTreeHelper.childrenMap["cd1"] = listOf(
            DocumentItem("disc_cover", "cover.jpg", "image/jpeg", 5000L, isDirectory = false),
            DocumentItem("t1", "track01.mp3", "audio/mpeg", 1000L, isDirectory = false)
        )

        scanner.processBookFolder(
            contentResolver = contentResolver,
            treeUri = treeUri,
            bookDir = bookDir,
            directChildren = directChildren,
            authorFallback = "Author",
            currentFileCount = 0
        )

        val book = fakeAudiobookDao.upsertedBooks.first()
        assertEquals("content://tree/root_cover", book.coverUri)
    }

    @Test
    fun scanFolder_withMultiDiscBook_identifiesBookFolderAndAggregatesDiscs(): Unit = runBlocking {
        // Root has 1 book folder that contains only CD subfolders
        fakeTreeHelper.childrenMap["root"] = listOf(
            DocumentItem("book_hobbit", "The Hobbit", "vnd.android.document/directory", 0L, isDirectory = true)
        )
        fakeTreeHelper.childrenMap["book_hobbit"] = listOf(
            DocumentItem("cd1", "CD1", "vnd.android.document/directory", 0L, isDirectory = true),
            DocumentItem("cd2", "CD2", "vnd.android.document/directory", 0L, isDirectory = true)
        )
        fakeTreeHelper.childrenMap["cd1"] = listOf(
            DocumentItem("t1", "01.mp3", "audio/mpeg", 1000L, isDirectory = false)
        )
        fakeTreeHelper.childrenMap["cd2"] = listOf(
            DocumentItem("t2", "02.mp3", "audio/mpeg", 1000L, isDirectory = false)
        )

        val summary = scanner.scanFolder(treeUri)

        assertEquals(1, summary.booksScanned)
        assertEquals(2, summary.tracksScanned)
        assertFalse(summary.isLimitExceeded)
    }

    @Test
    fun scanFolder_withAuthorHierarchyAndDiscSubfolders_assignsAuthorAndProcessesBook(): Unit = runBlocking {
        // Root / Author / Book / CD1
        fakeTreeHelper.childrenMap["root"] = listOf(
            DocumentItem("author_dir", "J. R. R. Tolkien", "vnd.android.document/directory", 0L, isDirectory = true)
        )
        fakeTreeHelper.childrenMap["author_dir"] = listOf(
            DocumentItem("book_towers", "The Two Towers", "vnd.android.document/directory", 0L, isDirectory = true)
        )
        fakeTreeHelper.childrenMap["book_towers"] = listOf(
            DocumentItem("cd1", "disk1", "vnd.android.document/directory", 0L, isDirectory = true)
        )
        fakeTreeHelper.childrenMap["cd1"] = listOf(
            DocumentItem("t1", "01-Chapter.mp3", "audio/mpeg", 1000L, isDirectory = false),
            DocumentItem("t2", "02-Chapter.mp3", "audio/mpeg", 1000L, isDirectory = false)
        )

        val summary = scanner.scanFolder(treeUri)

        assertEquals(1, summary.booksScanned)
        assertEquals(2, summary.tracksScanned)

        val book = fakeAudiobookDao.upsertedBooks.first()
        assertEquals("The Two Towers", book.title)
        assertEquals("J. R. R. Tolkien", book.author)
    }

    @Test
    fun processBookFolder_withDuplicateTrackNamesAcrossDiscs_preservesDiscSequenceWithoutInterleaving(): Unit = runBlocking {
        val bookDir = DocumentItem(
            docId = "book_lotr",
            name = "The Fellowship of the Ring",
            mimeType = "vnd.android.document/directory",
            size = 0L,
            isDirectory = true
        )
        // Discs in reverse order in SAF cursor to verify natural sorting of disc folders
        val directChildren = listOf(
            DocumentItem("intro", "00-intro.mp3", "audio/mpeg", 500L, isDirectory = false),
            DocumentItem("cd2", "CD2", "vnd.android.document/directory", 0L, isDirectory = true),
            DocumentItem("cd1", "CD1", "vnd.android.document/directory", 0L, isDirectory = true)
        )

        // Duplicate file names across discs (01.mp3, 02.mp3)
        fakeTreeHelper.childrenMap["cd1"] = listOf(
            DocumentItem("cd1_t1", "01.mp3", "audio/mpeg", 1000L, isDirectory = false),
            DocumentItem("cd1_t2", "02.mp3", "audio/mpeg", 1000L, isDirectory = false)
        )
        fakeTreeHelper.childrenMap["cd2"] = listOf(
            DocumentItem("cd2_t1", "01.mp3", "audio/mpeg", 1000L, isDirectory = false),
            DocumentItem("cd2_t2", "02.mp3", "audio/mpeg", 1000L, isDirectory = false)
        )

        val result = scanner.processBookFolder(
            contentResolver = contentResolver,
            treeUri = treeUri,
            bookDir = bookDir,
            directChildren = directChildren,
            authorFallback = "J. R. R. Tolkien",
            currentFileCount = 0
        )

        assertEquals(5, result.tracksProcessed)
        assertFalse(result.limitExceeded)

        val tracks = fakeAudiobookDao.insertedTracks

        // Expected sequence: direct root -> CD1/01 -> CD1/02 -> CD2/01 -> CD2/02 (NO interleaving)
        assertEquals("00-intro", tracks[0].title)
        assertEquals("content://tree/intro", tracks[0].fileUri)

        assertEquals("01", tracks[1].title)
        assertEquals("content://tree/cd1_t1", tracks[1].fileUri)

        assertEquals("02", tracks[2].title)
        assertEquals("content://tree/cd1_t2", tracks[2].fileUri)

        assertEquals("01", tracks[3].title)
        assertEquals("content://tree/cd2_t1", tracks[3].fileUri)

        assertEquals("02", tracks[4].title)
        assertEquals("content://tree/cd2_t2", tracks[4].fileUri)
    }

    @Test
    fun processBookFolder_whenLimitReached_truncatesFromSequentialOrder(): Unit = runBlocking {
        val bookDir = DocumentItem(
            docId = "book_1",
            name = "Book Over Capacity",
            mimeType = "vnd.android.document/directory",
            size = 0L,
            isDirectory = true
        )
        val directChildren = listOf(
            DocumentItem("cd1", "CD1", "vnd.android.document/directory", 0L, isDirectory = true),
            DocumentItem("cd2", "CD2", "vnd.android.document/directory", 0L, isDirectory = true)
        )
        fakeTreeHelper.childrenMap["cd1"] = listOf(
            DocumentItem("cd1_t1", "01.mp3", "audio/mpeg", 1000L, isDirectory = false),
            DocumentItem("cd1_t2", "02.mp3", "audio/mpeg", 1000L, isDirectory = false)
        )
        fakeTreeHelper.childrenMap["cd2"] = listOf(
            DocumentItem("cd2_t1", "01.mp3", "audio/mpeg", 1000L, isDirectory = false),
            DocumentItem("cd2_t2", "02.mp3", "audio/mpeg", 1000L, isDirectory = false)
        )

        // MAX_FILES_LIMIT is 2000; set currentFileCount = 1998, leaving capacity = 2
        val result = scanner.processBookFolder(
            contentResolver = contentResolver,
            treeUri = treeUri,
            bookDir = bookDir,
            directChildren = directChildren,
            authorFallback = "Author",
            currentFileCount = 1998
        )

        assertEquals(2, result.tracksProcessed)
        assertTrue(result.limitExceeded)

        val tracks = fakeAudiobookDao.insertedTracks

        // Only first 2 tracks in sequential order are processed
        assertEquals("content://tree/cd1_t1", tracks[0].fileUri)
        assertEquals("content://tree/cd1_t2", tracks[1].fileUri)
    }

    @Test
    fun scanFolder_withDeepHierarchy_preservesRelativePathAndAuthor(): Unit = runBlocking {
        // Root / Author / Series / Book / 01.mp3
        fakeTreeHelper.childrenMap["root"] = listOf(
            DocumentItem("author_dir", "Стругацкие", "vnd.android.document/directory", 0L, isDirectory = true)
        )
        fakeTreeHelper.childrenMap["author_dir"] = listOf(
            DocumentItem("series_dir", "Мир Полудня", "vnd.android.document/directory", 0L, isDirectory = true)
        )
        fakeTreeHelper.childrenMap["series_dir"] = listOf(
            DocumentItem("book_dir", "Обитаемый остров", "vnd.android.document/directory", 0L, isDirectory = true)
        )
        fakeTreeHelper.childrenMap["book_dir"] = listOf(
            DocumentItem("t1", "01-Chapter.mp3", "audio/mpeg", 1000L, isDirectory = false),
            DocumentItem("t2", "02-Chapter.mp3", "audio/mpeg", 1000L, isDirectory = false)
        )

        val summary = scanner.scanFolder(treeUri)

        assertEquals(1, summary.booksScanned)
        assertEquals(2, summary.tracksScanned)

        val book = fakeAudiobookDao.upsertedBooks.first()
        assertEquals("Обитаемый остров", book.title)
        assertEquals("Стругацкие", book.author)
        assertEquals("Стругацкие/Мир Полудня/Обитаемый остров", book.relativePath)
        assertFalse(book.isSingleFile)

        val tracks = fakeAudiobookDao.insertedTracks
        assertEquals(2, tracks.size)
        assertEquals("01-Chapter", tracks[0].title)
        assertEquals("02-Chapter", tracks[1].title)
    }

    @Test
    fun scanFolder_withID3TagsDifferingFromFileSystem_strictlyPrioritizesFileSystemNames(): Unit = runBlocking {
        fakeMetadataExtractor.metadataResult = ExtractedAudioMetadata(
            title = "ID3 Chapter Title",
            artist = "ID3 Artist Name",
            album = "ID3 Album Name",
            durationSeconds = 500L
        )

        fakeTreeHelper.childrenMap["root"] = listOf(
            DocumentItem("author_dir", "Толстой", "vnd.android.document/directory", 0L, isDirectory = true)
        )
        fakeTreeHelper.childrenMap["author_dir"] = listOf(
            DocumentItem("book_dir", "Война и мир", "vnd.android.document/directory", 0L, isDirectory = true)
        )
        fakeTreeHelper.childrenMap["book_dir"] = listOf(
            DocumentItem("t1", "01-Начало.mp3", "audio/mpeg", 1000L, isDirectory = false)
        )

        scanner.scanFolder(treeUri)

        val book = fakeAudiobookDao.upsertedBooks.first()
        // File system names MUST prevail over ID3 tags
        assertEquals("Война и мир", book.title)
        assertEquals("Толстой", book.author)
        assertEquals("Толстой/Война и мир", book.relativePath)

        val track = fakeAudiobookDao.insertedTracks.first()
        assertEquals("01-Начало", track.title)
        assertEquals(500L, track.durationSeconds) // Duration is retained from metadata
    }

    @Test
    fun scanFolder_withStandaloneAudioFileInAuthorFolder_extractsStandaloneBook(): Unit = runBlocking {
        // Aligned with mpod: standalone audio file alongside a book subfolder in author directory
        fakeTreeHelper.childrenMap["root"] = listOf(
            DocumentItem("author_dir", "Стругацкие", "vnd.android.document/directory", 0L, isDirectory = true)
        )
        fakeTreeHelper.childrenMap["author_dir"] = listOf(
            DocumentItem("standalone", "Хромая судьба.mp3", "audio/mpeg", 1000L, isDirectory = false),
            DocumentItem("sub_book", "Пикник на обочине", "vnd.android.document/directory", 0L, isDirectory = true)
        )
        fakeTreeHelper.childrenMap["sub_book"] = listOf(
            DocumentItem("sub_t1", "01.mp3", "audio/mpeg", 1000L, isDirectory = false)
        )

        val summary = scanner.scanFolder(treeUri)

        assertEquals(2, summary.booksScanned)
        assertEquals(2, summary.tracksScanned)

        val standaloneBook = fakeAudiobookDao.upsertedBooks.first { it.isSingleFile }
        assertEquals("Хромая судьба", standaloneBook.title)
        assertEquals("Стругацкие", standaloneBook.author)
        assertEquals("Стругацкие/Хромая судьба.mp3", standaloneBook.relativePath)
        assertTrue(standaloneBook.isSingleFile)

        val folderBook = fakeAudiobookDao.upsertedBooks.first { !it.isSingleFile }
        assertEquals("Пикник на обочине", folderBook.title)
        assertEquals("Стругацкие", folderBook.author)
    }

    @Test
    fun scanFolder_singleFileInRootSubfolderWithoutAudioSubdirs_recognizesAsFolderBookWithCover(): Unit = runBlocking {
        fakeTreeHelper.childrenMap["root"] = listOf(
            DocumentItem("book_dune", "Дюна", "vnd.android.document/directory", 0L, isDirectory = true)
        )
        fakeTreeHelper.childrenMap["book_dune"] = listOf(
            DocumentItem("dune_audio", "Dune.m4b", "audio/mp4", 5000L, isDirectory = false),
            DocumentItem("dune_cover", "cover.jpg", "image/jpeg", 2000L, isDirectory = false)
        )

        val summary = scanner.scanFolder(treeUri)

        assertEquals(1, summary.booksScanned)
        assertEquals(1, summary.tracksScanned)

        val book = fakeAudiobookDao.upsertedBooks.single()
        assertEquals("Дюна", book.title)
        assertEquals("Unknown Author", book.author)
        assertEquals("Дюна", book.relativePath)
        assertEquals("content://tree/dune_cover", book.coverUri)
        assertFalse(book.isSingleFile)
    }

    @Test
    fun scanFolder_bookFolderWithNonAudioSubdir_keepsBookIntact(): Unit = runBlocking {
        fakeTreeHelper.childrenMap["root"] = listOf(
            DocumentItem("author_dir", "Author", "vnd.android.document/directory", 0L, isDirectory = true)
        )
        fakeTreeHelper.childrenMap["author_dir"] = listOf(
            DocumentItem("book_dir", "Book", "vnd.android.document/directory", 0L, isDirectory = true)
        )
        fakeTreeHelper.childrenMap["book_dir"] = listOf(
            DocumentItem("t1", "01.mp3", "audio/mpeg", 1000L, isDirectory = false),
            DocumentItem("t2", "02.mp3", "audio/mpeg", 1000L, isDirectory = false),
            DocumentItem("extras_dir", "Extras", "vnd.android.document/directory", 0L, isDirectory = true)
        )
        fakeTreeHelper.childrenMap["extras_dir"] = listOf(
            DocumentItem("notes", "notes.txt", "text/plain", 100L, isDirectory = false)
        )

        val summary = scanner.scanFolder(treeUri)

        assertEquals(1, summary.booksScanned)
        assertEquals(2, summary.tracksScanned)
        val book = fakeAudiobookDao.upsertedBooks.single()
        assertEquals("Book", book.title)
        assertFalse(book.isSingleFile)
    }

    @Test
    fun scanFolder_whenTrackRenamed_deletesOldTrackAndKeepsOnlyNewTrack(): Unit = runBlocking {
        fakeTreeHelper.childrenMap["root"] = listOf(
            DocumentItem("book_dir", "Book", "vnd.android.document/directory", 0L, isDirectory = true)
        )
        fakeTreeHelper.childrenMap["book_dir"] = listOf(
            DocumentItem("t_old", "01-old.mp3", "audio/mpeg", 1000L, isDirectory = false)
        )

        scanner.scanFolder(treeUri)
        assertEquals(listOf("01-old"), fakeAudiobookDao.insertedTracks.map { it.title })

        // Rename file in storage (new SAF docId & URI)
        fakeTreeHelper.childrenMap["book_dir"] = listOf(
            DocumentItem("t_new", "01-new.mp3", "audio/mpeg", 1000L, isDirectory = false)
        )

        scanner.scanFolder(treeUri)

        assertEquals(1, fakeAudiobookDao.insertedTracks.size)
        assertEquals("01-new", fakeAudiobookDao.insertedTracks.single().title)
        assertEquals("content://tree/t_new", fakeAudiobookDao.insertedTracks.single().fileUri)
        assertTrue(fakeAudiobookDao.deletedEmptyPlaylistItemsCalled)
    }

    @Test
    fun scanFolder_whenExternalCoverRemoved_clearsStaleContentCoverUri(): Unit = runBlocking {
        fakeTreeHelper.childrenMap["root"] = listOf(
            DocumentItem("book_dir", "Book", "vnd.android.document/directory", 0L, isDirectory = true)
        )
        fakeTreeHelper.childrenMap["book_dir"] = listOf(
            DocumentItem("t1", "01.mp3", "audio/mpeg", 1000L, isDirectory = false),
            DocumentItem("cover_1", "cover.jpg", "image/jpeg", 500L, isDirectory = false)
        )

        scanner.scanFolder(treeUri)
        assertEquals("content://tree/cover_1", fakeAudiobookDao.upsertedBooks.single().coverUri)

        // Remove cover.jpg from directory
        fakeTreeHelper.childrenMap["book_dir"] = listOf(
            DocumentItem("t1", "01.mp3", "audio/mpeg", 1000L, isDirectory = false)
        )

        scanner.scanFolder(treeUri)
        assertEquals(null, fakeAudiobookDao.upsertedBooks.single().coverUri)
    }

    @Test
    fun scanFolder_whenStorageAccessThrows_returnsErrorMessageAndPreservesDb(): Unit = runBlocking {
        fakeAudiobookDao.upsertedBooks.add(
            AudiobookEntity(
                id = 1L,
                title = "Existing Book",
                folderUri = "content://tree/existing",
                relativePath = "Existing Book"
            )
        )
        fakeTreeHelper.throwOnQuery = SecurityException("Permission revoked for tree")

        val summary = scanner.scanFolder(treeUri)

        assertEquals(0, summary.booksScanned)
        assertTrue(summary.errorMessage != null)
        assertTrue(summary.errorMessage!!.contains("Permission revoked"))
        // Database must not be cleared when scan encounters an error
        assertEquals(1, fakeAudiobookDao.upsertedBooks.size)
    }

    @Test
    fun scanFolder_reconcilesAndDeletesMissingBooks(): Unit = runBlocking {
        // Pre-populate DB with an old deleted book
        fakeAudiobookDao.upsertedBooks.add(
            AudiobookEntity(
                id = 1L,
                title = "Old Deleted Book",
                folderUri = "content://tree/old_deleted",
                relativePath = "Old Deleted Book"
            )
        )

        // Storage currently only contains a single new audio file
        fakeTreeHelper.childrenMap["root"] = listOf(
            DocumentItem("new_track", "New Book.mp3", "audio/mpeg", 1000L, isDirectory = false)
        )

        val summary = scanner.scanFolder(treeUri)

        assertEquals(1, summary.booksScanned)
        // Old book should be deleted by reconciliation; only new book remains
        assertEquals(1, fakeAudiobookDao.upsertedBooks.size)
        assertEquals("New Book", fakeAudiobookDao.upsertedBooks.first().title)
    }

    @Test
    fun scanFolder_withExistingAudiobookInDb_preservesExistingIdAndInsertsTracksWithCorrectAudiobookId(): Unit = runBlocking {
        val existingBookId = 42L
        val bookFolderUri = "content://tree/book_dir"

        fakeAudiobookDao.upsertedBooks.add(
            AudiobookEntity(
                id = existingBookId,
                title = "Old Title",
                folderUri = bookFolderUri,
                relativePath = "Old Title"
            )
        )

        fakeTreeHelper.childrenMap["root"] = listOf(
            DocumentItem("book_dir", "Updated Book", "vnd.android.document/directory", 0L, isDirectory = true)
        )
        fakeTreeHelper.childrenMap["book_dir"] = listOf(
            DocumentItem("t1", "01-Chapter.mp3", "audio/mpeg", 1000L, isDirectory = false),
            DocumentItem("t2", "02-Chapter.mp3", "audio/mpeg", 1000L, isDirectory = false)
        )

        val summary = scanner.scanFolder(treeUri)

        assertEquals(1, summary.booksScanned)
        assertTrue(summary.errorMessage == null)

        val book = fakeAudiobookDao.upsertedBooks.find { it.folderUri == bookFolderUri }
        assertTrue(book != null)
        assertEquals(existingBookId, book!!.id)
        assertEquals("Updated Book", book.title)

        val insertedTrack = fakeAudiobookDao.insertedTracks.first()
        assertEquals(existingBookId, insertedTrack.audiobookId)
        assertTrue(insertedTrack.audiobookId > 0)
    }

    @Test
    fun scanFolder_rescanUnchangedBook_skipsMetadataExtraction(): Unit = runBlocking {
        val bookFolderUri = "content://tree/book_dir"
        val track1Uri = "content://tree/t1"
        val track2Uri = "content://tree/t2"

        fakeAudiobookDao.upsertedBooks.add(
            AudiobookEntity(
                id = 1L,
                title = "Cached Book",
                folderUri = bookFolderUri,
                coverUri = "content://covers/1",
                durationSeconds = 600L
            )
        )
        fakeAudiobookDao.insertedTracks.addAll(
            listOf(
                AudiobookTrackEntity(
                    id = 101L,
                    audiobookId = 1L,
                    title = "01-Chapter",
                    fileUri = track1Uri,
                    trackNumber = 1,
                    durationSeconds = 300L,
                    fileSizeBytes = 1000L,
                    lastModifiedMs = 5000L
                ),
                AudiobookTrackEntity(
                    id = 102L,
                    audiobookId = 1L,
                    title = "02-Chapter",
                    fileUri = track2Uri,
                    trackNumber = 2,
                    durationSeconds = 300L,
                    fileSizeBytes = 1200L,
                    lastModifiedMs = 5000L
                )
            )
        )

        fakeTreeHelper.childrenMap["root"] = listOf(
            DocumentItem("book_dir", "Cached Book", "vnd.android.document/directory", 0L, isDirectory = true)
        )
        fakeTreeHelper.childrenMap["book_dir"] = listOf(
            DocumentItem("cover_1", "cover.jpg", "image/jpeg", 500L, isDirectory = false),
            DocumentItem("t1", "01-Chapter.mp3", "audio/mpeg", 1000L, isDirectory = false, lastModified = 5000L),
            DocumentItem("t2", "02-Chapter.mp3", "audio/mpeg", 1200L, isDirectory = false, lastModified = 5000L)
        )

        fakeMetadataExtractor.extractCallCount = 0
        val summary = scanner.scanFolder(treeUri)

        assertEquals(1, summary.booksScanned)
        assertEquals(0, fakeMetadataExtractor.extractCallCount)
    }

    @Test
    fun scanFolder_whenLastModifiedZero_fallsBackToExtractingMetadata(): Unit = runBlocking {
        val bookFolderUri = "content://tree/book_dir"
        val track1Uri = "content://tree/t1"

        fakeAudiobookDao.upsertedBooks.add(
            AudiobookEntity(
                id = 1L,
                title = "Book",
                folderUri = bookFolderUri,
                coverUri = "content://covers/1",
                durationSeconds = 300L
            )
        )
        fakeAudiobookDao.insertedTracks.add(
            AudiobookTrackEntity(
                id = 101L,
                audiobookId = 1L,
                title = "01-Chapter",
                fileUri = track1Uri,
                trackNumber = 1,
                durationSeconds = 300L,
                fileSizeBytes = 1000L,
                lastModifiedMs = 5000L
            )
        )

        fakeTreeHelper.childrenMap["root"] = listOf(
            DocumentItem("book_dir", "Book", "vnd.android.document/directory", 0L, isDirectory = true)
        )
        // lastModified is 0L -> untrusted/changed -> must extract
        fakeTreeHelper.childrenMap["book_dir"] = listOf(
            DocumentItem("t1", "01-Chapter.mp3", "audio/mpeg", 1000L, isDirectory = false, lastModified = 0L)
        )

        fakeMetadataExtractor.extractCallCount = 0
        fakeMetadataExtractor.metadataResult = ExtractedAudioMetadata(durationSeconds = 350L)
        val summary = scanner.scanFolder(treeUri)

        assertEquals(1, summary.booksScanned)
        assertTrue(fakeMetadataExtractor.extractCallCount > 0)
    }

    @Test
    fun scanFolder_whenFileSizeDiffers_extractsMetadata(): Unit = runBlocking {
        val bookFolderUri = "content://tree/book_dir"
        val track1Uri = "content://tree/t1"

        fakeAudiobookDao.upsertedBooks.add(
            AudiobookEntity(
                id = 1L,
                title = "Book",
                folderUri = bookFolderUri,
                coverUri = "content://covers/1",
                durationSeconds = 300L
            )
        )
        fakeAudiobookDao.insertedTracks.add(
            AudiobookTrackEntity(
                id = 101L,
                audiobookId = 1L,
                title = "01-Chapter",
                fileUri = track1Uri,
                trackNumber = 1,
                durationSeconds = 300L,
                fileSizeBytes = 1000L,
                lastModifiedMs = 5000L
            )
        )

        fakeTreeHelper.childrenMap["root"] = listOf(
            DocumentItem("book_dir", "Book", "vnd.android.document/directory", 0L, isDirectory = true)
        )
        // File size changed from 1000L to 2000L
        fakeTreeHelper.childrenMap["book_dir"] = listOf(
            DocumentItem("t1", "01-Chapter.mp3", "audio/mpeg", 2000L, isDirectory = false, lastModified = 5000L)
        )

        fakeMetadataExtractor.extractCallCount = 0
        fakeMetadataExtractor.metadataResult = ExtractedAudioMetadata(durationSeconds = 600L)
        val summary = scanner.scanFolder(treeUri)

        assertEquals(1, summary.booksScanned)
        assertTrue(fakeMetadataExtractor.extractCallCount > 0)
    }

    internal class FakeAudiobookDao : AudiobookDao {
        val upsertedBooks = mutableListOf<AudiobookEntity>()
        val insertedTracks = mutableListOf<AudiobookTrackEntity>()
        var deletedEmptyPlaylistItemsCalled = false
        private var nextTrackId = 1L

        override suspend fun upsertAudiobook(audiobook: AudiobookEntity): Long {
            val existing = getAudiobookByFolderUri(audiobook.folderUri)
            return if (existing != null) {
                updateAudiobook(audiobook.copy(id = existing.id))
                -1L // Emulates Room's EntityUpsertionAdapter returning -1L on update
            } else {
                insertAudiobook(audiobook)
            }
        }

        override suspend fun insertAudiobook(audiobook: AudiobookEntity): Long {
            if (upsertedBooks.any { it.folderUri == audiobook.folderUri }) {
                return -1L
            }
            val generatedId = if (audiobook.id > 0) audiobook.id else (upsertedBooks.size + 1).toLong()
            upsertedBooks.add(audiobook.copy(id = generatedId))
            return generatedId
        }

        override suspend fun insertAudiobooks(audiobooks: List<AudiobookEntity>): List<Long> {
            return audiobooks.map { insertAudiobook(it) }
        }

        override suspend fun updateAudiobook(audiobook: AudiobookEntity) {
            val idx = upsertedBooks.indexOfFirst { it.id == audiobook.id }
            if (idx >= 0) {
                upsertedBooks[idx] = audiobook
            } else {
                upsertedBooks.add(audiobook)
            }
        }

        override suspend fun deleteAudiobook(audiobook: AudiobookEntity) {}
        override suspend fun deleteAudiobookById(audiobookId: Long) {}
        override suspend fun deleteAllAudiobooks() {
            upsertedBooks.clear()
        }
        override suspend fun deleteAudiobooksNotIn(retainedFolderUris: List<String>) {
            upsertedBooks.removeAll { it.folderUri !in retainedFolderUris }
        }
        override suspend fun getAudiobookById(audiobookId: Long): AudiobookEntity? = null
        override fun getAudiobookByIdFlow(audiobookId: Long): Flow<AudiobookEntity?> = emptyFlow()
        override suspend fun getAudiobookByFolderUri(folderUri: String): AudiobookEntity? =
            upsertedBooks.find { it.folderUri == folderUri }
        override suspend fun getAllAudiobooks(): List<AudiobookEntity> = upsertedBooks.toList()
        override fun getAllAudiobooksFlow(): Flow<List<AudiobookEntity>> = emptyFlow()
        override suspend fun getAudiobookCount(): Int = upsertedBooks.size

        override suspend fun upsertTrack(track: AudiobookTrackEntity): Long {
            insertedTracks.add(track)
            return track.id
        }

        override suspend fun upsertTracks(tracks: List<AudiobookTrackEntity>): List<Long> {
            insertedTracks.addAll(tracks)
            return tracks.map { it.id }
        }

        override suspend fun insertTrack(track: AudiobookTrackEntity): Long {
            val assigned = if (track.id > 0L) track else track.copy(id = nextTrackId++)
            insertedTracks.add(assigned)
            return assigned.id
        }

        override suspend fun insertTracks(tracks: List<AudiobookTrackEntity>): List<Long> {
            return tracks.map { insertTrack(it) }
        }

        override suspend fun updateTrack(track: AudiobookTrackEntity) {
            val idx = insertedTracks.indexOfFirst { it.id == track.id }
            if (idx >= 0) insertedTracks[idx] = track else insertedTracks.add(track)
        }

        override suspend fun updateTracks(tracks: List<AudiobookTrackEntity>) {
            tracks.forEach { updateTrack(it) }
        }
        override suspend fun getTrackByFileUri(audiobookId: Long, fileUri: String): AudiobookTrackEntity? =
            insertedTracks.find { it.audiobookId == audiobookId && it.fileUri == fileUri }
        override suspend fun updateTrackProgress(trackId: Long, positionMs: Long, isListened: Boolean) {}
        override suspend fun updateTrackAvailability(trackId: Long, isAvailable: Boolean) {}
        override suspend fun getTrackById(trackId: Long): AudiobookTrackEntity? =
            insertedTracks.find { it.id == trackId }
        override suspend fun getTracksForAudiobook(audiobookId: Long): List<AudiobookTrackEntity> =
            insertedTracks.filter { it.audiobookId == audiobookId && it.isAvailable }
        override fun getTracksForAudiobookFlow(audiobookId: Long): Flow<List<AudiobookTrackEntity>> = emptyFlow()
        override suspend fun getAllTracksForAudiobook(audiobookId: Long): List<AudiobookTrackEntity> =
            insertedTracks.filter { it.audiobookId == audiobookId }
        override suspend fun deleteTracksByIds(trackIds: List<Long>) {
            insertedTracks.removeAll { it.id in trackIds }
        }
        override suspend fun deleteTracksForAudiobook(audiobookId: Long) {}

        override suspend fun insertPlaylistTrack(playlistTrack: AudiobookPlaylistTrackEntity) {}
        override suspend fun insertPlaylistTracks(playlistTracks: List<AudiobookPlaylistTrackEntity>) {}
        override suspend fun removePlaylistTrack(audiobookId: Long, trackId: Long) {}
        override suspend fun clearPlaylistTracksForBook(audiobookId: Long) {}
        override suspend fun getSelectedTrackIdsForAudiobook(audiobookId: Long): List<Long> = emptyList()
        override suspend fun getSelectedTracksForAudiobook(audiobookId: Long): List<AudiobookTrackEntity> = emptyList()
        override fun getSelectedTracksForAudiobookFlow(audiobookId: Long): Flow<List<AudiobookTrackEntity>> = emptyFlow()
        override fun getPlaylistAudiobookTracksFlow(): Flow<List<AudiobookTrackEntity>> = emptyFlow()
        override suspend fun deleteEmptyAudiobookPlaylistItems() {
            deletedEmptyPlaylistItemsCalled = true
        }
        override suspend fun getFirstUnlistenedSelectedTrack(audiobookId: Long): AudiobookTrackEntity? = null
        override suspend fun getFirstSelectedTrack(audiobookId: Long): AudiobookTrackEntity? = null
        override suspend fun getCurrentTrackForAudiobook(audiobookId: Long): AudiobookTrackEntity? = null
        override suspend fun resetAudiobookProgress(audiobookId: Long) {}
    }

    internal class FakeAudioMetadataExtractor : AudioMetadataExtractor {
        var metadataResult: ExtractedAudioMetadata? = null
        var extractCallCount: Int = 0
        override fun extract(
            uri: Uri,
            includePicture: Boolean
        ): ExtractedAudioMetadata? {
            extractCallCount++
            return metadataResult
        }
    }

    internal class FakeDocumentTreeHelper : DocumentTreeHelper {
        val childrenMap = mutableMapOf<String, List<DocumentItem>>()
        var throwOnQuery: Throwable? = null

        override fun getTreeDocumentId(treeUri: Uri): String = "root"

        override fun queryChildren(
            contentResolver: ContentResolver,
            treeUri: Uri,
            parentDocId: String
        ): List<DocumentItem> {
            throwOnQuery?.let { throw it }
            return childrenMap[parentDocId] ?: emptyList()
        }

        override fun buildDocumentUri(treeUri: Uri, docId: String): Uri {
            return createMockUri("content://tree/$docId")
        }

        fun createMockUri(uriString: String): Uri {
            val mockUri = Mockito.mock(Uri::class.java)
            Mockito.`when`(mockUri.toString()).thenReturn(uriString)
            return mockUri
        }
    }
}
