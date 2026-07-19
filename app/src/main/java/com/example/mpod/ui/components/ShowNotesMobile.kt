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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
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
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
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
    onOpenLink: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val notesText = notes ?: rememberDefaultShowNotes()
    val linkColor = MaterialTheme.colorScheme.primary
    val uriHandler = LocalUriHandler.current
    val openLink = onOpenLink ?: uriHandler::openUri
    val annotatedNotes = remember(notesText, linkColor, openLink) {
        notesWithClickableLinks(notesText, linkColor, openLink)
    }
    val hasLongNotes = notesText.length > 280 || notesText.count { it == '\n' } > 6

    Column(
        modifier = modifier
            .widthIn(max = 320.dp)
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
                .then(if (hasLongNotes) Modifier.height(360.dp) else Modifier),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.Top
        ) {
            val scrollState = rememberScrollState()
            Text(
                text = annotatedNotes,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scrollState)
            )
            if (hasLongNotes) {
                StaticScrollbar()
            }
        }
    }
}

private const val SHOW_NOTES_URL_TAG = "show_notes_url"
private val SHOW_NOTES_URL_REGEX = Regex("https?://[^\\s<>]+")

internal fun notesWithClickableLinks(
    text: String,
    linkColor: Color,
    onOpenLink: (String) -> Unit = {}
): AnnotatedString {
    val builder = AnnotatedString.Builder()
    var cursor = 0
    SHOW_NOTES_URL_REGEX.findAll(text).forEach { match ->
        builder.append(text.substring(cursor, match.range.first))
        val rawUrl = match.value
        val trailingPunctuation = rawUrl.takeLastWhile { it in ".,;:!?)]}" }
        val url = rawUrl.dropLast(trailingPunctuation.length)
        builder.pushStringAnnotation(SHOW_NOTES_URL_TAG, url)
        builder.withLink(
            LinkAnnotation.Clickable(
                tag = url,
                styles = TextLinkStyles(
                    style = SpanStyle(
                        color = linkColor,
                        textDecoration = TextDecoration.Underline
                    )
                ),
                linkInteractionListener = { onOpenLink(url) }
            )
        ) {
            append(url)
        }
        builder.pop()
        builder.append(trailingPunctuation)
        cursor = match.range.last + 1
    }
    builder.append(text.substring(cursor))
    return builder.toAnnotatedString()
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
