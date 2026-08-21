package com.example.mpod.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
    MarkListened,
    MarkUnlistened,
    MoveUp,
    MoveDown
}

@Composable
fun PlayerPlaylistItem(
    title: String,
    podcastName: String,
    duration: String,
    isCurrent: Boolean,
    isPlaying: Boolean,
    downloaded: Boolean,
    actionsEnabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    onRemoveFromPlaylist: () -> Unit = {},
    onPlayToggle: () -> Unit = {}
) {
    EpisodeItemSurface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 112.dp)
            .clickable(enabled = actionsEnabled, onClick = onClick),
        radius = 8,
        horizontalPaddingEnd = 12
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_huge_menu_09),
            contentDescription = null,
            modifier = Modifier.size(32.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            EpisodeTitle(text = title)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                EpisodeMetaLine(
                    text = if (isCurrent) "Now playing" else podcastName,
                    downloaded = downloaded,
                    highlight = true,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = duration,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EpisodeInlineActionButton(
                        iconRes = R.drawable.ic_huge_playlist_remove,
                        contentDescription = "Remove $title from playlist",
                        testTag = "player_episode_remove_action",
                        enabled = actionsEnabled,
                        onClick = onRemoveFromPlaylist
                    )
                    EpisodeInlineActionButton(
                        iconRes = if (isPlaying) {
                            R.drawable.ic_huge_pause
                        } else {
                            R.drawable.ic_huge_play
                        },
                        contentDescription = if (isPlaying) "Pause $title" else "Play $title",
                        testTag = "player_episode_play_action",
                        enabled = actionsEnabled,
                        onClick = onPlayToggle
                    )
                }
            }
        }
    }
}

@Composable
fun SubscriptionEpisodeItem(
    title: String,
    podcastName: String,
    duration: String,
    date: String?,
    inPlaylist: Boolean,
    isListened: Boolean,
    downloaded: Boolean,
    actionsEnabled: Boolean,
    modifier: Modifier = Modifier,
    onAddToPlaylist: () -> Unit = {},
    onRemoveFromPlaylist: () -> Unit = {},
    onShowNotes: () -> Unit = {},
    onMarkListened: () -> Unit = {},
    onMarkUnlistened: () -> Unit = {}
) {
    EpisodeItemSurface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 116.dp),
        radius = 16,
        horizontalPaddingStart = 12,
        horizontalPaddingEnd = 12
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            EpisodeTitle(text = title)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    EpisodeMetaLine(
                        text = podcastName,
                        downloaded = downloaded,
                        highlight = true
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = duration,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                        date?.let {
                            Text(
                                text = it,
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EpisodeInlineActionButton(
                        iconRes = if (inPlaylist) {
                            R.drawable.ic_huge_playlist_remove
                        } else {
                            R.drawable.ic_huge_playlist_add
                        },
                        contentDescription = if (inPlaylist) {
                            "Remove $title from playlist"
                        } else {
                            "Add $title to playlist"
                        },
                        testTag = "episode_action_icon_playlist",
                        enabled = actionsEnabled,
                        onClick = if (inPlaylist) onRemoveFromPlaylist else onAddToPlaylist
                    )
                    EpisodeInlineActionButton(
                        iconRes = R.drawable.ic_huge_note,
                        contentDescription = "Show notes for $title",
                        testTag = "episode_action_icon_notes",
                        enabled = actionsEnabled,
                        onClick = onShowNotes
                    )
                    EpisodeInlineActionButton(
                        iconRes = if (isListened) {
                            R.drawable.ic_huge_view_off
                        } else {
                            R.drawable.ic_huge_view
                        },
                        contentDescription = if (isListened) {
                            "Mark $title as unlistened"
                        } else {
                            "Mark $title as listened"
                        },
                        testTag = "episode_action_icon_listened",
                        enabled = actionsEnabled,
                        onClick = if (isListened) onMarkUnlistened else onMarkListened
                    )
                }
            }
        }
    }
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
    showStatusIcons: Boolean = true,
    compactPlaybackMenu: Boolean = false,
    compactPlaybackActionLabel: String = "Play",
    statusTextOverride: String? = null,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onAction: ((EpisodeRowAction) -> Unit)? = null
) {
    val metaText = statusTextOverride ?: podcastName
    if (compactPlaybackMenu) {
        PlayerPlaylistItem(
            title = title,
            podcastName = metaText,
            duration = duration,
            isCurrent = isPlaying,
            isPlaying = compactPlaybackActionLabel == "Pause",
            downloaded = downloaded,
            actionsEnabled = actionsEnabled,
            modifier = modifier,
            onClick = { onClick?.invoke() },
            onRemoveFromPlaylist = { onAction?.invoke(EpisodeRowAction.RemoveFromPlaylist) },
            onPlayToggle = { onAction?.invoke(EpisodeRowAction.Play) }
        )
    } else {
        SubscriptionEpisodeItem(
            title = title,
            podcastName = metaText,
            duration = duration,
            date = date,
            inPlaylist = inPlaylist,
            isListened = isListened,
            downloaded = showStatusIcons && downloaded,
            actionsEnabled = actionsEnabled,
            modifier = modifier,
            onAddToPlaylist = { onAction?.invoke(EpisodeRowAction.AddToPlaylist) },
            onRemoveFromPlaylist = { onAction?.invoke(EpisodeRowAction.RemoveFromPlaylist) },
            onShowNotes = { onAction?.invoke(EpisodeRowAction.ShowNotes) },
            onMarkListened = { onAction?.invoke(EpisodeRowAction.MarkListened) },
            onMarkUnlistened = { onAction?.invoke(EpisodeRowAction.MarkUnlistened) }
        )
    }
}

@Composable
private fun EpisodeItemSurface(
    modifier: Modifier,
    radius: Int,
    horizontalPaddingStart: Int = 8,
    horizontalPaddingEnd: Int = 8,
    leadingOverlayIconRes: Int? = null,
    content: @Composable RowScope.() -> Unit
) {
    Box(
        modifier = modifier
            .figmaDropShadow(radius = 2.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(radius.dp))
    ) {
        if (leadingOverlayIconRes != null) {
            Icon(
                painter = painterResource(id = leadingOverlayIconRes),
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(32.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = horizontalPaddingStart.dp,
                    top = 12.dp,
                    end = horizontalPaddingEnd.dp,
                    bottom = 12.dp
                ),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

@Composable
private fun EpisodeTitle(text: String) {
    Text(
        text = text,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun EpisodeMetaLine(
    text: String,
    downloaded: Boolean,
    highlight: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.heightIn(min = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (downloaded) {
            Icon(
                painter = painterResource(id = R.drawable.ic_episode_status_downloaded),
                contentDescription = "Downloaded",
                modifier = Modifier.size(20.dp),
                tint = Color.Unspecified
            )
        }
        Text(
            text = text,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            color = if (highlight) {
                MaterialTheme.colorScheme.tertiary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun EpisodeInlineActionButton(
    iconRes: Int,
    contentDescription: String,
    testTag: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    SquareIconButton(
        iconRes = iconRes,
        contentDescription = contentDescription,
        modifier = Modifier
            .testTag(testTag)
            .semantics {
                this.contentDescription = contentDescription
                role = Role.Button
            }
            .alpha(if (enabled) 1f else 0.45f),
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.primary,
        size = 44.dp,
        iconSize = 24.dp,
        radius = 10.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = 1.dp,
        onClick = if (enabled) onClick else null
    )
}
