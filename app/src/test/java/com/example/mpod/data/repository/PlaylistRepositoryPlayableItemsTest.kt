package com.example.mpod.data.repository

import com.example.mpod.data.local.MpodDatabase
import com.example.mpod.data.local.dao.AudiobookDao
import com.example.mpod.data.local.dao.PlaylistDao
import com.example.mpod.data.local.entity.AudiobookEntity
import com.example.mpod.data.local.entity.AudiobookPlaylistTrackEntity
import com.example.mpod.data.local.entity.AudiobookTrackEntity
import com.example.mpod.data.local.entity.EpisodeEntity
import com.example.mpod.data.local.entity.PlaylistItemEntity
import com.example.mpod.data.local.model.PlayableItem
import com.example.mpod.data.local.model.PlaylistItemWithAudiobook
import com.example.mpod.data.local.model.PlaylistItemWithEpisode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import com.example.mpod.testutil.any
import com.example.mpod.testutil.argThat
import com.example.mpod.testutil.mock
import com.example.mpod.testutil.never
import com.example.mpod.testutil.verify
import com.example.mpod.testutil.whenever

class PlaylistRepositoryPlayableItemsTest {

    private lateinit var playlistDao: PlaylistDao
    private lateinit var audiobookDao: AudiobookDao
    private lateinit var database: MpodDatabase
    private lateinit var repository: PlaylistRepository

    @Before
    fun setUp() {
        playlistDao = mock()
        audiobookDao = mock()
        database = mock()
        whenever(database.transactionExecutor).thenReturn(java.util.concurrent.Executor { it.run() })
        whenever(database.suspendingTransactionId).thenReturn(ThreadLocal())
        whenever(audiobookDao.getPlaylistAudiobookTracksFlow()).thenReturn(flowOf(emptyList()))
        repository = PlaylistRepository(playlistDao, audiobookDao, database)
    }

    private fun createEpisode(id: Long, title: String) = EpisodeEntity(
        id = id,
        podcastId = 1L,
        guid = "guid-$id",
        title = title,
        audioUrl = "https://example.com/$id.mp3"
    )

    private fun createAudiobook(id: Long, title: String) = AudiobookEntity(
        id = id,
        title = title,
        folderUri = "content://audiobooks/$id"
    )

    private fun createTrack(id: Long, bookId: Long, title: String, number: Int) = AudiobookTrackEntity(
        id = id,
        audiobookId = bookId,
        title = title,
        fileUri = "content://audiobooks/$bookId/$id.mp3",
        trackNumber = number
    )

    @Test
    fun getPlayableItems_returnsEpisodesAndAudiobooksInPositionOrder(): Unit = runBlocking {
        val playlistItems = listOf(
            PlaylistItemEntity(id = 100L, episodeId = 1L, position = 0),
            PlaylistItemEntity(id = 101L, audiobookId = 20L, position = 1),
            PlaylistItemEntity(id = 102L, episodeId = 2L, position = 2)
        )
        whenever(playlistDao.getAllPlaylistItems()).thenReturn(playlistItems)

        val episode1 = createEpisode(1L, "Episode 1")
        val episode2 = createEpisode(2L, "Episode 2")
        val audiobook = createAudiobook(20L, "Audiobook 20")
        val tracks = listOf(
            createTrack(201L, 20L, "Chapter 1", 1),
            createTrack(202L, 20L, "Chapter 2", 2)
        )

        whenever(playlistDao.getPlaylistItemsWithEpisodes()).thenReturn(
            listOf(
                PlaylistItemWithEpisode(100L, 0, episode1, "Podcast A", "https://artwork/a.jpg"),
                PlaylistItemWithEpisode(102L, 2, episode2, "Podcast B", "https://artwork/b.jpg")
            )
        )
        whenever(playlistDao.getPlaylistItemsWithAudiobooks()).thenReturn(
            listOf(
                PlaylistItemWithAudiobook(101L, 1, audiobook)
            )
        )
        whenever(audiobookDao.getSelectedTracksForAudiobook(20L)).thenReturn(tracks)
        whenever(audiobookDao.getCurrentTrackForAudiobook(20L)).thenReturn(tracks[0])

        val playableItems = repository.getPlayableItems()

        assertEquals(3, playableItems.size)
        assertTrue(playableItems[0] is PlayableItem.EpisodeItem)
        assertEquals(1L, (playableItems[0] as PlayableItem.EpisodeItem).episode.id)
        assertEquals(0, playableItems[0].position)

        assertTrue(playableItems[1] is PlayableItem.AudiobookItem)
        val abkItem = playableItems[1] as PlayableItem.AudiobookItem
        assertEquals(20L, abkItem.audiobook.id)
        assertEquals(1, abkItem.position)
        assertEquals(2, abkItem.totalChaptersCount)
        assertEquals(1, abkItem.currentChapterIndex)

        assertTrue(playableItems[2] is PlayableItem.EpisodeItem)
        assertEquals(2L, (playableItems[2] as PlayableItem.EpisodeItem).episode.id)
        assertEquals(2, playableItems[2].position)
    }

