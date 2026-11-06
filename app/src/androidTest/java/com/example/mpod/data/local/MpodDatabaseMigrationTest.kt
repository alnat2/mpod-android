package com.example.mpod.data.local

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.mpod.data.local.entity.PendingShareImportEntity
import com.example.mpod.data.local.entity.ShareImportStatus
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Version 1 data survives the first Share-import schema migration. */
@RunWith(AndroidJUnit4::class)
class MpodDatabaseMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MpodDatabase::class.java
    )

    private var database: MpodDatabase? = null

    @After
    fun tearDown() {
        database?.close()
        ApplicationProvider.getApplicationContext<Context>().deleteDatabase(TEST_DATABASE)
    }

    @Test
    fun version1Schema_migratesToVersion2WithoutLosingExistingData() = runBlocking {
        helper.createDatabase(TEST_DATABASE, 1).apply {
            insertVersion1Fixture()
            close()
        }

        helper.runMigrationsAndValidate(
            TEST_DATABASE,
            2,
            true,
            MpodDatabaseMigrations.MIGRATION_1_2
        ).close()

        database = Room.databaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MpodDatabase::class.java,
            TEST_DATABASE
        ).addMigrations(MpodDatabaseMigrations.MIGRATION_1_2).build()

        val podcast = database!!.podcastDao().getPodcastById(PODCAST_ID)
        val episode = database!!.episodeDao().getEpisodeById(EPISODE_ID)
        val playlist = database!!.playlistDao().getAllPlaylistItems()
        val pendingImports = database!!.pendingShareImportDao().getByStatuses(
            listOf(ShareImportStatus.ENQUEUED, ShareImportStatus.COPYING, ShareImportStatus.IMPORTING)
        )

        assertNotNull(podcast)
        assertEquals("Migration fixture", podcast!!.title)
        assertNotNull(episode)
        assertEquals("Fixture episode", episode!!.title)
        assertEquals(PODCAST_ID, episode.podcastId)
        assertEquals(listOf(EPISODE_ID), playlist.map { it.episodeId })
        assertEquals(emptyList<Any>(), pendingImports)

        val pendingShare = PendingShareImportEntity(
            token = "migration-share-token",
            payload = "content://test/audio/1",
            title = "Shared audio",
            status = ShareImportStatus.COPYING,
            sourceUri = "content://test/audio/1",
            copyPath = "/data/user/0/com.prod.mpod/files/share-imports/migration-share-token.audio",
            attempts = 1,
            batchId = "migration-batch",
            fingerprint = "migration-fingerprint",
            progressBytes = 512,
            totalBytes = 1024,
            createdAt = 100L,
            updatedAt = 200L
        )
        database!!.pendingShareImportDao().insertIfMissing(pendingShare)
        database!!.close()
        database = Room.databaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MpodDatabase::class.java,
            TEST_DATABASE
        ).addMigrations(MpodDatabaseMigrations.MIGRATION_1_2).build()
        val restoredShare = database!!.pendingShareImportDao().getByToken(pendingShare.token)
        assertEquals(ShareImportStatus.COPYING, restoredShare?.status)
        assertEquals(pendingShare.copyPath, restoredShare?.copyPath)
        assertEquals(512L, restoredShare?.progressBytes)
        database!!.pendingShareImportDao().updateStatus(
            token = pendingShare.token,
            status = ShareImportStatus.FAILED,
            error = "Import cancelled.",
            attempts = pendingShare.attempts,
            updatedAt = 300L
        )
        val lateProgressUpdate = database!!.pendingShareImportDao().updateCopyProgress(
            token = pendingShare.token,
            status = ShareImportStatus.IMPORTING,
            copyPath = pendingShare.copyPath,
            progressBytes = 1024L,
            totalBytes = 1024L,
            error = null,
            updatedAt = 400L
        )
        assertEquals(0, lateProgressUpdate)
        assertEquals(ShareImportStatus.FAILED, database!!.pendingShareImportDao().getByToken(pendingShare.token)?.status)
    }

    private fun SupportSQLiteDatabase.insertVersion1Fixture() {
        execSQL(
            """INSERT INTO podcasts
               (id, feedUrl, title, description, author, artworkUrl, link, lastBuildDate, lastRefreshedAt)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""",
            arrayOf(PODCAST_ID, "https://test.invalid/feed", "Migration fixture", "", "", "", "", "", 1L)
        )
        execSQL(
            """INSERT INTO episodes
               (id, podcastId, guid, title, description, audioUrl, durationSeconds, publishedAt,
                publishedAtString, isListened, playbackPositionMs, isDownloaded, localFilePath)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
            arrayOf(
                EPISODE_ID, PODCAST_ID, "migration-guid", "Fixture episode", "",
                "https://test.invalid/audio.mp3", 0L, 0L, "", 0, 0L, 0, null
            )
        )
        execSQL(
            "INSERT INTO playlist_items (id, episodeId, position) VALUES (?, ?, ?)",
            arrayOf(1L, EPISODE_ID, 0)
        )
    }

    private companion object {
        const val TEST_DATABASE = "mpod-migration-test"
        const val PODCAST_ID = 101L
        const val EPISODE_ID = 201L
    }
}
