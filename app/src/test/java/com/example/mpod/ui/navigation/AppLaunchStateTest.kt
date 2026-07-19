package com.example.mpod.ui.navigation

import com.example.mpod.data.network.model.SessionDto
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.Response

class AppLaunchStateTest {
    @Test
    fun restoresAuthenticatedSession() {
        val state = resolveLaunchState(
            responseCode = 200,
            session = SessionDto(authenticated = true, setupRequired = false, user = null)
        )

        assertEquals(AppLaunchState.Authenticated, state)
    }

    @Test
    fun setupRequiredWinsBeforeAuthentication() {
        val state = resolveLaunchState(
            responseCode = 200,
            session = SessionDto(authenticated = true, setupRequired = true, user = null)
        )

        assertEquals(AppLaunchState.SetupRequired, state)
    }

    @Test
    fun unauthorizedSessionOpensLogin() {
        assertEquals(
            AppLaunchState.Unauthenticated,
            resolveLaunchState(responseCode = 401, session = null)
        )
    }

    @Test
    fun backendFailureOrEmptySuccessfulResponseIsUnavailable() {
        assertEquals(
            AppLaunchState.BackendUnavailable,
            resolveLaunchState(responseCode = 503, session = null)
        )
        assertEquals(
            AppLaunchState.BackendUnavailable,
            resolveLaunchState(responseCode = 200, session = null)
        )
    }

    @Test
    fun successfulUnauthenticatedSessionOpensLogin() {
        assertEquals(
            AppLaunchState.Unauthenticated,
            resolveLaunchState(
                responseCode = 200,
                session = SessionDto(authenticated = false, setupRequired = false, user = null)
            )
        )
    }

    @Test
    fun transportFailureIsUnavailableAndRetryCanRecover() = runBlocking {
        var calls = 0

        val first = loadLaunchState {
            calls += 1
            error("Backend is offline")
        }
        val second = loadLaunchState {
            calls += 1
            Response.success(
                SessionDto(authenticated = true, setupRequired = false, user = null)
            )
        }

        assertEquals(AppLaunchState.BackendUnavailable, first)
        assertEquals(AppLaunchState.Authenticated, second)
        assertEquals(2, calls)
    }

    @Test
    fun serverFailureIsUnavailable() = runBlocking {
        val state = loadLaunchState {
            Response.error(503, "Backend unavailable".toResponseBody())
        }

        assertEquals(AppLaunchState.BackendUnavailable, state)
    }

    @Test
    fun logoutOutcomesPreserveAuthoritativeSessionRules() {
        assertEquals(LogoutOutcome.RefreshSession, resolveLogoutOutcome(204))
        assertEquals(LogoutOutcome.Unauthenticated, resolveLogoutOutcome(401))
        assertEquals(LogoutOutcome.BackendUnavailable, resolveLogoutOutcome(503))
        assertEquals(LogoutOutcome.BackendUnavailable, resolveLogoutOutcome(null))
    }

    @Test
    fun failedLogoutCanRecoverTheStillAuthenticatedSession() = runBlocking {
        assertEquals(LogoutOutcome.BackendUnavailable, resolveLogoutOutcome(null))

        val recovered = loadLaunchState {
            Response.success(
                SessionDto(authenticated = true, setupRequired = false, user = null)
            )
        }

        assertEquals(AppLaunchState.Authenticated, recovered)
    }
}
