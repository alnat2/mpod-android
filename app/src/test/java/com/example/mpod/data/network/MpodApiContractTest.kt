package com.example.mpod.data.network

import com.example.mpod.data.network.model.CreatePodcastRequest
import com.example.mpod.data.network.model.LoginRequest
import com.example.mpod.data.network.model.EpisodeListenedRequest
import com.example.mpod.data.network.model.PlaylistAddRequest
import com.example.mpod.data.network.model.PlaylistReorderRequest
import com.example.mpod.data.network.model.ActivePlaybackRequest
import com.example.mpod.data.network.model.PlaybackUpdateRequest
import com.example.mpod.data.network.model.SettingsUpdateRequest
import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.ByteArrayInputStream

class MpodApiContractTest {
    private lateinit var server: MockWebServer
    private lateinit var api: MpodApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(GsonConverterFactory.create(Gson()))
            .build()
            .create(MpodApi::class.java)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `session login registration and logout use the auth contract`() = runBlocking {
        repeat(4) { server.enqueue(success()) }

        api.getSession()
        api.login(LoginRequest("listener", "secret"))
        api.register(LoginRequest("owner", "password"))
        api.logout()

        assertRequest("GET", "/api/auth/session")
        assertJsonRequest("POST", "/api/auth/login", "username" to "listener", "password" to "secret")
        assertJsonRequest("POST", "/api/auth/register", "username" to "owner", "password" to "password")
        assertRequest("POST", "/api/auth/logout")
    }

