package com.example.mpod.data.audiobook

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.example.mpod.data.local.dao.AudiobookDao
import com.example.mpod.data.local.entity.AudiobookEntity
import com.example.mpod.data.local.entity.AudiobookTrackEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class AudiobookStorageScannerTest {

    private lateinit var context: Context
    private lateinit var contentResolver: ContentResolver
    private lateinit var audiobookDao: AudiobookDao
    private lateinit var metadataExtractor: AudioMetadataExtractor
    private lateinit var fakeTreeHelper: FakeDocumentTreeHelper
    private lateinit var scanner: AudiobookStorageScanner
    private lateinit var treeUri: Uri

    @Before
    fun setUp() = runBlocking {
        context = mock()
        contentResolver = mock()
        whenever(context.contentResolver).thenReturn(contentResolver)

        audiobookDao = mock()
        whenever(audiobookDao.upsertAudiobook(any())).thenReturn(100L)
        whenever(audiobookDao.getTracksForAudiobook(any())).thenReturn(emptyList())

        metadataExtractor = mock()
        whenever(metadataExtractor.extract(any(), any())).thenReturn(null)

        fakeTreeHelper = FakeDocumentTreeHelper()
        scanner = AudiobookStorageScanner(context, audiobookDao, metadataExtractor)
        scanner.treeHelper = fakeTreeHelper

        treeUri = fakeTreeHelper.createMockUri("content://tree/root")
    }

    @Test
    fun processBookFolder_withDiscSubfolders_aggregatesTracksAcrossDiscsInNaturalOrder() = runBlocking {
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
        val bookCaptor = argumentCaptor<AudiobookEntity>()
        verify(audiobookDao).upsertAudiobook(bookCaptor.capture())
        assertEquals("War and Peace", bookCaptor.firstValue.title)
        assertEquals("Leo Tolstoy", bookCaptor.firstValue.author)

        // Verify tracks upserted in natural order across both discs
        val trackCaptor = argumentCaptor<AudiobookTrackEntity>()
        verify(audiobookDao, times(4)).insertTrack(trackCaptor.capture())
        val insertedTracks = trackCaptor.allValues

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
    fun processBookFolder_withDiscSubfolders_discoversCoverInsideDiscFolderWhenMissingInRoot() = runBlocking {
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

        val bookCaptor = argumentCaptor<AudiobookEntity>()
        verify(audiobookDao).upsertAudiobook(bookCaptor.capture())
        assertEquals("content://tree/cd1_cover", bookCaptor.firstValue.coverUri)
    }

    @Test
    fun processBookFolder_withCoverInBookRoot_prefersRootCoverOverDiscCover() = runBlocking {
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

        val bookCaptor = argumentCaptor<AudiobookEntity>()
        verify(audiobookDao).upsertAudiobook(bookCaptor.capture())
        assertEquals("content://tree/root_cover", bookCaptor.firstValue.coverUri)
    }

    @Test
    fun scanFolder_withMultiDiscBook_identifiesBookFolderAndAggregatesDiscs() = runBlocking {
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
    fun scanFolder_withAuthorHierarchyAndDiscSubfolders_assignsAuthorAndProcessesBook() = runBlocking {
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

        val bookCaptor = argumentCaptor<AudiobookEntity>()
        verify(audiobookDao).upsertAudiobook(bookCaptor.capture())
        assertEquals("The Two Towers", bookCaptor.firstValue.title)
        assertEquals("J. R. R. Tolkien", bookCaptor.firstValue.author)
    }

    class FakeDocumentTreeHelper : DocumentTreeHelper {
        val childrenMap = mutableMapOf<String, List<DocumentItem>>()

        override fun getTreeDocumentId(treeUri: Uri): String = "root"

        override fun queryChildren(
            contentResolver: ContentResolver,
            treeUri: Uri,
            parentDocId: String
        ): List<DocumentItem> {
            return childrenMap[parentDocId] ?: emptyList()
        }

        override fun buildDocumentUri(treeUri: Uri, docId: String): Uri {
            return createMockUri("content://tree/$docId")
        }

        fun createMockUri(uriString: String): Uri {
            val mockUri = mock<Uri>()
            whenever(mockUri.toString()).thenReturn(uriString)
            return mockUri
        }
    }
}
