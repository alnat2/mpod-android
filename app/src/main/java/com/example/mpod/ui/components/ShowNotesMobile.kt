package com.example.mpod.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mpod.R

@Composable
fun ModalScreenMobile(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit = { ShowNotesMobile() }
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.30f))
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
fun ShowNotesMobile(
    podcastTitle: String = "Decoder Ring - Why store loyalty cards became a UX minefield",
    notes: String? = null,
    onClose: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val notesText = notes ?: rememberDefaultShowNotes()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .figmaDropShadow(radius = 20.dp, offsetY = 2.dp, blur = 4.dp)
            .figmaDropShadow(radius = 20.dp, offsetY = 4.dp, blur = 6.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "Show notes",
                    fontSize = 24.sp,
                    lineHeight = 32.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = podcastTitle,
                    fontSize = 16.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            SquareIconButton(
                iconRes = R.drawable.ic_multiplication_sign,
                contentDescription = "Close show notes",
                size = 36.dp,
                iconSize = 16.dp,
                radius = 10.dp,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outline
                ),
                contentColor = MaterialTheme.colorScheme.primary,
                onClick = onClose
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(360.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.Top
        ) {
            val scrollState = rememberScrollState()
            Text(
                text = notesText,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scrollState)
            )
            StaticScrollbar()
        }
    }
}

@Composable
private fun StaticScrollbar() {
    Box(
        modifier = Modifier
            .width(6.dp)
            .height(360.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Box(
            modifier = Modifier
                .padding(top = 36.dp)
                .width(6.dp)
                .height(116.dp)
                .clip(CircleShape)
                .background(Color(0xFF696867))
        )
    }
}

@Composable
private fun rememberDefaultShowNotes(): String = remember {
    """
    This modal version keeps the main player context visible behind a muted backdrop while giving long show notes enough dedicated space. It is useful when notes need more reading focus than a side panel can comfortably provide.

    Some podcast feeds include full essays, dense links, guest bios, sponsor copy, chapters, transcript excerpts, and source references. In modal mode the text area should scroll independently, while the modal header and close action remain obvious.

    Recommendation for MVP: use this modal pattern when show notes are opened from the focused player on smaller screens or when notes are long. On wider desktop layouts, the side panel can still work, but the modal is safer for overflow-heavy content.

    The scrollbar on the right is intentionally visible here. It communicates that there is more content without inventing a fake button-like affordance.

    Additional paragraph to demonstrate overflow: the implementation should preserve playback state, avoid changing queue order, and avoid marking the episode listened just because the notes were opened.

    Another paragraph: show notes are read-only feed content for MVP. Links can open externally later, but the core state is simple: open, read, scroll, close.
    """.trimIndent()
}
