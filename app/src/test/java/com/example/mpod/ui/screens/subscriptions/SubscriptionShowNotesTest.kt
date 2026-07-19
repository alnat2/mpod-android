package com.example.mpod.ui.screens.subscriptions

import com.example.mpod.data.network.model.EpisodeDto
import com.example.mpod.ui.components.notesWithClickableLinks
import org.junit.Assert.assertEquals
import androidx.compose.ui.graphics.Color
import org.junit.Test

class SubscriptionShowNotesTest {
    @Test
    fun backendShowNotesTakePriorityOverLegacyFields() {
        assertEquals(
            "Full show notes",
            episode(showNotes = "Full show notes", description = "Description", summary = "Summary")
                .subscriptionShowNotes()
        )
    }

    @Test
    fun backendDescriptionIsUsedWhenShowNotesAreMissing() {
        assertEquals(
            "Description",
            episode(showNotes = null, description = "Description", summary = null)
                .subscriptionShowNotes()
        )
    }

    @Test
    fun webLinksRemainVisibleAndAnnotatedWithoutTrailingPunctuation() {
        val notes = notesWithClickableLinks(
            "Details: https://example.com/episode).",
            Color.Green
        )

        assertEquals("Details: https://example.com/episode).", notes.text)
        assertEquals(
            "https://example.com/episode",
            notes.getStringAnnotations("show_notes_url", 0, notes.length).single().item
        )
    }

    private fun episode(
        showNotes: String?,
        description: String?,
        summary: String?
    ) = EpisodeDto(
        id = 1,
        podcastId = 1,
        title = "Episode",
        description = description,
        showNotes = showNotes,
        audioUrl = "https://example.com/audio.mp3",
        duration = 60.0,
        isListened = false,
        downloaded = false,
        summary = summary,
        publishedAt = null
    )
}
