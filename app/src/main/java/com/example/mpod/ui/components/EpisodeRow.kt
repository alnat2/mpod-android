package com.example.mpod.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mpod.R

enum class EpisodeRowAction {
    AddToPlaylist,
    RemoveFromPlaylist,
    ShowNotes,
    Download,
    MarkListened,
    MarkUnlistened,
    MoveUp,
    MoveDown
}

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
    actionsEnabled: Boolean = true,
    canMoveUp: Boolean = false,
    canMoveDown: Boolean = false,
    showDragHandle: Boolean = true,
    statusTextOverride: String? = null,
    modifier: Modifier = Modifier,
    onAction: ((EpisodeRowAction) -> Unit)? = null
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val backgroundColor = if (isPlaying)
        MaterialTheme.colorScheme.surfaceVariant
    else
        MaterialTheme.colorScheme.surface
    val statusText = statusTextOverride ?: when {
        inPlaylist -> "In playlist"
        isPlaying -> "$podcastName · now playing"
        else -> podcastName
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(76.dp)
            .figmaDropShadow(radius = 4.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(backgroundColor)
            .padding(horizontal = if (showDragHandle) 8.dp else 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (showDragHandle) {
            Icon(
                painter = painterResource(id = R.drawable.ic_drag),
                contentDescription = "Drag",
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
            Text(
                text = statusText,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Normal,
                color = if (isPlaying) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
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
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_ellipsis_vertical),
                contentDescription = "Options",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            if (onAction != null) {
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    if (canMoveUp) {
                        EpisodeActionItem(
                            text = "Move up",
                            onClick = {
                                menuExpanded = false
                                onAction(EpisodeRowAction.MoveUp)
                            }
                        )
                    }
                    if (canMoveDown) {
                        EpisodeActionItem(
                            text = "Move down",
                            onClick = {
                                menuExpanded = false
                                onAction(EpisodeRowAction.MoveDown)
                            }
                        )
                    }
                    EpisodeActionItem(
                        text = if (inPlaylist) "Remove from playlist" else "Add to playlist",
                        onClick = {
                            menuExpanded = false
                            onAction(if (inPlaylist) EpisodeRowAction.RemoveFromPlaylist else EpisodeRowAction.AddToPlaylist)
                        }
                    )
                    EpisodeActionItem(
                        text = "Show notes",
                        onClick = {
                            menuExpanded = false
                            onAction(EpisodeRowAction.ShowNotes)
                        }
                    )
                    EpisodeActionItem(
                        text = if (downloaded) "Downloaded" else "Download",
                        enabled = !downloaded,
                        onClick = {
                            menuExpanded = false
                            onAction(EpisodeRowAction.Download)
                        }
                    )
                    EpisodeActionItem(
                        text = if (isListened) "Mark as unlistened" else "Mark as listened",
                        onClick = {
                            menuExpanded = false
                            onAction(if (isListened) EpisodeRowAction.MarkUnlistened else EpisodeRowAction.MarkListened)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun EpisodeActionItem(
    text: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = {
            Text(
                text = text,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        },
        enabled = enabled,
        onClick = onClick
    )
}
