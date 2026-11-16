package com.example.mpod.ui.screens.audiobooks

import android.net.Uri
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
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

data class AudiobooksUiState(
    val isLoading: Boolean = false,
    val isScanning: Boolean = false,
    val errorMessage: String? = null,
    val statusMessage: String? = null,
    val audiobooksFolderUri: String? = null,
    val currentPath: List<String> = emptyList(),
    val items: List<FilemanagerItemUi> = emptyList(),
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

    private var allAudiobooks: List<AudiobookEntity> = emptyList()
    private var playlistAudiobookIds: Set<Long> = emptySet()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            combine(
                appSettingsStore.settingsFlow,
                audiobookRepository.audiobooksFlow,
                playlistDao.getPlaylistItemsWithAudiobooksFlow()
            ) { settings, audiobooks, playlistItems ->
                allAudiobooks = audiobooks
                playlistAudiobookIds = playlistItems.map { it.audiobook.id }.toSet()

                val currentPath = _state.value.currentPath
                val items = buildFilemanagerItems(
                    audiobooks = audiobooks,
                    playlistIds = playlistAudiobookIds,
                    currentPath = currentPath
                )

                _state.value.copy(
                    isLoading = false,
                    audiobooksFolderUri = settings.audiobooksFolderUri,
                    items = items
                )
            }.collect { nextState ->
                _state.value = nextState
            }
        }
    }

    fun navigateToFolder(folderName: String) {
        val nextPath = _state.value.currentPath + folderName
        updatePath(nextPath)
    }

    fun navigateToBreadcrumb(index: Int) {
        if (index < 0) {
            updatePath(emptyList())
        } else {
            val nextPath = _state.value.currentPath.take(index + 1)
            updatePath(nextPath)
        }
    }

    private fun updatePath(path: List<String>) {
        val items = buildFilemanagerItems(
            audiobooks = allAudiobooks,
            playlistIds = playlistAudiobookIds,
            currentPath = path
        )
        _state.value = _state.value.copy(
            currentPath = path,
            items = items
        )
    }

    fun onScanFolder(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = _state.value.copy(isScanning = true, errorMessage = null, statusMessage = "Scanning audiobooks...")
            try {
                val summary = audiobookRepository.scanDirectory(uri)
                _state.value = _state.value.copy(
                    isScanning = false,
                    statusMessage = "Found ${summary.booksAdded} audiobooks (${summary.tracksAdded} tracks)"
                )
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
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = _state.value.copy(isScanning = true, errorMessage = null, statusMessage = "Rescanning library...")
            try {
                val summary = audiobookRepository.rescanLibrary()
                if (summary != null) {
                    _state.value = _state.value.copy(
                        isScanning = false,
                        statusMessage = "Rescan complete: ${summary.booksAdded} added, ${summary.tracksAdded} tracks"
                    )
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
        viewModelScope.launch(Dispatchers.IO) {
            if (bookId in playlistAudiobookIds) {
                playlistRepository.removeAudiobookFromPlaylist(bookId)
            } else {
                playlistRepository.addAudiobookToPlaylist(bookId)
            }
            queueInvalidator.invalidate()
        }
    }

    fun openBookModal(book: AudiobookEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val tracks = audiobookRepository.getTracksForAudiobook(book.id)
            val selectedTracks = audiobookRepository.getSelectedTracksForAudiobook(book.id)
            val selectedIds = if (selectedTracks.isNotEmpty()) {
                selectedTracks.map { it.id }.toSet()
            } else {
                tracks.map { it.id }.toSet()
            }

            _state.value = _state.value.copy(
                selectedBookForModal = book,
                selectedBookTracks = tracks,
                selectedBookPlaylistTrackIds = selectedIds
            )
        }
    }

    fun closeBookModal() {
        _state.value = _state.value.copy(
            selectedBookForModal = null,
            selectedBookTracks = emptyList(),
            selectedBookPlaylistTrackIds = emptySet()
        )
    }

    fun toggleTrackSelection(track: AudiobookTrackEntity) {
        val book = _state.value.selectedBookForModal ?: return
        val currentSelected = _state.value.selectedBookPlaylistTrackIds.toMutableSet()

        if (track.id in currentSelected) {
            currentSelected.remove(track.id)
        } else {
            currentSelected.add(track.id)
        }

        _state.value = _state.value.copy(selectedBookPlaylistTrackIds = currentSelected)

        viewModelScope.launch(Dispatchers.IO) {
            audiobookRepository.setSelectedTracks(book.id, currentSelected.toList())
            queueInvalidator.invalidate()
        }
    }

    fun dismissError() {
        _state.value = _state.value.copy(errorMessage = null)
    }

    fun dismissStatus() {
        _state.value = _state.value.copy(statusMessage = null)
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

            return if (currentPath.isEmpty()) {
                // Root level: Group by Author (if author != "Unknown Author" and book author is present)
                val authors = audiobooks.map { it.author.trim() }
                    .filter { it.isNotBlank() && !it.equals("Unknown Author", ignoreCase = true) }
                    .distinct()
                    .sorted()

                val authorItems = authors.map { authorName ->
                    FilemanagerItemUi(
                        id = "dir:$authorName",
                        title = authorName,
                        type = FilemanagerItemType.Directory,
                        author = authorName
                    )
                }

                // Books without recognized author (or "Unknown Author") placed at root
                val rootBooks = audiobooks.filter {
                    it.author.isBlank() || it.author.equals("Unknown Author", ignoreCase = true)
                }.sortedBy { it.title }

                val bookItems = rootBooks.map { book ->
                    val type = if (book.isSingleFile) {
                        FilemanagerItemType.AudioFile
                    } else {
                        FilemanagerItemType.AudiobookDirectory
                    }
                    FilemanagerItemUi(
                        id = "book:${book.id}",
                        title = book.title,
                        type = type,
                        durationText = formatFmDuration(book.durationSeconds),
                        audiobookId = book.id,
                        isInPlaylist = book.id in playlistIds,
                        author = book.author,
                        book = book
                    )
                }

                authorItems + bookItems
            } else {
                // Subdirectory level: books for selected author (or folder)
                val targetAuthor = currentPath.first()
                val authorBooks = audiobooks.filter {
                    it.author.equals(targetAuthor, ignoreCase = true)
                }.sortedBy { it.title }

                authorBooks.map { book ->
                    val type = if (book.isSingleFile) {
                        FilemanagerItemType.AudioFile
                    } else {
                        FilemanagerItemType.AudiobookDirectory
                    }
                    FilemanagerItemUi(
                        id = "book:${book.id}",
                        title = book.title,
                        type = type,
                        durationText = formatFmDuration(book.durationSeconds),
                        audiobookId = book.id,
                        isInPlaylist = book.id in playlistIds,
                        author = book.author,
                        book = book
                    )
                }
            }
        }
    }
}
