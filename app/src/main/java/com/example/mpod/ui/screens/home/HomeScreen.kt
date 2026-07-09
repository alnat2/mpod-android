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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mpod.ui.components.EpisodeRow
import com.example.mpod.ui.components.ModalScreenMobile
import com.example.mpod.ui.components.PageHeader
import com.example.mpod.ui.components.PlayerView
import com.example.mpod.ui.components.ShowNotesMobile
import com.example.mpod.ui.components.figmaDropShadow

@Composable
fun HomeScreen() {
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
