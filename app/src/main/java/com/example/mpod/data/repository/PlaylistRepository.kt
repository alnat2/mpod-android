package com.example.mpod.data.repository

import androidx.room.withTransaction
import com.example.mpod.data.local.MpodDatabase
import com.example.mpod.data.local.dao.AudiobookDao
import com.example.mpod.data.local.dao.PlaylistDao
import com.example.mpod.data.local.entity.AudiobookPlaylistTrackEntity
import com.example.mpod.data.local.entity.PlaylistItemEntity
import com.example.mpod.data.local.model.PlayableItem
import com.example.mpod.data.local.model.PlaylistItemWithEpisode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

class PlaylistRepository(
    private val playlistDao: PlaylistDao,
    private val audiobookDao: AudiobookDao? = null,
    private val database: MpodDatabase? = null
) {
    private suspend fun <T> runInTransaction(block: suspend () -> T): T {
        val db = database
        return if (db != null) {
            db.withTransaction { block() }
        } else {
            block()
        }
    }
    fun getPlaylistFlow(): Flow<List<PlaylistItemWithEpisode>> =
        playlistDao.getPlaylistItemsWithEpisodesFlow()

    suspend fun getPlaylistItems(): List<PlaylistItemWithEpisode> = withContext(Dispatchers.IO) {
        playlistDao.getPlaylistItemsWithEpisodes()
    }

    fun getPlayableItemsFlow(): Flow<List<PlayableItem>> =
        combine(
            playlistDao.getAllPlaylistItemsFlow(),
            playlistDao.getPlaylistItemsWithEpisodesFlow(),
            playlistDao.getPlaylistItemsWithAudiobooksFlow(),
            audiobookDao?.getPlaylistAudiobookTracksFlow() ?: flowOf(emptyList())
        ) { allItems, episodes, audiobooks, _ ->
            val episodeMap = episodes.associateBy { it.playlistItemId }
            val audiobookMap = audiobooks.associateBy { it.playlistItemId }

            allItems.mapNotNull { item ->
                when {
                    item.episodeId != null -> {
                        episodeMap[item.id]?.let { epItem ->
                            PlayableItem.EpisodeItem(
                                playlistItemId = item.id,
                                position = item.position,
                                episode = epItem.episode,
                                podcastTitle = epItem.podcastTitle,
                                podcastArtworkUrl = epItem.podcastArtworkUrl
                            )
                        }
                    }
                    item.audiobookId != null -> {
                        audiobookMap[item.id]?.let { abkItem ->
                            val selectedTracks = audiobookDao?.getSelectedTracksForAudiobook(abkItem.audiobook.id) ?: emptyList()
                            val currentTrack = audiobookDao?.getCurrentTrackForAudiobook(abkItem.audiobook.id)
                            PlayableItem.AudiobookItem(
                                playlistItemId = item.id,
                                position = item.position,
                                audiobook = abkItem.audiobook,
                                selectedTracks = selectedTracks,
                                currentTrack = currentTrack
                            )
                        }
                    }
                    else -> null
                }
            }
        }

    suspend fun getPlayableItems(): List<PlayableItem> = withContext(Dispatchers.IO) {
        val allItems = playlistDao.getAllPlaylistItems()
        if (allItems.isEmpty()) return@withContext emptyList()

        val episodes = playlistDao.getPlaylistItemsWithEpisodes().associateBy { it.playlistItemId }
        val audiobooks = playlistDao.getPlaylistItemsWithAudiobooks().associateBy { it.playlistItemId }

        allItems.mapNotNull { item ->
            when {
                item.episodeId != null -> {
                    episodes[item.id]?.let { epItem ->
                        PlayableItem.EpisodeItem(
                            playlistItemId = item.id,
                            position = item.position,
                            episode = epItem.episode,
                            podcastTitle = epItem.podcastTitle,
                            podcastArtworkUrl = epItem.podcastArtworkUrl
                        )
                    }
                }
                item.audiobookId != null -> {
                    audiobooks[item.id]?.let { abkItem ->
                        val selectedTracks = audiobookDao?.getSelectedTracksForAudiobook(abkItem.audiobook.id) ?: emptyList()
                        val currentTrack = audiobookDao?.getCurrentTrackForAudiobook(abkItem.audiobook.id)
                        PlayableItem.AudiobookItem(
                            playlistItemId = item.id,
                            position = item.position,
                            audiobook = abkItem.audiobook,
                            selectedTracks = selectedTracks,
                            currentTrack = currentTrack
                        )
                    }
                }
                else -> null
            }
        }
    }

    suspend fun addEpisodeToPlaylist(episodeId: Long) = withContext(Dispatchers.IO) {
        playlistDao.appendEpisodeIfMissing(episodeId)
    }

    suspend fun isAudiobookInPlaylist(audiobookId: Long): Boolean = withContext(Dispatchers.IO) {
        playlistDao.isAudiobookInPlaylist(audiobookId)
    }

    suspend fun syncAudiobookSelection(audiobookId: Long, trackIds: List<Long>): Boolean = withContext(Dispatchers.IO) {
        runInTransaction {
            val dao = audiobookDao
            if (trackIds.isNotEmpty()) {
                dao?.clearPlaylistTracksForBook(audiobookId)
                val now = System.currentTimeMillis()
                val entries = trackIds.map {
                    AudiobookPlaylistTrackEntity(audiobookId = audiobookId, trackId = it, addedAt = now)
                }
                dao?.insertPlaylistTracks(entries)
                playlistDao.appendAudiobookIfMissing(audiobookId)
                true
            } else {
                dao?.clearPlaylistTracksForBook(audiobookId)
                playlistDao.removeAudiobookFromPlaylist(audiobookId)
                true
            }
        }
    }

    suspend fun addAudiobookToPlaylist(audiobookId: Long, trackIds: List<Long>? = null): Boolean = withContext(Dispatchers.IO) {
        runInTransaction {
            val now = System.currentTimeMillis()
            if (!trackIds.isNullOrEmpty()) {
                val existing = audiobookDao?.getSelectedTrackIdsForAudiobook(audiobookId) ?: emptyList()
                val existingSet = existing.toSet()
                val toInsert = trackIds.filter { it !in existingSet }.map {
                    AudiobookPlaylistTrackEntity(audiobookId = audiobookId, trackId = it, addedAt = now)
                }
                if (toInsert.isNotEmpty()) {
                    audiobookDao?.insertPlaylistTracks(toInsert)
                }
            } else {
                val existing = audiobookDao?.getSelectedTrackIdsForAudiobook(audiobookId) ?: emptyList()
                if (existing.isEmpty()) {
                    val allTracks = audiobookDao?.getTracksForAudiobook(audiobookId) ?: emptyList()
                    val entries = allTracks.map {
                        AudiobookPlaylistTrackEntity(audiobookId = audiobookId, trackId = it.id, addedAt = now)
                    }
                    if (entries.isNotEmpty()) {
                        audiobookDao?.insertPlaylistTracks(entries)
                    }
                }
            }
            val appended = playlistDao.appendAudiobookIfMissing(audiobookId)
            appended
        }
    }

    suspend fun removeFromPlaylist(episodeId: Long) = withContext(Dispatchers.IO) {
        playlistDao.removeFromPlaylist(episodeId)
    }

    suspend fun removeAudiobookFromPlaylist(audiobookId: Long) = withContext(Dispatchers.IO) {
        playlistDao.removeAudiobookFromPlaylist(audiobookId)
    }

    suspend fun reorderPlaylist(reorderedEpisodeIds: List<Long>) = withContext(Dispatchers.IO) {
        val items = reorderedEpisodeIds.mapIndexed { index, epId ->
            PlaylistItemEntity(episodeId = epId, position = index)
        }
        if (items.isNotEmpty()) {
            playlistDao.reorderPlaylist(items)
        } else {
            playlistDao.clearPlaylist()
        }
    }

    suspend fun reorderPlayableItems(items: List<PlaylistItemEntity>) = withContext(Dispatchers.IO) {
        playlistDao.reorderPlaylist(items)
    }

    suspend fun clearPlaylist() = withContext(Dispatchers.IO) {
        playlistDao.clearPlaylist()
    }
}
