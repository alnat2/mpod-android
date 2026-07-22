package com.example.mpod.data.network.model

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class PlaybackModelsTest {
    private val gson = Gson()

    @Test
    fun parsesPlaybackReadyQueueAndActiveEpisode() {
        val payload = gson.fromJson(
            """
            {
              "queue": [{
                "id": 55,
                "podcastId": 12,
                "title": "Episode",
                "description": "Notes",
                "audioUrl": "https://feed.test/episode.mp3",
                "duration": 2400,
                "downloaded": true,
                "isListened": false,
                "publishedAt": "2026-07-14T10:00:00Z",
                "podcastTitle": "Podcast",
                "podcastImageUrl": "/api/podcasts/12/image",
                "playback": {
                  "episodeId": 55,
                  "positionSeconds": 812,
                  "lastUpdated": "2026-07-14T11:58:00Z"
                }
              }],
              "activePlayback": {
                "episodeId": 55,
                "lastUpdated": "2026-07-14T12:00:00Z"
              }
            }
            """.trimIndent(),
            PlaybackQueueResponse::class.java
        )
        val queue = requireNotNull(payload.queue)

        assertEquals(55, payload.activePlayback?.episodeId)
        assertEquals(1, queue.size)
        assertEquals("Podcast", queue.single().podcastTitle)
        assertEquals(812, queue.single().playback?.positionSeconds)
        assertNotNull(queue.single().podcastImageUrl)
    }

    @Test
    fun parsesQueueWithoutActivePlayback() {
        val payload = gson.fromJson(
            """{"queue":[],"activePlayback":null}""",
            PlaybackQueueResponse::class.java
        )

        assertEquals(emptyList<PlaybackQueueEpisodeDto>(), payload.queue)
        assertEquals(null, payload.activePlayback)
    }

    @Test
    fun missingQueueIsNotSilentlyConvertedToAnEmptyPlaylist() {
        val payload = gson.fromJson(
            """{"activePlayback":null}""",
            PlaybackQueueResponse::class.java
        )

        assertEquals(null, payload.queue)
    }
}
