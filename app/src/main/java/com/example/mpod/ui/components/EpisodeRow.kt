package com.example.mpod.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mpod.R

enum class EpisodeRowAction {
    Play,
    AddToPlaylist,
    RemoveFromPlaylist,
    ShowNotes,
    Download,
    MarkListened,
    MarkUnlistened,
    MoveUp,
    MoveDown
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpisodeRow(
    title: String,
    podcastName: String,
    duration: String,
    date: String? = null,
    isPlaying: Boolean = false,
    inPlaylist: Boolean = false,
    isListened: Boolean = false,
    downloaded: Boolean = false,
    isDownloading: Boolean = false,
    actionsEnabled: Boolean = true,
    canMoveUp: Boolean = false,
    canMoveDown: Boolean = false,
    showDragHandle: Boolean = true,
    compactPlaybackMenu: Boolean = false,
    compactPlaybackActionLabel: String = "Play",
    statusTextOverride: String? = null,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onAction: ((EpisodeRowAction) -> Unit)? = null
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val backgroundColor = if (isPlaying)
        MaterialTheme.colorScheme.surfaceVariant
    else
        MaterialTheme.colorScheme.surface
    val showStatusIcons = downloaded || inPlaylist
    val statusText = when {
        isDownloading -> "Downloading…"
        statusTextOverride != null -> statusTextOverride
        isPlaying -> "$podcastName · now playing"
        showStatusIcons -> null
        else -> podcastName
    }

    if (menuExpanded && onAction != null) {
        EpisodeActionsBottomSheet(
            title = title,
            podcastName = podcastName,
            compactPlaybackMenu = compactPlaybackMenu,
            compactPlaybackActionLabel = compactPlaybackActionLabel,
            inPlaylist = inPlaylist,
            isListened = isListened,
            downloaded = downloaded,
            isDownloading = isDownloading,
            onDismiss = { menuExpanded = false },
            onAction = { action ->
                menuExpanded = false
                onAction(action)
            }
        )
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 76.dp)
            .figmaDropShadow(radius = 4.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(backgroundColor)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = if (showDragHandle) 8.dp else 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (showDragHandle) {
            Icon(
                painter = painterResource(id = R.drawable.ic_huge_drag_drop_vertical),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            EpisodeStatusLine(
                text = statusText,
                showDownloaded = downloaded,
                showInPlaylist = inPlaylist,
                isPlaying = isPlaying
            )
        }

        if (date == null) {
            Text(
                text = duration,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        } else {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = date,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = duration,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.background)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                .then(
                    if (onAction == null || !actionsEnabled) {
                        Modifier
                    } else {
                        Modifier.clickable { menuExpanded = true }
                    }
                )
                .semantics {
                    contentDescription = "Options for $title"
                    role = Role.Button
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_huge_more_vertical),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun EpisodeStatusLine(
    text: String?,
    showDownloaded: Boolean,
    showInPlaylist: Boolean,
    isPlaying: Boolean
) {
    val showText = !text.isNullOrBlank()
    if (!showDownloaded && !showInPlaylist && !showText) return

    Row(
        modifier = Modifier.heightIn(min = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (showDownloaded) {
            Icon(
                painter = painterResource(id = R.drawable.ic_episode_status_downloaded),
                contentDescription = "Downloaded",
                modifier = Modifier.size(16.dp),
                tint = Color.Unspecified
            )
        }
        if (showInPlaylist) {
            Icon(
                painter = painterResource(id = R.drawable.ic_episode_status_in_playlist),
                contentDescription = "In playlist",
                modifier = Modifier.size(16.dp),
                tint = Color.Unspecified
            )
        }
        if (showText) {
            Text(
                text = text.orEmpty(),
                modifier = Modifier.weight(1f, fill = false),
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Normal,
                color = if (isPlaying) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EpisodeActionsBottomSheet(
    title: String,
    podcastName: String,
    compactPlaybackMenu: Boolean,
    compactPlaybackActionLabel: String,
    inPlaylist: Boolean,
    isListened: Boolean,
    downloaded: Boolean,
    isDownloading: Boolean,
    onDismiss: () -> Unit,
    onAction: (EpisodeRowAction) -> Unit
) {
    MpodBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
                .testTag("episode_actions_sheet")
        ) {
            Column(
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    fontSize = 18.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = podcastName,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            if (compactPlaybackMenu) {
                EpisodeSheetAction(
                    text = compactPlaybackActionLabel,
                    iconRes = if (compactPlaybackActionLabel == "Pause") {
                        R.drawable.ic_huge_pause
                    } else {
                        R.drawable.ic_huge_play
                    },
                    iconTag = "episode_action_icon_play",
                    modifier = Modifier.testTag("home_episode_play_action"),
                    onClick = { onAction(EpisodeRowAction.Play) }
                )
                EpisodeSheetAction(
                    text = if (inPlaylist) "Remove from playlist" else "Add to playlist",
                    iconRes = if (inPlaylist) {
                        R.drawable.ic_huge_playlist_remove
                    } else {
                        R.drawable.ic_huge_playlist_add
                    },
                    iconTag = "episode_action_icon_playlist",
                    modifier = Modifier.testTag("home_episode_playlist_action"),
                    onClick = {
                        onAction(
                            if (inPlaylist) EpisodeRowAction.RemoveFromPlaylist
                            else EpisodeRowAction.AddToPlaylist
                        )
                    }
                )
            } else {
                EpisodeSheetAction(
                    text = if (inPlaylist) "Remove from playlist" else "Add to playlist",
                    iconRes = if (inPlaylist) {
                        R.drawable.ic_huge_playlist_remove
                    } else {
                        R.drawable.ic_huge_playlist_add
                    },
                    iconTag = "episode_action_icon_playlist",
                    onClick = {
                        onAction(
                            if (inPlaylist) EpisodeRowAction.RemoveFromPlaylist
                            else EpisodeRowAction.AddToPlaylist
                        )
                    }
                )
                EpisodeSheetAction(
                    text = "Show notes",
                    iconRes = R.drawable.ic_huge_note,
                    iconTag = "episode_action_icon_notes",
                    onClick = { onAction(EpisodeRowAction.ShowNotes) }
                )
                EpisodeSheetAction(
                    text = when {
                        isDownloading -> "Downloading…"
                        downloaded -> "Downloaded"
                        else -> "Download"
                    },
                    iconRes = if (downloaded) {
                        R.drawable.ic_huge_download_square_02
                    } else {
                        R.drawable.ic_huge_download_square_01
                    },
                    iconTag = "episode_action_icon_download",
                    enabled = !downloaded && !isDownloading,
                    showProgress = isDownloading,
                    onClick = { onAction(EpisodeRowAction.Download) }
                )
                EpisodeSheetAction(
                    text = if (isListened) "Mark as unlistened" else "Mark as listened",
                    iconRes = if (isListened) {
                        R.drawable.ic_huge_view_off
                    } else {
                        R.drawable.ic_huge_view
                    },
                    iconTag = "episode_action_icon_listened",
                    onClick = {
                        onAction(
                            if (isListened) EpisodeRowAction.MarkUnlistened
                            else EpisodeRowAction.MarkListened
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun EpisodeSheetAction(
    text: String,
    @DrawableRes iconRes: Int,
    iconTag: String,
    enabled: Boolean = true,
    showProgress: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showProgress) {
            HugeLoadingIcon(modifier = Modifier.size(24.dp))
        } else {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier
                    .size(24.dp)
                    .testTag(iconTag),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = text,
            fontSize = 16.sp,
            lineHeight = 24.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun HugeLoadingIcon(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "episode-download-loading")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing)
        ),
        label = "episode-download-loading-rotation"
    )
    Icon(
        painter = painterResource(R.drawable.ic_huge_loading_02),
        contentDescription = null,
        modifier = modifier.rotate(rotation),
        tint = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
