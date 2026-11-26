package com.example.mpod.ui.screens.audiobooks

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mpod.data.audiobook.AudiobookStorageScanner
import com.example.mpod.data.audiobook.ScanSummary
import com.example.mpod.data.local.dao.PlaylistDao
import com.example.mpod.data.local.entity.AudiobookEntity
import com.example.mpod.data.local.entity.AudiobookTrackEntity
import com.example.mpod.data.local.preferences.AppSettingsStore
import com.example.mpod.data.repository.AudiobookRepository
import com.example.mpod.data.repository.PlaylistRepository
import com.example.mpod.playback.PlaybackQueueInvalidator
import com.example.mpod.ui.components.FilemanagerItemType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class FilemanagerItemUi(
    val id: String,
    val title: String,
    val type: FilemanagerItemType,
    val durationText: String? = null,
    val audiobookId: Long? = null,
    val isInPlaylist: Boolean = false,
    val author: String? = null,
    val book: AudiobookEntity? = null
)

private data class ChapterSyncRequest(
    val bookId: Long,
    val trackIds: List<Long>
)

private data class AudiobookCombineSnapshot(
    val audiobooksFolderUri: String?,
    val audiobooks: List<AudiobookEntity>,
    val playlistAudiobookIds: Set<Long>,
    val currentPath: List<String>
)

data class AudiobooksUiState(
    val isLoading: Boolean = false,
    val isScanning: Boolean = false,
    val errorMessage: String? = null,
    val statusMessage: String? = null,
    val audiobooksFolderUri: String? = null,
    val currentPath: List<String> = emptyList(),
    val items: List<FilemanagerItemUi> = emptyList(),
    val totalBooksCount: Int = 0,
    val selectedBookForModal: AudiobookEntity? = null,
    val selectedBookTracks: List<AudiobookTrackEntity> = emptyList(),
    val selectedBookPlaylistTrackIds: Set<Long> = emptySet()
)

