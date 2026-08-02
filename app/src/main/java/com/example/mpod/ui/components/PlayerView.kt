package com.example.mpod.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mpod.R
import com.example.mpod.ui.theme.InterFontFamily

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerView(
    modifier: Modifier = Modifier,
    title: String = "Why store loyalty cards became a UX minefield",
    podcastTitle: String = "Decoder Ring",
    elapsedLabel: String = "23:14",
    durationLabel: String = "14:03",
    progress: Float = 0.6f,
    isPlaying: Boolean = false,
    speedLabel: String = "1.5",
    onSpeedChange: (String) -> Unit = {},
    onPlayClick: () -> Unit = {},
    onSeekBackward: () -> Unit = {},
    onSeekForward: () -> Unit = {},
    onSeekTo: (Float) -> Unit = {},
    onNotesClick: () -> Unit = {}
) {
    var showSpeedSheet by remember { mutableStateOf(false) }
    var draggedProgress by remember { mutableStateOf<Float?>(null) }
    val speedSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val speeds = listOf("0.5", "0.75", "1.0", "1.3", "1.5", "2.0")
    val displayedProgress = draggedProgress ?: progress.coerceIn(0f, 1f)
    val progressTrackColor = MaterialTheme.colorScheme.surfaceVariant
    val progressFillColor = MaterialTheme.colorScheme.primary

    if (showSpeedSheet) {
        MpodBottomSheet(
            onDismissRequest = { showSpeedSheet = false },
            sheetState = speedSheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                Text("Playback Speed", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(16.dp))
                speeds.forEach { speed ->
                    TextButton(
                        onClick = {
                            onSpeedChange(speed)
                            showSpeedSheet = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "${speed}x",
                            fontWeight = if (speed == speedLabel) FontWeight.Bold else FontWeight.Normal,
                            color = if (speed == speedLabel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    // Figma component version 2: content-height card, border 1dp, radius 16dp, shadow-xs.
    OutlinedCard(
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier
            .fillMaxWidth()
            .figmaDropShadow(radius = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Title and Subtitle — gap 8dp
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Figma: Inter Bold, 18sp, lineHeight 28sp, center
                Text(
                    text = title,
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    lineHeight = 28.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
                // Figma: Inter Regular, 14sp, lineHeight 20sp, muted-foreground, center
                Text(
                    text = podcastTitle,
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.Normal,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Figma content structure: progress/actions group (8dp), then notes (12dp).
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Progress group: 18dp time row, 8dp gap, 16dp bar, 8dp bottom padding.
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(18.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = elapsedLabel,
                                fontFamily = InterFontFamily,
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = durationLabel,
                                fontFamily = InterFontFamily,
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(16.dp)
                                .testTag("player_seek_bar")
                                .semantics {
                                    contentDescription = "Playback position"
                                    progressBarRangeInfo = ProgressBarRangeInfo(
                                        current = displayedProgress,
                                        range = 0f..1f
                                    )
                                    setProgress { targetProgress ->
                                        onSeekTo(targetProgress.coerceIn(0f, 1f))
                                        true
                                    }
                                }
                                .pointerInput(onSeekTo) {
                                    awaitEachGesture {
                                        val down = awaitFirstDown(requireUnconsumed = false)
                                        var targetProgress =
                                            (down.position.x / size.width).coerceIn(0f, 1f)
                                        draggedProgress = targetProgress

                                        do {
                                            val event = awaitPointerEvent()
                                            val change = event.changes.firstOrNull { it.id == down.id }
                                                ?: break
                                            targetProgress =
                                                (change.position.x / size.width).coerceIn(0f, 1f)
                                            draggedProgress = targetProgress
                                            change.consume()
                                        } while (change.pressed)

                                        draggedProgress = null
                                        onSeekTo(targetProgress)
                                    }
                                }
                                .clip(CircleShape)
                                .drawBehind {
                                    val corner = CornerRadius(size.height / 2f, size.height / 2f)
                                    drawRoundRect(
                                        color = progressTrackColor,
                                        size = size,
                                        cornerRadius = corner
                                    )
                                    drawRoundRect(
                                        color = progressFillColor,
                                        size = Size(
                                            width = size.width * displayedProgress,
                                            height = size.height
                                        ),
                                        cornerRadius = corner
                                    )
                                }
                        )
                    }

                    // Component version 2 order: speed, play, rewind 15, forward 30.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        PlayerLabelControl(
                            icon = R.drawable.ic_huge_gauge,
                            label = speedLabel,
                            contentDescription = "Playback speed ${speedLabel}x",
                            onClick = { showSpeedSheet = true }
                        )

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(56.dp)
                                .shadow(
                                    elevation = 4.dp,
                                    shape = CircleShape,
                                    ambientColor = Color(0x1A000000),
                                    spotColor = Color(0x1A000000)
                                )
                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                                .semantics { contentDescription = if (isPlaying) "Pause" else "Play" }
                                .clickable(role = Role.Button, onClick = onPlayClick)
                        ) {
                            Icon(
                                painter = painterResource(
                                    id = if (isPlaying) {
                                        R.drawable.ic_huge_player_pause
                                    } else {
                                        R.drawable.ic_huge_player_play
                                    }
                                ),
                                contentDescription = null,
                                tint = Color(0xFFF7FEE7),
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        PlayerLabelControl(
                            icon = R.drawable.ic_huge_forward_02_wide,
                            label = "-15",
                            contentDescription = "Rewind 15 seconds",
                            onClick = onSeekBackward
                        )

                        PlayerLabelControl(
                            icon = R.drawable.ic_huge_forward_02_wide,
                            label = "+30",
                            contentDescription = "Forward 30 seconds",
                            iconModifier = Modifier.rotate(180f),
                            onClick = onSeekForward
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .semantics {
                            contentDescription = "Show notes"
                            role = Role.Button
                        }
                        .clickable(role = Role.Button, onClick = onNotesClick),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_huge_note),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Show notes",
                        fontFamily = InterFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerLabelControl(
    icon: Int,
    label: String,
    contentDescription: String,
    iconModifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .size(56.dp)
            .semantics {
                this.contentDescription = contentDescription
                role = Role.Button
            }
            .clickable(role = Role.Button, onClick = onClick),
        verticalArrangement = Arrangement.Bottom,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            painter = painterResource(id = icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = iconModifier.height(22.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
