package com.example.mpod.data.local

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Baseline for future migrations: version 1 is archived and must reopen without data loss. */
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
    fun exportedVersion1Schema_reopensWithExistingData() {
        helper.createDatabase(TEST_DATABASE, 1).apply {
            insertVersion1Fixture()
            close()
        }

        database = Room.databaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MpodDatabase::class.java,
            TEST_DATABASE
        ).build()

        val podcast = database!!.podcastDao().getPodcastById(PODCAST_ID)
        val episode = database!!.episodeDao().getEpisodeById(EPISODE_ID)
        val playlist = database!!.playlistDao().getAllPlaylistItems()

        assertNotNull(podcast)
        assertEquals("Migration fixture", podcast!!.title)
        assertNotNull(episode)
        assertEquals("Fixture episode", episode!!.title)
        assertEquals(PODCAST_ID, episode.podcastId)
        assertEquals(listOf(EPISODE_ID), playlist.map { it.episodeId })
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
