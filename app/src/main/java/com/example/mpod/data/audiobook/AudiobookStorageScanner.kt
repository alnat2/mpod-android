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

    suspend fun scanFolder(treeUri: Uri): ScanSummary = withContext(Dispatchers.IO) {
        val contentResolver = context.contentResolver
        var totalFilesCount = 0
        var totalBooksCount = 0
        var totalTracksCount = 0
        var limitExceeded = false

        try {
            val rootDocId = treeHelper.getTreeDocumentId(treeUri)
            val rootChildren = treeHelper.queryChildren(contentResolver, treeUri, rootDocId)

            // 1. Check for standalone single-file audiobooks placed directly in the root directory
            val rootAudioFiles = rootChildren.filter { isAudioFile(it) }
            for (audioFile in rootAudioFiles) {
                if (totalFilesCount >= MAX_FILES_LIMIT) {
                    limitExceeded = true
                    break
                }
                totalFilesCount++ // Guaranteed increment for every candidate file evaluated
                val trackUri = treeHelper.buildDocumentUri(treeUri, audioFile.docId)
                val book = scanSingleTrackAsBook(
                    fileUri = trackUri,
                    displayName = audioFile.name,
                    authorFallback = ""
                )
                if (book != null) {
                    totalBooksCount++
                    totalTracksCount++
                }
            }

            if (limitExceeded) {
                return@withContext ScanSummary(
                    booksScanned = totalBooksCount,
                    tracksScanned = totalTracksCount,
                    isLimitExceeded = true
                )
            }

            // 2. Traverse first-level subdirectories
            val subDirs = rootChildren.filter { it.isDirectory }
            for (dir in subDirs) {
                if (limitExceeded || totalFilesCount >= MAX_FILES_LIMIT) {
                    limitExceeded = true
                    break
                }

                val dirChildren = treeHelper.queryChildren(contentResolver, treeUri, dir.docId)
                val isBookFolder = dirChildren.any {
                    isAudioFile(it) || (it.isDirectory && DISC_FOLDER_PATTERN.matches(it.name))
                }

                if (isBookFolder) {
                    // This directory is a Book folder (Pattern: Root / Book / Chapters or Root / Book / CD1..)
                    val bookResult = processBookFolder(
                        contentResolver = contentResolver,
                        treeUri = treeUri,
                        bookDir = dir,
                        directChildren = dirChildren,
                        authorFallback = "",
                        currentFileCount = totalFilesCount
                    )
                    totalFilesCount += bookResult.tracksProcessed
                    totalTracksCount += bookResult.tracksProcessed
                    if (bookResult.tracksProcessed > 0) totalBooksCount++
                    if (bookResult.limitExceeded) {
                        limitExceeded = true
                        break
                    }
                } else {
                    // Check if this directory is an Author folder (Pattern: Root / Author / Book / Chapters)
                    val authorDirChildren = dirChildren.filter { it.isDirectory }
                    for (bookDir in authorDirChildren) {
                        if (totalFilesCount >= MAX_FILES_LIMIT) {
                            limitExceeded = true
                            break
                        }
                        val bookDirChildren = treeHelper.queryChildren(contentResolver, treeUri, bookDir.docId)
                        val isSubBookFolder = bookDirChildren.any {
                            isAudioFile(it) || (it.isDirectory && DISC_FOLDER_PATTERN.matches(it.name))
                        }
                        if (isSubBookFolder) {
                            val bookResult = processBookFolder(
                                contentResolver = contentResolver,
                                treeUri = treeUri,
                                bookDir = bookDir,
                                directChildren = bookDirChildren,
                                authorFallback = dir.name,
                                currentFileCount = totalFilesCount
                            )
                            totalFilesCount += bookResult.tracksProcessed
                            totalTracksCount += bookResult.tracksProcessed
                            if (bookResult.tracksProcessed > 0) totalBooksCount++
                            if (bookResult.limitExceeded) {
                                limitExceeded = true
                                break
                            }
                        }
                    }
                    if (limitExceeded) {
                        break
                    }
                }
            }

            ScanSummary(
                booksScanned = totalBooksCount,
                tracksScanned = totalTracksCount,
                isLimitExceeded = limitExceeded
            )
        } catch (e: Exception) {
            ScanSummary(
                booksScanned = totalBooksCount,
                tracksScanned = totalTracksCount,
                isLimitExceeded = limitExceeded,
                errorMessage = e.message
            )
        }
    }

    suspend fun scanSingleAudioFile(fileUri: Uri): AudiobookEntity? = withContext(Dispatchers.IO) {
        val displayName = queryDisplayName(context.contentResolver, fileUri)
            ?: fileUri.lastPathSegment?.substringAfterLast('/')
            ?: "Audiobook"
        scanSingleTrackAsBook(
            fileUri = fileUri,
            displayName = displayName,
            authorFallback = ""
        )
    }

    internal suspend fun processBookFolder(
        contentResolver: ContentResolver,
        treeUri: Uri,
        bookDir: DocumentItem,
        directChildren: List<DocumentItem>,
        authorFallback: String,
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

        val bookTitle = firstMetadata?.album?.takeIf { it.isNotBlank() }
            ?: bookDir.name
        val bookAuthor = firstMetadata?.artist?.takeIf { it.isNotBlank() }
            ?: authorFallback

        val bookFolderUri = treeHelper.buildDocumentUri(treeUri, bookDir.docId).toString()

        val bookEntity = AudiobookEntity(
            title = bookTitle,
            author = bookAuthor,
            folderUri = bookFolderUri,
            coverUri = coverUriString,
            durationSeconds = 0L,
            isSingleFile = sortedAudioFiles.size == 1,
            addedAt = System.currentTimeMillis()
        )
        val bookId = audiobookDao.upsertAudiobook(bookEntity)

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
            val trackTitle = trackMetadata?.title?.takeIf { it.isNotBlank() }
                ?: audioItem.name.substringBeforeLast('.')

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

    private suspend fun scanSingleTrackAsBook(
        fileUri: Uri,
        displayName: String,
        authorFallback: String
    ): AudiobookEntity? {
        val metadata = metadataExtractor.extract(fileUri, includePicture = true)
        val title = metadata?.title?.takeIf { it.isNotBlank() }
            ?: metadata?.album?.takeIf { it.isNotBlank() }
            ?: displayName.substringBeforeLast('.')
        val author = metadata?.artist?.takeIf { it.isNotBlank() }
            ?: authorFallback
        val duration = metadata?.durationSeconds ?: 0L

        var coverUriString: String? = null
        if (metadata?.embeddedPicture != null) {
            coverUriString = saveCoverToCache(fileUri.toString(), metadata.embeddedPicture)
        }

        val bookEntity = AudiobookEntity(
            title = title,
            author = author,
            folderUri = fileUri.toString(),
            coverUri = coverUriString,
            durationSeconds = duration,
            isSingleFile = true,
            addedAt = System.currentTimeMillis()
        )
        val bookId = audiobookDao.upsertAudiobook(bookEntity)

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
        try {
            contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)

                while (cursor.moveToNext()) {
                    val docId = if (idIdx >= 0) cursor.getString(idIdx) else ""
                    val name = if (nameIdx >= 0) cursor.getString(nameIdx) ?: "" else ""
                    val mime = if (mimeIdx >= 0) cursor.getString(mimeIdx) ?: "" else ""
                    val size = if (sizeIdx >= 0) cursor.getLong(sizeIdx) else 0L
                    val isDir = mime == DocumentsContract.Document.MIME_TYPE_DIR
                    if (docId.isNotBlank()) {
                        items.add(DocumentItem(docId, name, mime, size, isDir))
                    }
                }
            }
        } catch (_: Exception) {
            // Permission lost or directory inaccessible
        }
        return items
    }

    override fun buildDocumentUri(treeUri: Uri, docId: String): Uri {
        return DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
    }
}
