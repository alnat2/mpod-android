package com.example.mpod.ui.screens.subscriptions

import com.example.mpod.data.network.model.EpisodeDto
import org.junit.Assert.assertEquals
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
