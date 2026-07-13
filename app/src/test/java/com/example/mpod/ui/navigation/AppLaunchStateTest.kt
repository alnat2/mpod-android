package com.example.mpod.ui.navigation

import com.example.mpod.data.network.model.SessionDto
import org.junit.Assert.assertEquals
import org.junit.Test

class AppLaunchStateTest {
    @Test
    fun restoresAuthenticatedSession() {
        val state = resolveLaunchState(
            responseSuccessful = true,
            session = SessionDto(authenticated = true, setupRequired = false, user = null)
        )

        assertEquals(AppLaunchState.Authenticated, state)
    }

    @Test
    fun setupRequiredWinsBeforeAuthentication() {
        val state = resolveLaunchState(
            responseSuccessful = true,
            session = SessionDto(authenticated = true, setupRequired = true, user = null)
        )

        assertEquals(AppLaunchState.SetupRequired, state)
    }

    @Test
    fun failedOrEmptySessionFallsBackToUnauthenticated() {
        assertEquals(
            AppLaunchState.Unauthenticated,
            resolveLaunchState(responseSuccessful = false, session = null)
        )
        assertEquals(
            AppLaunchState.Unauthenticated,
            resolveLaunchState(
                responseSuccessful = true,
                session = SessionDto(authenticated = false, setupRequired = false, user = null)
            )
        )
    }
}
