package com.example.mpod.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.mpod.data.local.entity.AudiobookEntity
import com.example.mpod.data.local.entity.AudiobookPlaylistTrackEntity
import com.example.mpod.data.local.entity.AudiobookTrackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AudiobookDao {

    // --- Audiobooks ---

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAudiobook(audiobook: AudiobookEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAudiobooks(audiobooks: List<AudiobookEntity>): List<Long>

    @Update
    suspend fun updateAudiobook(audiobook: AudiobookEntity)

    @Transaction
    suspend fun upsertAudiobook(audiobook: AudiobookEntity): Long {
        val id = insertAudiobook(audiobook)
        if (id != -1L) return id
        val existing = getAudiobookByFolderUri(audiobook.folderUri)
            ?: error("Audiobook with folderUri ${audiobook.folderUri} must exist on conflict")
        updateAudiobook(audiobook.copy(id = existing.id))
        return existing.id
    }

    @Delete
    suspend fun deleteAudiobook(audiobook: AudiobookEntity)

    @Query("DELETE FROM audiobooks WHERE id = :audiobookId")
    suspend fun deleteAudiobookById(audiobookId: Long)

    @Query("SELECT * FROM audiobooks WHERE id = :audiobookId")
    suspend fun getAudiobookById(audiobookId: Long): AudiobookEntity?

    @Query("SELECT * FROM audiobooks WHERE id = :audiobookId")
    fun getAudiobookByIdFlow(audiobookId: Long): Flow<AudiobookEntity?>

    @Query("SELECT * FROM audiobooks WHERE folderUri = :folderUri")
    suspend fun getAudiobookByFolderUri(folderUri: String): AudiobookEntity?

    @Query("SELECT * FROM audiobooks ORDER BY title ASC")
    suspend fun getAllAudiobooks(): List<AudiobookEntity>

    @Query("SELECT * FROM audiobooks ORDER BY title ASC")
    fun getAllAudiobooksFlow(): Flow<List<AudiobookEntity>>

    @Query("SELECT COUNT(*) FROM audiobooks")
    suspend fun getAudiobookCount(): Int

    // --- Tracks ---

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTrack(track: AudiobookTrackEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTracks(tracks: List<AudiobookTrackEntity>): List<Long>

    @Update
    suspend fun updateTrack(track: AudiobookTrackEntity)

    @Query("SELECT * FROM audiobook_tracks WHERE audiobookId = :audiobookId AND fileUri = :fileUri")
    suspend fun getTrackByFileUri(audiobookId: Long, fileUri: String): AudiobookTrackEntity?

    @Transaction
    suspend fun upsertTrack(track: AudiobookTrackEntity): Long {
        val id = insertTrack(track)
        if (id != -1L) return id
        val existing = getTrackByFileUri(track.audiobookId, track.fileUri)
            ?: error("Track with fileUri ${track.fileUri} must exist on conflict")
        updateTrack(track.copy(id = existing.id))
        return existing.id
    }

    @Query("UPDATE audiobook_tracks SET playbackPositionMs = :positionMs, isListened = :isListened WHERE id = :trackId")
    suspend fun updateTrackProgress(trackId: Long, positionMs: Long, isListened: Boolean)

    @Query("UPDATE audiobook_tracks SET isAvailable = :isAvailable WHERE id = :trackId")
    suspend fun updateTrackAvailability(trackId: Long, isAvailable: Boolean)

    @Query("SELECT * FROM audiobook_tracks WHERE id = :trackId")
    suspend fun getTrackById(trackId: Long): AudiobookTrackEntity?

    @Query("SELECT * FROM audiobook_tracks WHERE audiobookId = :audiobookId ORDER BY trackNumber ASC")
    suspend fun getTracksForAudiobook(audiobookId: Long): List<AudiobookTrackEntity>

    @Query("SELECT * FROM audiobook_tracks WHERE audiobookId = :audiobookId ORDER BY trackNumber ASC")
    fun getTracksForAudiobookFlow(audiobookId: Long): Flow<List<AudiobookTrackEntity>>

    @Query("DELETE FROM audiobook_tracks WHERE audiobookId = :audiobookId")
    suspend fun deleteTracksForAudiobook(audiobookId: Long)

    // --- Playlist Tracks (Selected chapters) ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistTrack(playlistTrack: AudiobookPlaylistTrackEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistTracks(playlistTracks: List<AudiobookPlaylistTrackEntity>)

    @Query("DELETE FROM audiobook_playlist_tracks WHERE audiobookId = :audiobookId AND trackId = :trackId")
    suspend fun removePlaylistTrack(audiobookId: Long, trackId: Long)

    @Query("DELETE FROM audiobook_playlist_tracks WHERE audiobookId = :audiobookId")
    suspend fun clearPlaylistTracksForBook(audiobookId: Long)

    @Query("SELECT trackId FROM audiobook_playlist_tracks WHERE audiobookId = :audiobookId")
    suspend fun getSelectedTrackIdsForAudiobook(audiobookId: Long): List<Long>

    @Query("""
        SELECT t.* FROM audiobook_tracks t
        INNER JOIN audiobook_playlist_tracks pt ON t.id = pt.trackId
        WHERE pt.audiobookId = :audiobookId
        ORDER BY t.trackNumber ASC
    """)
    suspend fun getSelectedTracksForAudiobook(audiobookId: Long): List<AudiobookTrackEntity>

    @Query("""
        SELECT t.* FROM audiobook_tracks t
        INNER JOIN audiobook_playlist_tracks pt ON t.id = pt.trackId
        WHERE pt.audiobookId = :audiobookId
        ORDER BY t.trackNumber ASC
    """)
    fun getSelectedTracksForAudiobookFlow(audiobookId: Long): Flow<List<AudiobookTrackEntity>>

    @Transaction
    suspend fun setSelectedTracksForBook(audiobookId: Long, trackIds: List<Long>) {
        clearPlaylistTracksForBook(audiobookId)
        val now = System.currentTimeMillis()
        val entries = trackIds.map { AudiobookPlaylistTrackEntity(audiobookId = audiobookId, trackId = it, addedAt = now) }
        insertPlaylistTracks(entries)
    }

    // Invariant: currentTrack = первая незаслушанная выбранная глава, или первая выбранная глава, если все прослушаны
    @Query("""
        SELECT t.* FROM audiobook_tracks t
        INNER JOIN audiobook_playlist_tracks pt ON t.id = pt.trackId
        WHERE pt.audiobookId = :audiobookId AND t.isListened = 0
        ORDER BY t.trackNumber ASC
        LIMIT 1
    """)
    suspend fun getFirstUnlistenedSelectedTrack(audiobookId: Long): AudiobookTrackEntity?

    @Query("""
        SELECT t.* FROM audiobook_tracks t
        INNER JOIN audiobook_playlist_tracks pt ON t.id = pt.trackId
        WHERE pt.audiobookId = :audiobookId
        ORDER BY t.trackNumber ASC
        LIMIT 1
    """)
    suspend fun getFirstSelectedTrack(audiobookId: Long): AudiobookTrackEntity?

    @Transaction
    suspend fun getCurrentTrackForAudiobook(audiobookId: Long): AudiobookTrackEntity? {
        return getFirstUnlistenedSelectedTrack(audiobookId) ?: getFirstSelectedTrack(audiobookId)
    }

    // --- Atomic Progress Reset (ABK-10) ---

    @Query("UPDATE audiobook_tracks SET playbackPositionMs = 0, isListened = 0 WHERE audiobookId = :audiobookId")
    suspend fun resetAudiobookProgress(audiobookId: Long)
}
