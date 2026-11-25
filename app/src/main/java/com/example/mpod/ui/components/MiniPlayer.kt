package com.example.mpod.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Color
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

@Composable
fun MiniPlayer(
    isPlaying: Boolean,
    onPlayToggle: () -> Unit,
    onSeekBackward: () -> Unit,
    onSeekForward: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pillShape = RoundedCornerShape(26.dp)

    Row(
        modifier = modifier
            .testTag("mini_player")
            .figmaDropShadow(style = FigmaShadowStyle.Md, shape = pillShape)
            .border(
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                shape = pillShape
            )
            .clip(pillShape)
            .background(MaterialTheme.colorScheme.surface, pillShape)
            .height(52.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        // Rewind 15: text "-15" + rewind icon
        Row(
            modifier = Modifier
                .testTag("mini_player_seek_backward")
                .clip(RoundedCornerShape(16.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = true),
                    role = Role.Button,
                    onClick = onSeekBackward
                )
                .semantics {
                    contentDescription = "Rewind 15 seconds"
                    role = Role.Button
                }
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "-15",
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                painter = painterResource(id = R.drawable.ic_huge_forward_02_wide),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.height(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(4.dp))

        // Center Play / Pause button
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .testTag("mini_player_play_pause")
                .size(40.dp)
                .figmaDropShadow(style = FigmaShadowStyle.Sm, shape = CircleShape)
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
                tint = Color(0xFFF7FEE7),
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(4.dp))

        // Forward 30: forward icon (rotated 180) + text "+30"
        Row(
            modifier = Modifier
                .testTag("mini_player_seek_forward")
                .clip(RoundedCornerShape(16.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = true),
                    role = Role.Button,
                    onClick = onSeekForward
                )
                .semantics {
                    contentDescription = "Forward 30 seconds"
                    role = Role.Button
                }
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_huge_forward_02_wide),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .rotate(180f)
                    .height(18.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "+30",
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