    @Test
    fun getPlayableItemsFlow_emitsCombinedItemsInPositionOrder(): Unit = runBlocking {
        val playlistItems = listOf(
            PlaylistItemEntity(id = 100L, episodeId = 1L, position = 0),
            PlaylistItemEntity(id = 101L, audiobookId = 20L, position = 1)
        )
        whenever(playlistDao.getAllPlaylistItemsFlow()).thenReturn(flowOf(playlistItems))

        val episode1 = createEpisode(1L, "Episode 1")
        val audiobook = createAudiobook(20L, "Audiobook 20")
        val track = createTrack(201L, 20L, "Chapter 1", 1)

        whenever(playlistDao.getPlaylistItemsWithEpisodesFlow()).thenReturn(
            flowOf(listOf(PlaylistItemWithEpisode(100L, 0, episode1, "Podcast A", "https://artwork/a.jpg")))
        )
        whenever(playlistDao.getPlaylistItemsWithAudiobooksFlow()).thenReturn(
            flowOf(listOf(PlaylistItemWithAudiobook(101L, 1, audiobook)))
        )
        whenever(audiobookDao.getSelectedTracksForAudiobook(20L)).thenReturn(listOf(track))
        whenever(audiobookDao.getCurrentTrackForAudiobook(20L)).thenReturn(track)

        val playableItems = repository.getPlayableItemsFlow().first()

        assertEquals(2, playableItems.size)
        assertTrue(playableItems[0] is PlayableItem.EpisodeItem)
        assertTrue(playableItems[1] is PlayableItem.AudiobookItem)
    }

    @Test
    fun addAudiobookToPlaylist_withTrackIds_insertsMissingTracksAndAppends(): Unit = runBlocking {
        whenever(audiobookDao.getSelectedTrackIdsForAudiobook(10L)).thenReturn(listOf(101L))
        whenever(playlistDao.appendAudiobookIfMissing(10L)).thenReturn(true)

        val result = repository.addAudiobookToPlaylist(10L, listOf(101L, 102L, 103L))

        assertTrue(result)
        verify(audiobookDao).insertPlaylistTracks(argThat {
            size == 2 && map { it.trackId } == listOf(102L, 103L) && all { it.audiobookId == 10L }
        })
        verify(playlistDao).appendAudiobookIfMissing(10L)
    }

    @Test
    fun addAudiobookToPlaylist_withNullTrackIdsAndNoExistingSelection_selectsAllTracks(): Unit = runBlocking {
        whenever(audiobookDao.getSelectedTrackIdsForAudiobook(10L)).thenReturn(emptyList())
        val allTracks = listOf(
            createTrack(101L, 10L, "Ch 1", 1),
            createTrack(102L, 10L, "Ch 2", 2)
        )
        whenever(audiobookDao.getTracksForAudiobook(10L)).thenReturn(allTracks)
        whenever(playlistDao.appendAudiobookIfMissing(10L)).thenReturn(true)

        val result = repository.addAudiobookToPlaylist(10L, null)

        assertTrue(result)
        verify(audiobookDao).insertPlaylistTracks(argThat {
            size == 2 && map { it.trackId } == listOf(101L, 102L)
        })
        verify(playlistDao).appendAudiobookIfMissing(10L)
    }

