package com.example.mpod.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mpod.R
import com.example.mpod.ui.theme.accent

enum class FilemanagerItemType {
    Directory,
    AudiobookDirectory,
    AudioFile
}

@Composable
fun FilemanagerItem(
    title: String,
    type: FilemanagerItemType,
    modifier: Modifier = Modifier,
    durationText: String? = null,
    @DrawableRes actionIconRes: Int? = null,
    actionContentDescription: String? = null,
    onActionClick: (() -> Unit)? = null,
    isHighlighted: Boolean = false,
    isMuted: Boolean = false,
    onClick: () -> Unit
) {
    val containerColor = if (isHighlighted) {
        MaterialTheme.colorScheme.accent
    } else {
        MaterialTheme.colorScheme.surface
    }

    val titleColor = if (isMuted) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    val actionIconTint = if (isMuted) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.primary
    }

    val iconRes = when (type) {
        FilemanagerItemType.Directory -> R.drawable.ic_huge_folder_03
        FilemanagerItemType.AudiobookDirectory -> R.drawable.ic_huge_folder_audio
        FilemanagerItemType.AudioFile -> R.drawable.ic_huge_audio_book_01
    }

    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 70.dp)
            .testTag("filemanager_item_$title")
            .figmaDropShadow(style = FigmaShadowStyle.Xs, radius = 16.dp)
            .clip(shape)
            .background(containerColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon + Title
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = title,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = titleColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }

        // Optional Duration Text
        if (!durationText.isNullOrBlank()) {
            Text(
                text = durationText,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Normal,
                color = if (isMuted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.End,
                maxLines = 1
            )
        }

        // Optional Action Button (e.g. Add to Playlist, Replay, Play, Pause)
        if (actionIconRes != null && onActionClick != null) {
            SquareIconButton(
                iconRes = actionIconRes,
                contentDescription = actionContentDescription ?: "",
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = actionIconTint,
                size = 44.dp,
                iconSize = 24.dp,
                radius = 10.dp,
                shadowStyle = FigmaShadowStyle.Sm,
                onClick = onActionClick
            )
        }
    }
}
