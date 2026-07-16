package com.example.mpod.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
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
    onNotesClick: () -> Unit = {}
) {
    var showSpeedSheet by remember { mutableStateOf(false) }
    val speedSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val speeds = listOf("0.5", "0.75", "1.0", "1.3", "1.5", "2.0")

    if (showSpeedSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSpeedSheet = false },
            sheetState = speedSheetState,
            containerColor = MaterialTheme.colorScheme.surface
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

    // Figma: 320x246, border 1dp, radius 16dp, shadow-xs.
    OutlinedCard(
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 246.dp)
            .figmaDropShadow(radius = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
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

            // Progress Bar group — gap 8dp
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Figma: height 16dp, muted bg, radius full
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(16.dp)
                        .progressSemantics(progress.coerceIn(0f, 1f))
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                    )
                }
                // Figma: xs/Regular, 12sp, muted-foreground, space-between
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(elapsedLabel, fontSize = 12.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(durationLabel, fontSize = 12.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Controls row — space-between, full width
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Speed button — Figma: 36dp, border 2dp primary, radius 10dp, transparent bg
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp))
                        .semantics {
                            contentDescription = "Playback speed ${speedLabel}x"
                            role = Role.Button
                        }
                        .clickable(role = Role.Button) { showSpeedSheet = true }
                ) {
                    Text(
                        text = speedLabel,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Rewind 10 — Figma: container 40dp, icon fills container
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(40.dp)
                        .semantics { contentDescription = "Rewind 10 seconds" }
                        .clickable(role = Role.Button, onClick = onSeekBackward)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_go_backward_10_sec),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(40.dp)
                    )
                }

                // Play Button — Figma: 56dp circle, primary bg,
                // shadow: 0px 4px 3px rgba(0,0,0,0.1) + 0px 2px 2px rgba(0,0,0,0.1)
                // icon: 20dp
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
                        painter = painterResource(id = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Forward 15 — Figma: container 40dp, icon fills container
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(40.dp)
                        .semantics { contentDescription = "Forward 15 seconds" }
                        .clickable(role = Role.Button, onClick = onSeekForward)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_go_forward_15_sec),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(40.dp)
                    )
                }

                // Notes Button — Figma: container 36dp, icon fills container (30dp effective)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(36.dp)
                        .semantics { contentDescription = "Show notes" }
                        .clickable(role = Role.Button, onClick = onNotesClick)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_note),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
        }
    }
}