    @Test
    fun addAudiobookToPlaylist_withNullTrackIdsAndExistingSelection_doesNotOverwriteTracks(): Unit = runBlocking {
        whenever(audiobookDao.getSelectedTrackIdsForAudiobook(10L)).thenReturn(listOf(101L))
        whenever(playlistDao.appendAudiobookIfMissing(10L)).thenReturn(true)

        val result = repository.addAudiobookToPlaylist(10L, null)

        assertTrue(result)
        verify(audiobookDao, never()).insertPlaylistTracks(any())
        verify(playlistDao).appendAudiobookIfMissing(10L)
    }

    @Test
    fun isAudiobookInPlaylist_delegatesToPlaylistDao(): Unit = runBlocking {
        whenever(playlistDao.isAudiobookInPlaylist(42L)).thenReturn(true)
        val result = repository.isAudiobookInPlaylist(42L)
        assertTrue(result)
        verify(playlistDao).isAudiobookInPlaylist(42L)
    }

    @Test
    fun isAudiobookInPlaylist_returnsFalseWhenNotInPlaylist(): Unit = runBlocking {
        whenever(playlistDao.isAudiobookInPlaylist(42L)).thenReturn(false)
        val result = repository.isAudiobookInPlaylist(42L)
        org.junit.Assert.assertFalse(result)
        verify(playlistDao).isAudiobookInPlaylist(42L)
    }

    @Test
    fun syncAudiobookSelection_withTrackIds_clearsOldTracksInsertsNewAndAppendsAudiobook(): Unit = runBlocking {
        whenever(playlistDao.appendAudiobookIfMissing(10L)).thenReturn(true)

        val result = repository.syncAudiobookSelection(10L, listOf(101L, 102L))

        assertTrue(result)
        verify(audiobookDao).clearPlaylistTracksForBook(10L)
        verify(audiobookDao).insertPlaylistTracks(argThat {
            size == 2 && map { it.trackId } == listOf(101L, 102L) && all { it.audiobookId == 10L }
        })
        verify(playlistDao).appendAudiobookIfMissing(10L)
    }

    @Test
    fun syncAudiobookSelection_orderOfOperationsIsClearThenInsertThenAppend(): Unit = runBlocking {
        whenever(playlistDao.appendAudiobookIfMissing(10L)).thenReturn(true)

        val result = repository.syncAudiobookSelection(10L, listOf(101L, 102L))

        assertTrue(result)
        val order = org.mockito.Mockito.inOrder(audiobookDao, playlistDao)
        order.verify(audiobookDao).clearPlaylistTracksForBook(10L)
        order.verify(audiobookDao).insertPlaylistTracks(any())
        order.verify(playlistDao).appendAudiobookIfMissing(10L)
    }

    @Test
    fun syncAudiobookSelection_withEmptyTrackIds_clearsTracksAndRemovesFromPlaylist(): Unit = runBlocking {
        val result = repository.syncAudiobookSelection(10L, emptyList())

        assertTrue(result)
        verify(audiobookDao).clearPlaylistTracksForBook(10L)
        verify(playlistDao).removeAudiobookFromPlaylist(10L)
    }

    @Test
    fun removeAudiobookFromPlaylist_delegatesToPlaylistDao(): Unit = runBlocking {
        repository.removeAudiobookFromPlaylist(15L)
        verify(playlistDao).removeAudiobookFromPlaylist(15L)
    }

    @Test
    fun reorderPlayableItems_delegatesToPlaylistDao(): Unit = runBlocking {
        val items = listOf(
            PlaylistItemEntity(id = 1L, audiobookId = 10L, position = 0),
            PlaylistItemEntity(id = 2L, episodeId = 5L, position = 1)
        )
        repository.reorderPlayableItems(items)
        verify(playlistDao).reorderPlaylist(items)
    }
}
