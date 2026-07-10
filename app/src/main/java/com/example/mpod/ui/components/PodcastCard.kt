package com.example.mpod.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mpod.R

@Composable
fun PodcastCard(
    title: String,
    description: String,
    selected: Boolean,
    onUnsubscribe: () -> Unit,
    modifier: Modifier = Modifier
) {
    val background = if (selected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(160.dp)
            .figmaDropShadow(radius = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(background)
            .then(
                if (selected) Modifier else Modifier.border(
                    1.dp,
                    MaterialTheme.colorScheme.outline,
                    RoundedCornerShape(16.dp)
                )
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.onBackground)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "THE\nREAL\nTALK",
                    fontSize = 12.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                    maxLines = 3
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    fontSize = 20.sp,
                    lineHeight = 28.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = description,
                    fontSize = 16.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MpodButton(
                text = "Refresh",
                primary = false,
                outlined = true,
                iconRes = R.drawable.ic_refresh_dot,
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.onBackground,
                height = 32.dp,
                modifier = Modifier.weight(1f)
            )
            MpodButton(
                text = "Unsubscribe",
                primary = false,
                elevation = 0.dp,
                height = 32.dp,
                modifier = Modifier.weight(1f),
                onClick = onUnsubscribe
            )
        }
    }
}

@Composable
fun MarkAllListenedHeader(
    summary: String = "21 / 15 episodes",
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .figmaDropShadow(radius = 4.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = summary,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Mark all listened",
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun RefreshButtonLabel() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_refresh_dot),
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Refresh",
            fontSize = 14.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}
