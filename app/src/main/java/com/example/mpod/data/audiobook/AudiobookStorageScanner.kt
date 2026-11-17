package com.example.mpod.data.audiobook

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.example.mpod.data.local.dao.AudiobookDao
import com.example.mpod.data.local.entity.AudiobookEntity
import com.example.mpod.data.local.entity.AudiobookTrackEntity
import com.example.mpod.util.NaturalOrderComparator
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

data class ScanSummary(
    val booksScanned: Int = 0,
    val tracksScanned: Int = 0,
    val isLimitExceeded: Boolean = false,
    val errorMessage: String? = null
)

internal data class DocumentItem(
    val docId: String,
    val name: String,
    val mimeType: String,
    val size: Long,
    val isDirectory: Boolean
)

@Singleton
class AudiobookStorageScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val audiobookDao: AudiobookDao,
    private val metadataExtractor: AudioMetadataExtractor
) {

    internal var treeHelper: DocumentTreeHelper = DefaultDocumentTreeHelper()

    companion object {
        const val MAX_FILES_LIMIT = 2000
        private val SUPPORTED_AUDIO_EXTENSIONS = setOf(
            "mp3", "m4b", "m4a", "aac", "flac", "ogg", "opus", "wav"
        )
        private val COVER_FILE_NAMES = setOf(
            "cover.jpg", "folder.jpg", "cover.png", "folder.png", "cover.jpeg", "folder.jpeg"
        )
        internal val DISC_FOLDER_PATTERN = Regex("^(cd|disc|disk)\\s*\\d+$", RegexOption.IGNORE_CASE)
    }

    private class ScanCounter(
        var filesEvaluated: Int = 0,
        var booksCount: Int = 0,
        var tracksCount: Int = 0,
        var limitExceeded: Boolean = false
    )

    suspend fun scanFolder(treeUri: Uri): ScanSummary = withContext(Dispatchers.IO) {
        val contentResolver = context.contentResolver
        val counter = ScanCounter()
        val scannedFolderUris = mutableSetOf<String>()

        try {
            val rootDocId = treeHelper.getTreeDocumentId(treeUri)
            scanDirectoryRecursively(
                contentResolver = contentResolver,
                treeUri = treeUri,
                dirDocId = rootDocId,
                currentPath = emptyList(),
                counter = counter,
                scannedFolderUris = scannedFolderUris
            )

            if (!counter.limitExceeded) {
                if (scannedFolderUris.isEmpty()) {
                    audiobookDao.deleteAllAudiobooks()
                } else {
                    audiobookDao.deleteAudiobooksNotIn(scannedFolderUris.toList())
                }
            }

            ScanSummary(
                booksScanned = counter.booksCount,
                tracksScanned = counter.tracksCount,
                isLimitExceeded = counter.limitExceeded
            )
        } catch (e: Exception) {
            ScanSummary(
                booksScanned = counter.booksCount,
                tracksScanned = counter.tracksCount,
                isLimitExceeded = counter.limitExceeded,
                errorMessage = e.message?.takeIf { it.isNotBlank() }
                    ?: e.localizedMessage?.takeIf { it.isNotBlank() }
                    ?: "Storage access error (${e.javaClass.simpleName})"
            )
        }
    }

    private suspend fun scanDirectoryRecursively(
        contentResolver: ContentResolver,
        treeUri: Uri,
        dirDocId: String,
        currentPath: List<String>,
        counter: ScanCounter,
        scannedFolderUris: MutableSet<String>
    ) {
        if (counter.limitExceeded || counter.filesEvaluated >= MAX_FILES_LIMIT) {
            counter.limitExceeded = true
            return
        }

        val children = treeHelper.queryChildren(contentResolver, treeUri, dirDocId)
        val directAudioFiles = children
            .filter { isAudioFile(it) }
            .sortedWith(NaturalOrderComparator.comparing { it.name })
        val subDirs = children
            .filter { it.isDirectory }
            .sortedWith(NaturalOrderComparator.comparing { it.name })

        val discFolders = subDirs.filter { DISC_FOLDER_PATTERN.matches(it.name) }
        val nonDiscSubDirs = subDirs.filter { !DISC_FOLDER_PATTERN.matches(it.name) }

        if (currentPath.isEmpty()) {
            // 1. Direct audio files in root are standalone books
            for (audioFile in directAudioFiles) {
                if (counter.filesEvaluated >= MAX_FILES_LIMIT) {
                    counter.limitExceeded = true
                    return
                }
                counter.filesEvaluated++
                val trackUri = treeHelper.buildDocumentUri(treeUri, audioFile.docId)
                val book = scanSingleTrackAsBook(
                    fileUri = trackUri,
                    displayName = audioFile.name,
                    authorFallback = "Unknown Author",
                    relativePath = audioFile.name
                )
                if (book != null) {
                    scannedFolderUris.add(book.folderUri)
                    counter.booksCount++
                    counter.tracksCount++
                }
            }

            // 2. Subdirectories in root
            for (subDir in nonDiscSubDirs) {
                if (counter.limitExceeded || counter.filesEvaluated >= MAX_FILES_LIMIT) {
                    counter.limitExceeded = true
                    return
                }
                scanDirectoryRecursively(
                    contentResolver = contentResolver,
                    treeUri = treeUri,
                    dirDocId = subDir.docId,
                    currentPath = listOf(subDir.name),
                    counter = counter,
                    scannedFolderUris = scannedFolderUris
                )
            }
        } else {
            val isBookFolder = if (currentPath.size > 1) {
                (directAudioFiles.isNotEmpty() || discFolders.isNotEmpty()) && nonDiscSubDirs.isEmpty()
            } else {
                (directAudioFiles.size > 1 || discFolders.isNotEmpty()) && nonDiscSubDirs.isEmpty()
            }

            if (isBookFolder) {
                // Folder-backed book
                val author = if (currentPath.size > 1) currentPath.first() else "Unknown Author"
                val relativePath = currentPath.joinToString("/")
                val bookDir = DocumentItem(
                    docId = dirDocId,
                    name = currentPath.last(),
                    mimeType = DocumentsContract.Document.MIME_TYPE_DIR,
                    size = 0L,
                    isDirectory = true
                )

                val bookResult = processBookFolder(
                    contentResolver = contentResolver,
                    treeUri = treeUri,
                    bookDir = bookDir,
                    directChildren = children,
                    authorFallback = author,
                    relativePath = relativePath,
                    currentFileCount = counter.filesEvaluated
                )

                counter.filesEvaluated += bookResult.tracksProcessed
                counter.tracksCount += bookResult.tracksProcessed
                if (bookResult.tracksProcessed > 0) {
                    val bookFolderUri = treeHelper.buildDocumentUri(treeUri, bookDir.docId).toString()
                    scannedFolderUris.add(bookFolderUri)
                    counter.booksCount++
                }
                if (bookResult.limitExceeded) {
                    counter.limitExceeded = true
                }
            } else {
                // Collection directory or folder with standalone files
                for (audioFile in directAudioFiles) {
                    if (counter.filesEvaluated >= MAX_FILES_LIMIT) {
                        counter.limitExceeded = true
                        return
                    }
                    counter.filesEvaluated++
                    val trackUri = treeHelper.buildDocumentUri(treeUri, audioFile.docId)
                    val author = currentPath.first()
                    val relativePath = (currentPath + audioFile.name).joinToString("/")
                    val book = scanSingleTrackAsBook(
                        fileUri = trackUri,
                        displayName = audioFile.name,
                        authorFallback = author,
                        relativePath = relativePath
                    )
                    if (book != null) {
                        scannedFolderUris.add(book.folderUri)
                        counter.booksCount++
                        counter.tracksCount++
                    }
                }

                for (subDir in nonDiscSubDirs) {
                    if (counter.limitExceeded || counter.filesEvaluated >= MAX_FILES_LIMIT) {
                        counter.limitExceeded = true
                        return
                    }
                    scanDirectoryRecursively(
                        contentResolver = contentResolver,
                        treeUri = treeUri,
                        dirDocId = subDir.docId,
                        currentPath = currentPath + subDir.name,
                        counter = counter,
                        scannedFolderUris = scannedFolderUris
                    )
                }
            }
        }
    }

    suspend fun scanSingleAudioFile(fileUri: Uri): AudiobookEntity? = withContext(Dispatchers.IO) {
        val displayName = queryDisplayName(context.contentResolver, fileUri)
            ?: fileUri.lastPathSegment?.substringAfterLast('/')
            ?: "Audiobook"
        scanSingleTrackAsBook(
            fileUri = fileUri,
            displayName = displayName,
            authorFallback = "Unknown Author",
            relativePath = displayName
        )
    }

    internal suspend fun processBookFolder(
        contentResolver: ContentResolver,
        treeUri: Uri,
        bookDir: DocumentItem,
        directChildren: List<DocumentItem>,
        authorFallback: String,
        relativePath: String = bookDir.name,
        currentFileCount: Int
    ): BookProcessResult {
        // 1. Collect direct root audio files and sort naturally
        val directAudioFiles = directChildren
            .filter { isAudioFile(it) }
            .sortedWith(NaturalOrderComparator.comparing { it.name })

        // 2. Discover and sort disc folders naturally (CD1, CD2, etc.)
        val discFolders = directChildren
            .filter { it.isDirectory && DISC_FOLDER_PATTERN.matches(it.name) }
            .sortedWith(NaturalOrderComparator.comparing { it.name })

        val discChildrenMap = mutableMapOf<DocumentItem, List<DocumentItem>>()
        val discAudioFiles = mutableListOf<DocumentItem>()

        for (discDir in discFolders) {
            val discChildren = treeHelper.queryChildren(contentResolver, treeUri, discDir.docId)
            discChildrenMap[discDir] = discChildren
            val sortedChildrenAudio = discChildren
                .filter { isAudioFile(it) }
                .sortedWith(NaturalOrderComparator.comparing { it.name })
            discAudioFiles.addAll(sortedChildrenAudio)
        }

        // Ordered audio files: direct root tracks first, then tracks ordered by disc sequence
        val allAudioFiles = directAudioFiles + discAudioFiles

        if (allAudioFiles.isEmpty()) {
            return BookProcessResult(tracksProcessed = 0, limitExceeded = false)
        }

        val availableCapacity = (MAX_FILES_LIMIT - currentFileCount).coerceAtLeast(0)
        val limitExceeded = allAudioFiles.size > availableCapacity
        val sortedAudioFiles = if (limitExceeded) allAudioFiles.take(availableCapacity) else allAudioFiles

        if (sortedAudioFiles.isEmpty()) {
            return BookProcessResult(tracksProcessed = 0, limitExceeded = limitExceeded)
        }

        // Check for standalone cover image in book directory, fallback to disc subfolders (in natural disc order)
        var coverFile = directChildren.firstOrNull { isCoverFile(it) }
        if (coverFile == null) {
            for (discDir in discFolders) {
                val discChildren = discChildrenMap[discDir] ?: continue
                val discCover = discChildren.firstOrNull { isCoverFile(it) }
                if (discCover != null) {
                    coverFile = discCover
                    break
                }
            }
        }

        var coverUriString: String? = null
        if (coverFile != null) {
            coverUriString = treeHelper.buildDocumentUri(treeUri, coverFile.docId).toString()
        }

        // Extract metadata from first track. Only request embedded picture if no folder cover exists
        val firstTrackDocUri = treeHelper.buildDocumentUri(treeUri, sortedAudioFiles[0].docId)
        val needPicture = (coverUriString == null)
        val firstMetadata = metadataExtractor.extract(firstTrackDocUri, includePicture = needPicture)

        if (coverUriString == null && firstMetadata?.embeddedPicture != null) {
            coverUriString = saveCoverToCache(bookDir.docId, firstMetadata.embeddedPicture)
        }

        val bookTitle = bookDir.name
        val bookAuthor = authorFallback.ifBlank { "Unknown Author" }

        val bookFolderUri = treeHelper.buildDocumentUri(treeUri, bookDir.docId).toString()
        val existingBook = audiobookDao.getAudiobookByFolderUri(bookFolderUri)

        val bookEntity = AudiobookEntity(
            id = existingBook?.id ?: 0L,
            title = bookTitle,
            author = bookAuthor,
            folderUri = bookFolderUri,
            coverUri = coverUriString ?: existingBook?.coverUri,
            durationSeconds = existingBook?.durationSeconds ?: 0L,
            isSingleFile = sortedAudioFiles.size == 1,
            addedAt = existingBook?.addedAt ?: System.currentTimeMillis(),
            relativePath = relativePath
        )
        val bookId = if (existingBook != null) {
            audiobookDao.updateAudiobook(bookEntity)
            existingBook.id
        } else {
            val insertedId = audiobookDao.insertAudiobook(bookEntity)
            if (insertedId > 0) insertedId else (audiobookDao.getAudiobookByFolderUri(bookFolderUri)?.id ?: -1L)
        }

        // Sync tracks
        val existingTracks = audiobookDao.getTracksForAudiobook(bookId)
        val existingByFileUri = existingTracks.associateBy { it.fileUri }
        val processedFileUris = mutableSetOf<String>()
        var totalBookDuration = 0L

        sortedAudioFiles.forEachIndexed { index, audioItem ->
            val trackUri = treeHelper.buildDocumentUri(treeUri, audioItem.docId)
            val trackUriString = trackUri.toString()
            processedFileUris.add(trackUriString)

            // For tracks 1..N, never request embedded picture to avoid OOM
            val trackMetadata = if (index == 0) firstMetadata else metadataExtractor.extract(trackUri, includePicture = false)
            val trackDuration = trackMetadata?.durationSeconds ?: 0L
            val trackTitle = audioItem.name.substringBeforeLast('.')

            totalBookDuration += trackDuration
            val existing = existingByFileUri[trackUriString]

            if (existing != null) {
                audiobookDao.updateTrack(
                    existing.copy(
                        title = trackTitle,
                        trackNumber = index + 1,
                        durationSeconds = if (trackDuration > 0) trackDuration else existing.durationSeconds,
                        isAvailable = true
                    )
                )
            } else {
                audiobookDao.insertTrack(
                    AudiobookTrackEntity(
                        audiobookId = bookId,
                        title = trackTitle,
                        fileUri = trackUriString,
                        trackNumber = index + 1,
                        durationSeconds = trackDuration,
                        playbackPositionMs = 0L,
                        isListened = false,
                        isAvailable = true
                    )
                )
            }
        }

        // Rescan: mark tracks previously in DB that were removed from storage as unavailable
        for (existing in existingTracks) {
            if (existing.fileUri !in processedFileUris && existing.isAvailable) {
                audiobookDao.updateTrackAvailability(existing.id, isAvailable = false)
            }
        }

        // Update total book duration
        if (totalBookDuration > 0) {
            audiobookDao.updateAudiobook(bookEntity.copy(id = bookId, durationSeconds = totalBookDuration))
        }

        return BookProcessResult(
            tracksProcessed = sortedAudioFiles.size,
            limitExceeded = limitExceeded
        )
    }

    internal suspend fun scanSingleTrackAsBook(
        fileUri: Uri,
        displayName: String,
        authorFallback: String,
        relativePath: String = displayName
    ): AudiobookEntity? {
        val metadata = metadataExtractor.extract(fileUri, includePicture = true)
        val title = displayName.substringBeforeLast('.')
        val author = authorFallback.ifBlank { "Unknown Author" }
        val duration = metadata?.durationSeconds ?: 0L

        var coverUriString: String? = null
        if (metadata?.embeddedPicture != null) {
            coverUriString = saveCoverToCache(fileUri.toString(), metadata.embeddedPicture)
        }

        val existingBook = audiobookDao.getAudiobookByFolderUri(fileUri.toString())

        val bookEntity = AudiobookEntity(
            id = existingBook?.id ?: 0L,
            title = title,
            author = author,
            folderUri = fileUri.toString(),
            coverUri = coverUriString ?: existingBook?.coverUri,
            durationSeconds = duration,
            isSingleFile = true,
            addedAt = existingBook?.addedAt ?: System.currentTimeMillis(),
            relativePath = relativePath
        )
        val bookId = if (existingBook != null) {
            audiobookDao.updateAudiobook(bookEntity)
            existingBook.id
        } else {
            val insertedId = audiobookDao.insertAudiobook(bookEntity)
            if (insertedId > 0) insertedId else (audiobookDao.getAudiobookByFolderUri(fileUri.toString())?.id ?: -1L)
        }

        val existingTracks = audiobookDao.getTracksForAudiobook(bookId)
        val existing = existingTracks.firstOrNull { it.fileUri == fileUri.toString() }
        if (existing != null) {
            audiobookDao.updateTrack(
                existing.copy(
                    title = title,
                    durationSeconds = duration,
                    isAvailable = true
                )
            )
        } else {
            audiobookDao.insertTrack(
                AudiobookTrackEntity(
                    audiobookId = bookId,
                    title = title,
                    fileUri = fileUri.toString(),
                    trackNumber = 1,
                    durationSeconds = duration,
                    playbackPositionMs = 0L,
                    isListened = false,
                    isAvailable = true
                )
            )
        }

        return bookEntity.copy(id = bookId)
    }

    private fun queryDisplayName(contentResolver: ContentResolver, uri: Uri): String? {
        val projection = arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        return try {
            contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(0)
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun saveCoverToCache(key: String, pictureBytes: ByteArray): String? {
        return try {
            val cacheDir = File(context.cacheDir, "audiobook_covers").apply { if (!exists()) mkdirs() }
            val hash = sha256(key)
            val ext = detectImageExtension(pictureBytes)
            val fileName = "cover_$hash.$ext"
            val file = File(cacheDir, fileName)
            file.writeBytes(pictureBytes)
            Uri.fromFile(file).toString()
        } catch (_: Exception) {
            null
        }
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun detectImageExtension(bytes: ByteArray): String {
        if (bytes.size >= 8 &&
            bytes[0] == 0x89.toByte() &&
            bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() &&
            bytes[3] == 0x47.toByte()
        ) {
            return "png"
        }
        return "jpg"
    }

    private fun isAudioFile(item: DocumentItem): Boolean {
        if (item.isDirectory) return false
        if (item.mimeType.startsWith("audio/")) return true
        val ext = item.name.substringAfterLast('.', "").lowercase()
        return ext in SUPPORTED_AUDIO_EXTENSIONS
    }

    private fun isCoverFile(item: DocumentItem): Boolean {
        if (item.isDirectory) return false
        return item.name.lowercase() in COVER_FILE_NAMES
    }

    internal data class BookProcessResult(
        val tracksProcessed: Int,
        val limitExceeded: Boolean
    )
}

internal interface DocumentTreeHelper {
    fun getTreeDocumentId(treeUri: Uri): String
    fun queryChildren(contentResolver: ContentResolver, treeUri: Uri, parentDocId: String): List<DocumentItem>
    fun buildDocumentUri(treeUri: Uri, docId: String): Uri
}

internal class DefaultDocumentTreeHelper : DocumentTreeHelper {
    override fun getTreeDocumentId(treeUri: Uri): String {
        return DocumentsContract.getTreeDocumentId(treeUri)
    }

    override fun queryChildren(
        contentResolver: ContentResolver,
        treeUri: Uri,
        parentDocId: String
    ): List<DocumentItem> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE
        )
        val items = mutableListOf<DocumentItem>()
        val cursor = contentResolver.query(childrenUri, projection, null, null, null)
            ?: throw SecurityException("Cannot query SAF directory (permission revoked or directory inaccessible): $parentDocId")
        cursor.use { c ->
            val idIdx = c.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIdx = c.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeIdx = c.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val sizeIdx = c.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)

            while (c.moveToNext()) {
                val docId = if (idIdx >= 0) c.getString(idIdx) else ""
                val name = if (nameIdx >= 0) c.getString(nameIdx) ?: "" else ""
                val mime = if (mimeIdx >= 0) c.getString(mimeIdx) ?: "" else ""
                val size = if (sizeIdx >= 0) c.getLong(sizeIdx) else 0L
                val isDir = mime == DocumentsContract.Document.MIME_TYPE_DIR
                if (docId.isNotBlank()) {
                    items.add(DocumentItem(docId, name, mime, size, isDir))
                }
            }
        }
        return items
    }

    override fun buildDocumentUri(treeUri: Uri, docId: String): Uri {
        return DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
    }
}
