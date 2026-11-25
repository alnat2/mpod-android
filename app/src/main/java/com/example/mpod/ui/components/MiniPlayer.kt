package com.example.mpod.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mpod.R
import com.example.mpod.ui.theme.InterFontFamily

/**
 * Compact floating playback controls aligned to Figma `Player Mobile` (`1804:12156` in `1264:6524`).
 */
@Composable
fun MiniPlayer(
    isPlaying: Boolean,
    onPlayToggle: () -> Unit,
    onSeekBackward: () -> Unit,
    onSeekForward: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerShape = RoundedCornerShape(16.dp)

    Row(
        modifier = modifier
            .testTag("mini_player")
            .figmaDropShadow(style = FigmaShadowStyle.Xs, shape = containerShape)
            .clip(containerShape)
            .background(MaterialTheme.colorScheme.surface, containerShape)
            .border(
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                shape = containerShape
            )
            .height(52.dp)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MiniPlayerSeekButton(
            label = "-15",
            contentDescription = "Rewind 15 seconds",
            testTag = "mini_player_seek_backward",
            iconFirst = false,
            iconRotationDegrees = 0f,
            onClick = onSeekBackward
        )

        // Center Play / Pause button
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .testTag("mini_player_play_pause")
                .size(40.dp)
                .figmaDropShadow(style = FigmaShadowStyle.Md, shape = CircleShape)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary, CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = true),
                    role = Role.Button,
                    onClick = onPlayToggle
                )
                .semantics {
                    contentDescription = if (isPlaying) "Pause" else "Play"
                    role = Role.Button
                }
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
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(16.dp)
            )
        }

        MiniPlayerSeekButton(
            label = "+30",
            contentDescription = "Forward 30 seconds",
            testTag = "mini_player_seek_forward",
            iconFirst = true,
            iconRotationDegrees = 180f,
            onClick = onSeekForward
        )
    }
}

@Composable
private fun MiniPlayerSeekButton(
    label: String,
    contentDescription: String,
    testTag: String,
    iconFirst: Boolean,
    iconRotationDegrees: Float,
    onClick: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .testTag(testTag)
            .widthIn(min = 56.dp)
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true),
                role = Role.Button,
                onClick = onClick
            )
            .semantics {
                this.contentDescription = contentDescription
                role = Role.Button
            }
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            if (iconFirst) {
                MiniPlayerSeekIcon(rotationDegrees = iconRotationDegrees)
                MiniPlayerSeekLabel(text = label)
            } else {
                MiniPlayerSeekLabel(text = label)
                MiniPlayerSeekIcon(rotationDegrees = iconRotationDegrees)
            }
        }
    }
}

@Composable
private fun MiniPlayerSeekIcon(rotationDegrees: Float) {
    Icon(
        painter = painterResource(id = R.drawable.ic_huge_forward_02_wide),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .rotate(rotationDegrees)
            .size(width = 26.4.dp, height = 16.dp)
    )
}

@Composable
private fun MiniPlayerSeekLabel(text: String) {
    Text(
        text = text,
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        maxLines = 1,
        softWrap = false,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
