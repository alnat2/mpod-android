package com.example.mpod.ui.screens.subscriptions

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.mpod.ui.components.EpisodeRow
import com.example.mpod.ui.components.MarkAllListenedHeader
import com.example.mpod.ui.components.MpodBottomNav
import com.example.mpod.ui.components.MpodButton
import com.example.mpod.ui.components.PageHeader
import com.example.mpod.ui.components.PodcastCard
import com.example.mpod.ui.navigation.Screen
import com.example.mpod.ui.theme.MpodTheme

@Composable
fun SubscriptionsScreen(
    hasRefreshError: Boolean = false
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            PageHeader(
                title = "Subscriptions",
                subtitle = if (hasRefreshError) "Last refresh · today 3:04" else "12 podcasts",
                showActions = true
            )

            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val itemWidth = maxWidth
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(2) { index ->
                        Column(
                            modifier = Modifier.width(itemWidth),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            PodcastCard(
                                title = "Decoder Ring",
                                description = "Culture stories behind everyday design",
                                selected = index == 0,
                                onUnsubscribe = { /* TODO */ }
                            )
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(210.dp)
                                    .padding(horizontal = 2.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                MarkAllListenedHeader()
                                EpisodeRow(
                                    title = "Why store loyalty cards became a UX minefield",
                                    podcastName = "Decoder Ring",
                                    duration = "54m",
                                    date = "31.03.26",
                                    inPlaylist = true,
                                    showDragHandle = index != 0
                                )
                                EpisodeRow(
                                    title = "Why store loyalty cards became a UX minefield",
                                    podcastName = "Decoder Ring",
                                    duration = "54m",
                                    date = "31.03.26",
                                    showDragHandle = index != 0
                                )
                            }
                        }
                    }
                }
            }
        }

        if (hasRefreshError) {
            RefreshErrorBanner(
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(top = 10.dp)
                    .align(Alignment.TopCenter)
            )
        }
    }
}

@Composable
private fun RefreshErrorBanner(
    modifier: Modifier = Modifier
) {
    val destructive = Color(0xFFE7000B)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .border(1.dp, destructive, RoundedCornerShape(8.dp))
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Refresh failed for \"The Watch\" podcast",
            fontSize = MaterialTheme.typography.bodySmall.fontSize,
            lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
            fontWeight = FontWeight.Medium,
            color = destructive,
            modifier = Modifier.weight(1f)
        )
        MpodButton(
            text = "Try again",
            primary = false,
            outlined = true,
            height = 32.dp,
            radius = 6.dp,
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.width(86.dp)
        )
    }
}

@Preview(
    name = "Subscriptions error / 360",
    widthDp = 360,
    heightDp = 800,
    showBackground = true
)
@Composable
private fun SubscriptionsErrorScreenPreview() {
    MpodTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            Box(modifier = Modifier.weight(1f)) {
                SubscriptionsScreen(hasRefreshError = true)
            }
            MpodBottomNav(
                currentRoute = Screen.Subscriptions.route,
                onNavigate = {},
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }
    }
}
