package com.example.mpod.data.network

import com.example.mpod.data.network.model.CreatePodcastRequest
import com.example.mpod.data.network.model.LoginRequest
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
        repeat(3) { server.enqueue(success()) }
        val opml = "<opml version=\"2.0\"><body/></opml>".encodeToByteArray()
        val body = LimitedContentRequestBody(
            mediaType = "text/xml".toMediaType(),
            knownLength = opml.size.toLong(),
            openStream = { ByteArrayInputStream(opml) }
        )

        api.createPodcast(CreatePodcastRequest("https://feeds.example.com/show.xml"))
        api.importOpml(MultipartBody.Part.createFormData("file", "subscriptions.opml", body))
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
}
