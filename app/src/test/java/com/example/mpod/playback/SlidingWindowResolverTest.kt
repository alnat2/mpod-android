package com.example.mpod.playback

import com.example.mpod.data.local.entity.AudiobookEntity
import com.example.mpod.data.local.entity.AudiobookTrackEntity
import com.example.mpod.data.local.entity.EpisodeEntity
import com.example.mpod.data.local.model.PlayableItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SlidingWindowResolverTest {

    @Test
    fun resolveWindow_emptyQueue_returnsNullCurrentAndNext() {
        val window = SlidingWindowResolver.resolveWindow(emptyList())
        assertNull(window.current)
        assertNull(window.next)
    }

    @Test
    fun resolveWindow_singleEpisode_returnsEpisodeAndNullNext() {
        val ep = createEpisodeItem(1L, "Ep 1", position = 0)
        val window = SlidingWindowResolver.resolveWindow(listOf(ep))

        assertNotNull(window.current)
        assertTrue(window.current is PlaybackTrack.EpisodeTrack)
        assertEquals(1L, (window.current as PlaybackTrack.EpisodeTrack).episode.id)
        assertNull(window.next)
    }

    @Test
    fun resolveWindow_twoEpisodes_returnsFirstAsCurrentSecondAsNext() {
        val ep1 = createEpisodeItem(1L, "Ep 1", position = 0)
        val ep2 = createEpisodeItem(2L, "Ep 2", position = 1)
        val window = SlidingWindowResolver.resolveWindow(listOf(ep1, ep2))

        assertNotNull(window.current)
        assertEquals(1L, (window.current as PlaybackTrack.EpisodeTrack).episode.id)

        assertNotNull(window.next)
        assertEquals(2L, (window.next as PlaybackTrack.EpisodeTrack).episode.id)
    }

    @Test
    fun resolveWindow_episodeFollowedByAudiobook_returnsEpisodeAndFirstChapterAsNext() {
        val ep = createEpisodeItem(1L, "Ep 1", position = 0)
        val abk = createAudiobookItem(
            audiobookId = 10L,
            title = "The Hobbit",
            position = 1,
            tracks = listOf(
                createTrack(101L, 10L, "Ch 1", trackNumber = 1),
                createTrack(102L, 10L, "Ch 2", trackNumber = 2)
            )
        )

        val window = SlidingWindowResolver.resolveWindow(listOf(ep, abk))

        assertNotNull(window.current)
        assertTrue(window.current is PlaybackTrack.EpisodeTrack)
        assertEquals(1L, (window.current as PlaybackTrack.EpisodeTrack).episode.id)

        assertNotNull(window.next)
        assertTrue(window.next is PlaybackTrack.AudiobookChapterTrack)
        val nextTrack = window.next as PlaybackTrack.AudiobookChapterTrack
        assertEquals(101L, nextTrack.track.id)
        assertEquals(1, nextTrack.chapterIndex)
        assertEquals(2, nextTrack.totalChapters)
    }

    @Test
    fun resolveWindow_audiobookWithMultipleChapters_returnsChapter1AndChapter2AsNext() {
        val abk = createAudiobookItem(
            audiobookId = 10L,
            title = "Clean Code",
            position = 0,
            tracks = listOf(
                createTrack(101L, 10L, "Ch 1", trackNumber = 1),
                createTrack(102L, 10L, "Ch 2", trackNumber = 2),
                createTrack(103L, 10L, "Ch 3", trackNumber = 3)
            )
        )

        val window = SlidingWindowResolver.resolveWindow(listOf(abk))

        assertNotNull(window.current)
        assertTrue(window.current is PlaybackTrack.AudiobookChapterTrack)
        val currentTrack = window.current as PlaybackTrack.AudiobookChapterTrack
        assertEquals(101L, currentTrack.track.id)
        assertEquals(1, currentTrack.chapterIndex)
        assertEquals(3, currentTrack.totalChapters)

        assertNotNull(window.next)
        assertTrue(window.next is PlaybackTrack.AudiobookChapterTrack)
        val nextTrack = window.next as PlaybackTrack.AudiobookChapterTrack
        assertEquals(102L, nextTrack.track.id)
        assertEquals(2, nextTrack.chapterIndex)
        assertEquals(3, nextTrack.totalChapters)
    }

    @Test
    fun resolveWindow_audiobookOnFinalChapter_returnsFinalChapterAndNextQueueItemAsNext() {
        val t1 = createTrack(101L, 10L, "Ch 1", trackNumber = 1, isListened = true)
        val t2 = createTrack(102L, 10L, "Ch 2", trackNumber = 2, isListened = false)
        val abk = createAudiobookItem(
            audiobookId = 10L,
            title = "Clean Code",
            position = 0,
            tracks = listOf(t1, t2),
            currentTrack = t2 // currently on final chapter
        )
        val ep = createEpisodeItem(99L, "Next Episode", position = 1)

        val window = SlidingWindowResolver.resolveWindow(listOf(abk, ep))

        assertNotNull(window.current)
        assertEquals(102L, (window.current as PlaybackTrack.AudiobookChapterTrack).track.id)

        // Next item in sliding window is the episode!
        assertNotNull(window.next)
        assertTrue(window.next is PlaybackTrack.EpisodeTrack)
        assertEquals(99L, (window.next as PlaybackTrack.EpisodeTrack).episode.id)
    }

    @Test
    fun resolveWindow_audiobookOnFinalChapterAtEndOfQueue_returnsFinalChapterAndNullNext() {
        val t1 = createTrack(101L, 10L, "Ch 1", trackNumber = 1, isListened = true)
        val t2 = createTrack(102L, 10L, "Ch 2", trackNumber = 2, isListened = false)
        val abk = createAudiobookItem(
            audiobookId = 10L,
            title = "Clean Code",
            position = 0,
            tracks = listOf(t1, t2),
            currentTrack = t2
        )

        val window = SlidingWindowResolver.resolveWindow(listOf(abk))

        assertNotNull(window.current)
        assertEquals(102L, (window.current as PlaybackTrack.AudiobookChapterTrack).track.id)
        assertNull(window.next)
    }

    @Test
    fun resolveWindow_skipsUnavailableTracksInAudiobook() {
        val t1 = createTrack(101L, 10L, "Ch 1", trackNumber = 1, isAvailable = true)
        val t2 = createTrack(102L, 10L, "Ch 2 (Missing)", trackNumber = 2, isAvailable = false)
        val t3 = createTrack(103L, 10L, "Ch 3", trackNumber = 3, isAvailable = true)

        val abk = createAudiobookItem(
            audiobookId = 10L,
            title = "Book",
            position = 0,
            tracks = listOf(t1, t2, t3)
        )

        val window = SlidingWindowResolver.resolveWindow(listOf(abk))

        assertEquals(101L, (window.current as PlaybackTrack.AudiobookChapterTrack).track.id)
        // t2 is skipped because isAvailable = false; next is t3
        assertEquals(103L, (window.next as PlaybackTrack.AudiobookChapterTrack).track.id)
    }

    @Test
    fun resolveWindow_withPreferredTrackId_selectsSpecificChapter() {
        val t1 = createTrack(101L, 10L, "Ch 1", trackNumber = 1)
        val t2 = createTrack(102L, 10L, "Ch 2", trackNumber = 2)
        val t3 = createTrack(103L, 10L, "Ch 3", trackNumber = 3)

        val abk = createAudiobookItem(
            audiobookId = 10L,
            title = "Book",
            position = 0,
            tracks = listOf(t1, t2, t3)
        )

        val window = SlidingWindowResolver.resolveWindow(
            queue = listOf(abk),
            preferredTrackId = 102L
        )

        assertEquals(102L, (window.current as PlaybackTrack.AudiobookChapterTrack).track.id)
        assertEquals(103L, (window.next as PlaybackTrack.AudiobookChapterTrack).track.id)
    }

    @Test
    fun resolveWindow_audiobookWithUnavailableTrack_skipsToNextUnlistenedAvailableTrack() {
        val t1 = createTrack(101L, 10L, "Ch 1", trackNumber = 1, isListened = true, isAvailable = true)
        val t2 = createTrack(102L, 10L, "Ch 2", trackNumber = 2, isListened = false, isAvailable = false)
        val t3 = createTrack(103L, 10L, "Ch 3", trackNumber = 3, isListened = false, isAvailable = true)

        val abk = createAudiobookItem(
            audiobookId = 10L,
            title = "Book",
            position = 0,
            tracks = listOf(t1, t2, t3),
            currentTrack = null
        )

        val window = SlidingWindowResolver.resolveWindow(listOf(abk))

        // t1 is listened, t2 is unavailable, so current must be unlistened available track t3 (not jumping back to t1)
        assertEquals(103L, (window.current as PlaybackTrack.AudiobookChapterTrack).track.id)
        assertNull(window.next)
    }

    @Test
    fun resolveWindow_audiobookAllTracksUnavailable_skipsToNextPlayableItem() {
        val t1 = createTrack(101L, 10L, "Ch 1", trackNumber = 1, isAvailable = false)
        val t2 = createTrack(102L, 10L, "Ch 2", trackNumber = 2, isAvailable = false)

        val abk = createAudiobookItem(
            audiobookId = 10L,
            title = "Missing Book",
            position = 0,
            tracks = listOf(t1, t2)
        )
        val ep = createEpisodeItem(5L, "Playable Episode", position = 1)

        val window = SlidingWindowResolver.resolveWindow(listOf(abk, ep))

        // Audiobook has no available tracks, so resolver skips forward to the next item (ep)
        assertNotNull(window.current)
        assertTrue(window.current is PlaybackTrack.EpisodeTrack)
        assertEquals(5L, (window.current as PlaybackTrack.EpisodeTrack).episode.id)
        assertNull(window.next)
    }

    @Test
    fun mediaQueueId_parsingAndFormatting() {
        val epId = MediaQueueId.Episode(42L)
        assertEquals("episode:42", epId.toMediaId())
        assertEquals(epId, MediaQueueId.fromMediaId("episode:42"))
        assertEquals(epId, MediaQueueId.fromMediaId("42")) // backward compatibility

        val abkId = MediaQueueId.Audiobook(5L, 105L)
        assertEquals("audiobook:5:105", abkId.toMediaId())
        assertEquals(abkId, MediaQueueId.fromMediaId("audiobook:5:105"))

        assertNull(MediaQueueId.fromMediaId(null))
        assertNull(MediaQueueId.fromMediaId("invalid:format:extra"))
    }

    private fun createEpisodeItem(id: Long, title: String, position: Int): PlayableItem.EpisodeItem {
        return PlayableItem.EpisodeItem(
            playlistItemId = id * 10,
            position = position,
            episode = EpisodeEntity(
                id = id,
                podcastId = 1L,
                guid = "guid_$id",
                title = title,
                audioUrl = "https://example.com/audio_$id.mp3",
                durationSeconds = 1800L
            ),
            podcastTitle = "Podcast Title",
            podcastArtworkUrl = "https://example.com/art.jpg"
        )
    }

    private fun createAudiobookItem(
        audiobookId: Long,
        title: String,
        position: Int,
        tracks: List<AudiobookTrackEntity>,
        currentTrack: AudiobookTrackEntity? = tracks.firstOrNull()
    ): PlayableItem.AudiobookItem {
        return PlayableItem.AudiobookItem(
            playlistItemId = audiobookId * 100,
            position = position,
            audiobook = AudiobookEntity(
                id = audiobookId,
                title = title,
                author = "Author Name",
                folderUri = "content://audiobooks/$title"
            ),
            selectedTracks = tracks,
            currentTrack = currentTrack
        )
    }

    private fun createTrack(
        id: Long,
        audiobookId: Long,
        title: String,
        trackNumber: Int,
        isListened: Boolean = false,
        isAvailable: Boolean = true
    ): AudiobookTrackEntity {
        return AudiobookTrackEntity(
            id = id,
            audiobookId = audiobookId,
            title = title,
            fileUri = "content://audiobooks/$audiobookId/$id.mp3",
            durationSeconds = 600L,
            trackNumber = trackNumber,
            isListened = isListened,
            isAvailable = isAvailable
        )
    }
}