    @Test
    fun `RSS and OPML use the podcast import contracts`() = runBlocking {
        server.enqueue(success())
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"success":true,"imported":2,"skipped":1}"""
            )
        )
        server.enqueue(success())
        val opml = "<opml version=\"2.0\"><body/></opml>".encodeToByteArray()
        val body = LimitedContentRequestBody(
            mediaType = "text/xml".toMediaType(),
            knownLength = opml.size.toLong(),
            openStream = { ByteArrayInputStream(opml) }
        )

        api.createPodcast(CreatePodcastRequest("https://feeds.example.com/show.xml"))
        val importResponse = api.importOpml(
            MultipartBody.Part.createFormData("file", "subscriptions.opml", body)
        )
        api.exportOpml()

        assertJsonRequest(
            "POST",
            "/api/podcasts",
            "rssUrl" to "https://feeds.example.com/show.xml"
        )
        val importRequest = server.takeRequest()
        assertEquals("POST", importRequest.method)
        assertEquals("/api/podcasts/import-opml", importRequest.path)
        assertTrue(importRequest.getHeader("Content-Type").orEmpty().startsWith("multipart/form-data;"))
        assertTrue(importRequest.body.readUtf8().contains("<opml version=\"2.0\"><body/></opml>"))
        assertEquals(2, importResponse.body()?.imported)
        assertEquals(1, importResponse.body()?.skipped)
        assertRequest("GET", "/api/podcasts/export-opml")
    }

    @Test
    fun `settings writes preserve exact nullable patch fields`() = runBlocking {
        repeat(4) { server.enqueue(success()) }

        api.getSettings()
        api.updateSettings(SettingsUpdateRequest(dailyRefreshTime = "04:30"))
        api.updateSettings(SettingsUpdateRequest(proxyEnabled = true))
        api.getProxyStatus()

        assertRequest("GET", "/api/settings")
        assertJsonRequest("PATCH", "/api/settings", "dailyRefreshTime" to "04:30")
        assertJsonRequest("PATCH", "/api/settings", "proxyEnabled" to true)
        assertRequest("GET", "/api/proxy/status")
    }

    @Test
    fun `scheduler status uses the shared refresh job endpoint`() = runBlocking {
        server.enqueue(success())

        api.getJobsStatus()

        assertRequest("GET", "/api/jobs/status")
    }

    @Test
    fun `subscription refresh unsubscribe and mark all use podcast scoped endpoints`() = runBlocking {
        server.enqueue(success())
        server.enqueue(success())
        server.enqueue(success())
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody("""{"success":true,"markedEpisodes":3}""")
        )

        api.getPodcastEpisodes(42)
        api.refreshPodcast(42)
        api.removePodcast(42)
        val result = api.markAllListened(42)

        assertRequest("GET", "/api/podcasts/42/episodes")
        assertRequest("POST", "/api/podcasts/42/refresh")
        assertRequest("DELETE", "/api/podcasts/42")
        assertRequest("POST", "/api/podcasts/42/mark-all-listened")
        assertEquals(3, result.body()?.markedEpisodes)
    }

    @Test
    fun `playlist add remove reorder and reads use their exact contracts`() = runBlocking {
        repeat(4) { server.enqueue(success()) }

        api.getPlaylist()
        api.addToPlaylist(PlaylistAddRequest(episodeId = 7))
        api.removeFromPlaylist(7)
        api.reorderPlaylist(PlaylistReorderRequest(listOf(9, 7, 3)))

        assertRequest("GET", "/api/playlist")
        assertJsonRequest("POST", "/api/playlist", "episodeId" to 7)
        assertRequest("DELETE", "/api/playlist/7")
        val reorder = server.takeRequest()
        assertEquals("PATCH", reorder.method)
        assertEquals("/api/playlist/reorder", reorder.path)
        assertEquals(listOf(9, 7, 3), JsonParser.parseString(reorder.body.readUtf8())
            .asJsonObject["episodeIds"].asJsonArray.map { it.asInt })
    }

    @Test
    fun `episode listened state and download use episode scoped endpoints`() = runBlocking {
        repeat(3) { server.enqueue(success()) }

        api.getEpisode(7)
        api.setEpisodeListened(7, EpisodeListenedRequest(isListened = true))
        api.downloadEpisode(7)

        assertRequest("GET", "/api/episodes/7")
        assertJsonRequest("PATCH", "/api/episodes/7", "isListened" to true)
        assertRequest("POST", "/api/episodes/7/download")
    }

    @Test
    fun `playback queue active progress completion and speed use shared contracts`() = runBlocking {
        repeat(5) { server.enqueue(success()) }

        api.getPlaybackQueue()
        api.setActivePlayback(ActivePlaybackRequest(episodeId = 7))
        api.updatePlayback(playbackRequest(completed = false, didSeek = true))
        api.updatePlayback(playbackRequest(completed = true, didSeek = false))
        api.updateSettings(SettingsUpdateRequest(playbackSpeed = "Speed 1.5x"))

        assertRequest("GET", "/api/playback/queue")
        assertJsonRequest("PUT", "/api/playback/active", "episodeId" to 7)
        assertPlaybackRequest(completed = false, didSeek = true)
        assertPlaybackRequest(completed = true, didSeek = false)
        assertJsonRequest("PATCH", "/api/settings", "playbackSpeed" to "Speed 1.5x")
    }

    private fun success() = MockResponse().setResponseCode(200).setBody("{}")

    private fun assertRequest(method: String, path: String) {
        val request = server.takeRequest()
        assertEquals(method, request.method)
        assertEquals(path, request.path)
    }

    private fun assertJsonRequest(
        method: String,
        path: String,
        vararg fields: Pair<String, Any>
    ) {
        val request = server.takeRequest()
        assertEquals(method, request.method)
        assertEquals(path, request.path)
        val json = JsonParser.parseString(request.body.readUtf8()).asJsonObject
        assertEquals(fields.map { it.first }.toSet(), json.keySet())
        fields.forEach { (name, value) -> assertEquals(value.toString(), json[name].asString) }
    }

    private fun playbackRequest(completed: Boolean, didSeek: Boolean) = PlaybackUpdateRequest(
        episodeId = 7,
        positionSeconds = if (completed) 120 else 42,
        durationSeconds = 120,
        completed = completed,
        didSeek = didSeek,
        clientUpdatedAt = "2026-07-16T12:00:00Z"
    )

    private fun assertPlaybackRequest(completed: Boolean, didSeek: Boolean) {
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/playback", request.path)
        val json = JsonParser.parseString(request.body.readUtf8()).asJsonObject
        assertEquals(7, json["episodeId"].asInt)
        assertEquals(120, json["durationSeconds"].asInt)
        assertEquals(completed, json["completed"].asBoolean)
        assertEquals(didSeek, json["didSeek"].asBoolean)
    }
}
