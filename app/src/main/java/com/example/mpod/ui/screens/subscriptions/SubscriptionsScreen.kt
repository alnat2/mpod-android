package com.example.mpod.ui.screens.subscriptions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.mpod.ui.components.EpisodeRow
import com.example.mpod.ui.components.MarkAllListenedHeader
import com.example.mpod.ui.components.PageHeader
import com.example.mpod.ui.components.PodcastCard

@Composable
fun SubscriptionsScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        PageHeader(
            title = "Subscriptions",
            subtitle = "12 podcasts",
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
                                .height(210.dp),
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
                                isPlaying = true,
                                showDragHandle = index != 0
                            )
                        }
                    }
                }
            }
        }
    }
}
