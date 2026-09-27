package com.example.mpod.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mpod.data.local.MpodDatabase
import com.example.mpod.data.local.entity.EpisodeEntity
import com.example.mpod.data.local.entity.PlaylistItemEntity
import com.example.mpod.data.local.entity.PodcastEntity
import com.example.mpod.ui.screens.subscriptions.subscriptionUiFlow
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Collections
import java.util.concurrent.Executor

@RunWith(AndroidJUnit4::class)
class SubscriptionsQueryTest {
    private lateinit var db: MpodDatabase
    private val statements = Collections.synchronizedList(mutableListOf<String>())

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), MpodDatabase::class.java)
            .setQueryCallback({ sql, _ -> statements += sql.trim().replace(Regex("\\s+"), " ").uppercase() },
                Executor { it.run() }).build()
    }

    @After fun tearDown() { db.close() }

    @Test fun queryCountForOneAndThirtyPodcastsIsConstantComparedWithLegacyNPlusOne() = runBlocking {
        for (count in listOf(1, 30)) {
            db.podcastDao().deleteAll()
            repeat(count) { i ->
                val id = (i + 1).toLong()
                db.podcastDao().insert(PodcastEntity(id = id, title = "Podcast $i", feedUrl = "feed:$i"))
                db.episodeDao().insertEpisode(episode(id, id))
            }
            statements.clear()
            // Exact former read pattern, measured on the same actual Room/SQLite database.
            combine(db.podcastDao().getAllPodcastsFlow(), db.playlistDao().getPlaylistItemsWithEpisodesFlow()) { pods, _ ->
                pods.map { db.episodeDao().getEpisodesByPodcastId(it.id) }
            }.first()
            val legacy = statements.count { it.startsWith("SELECT * FROM EPISODES WHERE PODCASTID =") }
            assertEquals(count, legacy)
            statements.clear()
            val initial = db.podcastDao().getSubscriptionRowsFlow().first()
            assertEquals(count, initial.size)
            assertEquals(1, subscriptionQueries())
            assertEquals(0, statements.count { it.startsWith("SELECT * FROM EPISODES WHERE PODCASTID =") })
            statements.clear()
            val updated = async(start = CoroutineStart.UNDISPATCHED) {
                db.podcastDao().getSubscriptionRowsFlow().first { rows -> rows.any { it.episode?.title == "Updated" } }
            }
            db.episodeDao().update(episode(1, 1).copy(title = "Updated"))
            withTimeout(5000) { updated.await() }
            // At most the initial read and one invalidation read, independent of count.
            val updateQueries = subscriptionQueries()
            assertTrue("count=$count queries=$updateQueries", updateQueries in 1..2)
            assertEquals(0, statements.count { it.startsWith("SELECT * FROM EPISODES WHERE PODCASTID =") })
            println("R03 Room podcasts=$count: legacy episode queries=$legacy; new initial=1; update=$updateQueries")
        }
    }

    @Test fun liveRoomProjectionDeliversRssMetadataFlagsQueueAndEmptyPodcasts() = runBlocking {
        val zulu = PodcastEntity(id = 1, title = "Zulu", feedUrl = "feed:1")
        val alpha = zulu.copy(id = 2, title = "Alpha", feedUrl = "feed:2")
        db.podcastDao().insert(zulu)
        db.podcastDao().insert(alpha)
        db.episodeDao().insertEpisode(episode(1, 1))
        val results = Channel<List<com.example.mpod.ui.screens.subscriptions.SubscriptionPodcastUi>>(Channel.UNLIMITED)
        val observer = launch {
            db.podcastDao().getSubscriptionRowsFlow().subscriptionUiFlow().collect { results.send(it) }
        }
        suspend fun next(predicate: (List<com.example.mpod.ui.screens.subscriptions.SubscriptionPodcastUi>) -> Boolean) =
            withTimeout(5000) { var value = results.receive(); while (!predicate(value)) value = results.receive(); value }
        try {
            val initial = next { it.size == 2 }
            assertEquals(listOf(2L, 1L), initial.map { it.id })
            assertEquals(0, initial.first().totalEpisodeCount)
            db.episodeDao().insertEpisode(episode(3, 1).copy(publishedAt = 10, title = "RSS new"))
            val rss = next { it.last().totalEpisodeCount == 2 }
            assertEquals(listOf(3L, 1L), rss.last().episodes.map { it.id })
            assertEquals(2, rss.last().unlistenedEpisodeCount)
            db.episodeDao().setListened(3, true)
            next { it.last().unlistenedEpisodeCount == 1 }
            db.episodeDao().updateDownloadState(3, true, "/download/3.mp3")
            next { it.last().episodes.first().downloaded }
            db.playlistDao().insertPlaylistItem(PlaylistItemEntity(episodeId = 3, position = 0))
            next { it.last().episodes.first().inPlaylist }
            db.playlistDao().removeFromPlaylist(3)
            next { !it.last().episodes.first().inPlaylist }
            db.episodeDao().update(episode(3, 1).copy(title = "RSS renamed", description = "Notes", durationSeconds = 90,
                publishedAt = 10, publishedAtString = "Today", isListened = true, isDownloaded = true))
            val metadata = next { it.last().episodes.first().title == "RSS renamed" }.last().episodes.first()
            assertEquals("Notes", metadata.summary)
            assertEquals(90, metadata.durationSeconds)
            assertEquals("Today", metadata.publishedAt)
            db.podcastDao().update(zulu.copy(title = "Beta", description = "Description", artworkUrl = "art"))
            val changed = next { it.last().title == "Beta" }.last()
            assertEquals("Description", changed.description)
            assertEquals("art", changed.imageUrl)
            db.podcastDao().deleteById(1)
            assertEquals(2L, next { it.size == 1 }.single().id)
        } finally { observer.cancel(); observer.join(); results.close() }
    }

    @Test fun positionOnlyWriteRequeriesRoomButKeepsProjectionEqual() = runBlocking {
        db.podcastDao().insert(PodcastEntity(id = 1, title = "Podcast", feedUrl = "feed"))
        db.episodeDao().insertEpisode(episode(1, 1))
        val raw = Channel<List<com.example.mpod.data.local.model.SubscriptionRow>>(Channel.UNLIMITED)
        val observer = launch { db.podcastDao().getSubscriptionRowsFlow().collect { raw.send(it) } }
        try {
            val initial = withTimeout(5000) { raw.receive() }
            statements.clear()
            db.episodeDao().updatePlaybackPosition(1, 5000)
            val afterPosition = withTimeout(5000) { raw.receive() }
            assertEquals(initial, afterPosition)
            assertEquals(1, subscriptionQueries())
        } finally { observer.cancel(); observer.join(); raw.close() }
    }

    private fun subscriptionQueries() = statements.count { it.startsWith("SELECT POD.ID AS PODCAST_ID") }
    private fun episode(id: Long, podcastId: Long) = EpisodeEntity(id = id, podcastId = podcastId,
        guid = "guid:$id", title = "Episode $id", audioUrl = "audio:$id")
}
