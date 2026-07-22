package com.example.mpod.data.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionExpiryPolicyTest {
    @Test
    fun `authenticated action 401 invalidates stale session`() {
        assertTrue(
            shouldInvalidateAuthenticatedSession(
                responseCode = 401,
                requestPath = "/api/settings",
                hadSessionCookie = true
            )
        )
    }

    @Test
    fun `invalid login and anonymous 401 do not masquerade as session expiry`() {
        assertFalse(
            shouldInvalidateAuthenticatedSession(
                responseCode = 401,
                requestPath = "/api/auth/login",
                hadSessionCookie = true
            )
        )
        assertFalse(
            shouldInvalidateAuthenticatedSession(
                responseCode = 401,
                requestPath = "/api/settings",
                hadSessionCookie = false
            )
        )
        assertFalse(
            shouldInvalidateAuthenticatedSession(
                responseCode = 500,
                requestPath = "/api/settings",
                hadSessionCookie = true
            )
        )
    }
}
