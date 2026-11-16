package com.example.mpod.ui.screens.audiobooks

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mpod.R
import com.example.mpod.data.local.entity.AudiobookEntity
import com.example.mpod.data.local.entity.AudiobookTrackEntity
import com.example.mpod.ui.components.ChaptersModal
import com.example.mpod.ui.components.ChaptersModalMode
import com.example.mpod.ui.components.FilemanagerItem
import com.example.mpod.ui.components.FilemanagerItemType
import com.example.mpod.ui.components.MpodButton
import com.example.mpod.ui.components.MpodStatusBanner
import com.example.mpod.ui.components.PageHeader
import com.example.mpod.ui.components.StatusBannerType
import com.example.mpod.ui.theme.InterFontFamily

@Composable
fun AudiobooksRoute(
    viewModel: AudiobooksViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, flags)
            }
            viewModel.onScanFolder(uri)
        }
    }

    AudiobooksScreen(
        state = state,
        onChooseFolderClick = { folderPickerLauncher.launch(null) },
        onRescanClick = viewModel::onRescanLibrary,
        onFolderClick = viewModel::navigateToFolder,
        onBreadcrumbClick = viewModel::navigateToBreadcrumb,
        onToggleBookPlaylist = viewModel::toggleBookPlaylist,
        onOpenBookModal = viewModel::openBookModal,
        onCloseBookModal = viewModel::closeBookModal,
        onToggleTrackSelection = viewModel::toggleTrackSelection,
        onDismissError = viewModel::dismissError,
        onDismissStatus = viewModel::dismissStatus
    )
}

@Composable
fun AudiobooksScreen(
    state: AudiobooksUiState = AudiobooksUiState(),
    onChooseFolderClick: () -> Unit = {},
    onRescanClick: () -> Unit = {},
    onFolderClick: (String) -> Unit = {},
    onBreadcrumbClick: (Int) -> Unit = {},
    onToggleBookPlaylist: (Long) -> Unit = {},
    onOpenBookModal: (AudiobookEntity) -> Unit = {},
    onCloseBookModal: () -> Unit = {},
    onToggleTrackSelection: (AudiobookTrackEntity) -> Unit = {},
    onDismissError: () -> Unit = {},
    onDismissStatus: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header (Figma 1304:10573)
            item {
                PageHeader(
                    title = "Audiobooks",
                    showActions = state.audiobooksFolderUri != null,
                    onRefreshClick = if (state.isScanning) null else onRescanClick,
                    isRefreshing = state.isScanning
                )
            }

            // Status / Error Banners
            if (state.errorMessage != null) {
                item {
                    MpodStatusBanner(
                        message = state.errorMessage,
                        type = StatusBannerType.Error,
                        actionLabel = "Dismiss",
                        onAction = onDismissError
                    )
                }
            }

            if (state.isScanning) {
                item {
                    MpodStatusBanner(
                        message = state.statusMessage ?: "Scanning audiobooks...",
                        type = StatusBannerType.Loading
                    )
                }
            } else if (state.statusMessage != null && state.errorMessage == null) {
                item {
                    MpodStatusBanner(
                        message = state.statusMessage,
                        type = StatusBannerType.Info,
                        onDismiss = onDismissStatus
                    )
                }
            }

            when {
                state.isLoading && state.audiobooksFolderUri == null -> {
                    item {
                        MpodStatusBanner(
                            message = "Loading audiobooks",
                            type = StatusBannerType.Loading
                        )
                    }
                }

                state.audiobooksFolderUri == null -> {
                    item {
                        AudiobooksEmptyState(
                            title = "No audiobooks folder",
                            description = "Choose a folder on your device containing your audiobooks to scan and play them.",
                            buttonText = "Choose folder",
                            onButtonClick = onChooseFolderClick
                        )
                    }
                }

                state.items.isEmpty() && !state.isScanning -> {
                    item {
                        AudiobooksEmptyState(
                            title = "No audiobooks found",
                            description = "Place audiobooks in the selected folder, or choose another folder.",
                            buttonText = "Rescan library",
                            onButtonClick = onRescanClick
                        )
                    }
                }

                else -> {
                    // Breadcrumb navigation (Figma 1285:9059)
                    item {
                        BreadcrumbsBar(
                            currentPath = state.currentPath,
                            onBreadcrumbClick = onBreadcrumbClick
                        )
                    }

                    // Items list (Figma 1285:8976)
                    items(
                        items = state.items,
                        key = { it.id }
                    ) { item ->
                        val actionIcon = when {
                            item.type == FilemanagerItemType.Directory -> null
                            item.isInPlaylist -> R.drawable.ic_huge_playlist_remove
                            else -> R.drawable.ic_huge_playlist_add
                        }

                        val actionContentDescription = when {
                            item.type == FilemanagerItemType.Directory -> null
                            item.isInPlaylist -> "Remove from playlist"
                            else -> "Add to playlist"
                        }

                        FilemanagerItem(
                            title = item.title,
                            type = item.type,
                            durationText = item.durationText,
                            actionIconRes = actionIcon,
                            actionContentDescription = actionContentDescription,
                            onActionClick = if (item.audiobookId != null) {
                                { onToggleBookPlaylist(item.audiobookId) }
                            } else null,
                            onClick = {
                                when (item.type) {
                                    FilemanagerItemType.Directory -> onFolderClick(item.title)
                                    FilemanagerItemType.AudiobookDirectory,
                                    FilemanagerItemType.AudioFile -> {
                                        item.book?.let { onOpenBookModal(it) }
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }

        // Chapters Modal (FileManager mode)
        state.selectedBookForModal?.let { book ->
            ChaptersModal(
                title = book.title,
                author = book.author,
                coverUri = book.coverUri,
                tracks = state.selectedBookTracks,
                mode = ChaptersModalMode.FileManager,
                selectedTrackIds = state.selectedBookPlaylistTrackIds,
                onDismiss = onCloseBookModal,
                onToggleTrackSelection = onToggleTrackSelection
            )
        }
    }
}

@Composable
private fun BreadcrumbsBar(
    currentPath: List<String>,
    onBreadcrumbClick: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val isRoot = currentPath.isEmpty()

        // Root "Abooks"
        Text(
            text = "Abooks",
            fontFamily = InterFontFamily,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Normal,
            color = if (isRoot) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .clickable { onBreadcrumbClick(-1) }
                .padding(vertical = 4.dp)
        )

        currentPath.forEachIndexed { index, segment ->
            Icon(
                painter = painterResource(id = R.drawable.ic_huge_arrow_right_01),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp)
            )

            val isLast = index == currentPath.lastIndex
            Text(
                text = segment,
                fontFamily = InterFontFamily,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Normal,
                color = if (isLast) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { onBreadcrumbClick(index) }
                    .padding(vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun AudiobooksEmptyState(
    title: String,
    description: String,
    buttonText: String,
    onButtonClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 228.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 228.dp)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_huge_audio_book_01),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = title,
                fontSize = 18.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = description,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            MpodButton(
                text = buttonText,
                onClick = onButtonClick
            )
        }
    }
}
