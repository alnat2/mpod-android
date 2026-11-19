package com.example.mpod.ui.screens.audiobooks

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    val trackIds: List<Long>,
    val fallbackTrackIds: Set<Long>
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

    private val activeBookSyncChannels = java.util.concurrent.ConcurrentHashMap<Long, Pair<Channel<ChapterSyncRequest>, Job>>()
    private val syncChannelsLock = Any()

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
                allAudiobooks = audiobooks
                playlistAudiobookIds = playlistItems.map { it.audiobook.id }.toSet()

                if (!hasTriggeredLegacyRescan && settings.audiobooksFolderUri != null && audiobooks.any { it.relativePath.isBlank() }) {
                    hasTriggeredLegacyRescan = true
                    viewModelScope.launch(ioDispatcher) {
                        audiobookRepository.rescanLibrary()
                    }
                }

                val items = buildFilemanagerItems(
                    audiobooks = audiobooks,
                    playlistIds = playlistAudiobookIds,
                    currentPath = currentPath
                )

                _state.value.copy(
                    isLoading = false,
                    audiobooksFolderUri = settings.audiobooksFolderUri,
                    currentPath = currentPath,
                    items = items,
                    totalBooksCount = audiobooks.size
                )
            }.collect { nextState ->
                _state.value = nextState
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
            _state.value = _state.value.copy(isScanning = true, errorMessage = null, statusMessage = "Scanning audiobooks...")
            try {
                val summary = audiobookRepository.scanDirectory(uri)
                if (summary.errorMessage != null) {
                    _state.value = _state.value.copy(
                        isScanning = false,
                        errorMessage = "Scan failed: ${summary.errorMessage}",
                        statusMessage = null
                    )
                } else {
                    _state.value = _state.value.copy(
                        isScanning = false,
                        statusMessage = "Found ${summary.booksScanned} audiobooks (${summary.tracksScanned} tracks)"
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isScanning = false,
                    errorMessage = "Scan failed: ${e.localizedMessage ?: "Unknown error"}",
                    statusMessage = null
                )
            }
        }
    }

    fun onRescanLibrary() {
        viewModelScope.launch(ioDispatcher) {
            _state.value = _state.value.copy(isScanning = true, errorMessage = null, statusMessage = "Rescanning library...")
            try {
                val summary = audiobookRepository.rescanLibrary()
                if (summary != null) {
                    if (summary.errorMessage != null) {
                        _state.value = _state.value.copy(
                            isScanning = false,
                            errorMessage = "Rescan failed: ${summary.errorMessage}",
                            statusMessage = null
                        )
                    } else {
                        _state.value = _state.value.copy(
                            isScanning = false,
                            statusMessage = "Rescan complete: ${summary.booksScanned} added, ${summary.tracksScanned} tracks"
                        )
                    }
                } else {
                    _state.value = _state.value.copy(
                        isScanning = false,
                        errorMessage = "No audiobooks folder configured"
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isScanning = false,
                    errorMessage = "Rescan failed: ${e.localizedMessage ?: "Unknown error"}",
                    statusMessage = null
                )
            }
        }
    }

    fun toggleBookPlaylist(bookId: Long) {
        viewModelScope.launch(ioDispatcher) {
            val inQueue = playlistDao.isAudiobookInPlaylist(bookId)
            if (inQueue) {
                playlistRepository.removeAudiobookFromPlaylist(bookId)
                _state.update { s ->
                    if (s.selectedBookForModal?.id == bookId) {
                        s.copy(selectedBookPlaylistTrackIds = emptySet())
                    } else s
                }
            } else {
                playlistRepository.addAudiobookToPlaylist(bookId)
                if (_state.value.selectedBookForModal?.id == bookId) {
                    val tracks = audiobookRepository.getTracksForAudiobook(bookId)
                    _state.update { s ->
                        if (s.selectedBookForModal?.id == bookId) {
                            s.copy(selectedBookPlaylistTrackIds = tracks.map { it.id }.toSet())
                        } else s
                    }
                }
            }
            queueInvalidator.invalidate()
        }
    }

    fun openBookModal(book: AudiobookEntity) {
        viewModelScope.launch(ioDispatcher) {
            val inQueue = playlistDao.isAudiobookInPlaylist(book.id)
            val tracks = audiobookRepository.getTracksForAudiobook(book.id)
            val selectedIds = if (inQueue) {
                val selectedTracks = audiobookRepository.getSelectedTracksForAudiobook(book.id)
                if (selectedTracks.isNotEmpty()) {
                    selectedTracks.map { it.id }.toSet()
                } else {
                    tracks.map { it.id }.toSet()
                }
            } else {
                emptySet()
            }

            _state.value = _state.value.copy(
                selectedBookForModal = book,
                selectedBookTracks = tracks,
                selectedBookPlaylistTrackIds = selectedIds
            )
        }
    }

    fun closeBookModal() {
        val currentBook = _state.value.selectedBookForModal
        currentBook?.let { book ->
            synchronized(syncChannelsLock) {
                activeBookSyncChannels[book.id]?.first?.close()
            }
        }
        _state.update { s ->
            s.copy(
                selectedBookForModal = null,
                selectedBookTracks = emptyList(),
                selectedBookPlaylistTrackIds = emptySet()
            )
        }
    }

    fun toggleTrackSelection(track: AudiobookTrackEntity) {
        val book = _state.value.selectedBookForModal ?: return
        val previousSelected = _state.value.selectedBookPlaylistTrackIds
        val currentSelected = previousSelected.toMutableSet()

        if (track.id in currentSelected) {
            currentSelected.remove(track.id)
        } else {
            currentSelected.add(track.id)
        }

        _state.update { it.copy(selectedBookPlaylistTrackIds = currentSelected) }

        val channel = getSyncChannelForBook(book.id)
        channel.trySend(
            ChapterSyncRequest(
                bookId = book.id,
                trackIds = currentSelected.toList(),
                fallbackTrackIds = previousSelected
            )
        )
    }

    fun dismissError() {
        _state.value = _state.value.copy(errorMessage = null)
    }

    fun dismissStatus() {
        _state.value = _state.value.copy(statusMessage = null)
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

            val directoriesMap = mutableMapOf<String, String>()
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
                    directoriesMap.putIfAbsent(subDirName, (currentPath + subDirName).joinToString("/"))
                }
            }

            val dirItems = directoriesMap.keys
                .sortedWith(com.example.mpod.util.NaturalOrderComparator.INSTANCE)
                .map { dirName ->
                    val dirFullPath = directoriesMap[dirName] ?: dirName
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
