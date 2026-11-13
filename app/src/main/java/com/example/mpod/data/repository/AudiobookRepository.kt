package com.example.mpod.data.repository

import android.net.Uri
import com.example.mpod.data.audiobook.AudiobookStorageScanner
import com.example.mpod.data.audiobook.ScanSummary
import com.example.mpod.data.local.dao.AudiobookDao
import com.example.mpod.data.local.entity.AudiobookEntity
import com.example.mpod.data.local.entity.AudiobookTrackEntity
import com.example.mpod.data.local.preferences.AppSettingsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudiobookRepository @Inject constructor(
    private val audiobookDao: AudiobookDao,
    private val scanner: AudiobookStorageScanner,
    private val appSettingsStore: AppSettingsStore
) {
    val audiobooksFlow: Flow<List<AudiobookEntity>> = audiobookDao.getAllAudiobooksFlow()

    suspend fun getAllAudiobooks(): List<AudiobookEntity> = audiobookDao.getAllAudiobooks()

    suspend fun getAudiobookById(id: Long): AudiobookEntity? = audiobookDao.getAudiobookById(id)

    fun getAudiobookByIdFlow(id: Long): Flow<AudiobookEntity?> = audiobookDao.getAudiobookByIdFlow(id)

    suspend fun getTracksForAudiobook(id: Long): List<AudiobookTrackEntity> =
        audiobookDao.getTracksForAudiobook(id)

    fun getTracksForAudiobookFlow(id: Long): Flow<List<AudiobookTrackEntity>> =
        audiobookDao.getTracksForAudiobookFlow(id)

    suspend fun getSelectedTracksForAudiobook(id: Long): List<AudiobookTrackEntity> =
        audiobookDao.getSelectedTracksForAudiobook(id)

    fun getSelectedTracksForAudiobookFlow(id: Long): Flow<List<AudiobookTrackEntity>> =
        audiobookDao.getSelectedTracksForAudiobookFlow(id)

    suspend fun setSelectedTracks(audiobookId: Long, trackIds: List<Long>) {
        audiobookDao.setSelectedTracksForBook(audiobookId, trackIds)
    }

    suspend fun scanDirectory(treeUri: Uri): ScanSummary {
        appSettingsStore.setAudiobooksFolderUri(treeUri.toString())
        return scanner.scanFolder(treeUri)
    }

    suspend fun rescanLibrary(): ScanSummary? {
        val folderUriString = appSettingsStore.settingsFlow.first().audiobooksFolderUri ?: return null
        val treeUri = Uri.parse(folderUriString)
        return scanner.scanFolder(treeUri)
    }

    suspend fun scanSingleAudioFile(fileUri: Uri): AudiobookEntity? {
        return scanner.scanSingleAudioFile(fileUri)
    }

    suspend fun resetAudiobookProgress(audiobookId: Long) {
        audiobookDao.resetAudiobookProgress(audiobookId)
    }
}
