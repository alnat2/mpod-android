package com.example.mpod.data.network

import org.junit.Assert.assertTrue
import org.junit.Test

class UserAgentConsistencyTest {

    @Test
    fun userAgent_containsBrowserTokensToPreventCdnBlocking() {
        val ua = NetworkModule.USER_AGENT
        assertTrue("User-Agent must start with mpoddy/", ua.startsWith("mpoddy/"))
        assertTrue("User-Agent must contain AppleWebKit to pass browser checks", ua.contains("AppleWebKit/537.36"))
        assertTrue("User-Agent must contain Chrome to pass browser checks", ua.contains("Chrome/"))
        assertTrue("User-Agent must contain Mobile Safari to pass browser checks", ua.contains("Mobile Safari/"))
    }
}
