package com.example.mpod.ui.navigation

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.mpod.data.network.AuthSessionInvalidator
import com.example.mpod.data.network.MpodApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class AppLaunchViewModelTest {
    private lateinit var server: MockWebServer
    private lateinit var api: MpodApi
    private lateinit var sessionInvalidator: AuthSessionInvalidator

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        sessionInvalidator = AuthSessionInvalidator()
        api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MpodApi::class.java)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun setupRequiredRegistersOnceAndBecomesAuthenticated() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"authenticated":false,"setupRequired":true,"user":null}""")
        )
        val viewModel = viewModel()

        awaitState(viewModel, AppLaunchState.SetupRequired)
        server.enqueue(MockResponse().setResponseCode(204))
        viewModel.register("owner", "password")

        awaitState(viewModel, AppLaunchState.Authenticated)
        assertEquals("/api/auth/session", server.takeRequest().path)
        val register = server.takeRequest()
        assertEquals("/api/auth/register", register.path)
        assertEquals("POST", register.method)
        val body = JSONObject(register.body.readUtf8())
        assertEquals("owner", body.getString("username"))
        assertEquals("password", body.getString("password"))
    }

    @Test
    fun failedLogoutCanRetryTheStillAuthenticatedSession() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"authenticated":true,"setupRequired":false,"user":null}""")
        )
        val viewModel = viewModel()
        awaitState(viewModel, AppLaunchState.Authenticated)

        server.enqueue(MockResponse().setResponseCode(503))
        viewModel.logout()
        awaitState(viewModel, AppLaunchState.BackendUnavailable)

        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"authenticated":true,"setupRequired":false,"user":null}""")
        )
        viewModel.refreshSession()

        awaitState(viewModel, AppLaunchState.Authenticated)
        assertEquals("/api/auth/session", server.takeRequest().path)
        assertEquals("/api/auth/logout", server.takeRequest().path)
        assertEquals("/api/auth/session", server.takeRequest().path)
    }

    @Test
    fun authenticatedActionExpiryImmediatelyLeavesAuthenticatedNavigation() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"authenticated":true,"setupRequired":false,"user":null}""")
        )
        val viewModel = viewModel()
        awaitState(viewModel, AppLaunchState.Authenticated)

        sessionInvalidator.invalidate()

        awaitState(viewModel, AppLaunchState.Unauthenticated)
    }

    private fun viewModel(): AppLaunchViewModel {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return AppLaunchViewModel(context, api, sessionInvalidator)
    }

    private suspend fun awaitState(
        viewModel: AppLaunchViewModel,
        expected: AppLaunchState
    ) {
        withTimeout(5_000) {
            viewModel.state.first { it == expected }
        }
    }
}
