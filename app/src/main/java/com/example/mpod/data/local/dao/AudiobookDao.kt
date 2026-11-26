package com.example.mpod.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.example.mpod.data.local.entity.AudiobookEntity
import com.example.mpod.data.local.entity.AudiobookPlaylistTrackEntity
import com.example.mpod.data.local.entity.AudiobookTrackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AudiobookDao {

    // --- Audiobooks ---

    @Upsert
    suspend fun upsertAudiobook(audiobook: AudiobookEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAudiobook(audiobook: AudiobookEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAudiobooks(audiobooks: List<AudiobookEntity>): List<Long>

    @Update
    suspend fun updateAudiobook(audiobook: AudiobookEntity)

    @Delete
    suspend fun deleteAudiobook(audiobook: AudiobookEntity)

    @Query("DELETE FROM audiobooks WHERE id = :audiobookId")
    suspend fun deleteAudiobookById(audiobookId: Long)

    @Query("DELETE FROM audiobooks")
    suspend fun deleteAllAudiobooks()

    @Query("DELETE FROM audiobooks WHERE folderUri NOT IN (:retainedFolderUris)")
    suspend fun deleteAudiobooksNotIn(retainedFolderUris: List<String>)

    @Query("DELETE FROM audiobooks WHERE id IN (:audiobookIds)")
    suspend fun deleteAudiobooksByIds(audiobookIds: List<Long>)

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

    @Upsert
    suspend fun upsertTrack(track: AudiobookTrackEntity): Long

    @Upsert
    suspend fun upsertTracks(tracks: List<AudiobookTrackEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTrack(track: AudiobookTrackEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTracks(tracks: List<AudiobookTrackEntity>): List<Long>

    @Update
    suspend fun updateTrack(track: AudiobookTrackEntity)

    @Update
    suspend fun updateTracks(tracks: List<AudiobookTrackEntity>)

    @Query("SELECT * FROM audiobook_tracks WHERE audiobookId = :audiobookId AND fileUri = :fileUri")
    suspend fun getTrackByFileUri(audiobookId: Long, fileUri: String): AudiobookTrackEntity?

    @Query("UPDATE audiobook_tracks SET playbackPositionMs = :positionMs, isListened = :isListened WHERE id = :trackId")
    suspend fun updateTrackProgress(trackId: Long, positionMs: Long, isListened: Boolean)

    @Query("UPDATE audiobook_tracks SET isAvailable = :isAvailable WHERE id = :trackId")
    suspend fun updateTrackAvailability(trackId: Long, isAvailable: Boolean)

    @Query("SELECT * FROM audiobook_tracks WHERE id = :trackId")
    suspend fun getTrackById(trackId: Long): AudiobookTrackEntity?

    @Query("SELECT * FROM audiobook_tracks WHERE audiobookId = :audiobookId AND isAvailable = 1 ORDER BY trackNumber ASC")
    suspend fun getTracksForAudiobook(audiobookId: Long): List<AudiobookTrackEntity>

    @Query("SELECT * FROM audiobook_tracks WHERE audiobookId = :audiobookId AND isAvailable = 1 ORDER BY trackNumber ASC")
    fun getTracksForAudiobookFlow(audiobookId: Long): Flow<List<AudiobookTrackEntity>>

    @Query("SELECT * FROM audiobook_tracks WHERE audiobookId = :audiobookId ORDER BY trackNumber ASC")
    suspend fun getAllTracksForAudiobook(audiobookId: Long): List<AudiobookTrackEntity>

    @Query("DELETE FROM audiobook_tracks WHERE id IN (:trackIds)")
    suspend fun deleteTracksByIds(trackIds: List<Long>)

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
        WHERE pt.audiobookId = :audiobookId AND t.isAvailable = 1
        ORDER BY t.trackNumber ASC
    """)
    suspend fun getSelectedTracksForAudiobook(audiobookId: Long): List<AudiobookTrackEntity>

    @Query("""
        SELECT t.* FROM audiobook_tracks t
        INNER JOIN audiobook_playlist_tracks pt ON t.id = pt.trackId
        WHERE pt.audiobookId = :audiobookId AND t.isAvailable = 1
        ORDER BY t.trackNumber ASC
    """)
    fun getSelectedTracksForAudiobookFlow(audiobookId: Long): Flow<List<AudiobookTrackEntity>>

    @Query("""
        SELECT t.* FROM audiobook_tracks t
        INNER JOIN audiobook_playlist_tracks pt ON t.id = pt.trackId
        INNER JOIN playlist_items p ON pt.audiobookId = p.audiobookId
        WHERE t.isAvailable = 1
    """)
    fun getPlaylistAudiobookTracksFlow(): Flow<List<AudiobookTrackEntity>>

    @Query("""
        DELETE FROM playlist_items
        WHERE audiobookId IS NOT NULL
        AND NOT EXISTS (
            SELECT 1 FROM audiobook_playlist_tracks pt
            WHERE pt.audiobookId = playlist_items.audiobookId
        )
    """)
    suspend fun deleteEmptyAudiobookPlaylistItems()

    // Invariant: currentTrack = первая незаслушанная выбранная глава, или первая выбранная глава, если все прослушаны
    @Query("""
        SELECT t.* FROM audiobook_tracks t
        INNER JOIN audiobook_playlist_tracks pt ON t.id = pt.trackId
        WHERE pt.audiobookId = :audiobookId AND t.isListened = 0 AND t.isAvailable = 1
        ORDER BY t.trackNumber ASC
        LIMIT 1
    """)
    suspend fun getFirstUnlistenedSelectedTrack(audiobookId: Long): AudiobookTrackEntity?

    @Query("""
        SELECT t.* FROM audiobook_tracks t
        INNER JOIN audiobook_playlist_tracks pt ON t.id = pt.trackId
        WHERE pt.audiobookId = :audiobookId AND t.isAvailable = 1
        ORDER BY t.trackNumber ASC
        LIMIT 1
    """)
    suspend fun getFirstSelectedTrack(audiobookId: Long): AudiobookTrackEntity?

    suspend fun getCurrentTrackForAudiobook(audiobookId: Long): AudiobookTrackEntity? {
        return getFirstUnlistenedSelectedTrack(audiobookId) ?: getFirstSelectedTrack(audiobookId)
    }

    // --- Atomic Progress Reset (ABK-10) ---

    @Query("UPDATE audiobook_tracks SET playbackPositionMs = 0, isListened = 0 WHERE audiobookId = :audiobookId")
    suspend fun resetAudiobookProgress(audiobookId: Long)
}
