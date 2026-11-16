package com.example.mpod.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.mpod.R
import com.example.mpod.data.local.entity.AudiobookTrackEntity
import com.example.mpod.ui.util.formatEpisodeDuration
import com.example.mpod.ui.util.formatProgressTime

enum class ChaptersModalMode {
    Player,
    FileManager
}

@Composable
fun ChaptersModal(
    title: String,
    author: String,
    coverUri: String?,
    tracks: List<AudiobookTrackEntity>,
    currentTrackId: Long? = null,
    isPlaying: Boolean = false,
    mode: ChaptersModalMode = ChaptersModalMode.Player,
    selectedTrackIds: Set<Long> = emptySet(),
    onDismiss: () -> Unit,
    onSelectChapter: (AudiobookTrackEntity) -> Unit = {},
    onToggleTrackSelection: ((AudiobookTrackEntity) -> Unit)? = null
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .width(320.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(
                    width = 1.dp,
                    color = Color(0x1AFAFAFA),
                    shape = RoundedCornerShape(20.dp)
                )
                .padding(horizontal = 16.dp, vertical = 20.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header (Figma 1309:12272)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    // Thumbnail 64x64, radius 6dp
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.dp, Color(0x1AFAFAFA), RoundedCornerShape(6.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!coverUri.isNullOrBlank()) {
                            AsyncImage(
                                model = coverUri,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(64.dp)
                            )
                        } else {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_huge_audio_book_01),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }

                    // Wrap (Title + Author)
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = title,
                            fontSize = 20.sp,
                            lineHeight = 26.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (author.isNotBlank()) {
                            Text(
                                text = author,
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Close Button (44x44, radius 10dp)
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.background)
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_huge_multiplication_sign),
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Chapters list (height up to 360dp)
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(tracks, key = { it.id }) { track ->
                        val durationFormatted = formatEpisodeDuration(track.durationSeconds.toInt())

                        if (mode == ChaptersModalMode.FileManager) {
                            val isSelected = selectedTrackIds.contains(track.id)
                            val actionIcon = if (isSelected) {
                                R.drawable.ic_huge_playlist_remove
                            } else {
                                R.drawable.ic_huge_playlist_add
                            }

                            FilemanagerItem(
                                title = track.title,
                                type = FilemanagerItemType.AudioFile,
                                durationText = durationFormatted,
                                actionIconRes = actionIcon,
                                actionContentDescription = if (isSelected) "Remove from playlist" else "Add to playlist",
                                onActionClick = { onToggleTrackSelection?.invoke(track) },
                                isHighlighted = false,
                                isMuted = false,
                                onClick = { onToggleTrackSelection?.invoke(track) }
                            )
                        } else {
                            val isCurrent = track.id == currentTrackId
                            val isListened = track.isListened

                            val durationDisplay = if (isCurrent) {
                                val posSec = (track.playbackPositionMs / 1000L).toInt()
                                val posStr = formatProgressTime(posSec)
                                "$posStr / $durationFormatted"
                            } else {
                                durationFormatted
                            }

                            val actionIcon = when {
                                isCurrent && isPlaying -> R.drawable.ic_huge_pause
                                isCurrent && !isPlaying -> R.drawable.ic_huge_play
                                isListened -> R.drawable.ic_huge_replay
                                else -> R.drawable.ic_huge_play
                            }

                            FilemanagerItem(
                                title = track.title,
                                type = FilemanagerItemType.AudioFile,
                                durationText = durationDisplay,
                                actionIconRes = actionIcon,
                                actionContentDescription = if (isCurrent && isPlaying) "Pause" else "Play",
                                onActionClick = { onSelectChapter(track) },
                                isHighlighted = isCurrent,
                                isMuted = isListened && !isCurrent,
                                onClick = { onSelectChapter(track) }
                            )
                        }
                    }
                }
            }
        }
    }
}
