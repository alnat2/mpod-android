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
        fakeTreeHelper.childrenMap["root"] = listOf(
            DocumentItem("author_dir", "Стругацкие", "vnd.android.document/directory", 0L, isDirectory = true)
        )
        fakeTreeHelper.childrenMap["author_dir"] = listOf(
            DocumentItem("standalone", "Хромая судьба.mp3", "audio/mpeg", 1000L, isDirectory = false)
        )

        val summary = scanner.scanFolder(treeUri)

        assertEquals(1, summary.booksScanned)
        assertEquals(1, summary.tracksScanned)

        val book = fakeAudiobookDao.upsertedBooks.first()
        assertEquals("Хромая судьба", book.title)
        assertEquals("Стругацкие", book.author)
        assertEquals("Стругацкие/Хромая судьба.mp3", book.relativePath)
        assertTrue(book.isSingleFile)
    }

    internal class FakeAudiobookDao : AudiobookDao {
        val upsertedBooks = mutableListOf<AudiobookEntity>()
        val insertedTracks = mutableListOf<AudiobookTrackEntity>()

        override suspend fun upsertAudiobook(audiobook: AudiobookEntity): Long {
            upsertedBooks.add(audiobook)
            return 100L
        }

        override suspend fun insertAudiobook(audiobook: AudiobookEntity): Long = upsertAudiobook(audiobook)

        override suspend fun insertAudiobooks(audiobooks: List<AudiobookEntity>): List<Long> {
            upsertedBooks.addAll(audiobooks)
            return audiobooks.map { 100L }
        }

        override suspend fun updateAudiobook(audiobook: AudiobookEntity) {}
        override suspend fun deleteAudiobook(audiobook: AudiobookEntity) {}
        override suspend fun deleteAudiobookById(audiobookId: Long) {}
        override suspend fun getAudiobookById(audiobookId: Long): AudiobookEntity? = null
        override fun getAudiobookByIdFlow(audiobookId: Long): Flow<AudiobookEntity?> = emptyFlow()
        override suspend fun getAudiobookByFolderUri(folderUri: String): AudiobookEntity? = null
        override suspend fun getAllAudiobooks(): List<AudiobookEntity> = emptyList()
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
            insertedTracks.add(track)
            return track.id
        }

        override suspend fun insertTracks(tracks: List<AudiobookTrackEntity>): List<Long> {
            insertedTracks.addAll(tracks)
            return tracks.map { it.id }
        }

        override suspend fun updateTrack(track: AudiobookTrackEntity) {}
        override suspend fun getTrackByFileUri(audiobookId: Long, fileUri: String): AudiobookTrackEntity? =
            insertedTracks.find { it.audiobookId == audiobookId && it.fileUri == fileUri }
        override suspend fun updateTrackProgress(trackId: Long, positionMs: Long, isListened: Boolean) {}
        override suspend fun updateTrackAvailability(trackId: Long, isAvailable: Boolean) {}
        override suspend fun getTrackById(trackId: Long): AudiobookTrackEntity? =
            insertedTracks.find { it.id == trackId }
        override suspend fun getTracksForAudiobook(audiobookId: Long): List<AudiobookTrackEntity> =
            insertedTracks.filter { it.audiobookId == audiobookId }
        override fun getTracksForAudiobookFlow(audiobookId: Long): Flow<List<AudiobookTrackEntity>> = emptyFlow()
        override suspend fun deleteTracksForAudiobook(audiobookId: Long) {}

        override suspend fun insertPlaylistTrack(playlistTrack: AudiobookPlaylistTrackEntity) {}
        override suspend fun insertPlaylistTracks(playlistTracks: List<AudiobookPlaylistTrackEntity>) {}
        override suspend fun removePlaylistTrack(audiobookId: Long, trackId: Long) {}
        override suspend fun clearPlaylistTracksForBook(audiobookId: Long) {}
        override suspend fun getSelectedTrackIdsForAudiobook(audiobookId: Long): List<Long> = emptyList()
        override suspend fun getSelectedTracksForAudiobook(audiobookId: Long): List<AudiobookTrackEntity> = emptyList()
        override fun getSelectedTracksForAudiobookFlow(audiobookId: Long): Flow<List<AudiobookTrackEntity>> = emptyFlow()
        override fun getPlaylistAudiobookTracksFlow(): Flow<List<AudiobookTrackEntity>> = emptyFlow()
        override suspend fun getFirstUnlistenedSelectedTrack(audiobookId: Long): AudiobookTrackEntity? = null
        override suspend fun getFirstSelectedTrack(audiobookId: Long): AudiobookTrackEntity? = null
        override suspend fun getCurrentTrackForAudiobook(audiobookId: Long): AudiobookTrackEntity? = null
        override suspend fun resetAudiobookProgress(audiobookId: Long) {}
    }

    internal class FakeAudioMetadataExtractor : AudioMetadataExtractor {
        var metadataResult: ExtractedAudioMetadata? = null
        override fun extract(
            uri: Uri,
            includePicture: Boolean
        ): ExtractedAudioMetadata? = metadataResult
    }

    internal class FakeDocumentTreeHelper : DocumentTreeHelper {
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
            val mockUri = Mockito.mock(Uri::class.java)
            Mockito.`when`(mockUri.toString()).thenReturn(uriString)
            return mockUri
        }
    }
}
