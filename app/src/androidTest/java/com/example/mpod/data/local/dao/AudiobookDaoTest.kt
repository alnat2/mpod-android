package com.example.mpod.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mpod.data.local.MpodDatabase
import com.example.mpod.data.local.entity.AudiobookEntity
import com.example.mpod.data.local.entity.AudiobookPlaylistTrackEntity
import com.example.mpod.data.local.entity.AudiobookTrackEntity
import com.example.mpod.data.local.entity.EpisodeEntity
import com.example.mpod.data.local.entity.PlaylistItemEntity
import com.example.mpod.data.local.entity.PodcastEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AudiobookDaoTest {

    private lateinit var database: MpodDatabase
    private lateinit var audiobookDao: AudiobookDao
    private lateinit var playlistDao: PlaylistDao
    private lateinit var podcastDao: PodcastDao
    private lateinit var episodeDao: EpisodeDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, MpodDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        audiobookDao = database.audiobookDao()
        playlistDao = database.playlistDao()
        podcastDao = database.podcastDao()
        episodeDao = database.episodeDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertAndRetrieveAudiobookWithTracks() = runBlocking {
        val book = AudiobookEntity(
            title = "The Hobbit",
            author = "J.R.R. Tolkien",
            folderUri = "content://audiobooks/hobbit",
            coverUri = "content://audiobooks/hobbit/cover.jpg",
            durationSeconds = 3600
        )
        val bookId = audiobookDao.insertAudiobook(book)
        assertTrue(bookId > 0)

        val track1 = AudiobookTrackEntity(
            audiobookId = bookId,
            title = "Chapter 1",
            fileUri = "content://audiobooks/hobbit/01.mp3",
            trackNumber = 1,
            durationSeconds = 1200
        )
        val track2 = AudiobookTrackEntity(
            audiobookId = bookId,
            title = "Chapter 2",
            fileUri = "content://audiobooks/hobbit/02.mp3",
            trackNumber = 2,
            durationSeconds = 2400
        )
        audiobookDao.insertTracks(listOf(track2, track1)) // insert out of order

        val retrievedBook = audiobookDao.getAudiobookById(bookId)
        assertNotNull(retrievedBook)
        assertEquals("The Hobbit", retrievedBook?.title)
        assertEquals("J.R.R. Tolkien", retrievedBook?.author)

        val tracks = audiobookDao.getTracksForAudiobook(bookId)
        assertEquals(2, tracks.size)
        // Check order by trackNumber ASC
        assertEquals(1, tracks[0].trackNumber)
        assertEquals("Chapter 1", tracks[0].title)
        assertEquals(2, tracks[1].trackNumber)
        assertEquals("Chapter 2", tracks[1].title)
    }

    @Test
    fun chapterSelectionAndCurrentTrackInvariant() = runBlocking {
        val bookId = audiobookDao.insertAudiobook(
            AudiobookEntity(
                title = "Clean Code",
                author = "Robert C. Martin",
                folderUri = "content://audiobooks/clean_code"
            )
        )
        val t1 = audiobookDao.insertTrack(
            AudiobookTrackEntity(audiobookId = bookId, title = "Ch 1", fileUri = "1.mp3", trackNumber = 1)
        )
        val t2 = audiobookDao.insertTrack(
            AudiobookTrackEntity(audiobookId = bookId, title = "Ch 2", fileUri = "2.mp3", trackNumber = 2)
        )
        val t3 = audiobookDao.insertTrack(
            AudiobookTrackEntity(audiobookId = bookId, title = "Ch 3", fileUri = "3.mp3", trackNumber = 3)
        )

        // Select chapters 1 and 3 only
        audiobookDao.setSelectedTracksForBook(bookId, listOf(t1, t3))

        val selected = audiobookDao.getSelectedTracksForAudiobook(bookId)
        assertEquals(2, selected.size)
        assertEquals(listOf(1, 3), selected.map { it.trackNumber })

        // Initial invariant: first unlistened selected track is Ch 1
        val currentInitial = audiobookDao.getCurrentTrackForAudiobook(bookId)
        assertEquals("Ch 1", currentInitial?.title)

        // Mark Ch 1 as listened
        audiobookDao.updateTrackProgress(t1, positionMs = 5000L, isListened = true)

        // Invariant: next unlistened selected track is Ch 3 (Ch 2 was not selected)
        val currentNext = audiobookDao.getCurrentTrackForAudiobook(bookId)
        assertEquals("Ch 3", currentNext?.title)

        // Mark Ch 3 as listened
        audiobookDao.updateTrackProgress(t3, positionMs = 9000L, isListened = true)

        // All selected are listened -> invariant falls back to first selected track (Ch 1)
        val currentAllDone = audiobookDao.getCurrentTrackForAudiobook(bookId)
        assertEquals("Ch 1", currentAllDone?.title)
    }

    @Test
    fun atomicProgressReset_resetsAllTracksOfAudiobook() = runBlocking {
        val bookId = audiobookDao.insertAudiobook(
            AudiobookEntity(title = "Dune", author = "Frank Herbert", folderUri = "content://audiobooks/dune")
        )
        val t1 = audiobookDao.insertTrack(
            AudiobookTrackEntity(
                audiobookId = bookId,
                title = "Part 1",
                fileUri = "part1.mp3",
                trackNumber = 1,
                playbackPositionMs = 12345L,
                isListened = true
            )
        )
        val t2 = audiobookDao.insertTrack(
            AudiobookTrackEntity(
                audiobookId = bookId,
                title = "Part 2",
                fileUri = "part2.mp3",
                trackNumber = 2,
                playbackPositionMs = 67890L,
                isListened = true
            )
        )

        audiobookDao.resetAudiobookProgress(bookId)

        val updatedTracks = audiobookDao.getTracksForAudiobook(bookId)
        for (track in updatedTracks) {
            assertEquals(0L, track.playbackPositionMs)
            assertFalse(track.isListened)
        }
    }

    @Test
    fun cascadeDeleteAudiobook_removesTracksAndPlaylistItems() = runBlocking {
        val bookId = audiobookDao.insertAudiobook(
            AudiobookEntity(title = "1984", author = "George Orwell", folderUri = "content://audiobooks/1984")
        )
        val t1 = audiobookDao.insertTrack(
            AudiobookTrackEntity(audiobookId = bookId, title = "Ch 1", fileUri = "1.mp3", trackNumber = 1)
        )
        audiobookDao.insertPlaylistTrack(AudiobookPlaylistTrackEntity(audiobookId = bookId, trackId = t1))
        playlistDao.insertPlaylistItem(PlaylistItemEntity(audiobookId = bookId, position = 0))

        assertTrue(playlistDao.isAudiobookInPlaylist(bookId))
        assertEquals(1, audiobookDao.getTracksForAudiobook(bookId).size)

        // Delete audiobook
        audiobookDao.deleteAudiobookById(bookId)

        assertNull(audiobookDao.getAudiobookById(bookId))
        assertEquals(0, audiobookDao.getTracksForAudiobook(bookId).size)
        assertEquals(0, audiobookDao.getSelectedTracksForAudiobook(bookId).size)
        assertFalse(playlistDao.isAudiobookInPlaylist(bookId))
    }

    @Test
    fun polymorphicPlaylistSupportsBothEpisodesAndAudiobooks() = runBlocking {
        val podcastId = podcastDao.insertPodcast(
            PodcastEntity(feedUrl = "https://example.com/feed.xml", title = "Tech Podcast")
        )
        val episodeId = episodeDao.insertEpisode(
            EpisodeEntity(
                podcastId = podcastId,
                guid = "ep-1",
                title = "Episode 1",
                audioUrl = "https://example.com/ep1.mp3"
            )
        )
        val bookId = audiobookDao.insertAudiobook(
            AudiobookEntity(title = "Book 1", folderUri = "content://audiobooks/b1")
        )

        playlistDao.appendEpisodeIfMissing(episodeId)
        playlistDao.appendAudiobookIfMissing(bookId)

        val items = playlistDao.getAllPlaylistItems()
        assertEquals(2, items.size)
        assertEquals(0, items[0].position)
        assertEquals(episodeId, items[0].episodeId)
        assertNull(items[0].audiobookId)
        assertEquals(1, items[1].position)
        assertEquals(bookId, items[1].audiobookId)
        assertNull(items[1].episodeId)

        assertTrue(playlistDao.isEpisodeInPlaylist(episodeId))
        assertTrue(playlistDao.isAudiobookInPlaylist(bookId))

        // Remove audiobook only
        playlistDao.removeAudiobookFromPlaylist(bookId)
        assertFalse(playlistDao.isAudiobookInPlaylist(bookId))
        assertTrue(playlistDao.isEpisodeInPlaylist(episodeId))
    }

    @Test
    fun upsertAudiobook_preservesTracksAndPlaylistOnConflict() = runBlocking {
        val folderUri = "content://audiobooks/persistent"
        val bookId = audiobookDao.upsertAudiobook(
            AudiobookEntity(title = "Initial Title", author = "Author", folderUri = folderUri)
        )
        val trackId = audiobookDao.insertTrack(
            AudiobookTrackEntity(audiobookId = bookId, title = "Ch 1", fileUri = "1.mp3", trackNumber = 1)
        )
        audiobookDao.insertPlaylistTrack(AudiobookPlaylistTrackEntity(audiobookId = bookId, trackId = trackId))
        playlistDao.appendAudiobookIfMissing(bookId)

        // Rescan / re-import with updated title
        val reimportedId = audiobookDao.upsertAudiobook(
            AudiobookEntity(title = "Updated Title", author = "Author", folderUri = folderUri)
        )

        assertEquals(bookId, reimportedId)
        val updatedBook = audiobookDao.getAudiobookById(bookId)
        assertEquals("Updated Title", updatedBook?.title)

        // Tracks, playlist tracks, and queue items are preserved!
        assertEquals(1, audiobookDao.getTracksForAudiobook(bookId).size)
        assertEquals(1, audiobookDao.getSelectedTracksForAudiobook(bookId).size)
        assertTrue(playlistDao.isAudiobookInPlaylist(bookId))
    }

    @Test
    fun reorderPlaylist_episodesOnly_doesNotPruneAudiobooks() = runBlocking {
        val podcastId = podcastDao.insertPodcast(
            PodcastEntity(feedUrl = "https://example.com/feed.xml", title = "Podcast")
        )
        val ep1 = episodeDao.insertEpisode(
            EpisodeEntity(podcastId = podcastId, guid = "ep-1", title = "Ep 1", audioUrl = "ep1.mp3")
        )
        val ep2 = episodeDao.insertEpisode(
            EpisodeEntity(podcastId = podcastId, guid = "ep-2", title = "Ep 2", audioUrl = "ep2.mp3")
        )
        val bookId = audiobookDao.insertAudiobook(
            AudiobookEntity(title = "Book", folderUri = "content://audiobooks/book")
        )

        playlistDao.appendEpisodeIfMissing(ep1)
        playlistDao.appendAudiobookIfMissing(bookId)
        playlistDao.appendEpisodeIfMissing(ep2)

        // Reorder only episodes (as existing podcast code does)
        playlistDao.reorderPlaylist(
            listOf(
                PlaylistItemEntity(episodeId = ep2, position = 0),
                PlaylistItemEntity(episodeId = ep1, position = 2)
            )
        )

        // The audiobook must STILL be in the playlist!
        assertTrue(playlistDao.isAudiobookInPlaylist(bookId))
        val items = playlistDao.getAllPlaylistItems()
        assertTrue(items.any { it.audiobookId == bookId })
    }

    @Test
    fun playlistItemEntityValidation_enforcesExclusivity() {
        // Valid: episode only
        val epItem = PlaylistItemEntity(episodeId = 1L, position = 0)
        assertEquals(1L, epItem.episodeId)
        assertNull(epItem.audiobookId)

        // Valid: audiobook only
        val bookItem = PlaylistItemEntity(audiobookId = 2L, position = 1)
        assertEquals(2L, bookItem.audiobookId)
        assertNull(bookItem.episodeId)

        // Invalid: neither
        assertThrows(IllegalArgumentException::class.java) {
            PlaylistItemEntity(episodeId = null, audiobookId = null, position = 0)
        }

        // Invalid: both
        assertThrows(IllegalArgumentException::class.java) {
            PlaylistItemEntity(episodeId = 1L, audiobookId = 2L, position = 0)
        }
    }
}
