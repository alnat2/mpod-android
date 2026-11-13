package com.example.mpod.data.repository

import android.net.Uri
import com.example.mpod.data.audiobook.AudiobookStorageScanner
import com.example.mpod.data.audiobook.ScanSummary
import com.example.mpod.data.local.dao.AudiobookDao
import com.example.mpod.data.local.entity.AudiobookEntity
import com.example.mpod.data.local.entity.AudiobookTrackEntity
import com.example.mpod.data.local.preferences.FakeAppSettingsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class AudiobookRepositoryTest {

    private lateinit var audiobookDao: AudiobookDao
    private lateinit var scanner: AudiobookStorageScanner
    private lateinit var appSettingsStore: FakeAppSettingsStore
    private lateinit var repository: AudiobookRepository

    @Before
    fun setUp() {
        audiobookDao = mock()
        scanner = mock()
        appSettingsStore = FakeAppSettingsStore()
        repository = AudiobookRepository(audiobookDao, scanner, appSettingsStore)
    }

    @Test
    fun rescanLibrary_whenNoUriConfigured_returnsNull() = runBlocking {
        val result = repository.rescanLibrary()
        assertNull(result)
    }

    @Test
    fun scanDirectory_savesFolderUriAndDelegatesToScanner() = runBlocking {
        val uri = mock<Uri>()
        whenever(uri.toString()).thenReturn("content://audiobooks/tree")
        val summary = ScanSummary(booksScanned = 3, tracksScanned = 15)
        whenever(scanner.scanFolder(any())).thenReturn(summary)

        val result = repository.scanDirectory(uri)

        assertEquals(summary, result)
        assertEquals("content://audiobooks/tree", appSettingsStore.stateFlow.value.audiobooksFolderUri)
        verify(scanner).scanFolder(uri)
    }

    @Test
    fun setSelectedTracks_delegatesToDao() = runBlocking {
        repository.setSelectedTracks(42L, listOf(1L, 2L, 3L))
        verify(audiobookDao).setSelectedTracksForBook(42L, listOf(1L, 2L, 3L))
    }

    @Test
    fun resetAudiobookProgress_delegatesToDao() = runBlocking {
        repository.resetAudiobookProgress(101L)
        verify(audiobookDao).resetAudiobookProgress(101L)
    }
}
