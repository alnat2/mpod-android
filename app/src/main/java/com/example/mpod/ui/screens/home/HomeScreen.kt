package com.example.mpod.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mpod.R
import com.example.mpod.ui.components.EpisodeRow
import com.example.mpod.ui.components.ModalScreenMobile
import com.example.mpod.ui.components.MpodButton
import com.example.mpod.ui.components.MpodBottomNav
import com.example.mpod.ui.components.PageHeader
import com.example.mpod.ui.components.PlayerView
import com.example.mpod.ui.components.ShowNotesMobile
import com.example.mpod.ui.components.figmaDropShadow
import com.example.mpod.ui.navigation.Screen
import com.example.mpod.ui.theme.MpodTheme

@Composable
fun HomeScreen(
    hasPodcasts: Boolean = true
) {
    var showNotes by remember { mutableStateOf(false) }
    val episodes = listOf(
        Triple("Why store loyalty cards became a UX minefield", "54m", true),
        Triple("How public transit maps teach invisible habits", "36m", false),
        Triple("The app menu nobody understands but everyone...", "43m", false)
    )

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
            if (hasPodcasts) {
                PageHeader(
                    title = "Now playing",
                    showActions = true
                )

                PlayerView(
                    modifier = Modifier.fillMaxWidth(),
                    onNotesClick = { showNotes = true }
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Card(
                        shape = RoundedCornerShape(4.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .figmaDropShadow(radius = 4.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.CenterStart,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp)
                        ) {
                            Text(
                                text = "3 episodes · 2h 13m",
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                                fontWeight = FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    episodes.forEach { (title, duration, isPlaying) ->
                        EpisodeRow(
                            title = title,
                            podcastName = "Decoder Ring",
                            duration = duration,
                            isPlaying = isPlaying,
                            showDragHandle = true
                        )
                    }
                }
            } else {
                PageHeader(
                    title = "No podcasts",
                    subtitle = "Start with one RSS feed or import subscriptions from another app."
                )

                NoPodcastsEmptyState(
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (showNotes) {
            ModalScreenMobile {
                ShowNotesMobile(
                    onClose = { showNotes = false }
                )
            }
        }
    }
}

@Composable
private fun NoPodcastsEmptyState(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_podcast_empty),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = "No podcasts yet",
                    fontSize = 18.sp,
                    lineHeight = 28.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Add one RSS feed or bring subscriptions from another podcast app with OPML.",
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MpodButton(
                    text = "Add RSS feed",
                    height = 32.dp,
                    radius = 6.dp,
                    modifier = Modifier.weight(1f)
                )
                MpodButton(
                    text = "Import OPML",
                    primary = false,
                    outlined = true,
                    height = 32.dp,
                    radius = 6.dp,
                    containerColor = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Preview(
    name = "No podcasts screen / 360",
    widthDp = 360,
    heightDp = 800,
    showBackground = true
)
@Composable
private fun NoPodcastsScreenPreview() {
    MpodTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            Box(modifier = Modifier.weight(1f)) {
                HomeScreen(hasPodcasts = false)
            }
            MpodBottomNav(
                currentRoute = Screen.Home.route,
                onNavigate = {},
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }
    }
}