@HiltViewModel
class AudiobooksViewModel @Inject constructor(
    private val audiobookRepository: AudiobookRepository,
    private val playlistRepository: PlaylistRepository,
    private val playlistDao: PlaylistDao,
    private val appSettingsStore: AppSettingsStore,
    private val queueInvalidator: PlaybackQueueInvalidator
) : ViewModel() {

    private val _state = MutableStateFlow(AudiobooksUiState(isLoading = true))
    val state: StateFlow<AudiobooksUiState> = _state.asStateFlow()

    internal var ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    private val currentPathFlow = MutableStateFlow<List<String>>(emptyList())
    private var allAudiobooks: List<AudiobookEntity> = emptyList()
    private var playlistAudiobookIds: Set<Long> = emptySet()
    private var hasTriggeredLegacyRescan = false
    private val scanMutex = Mutex()
    private var modalTracksJob: Job? = null

    private val activeBookSyncChannels = java.util.concurrent.ConcurrentHashMap<Long, Pair<Channel<ChapterSyncRequest>, Job>>()
    private val syncChannelsLock = Any()
    private val toggleBookMutexes = java.util.concurrent.ConcurrentHashMap<Long, Mutex>()

    @OptIn(DelicateCoroutinesApi::class)
    private fun getSyncChannelForBook(bookId: Long): Channel<ChapterSyncRequest> {
        synchronized(syncChannelsLock) {
            val existing = activeBookSyncChannels[bookId]
            if (existing != null && !existing.first.isClosedForSend) {
                return existing.first
            }
            val channel = Channel<ChapterSyncRequest>(Channel.CONFLATED)
            val job = viewModelScope.launch(ioDispatcher) {
                try {
                    for (request in channel) {
                        try {
                            playlistRepository.syncAudiobookSelection(request.bookId, request.trackIds)
                            queueInvalidator.invalidate()
                        } catch (e: Exception) {
                            if (e is kotlinx.coroutines.CancellationException) throw e
                            handleSyncError(request, e)
                        }
                    }
                } finally {
                    synchronized(syncChannelsLock) {
                        if (activeBookSyncChannels[bookId]?.first == channel) {
                            activeBookSyncChannels.remove(bookId)
                        }
                    }
                }
            }
            activeBookSyncChannels[bookId] = channel to job
            return channel
        }
    }

    private suspend fun handleSyncError(request: ChapterSyncRequest, error: Exception) {
        Log.e("AudiobooksVM", "Failed to sync book ${request.bookId}", error)
        if (_state.value.selectedBookForModal?.id != request.bookId) {
            return
        }
        val actualDbState = try {
            withContext(ioDispatcher) {
                audiobookRepository.getSelectedTracksForAudiobook(request.bookId).map { it.id }.toSet()
            }
        } catch (dbReadError: Exception) {
            Log.e("AudiobooksVM", "Failed to read DB state after sync error", dbReadError)
            emptySet()
        }

        _state.update { s ->
            if (s.selectedBookForModal?.id == request.bookId) {
                s.copy(
                    selectedBookPlaylistTrackIds = actualDbState,
                    errorMessage = "Failed to update chapters: ${error.localizedMessage ?: "Unknown error"}"
                )
            } else {
                s
            }
        }
    }

    init {
        viewModelScope.launch(ioDispatcher) {
            combine(
                appSettingsStore.settingsFlow,
                audiobookRepository.audiobooksFlow,
                playlistDao.getPlaylistItemsWithAudiobooksFlow(),
                currentPathFlow
            ) { settings, audiobooks, playlistItems, currentPath ->
                if (!hasTriggeredLegacyRescan && settings.audiobooksFolderUri != null && audiobooks.any { it.relativePath.isBlank() }) {
                    hasTriggeredLegacyRescan = true
                    onRescanLibrary()
                }

                AudiobookCombineSnapshot(
                    audiobooksFolderUri = settings.audiobooksFolderUri,
                    audiobooks = audiobooks,
                    playlistAudiobookIds = playlistItems.map { it.audiobook.id }.toSet(),
                    currentPath = currentPath
                )
            }.collect { snapshot ->
                val previousAudiobookIds = allAudiobooks.mapTo(mutableSetOf()) { it.id }
                val previousPlaylistIds = playlistAudiobookIds
                allAudiobooks = snapshot.audiobooks
                playlistAudiobookIds = snapshot.playlistAudiobookIds

                val items = buildFilemanagerItems(
                    audiobooks = snapshot.audiobooks,
                    playlistIds = snapshot.playlistAudiobookIds,
                    currentPath = snapshot.currentPath
                )

                var shouldCancelModalTracks = false
                _state.update { current ->
                    val currentModalBook = current.selectedBookForModal
                    val updatedModalBook = currentModalBook?.let { modal ->
                        snapshot.audiobooks.firstOrNull { it.id == modal.id }
                    }
                    val wasModalBookRemoved = currentModalBook != null &&
                        currentModalBook.id in previousAudiobookIds &&
                        updatedModalBook == null
                    if (wasModalBookRemoved) {
                        shouldCancelModalTracks = true
                    }
                    val wasRemovedFromPlaylist = currentModalBook != null &&
                        currentModalBook.id in previousPlaylistIds &&
                        currentModalBook.id !in snapshot.playlistAudiobookIds

                    current.copy(
                        isLoading = false,
                        audiobooksFolderUri = snapshot.audiobooksFolderUri,
                        currentPath = snapshot.currentPath,
                        items = items,
                        totalBooksCount = snapshot.audiobooks.size,
                        selectedBookForModal = if (wasModalBookRemoved) null else (updatedModalBook ?: currentModalBook),
                        selectedBookTracks = if (wasModalBookRemoved) emptyList() else current.selectedBookTracks,
                        selectedBookPlaylistTrackIds = when {
                            wasModalBookRemoved || wasRemovedFromPlaylist -> emptySet()
                            else -> current.selectedBookPlaylistTrackIds
                        }
                    )
                }
                if (shouldCancelModalTracks) {
                    modalTracksJob?.cancel()
                    modalTracksJob = null
                }
            }
        }
    }

    fun navigateToFolder(folderName: String) {
        currentPathFlow.value = currentPathFlow.value + folderName
    }

    fun navigateToBreadcrumb(index: Int) {
        if (index < 0) {
            currentPathFlow.value = emptyList()
        } else {
            currentPathFlow.value = currentPathFlow.value.take(index + 1)
        }
    }

    fun onScanFolder(uri: Uri) {
        viewModelScope.launch(ioDispatcher) {
            executeScan(
                loadingMessage = "Scanning audiobooks...",
                failPrefix = "Scan failed",
                successFormatter = { summary ->
                    "Found ${summary.booksScanned} audiobooks (${summary.tracksScanned} tracks)"
                }
            ) {
                audiobookRepository.scanDirectory(uri)
            }
        }
    }

    fun onRescanLibrary() {
        viewModelScope.launch(ioDispatcher) {
            executeScan(
                loadingMessage = "Rescanning library...",
                failPrefix = "Rescan failed",
                successFormatter = { summary ->
                    "Rescan complete: ${summary.booksScanned} scanned, ${summary.tracksScanned} tracks"
                },
                nullSummaryError = "No audiobooks folder configured"
            ) {
                audiobookRepository.rescanLibrary()
            }
        }
    }

    private suspend fun executeScan(
        loadingMessage: String,
        failPrefix: String,
        successFormatter: (ScanSummary) -> String,
        nullSummaryError: String? = null,
        scanAction: suspend () -> ScanSummary?
    ) {
        scanMutex.withLock {
            _state.update {
                it.copy(isScanning = true, errorMessage = null, statusMessage = loadingMessage)
            }
            try {
                val summary = scanAction()
                when {
                    summary == null -> {
                        _state.update {
                            it.copy(
                                isScanning = false,
                                errorMessage = nullSummaryError ?: "$failPrefix: Unknown error",
                                statusMessage = null
                            )
                        }
                    }
                    summary.errorMessage != null -> {
                        _state.update {
                            it.copy(
                                isScanning = false,
                                errorMessage = "$failPrefix: ${summary.errorMessage}",
                                statusMessage = null
                            )
                        }
                    }
                    else -> {
                        val baseMessage = successFormatter(summary)
                        val message = if (summary.isLimitExceeded) {
                            "$baseMessage. File limit (${AudiobookStorageScanner.MAX_FILES_LIMIT}) reached — some files and cleanup were skipped."
                        } else {
                            baseMessage
                        }
                        _state.update {
                            it.copy(
                                isScanning = false,
                                statusMessage = message
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.update {
                    it.copy(
                        isScanning = false,
                        errorMessage = "$failPrefix: ${e.localizedMessage ?: "Unknown error"}",
                        statusMessage = null
                    )
                }
            }
        }
    }

    fun toggleBookPlaylist(bookId: Long) {
        viewModelScope.launch(ioDispatcher) {
            val mutex = toggleBookMutexes.getOrPut(bookId) { Mutex() }
            mutex.withLock {
                try {
                    val inQueue = playlistDao.isAudiobookInPlaylist(bookId)
                    if (inQueue) {
                        playlistRepository.removeAudiobookFromPlaylist(bookId)
                        _state.update { s ->
                            if (s.selectedBookForModal?.id == bookId) {
                                s.copy(selectedBookPlaylistTrackIds = emptySet())
                            } else s
                        }
                    } else {
                        val added = playlistRepository.addAudiobookToPlaylist(bookId)
                        if (added && _state.value.selectedBookForModal?.id == bookId) {
                            val selectedIds = audiobookRepository.getSelectedTracksForAudiobook(bookId)
                                .map { it.id }
                                .toSet()
                            _state.update { s ->
                                if (s.selectedBookForModal?.id == bookId) {
                                    s.copy(selectedBookPlaylistTrackIds = selectedIds)
                                } else s
                            }
                        }
                    }
                    queueInvalidator.invalidate()
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    _state.update {
                        it.copy(errorMessage = "Failed to update playlist: ${e.localizedMessage ?: "Unknown error"}")
                    }
                }
            }
        }
    }

    fun openBookModal(book: AudiobookEntity) {
        modalTracksJob?.cancel()
        modalTracksJob = viewModelScope.launch(ioDispatcher) {
            val inQueue = playlistDao.isAudiobookInPlaylist(book.id)
            val tracks = audiobookRepository.getTracksForAudiobook(book.id)
            val selectedIds = if (inQueue) {
                audiobookRepository.getSelectedTracksForAudiobook(book.id)
                    .map { it.id }
                    .toSet()
            } else {
                emptySet()
            }

            _state.update {
                it.copy(
                    selectedBookForModal = book,
                    selectedBookTracks = tracks,
                    selectedBookPlaylistTrackIds = selectedIds
                )
            }

            audiobookRepository.getTracksForAudiobookFlow(book.id).collect { updatedTracks ->
                val availableIds = updatedTracks.mapTo(mutableSetOf()) { it.id }
                _state.update { s ->
                    if (s.selectedBookForModal?.id == book.id) {
                        s.copy(
                            selectedBookTracks = updatedTracks,
                            selectedBookPlaylistTrackIds = s.selectedBookPlaylistTrackIds.intersect(availableIds)
                        )
                    } else {
                        s
                    }
                }
            }
        }
    }

    fun closeBookModal() {
        modalTracksJob?.cancel()
        modalTracksJob = null
        _state.update { s ->
            s.copy(
                selectedBookForModal = null,
                selectedBookTracks = emptyList(),
                selectedBookPlaylistTrackIds = emptySet()
            )
        }
    }

    fun toggleTrackSelection(track: AudiobookTrackEntity) {
        var targetBookId: Long? = null
        var nextSelected: Set<Long> = emptySet()
        _state.update { s ->
            val book = s.selectedBookForModal ?: return@update s
            targetBookId = book.id
            val updated = s.selectedBookPlaylistTrackIds.toMutableSet()
            if (track.id in updated) {
                updated.remove(track.id)
            } else {
                updated.add(track.id)
            }
            nextSelected = updated
            s.copy(selectedBookPlaylistTrackIds = updated)
        }
        val bookId = targetBookId ?: return
        getSyncChannelForBook(bookId).trySend(
            ChapterSyncRequest(
                bookId = bookId,
                trackIds = nextSelected.toList()
            )
        )
    }

    fun dismissError() {
        _state.update { it.copy(errorMessage = null) }
    }

    fun dismissStatus() {
        _state.update { it.copy(statusMessage = null) }
    }

    override fun onCleared() {
        super.onCleared()
        synchronized(syncChannelsLock) {
            activeBookSyncChannels.values.forEach { (channel, job) ->
                channel.close()
                job.cancel()
            }
            activeBookSyncChannels.clear()
        }
    }

    companion object {
        internal fun formatFmDuration(totalSeconds: Long): String {
            if (totalSeconds <= 0) return ""
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            return when {
                hours > 0 -> "${hours}h ${minutes}m"
                else -> "${minutes}m"
            }
        }

        internal fun buildFilemanagerItems(
            audiobooks: List<AudiobookEntity>,
            playlistIds: Set<Long>,
            currentPath: List<String>
        ): List<FilemanagerItemUi> {
            if (audiobooks.isEmpty()) return emptyList()

            val directoriesMap = mutableMapOf<String, Pair<String, String>>()
            val directBooks = mutableListOf<AudiobookEntity>()

            for (book in audiobooks) {
                val segments = if (book.relativePath.isNotBlank()) {
                    book.relativePath.split('/').filter { it.isNotBlank() }
                } else {
                    if (book.author.isNotBlank() && !book.author.equals("Unknown Author", ignoreCase = true)) {
                        listOf(book.author, book.title)
                    } else {
                        listOf(book.title)
                    }
                }

                if (segments.size <= currentPath.size) continue
                var matchesCurrentPath = true
                for (i in currentPath.indices) {
                    if (!segments[i].equals(currentPath[i], ignoreCase = true)) {
                        matchesCurrentPath = false
                        break
                    }
                }
                if (!matchesCurrentPath) continue

                val remaining = segments.drop(currentPath.size)
                if (remaining.size == 1) {
                    directBooks.add(book)
                } else {
                    val subDirName = remaining.first()
                    directoriesMap.putIfAbsent(
                        subDirName.lowercase(),
                        subDirName to (currentPath + subDirName).joinToString("/")
                    )
                }
            }

            val dirItems = directoriesMap.values
                .sortedWith(com.example.mpod.util.NaturalOrderComparator.comparing { it.first })
                .map { (dirName, dirFullPath) ->
                    FilemanagerItemUi(
                        id = "dir:$dirFullPath",
                        title = dirName,
                        type = FilemanagerItemType.Directory,
                        author = (currentPath + dirName).firstOrNull()
                    )
                }

            val folderBooks = directBooks
                .filter { !it.isSingleFile }
                .sortedWith(com.example.mpod.util.NaturalOrderComparator.comparing { it.title })
                .map { book ->
                    FilemanagerItemUi(
                        id = "book:${book.id}",
                        title = book.title,
                        type = FilemanagerItemType.AudiobookDirectory,
                        durationText = formatFmDuration(book.durationSeconds),
                        audiobookId = book.id,
                        isInPlaylist = book.id in playlistIds,
                        author = book.author,
                        book = book
                    )
                }

            val standaloneFiles = directBooks
                .filter { it.isSingleFile }
                .sortedWith(com.example.mpod.util.NaturalOrderComparator.comparing { it.title })
                .map { book ->
                    FilemanagerItemUi(
                        id = "book:${book.id}",
                        title = book.title,
                        type = FilemanagerItemType.AudioFile,
                        durationText = formatFmDuration(book.durationSeconds),
                        audiobookId = book.id,
                        isInPlaylist = book.id in playlistIds,
                        author = book.author,
                        book = book
                    )
                }

            val allFolders = (dirItems + folderBooks).sortedWith(com.example.mpod.util.NaturalOrderComparator.comparing { it.title })
            return allFolders + standaloneFiles
        }
    }
}
