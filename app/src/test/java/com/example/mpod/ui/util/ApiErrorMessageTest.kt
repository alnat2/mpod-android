package com.example.mpod.ui.util

import org.junit.Assert.assertEquals
import org.junit.Test

class ApiErrorMessageTest {
    @Test
    fun extractsNestedBackendErrorMessage() {
        assertEquals(
            "Failed to download episode",
            apiErrorMessage(
                rawBody = """{"error":{"code":"DOWNLOAD_FAILED","message":"Failed to download episode"}}""",
                defaultMessage = "Download failed"
            )
        )
    }

    @Test
    fun extractsTopLevelMessage() {
        assertEquals(
            "Episode not found",
            apiErrorMessage("""{"message":"Episode not found"}""", "Download failed")
        )
    }

    @Test
    fun keepsPlainTextError() {
        assertEquals(
            "Gateway timeout",
            apiErrorMessage("Gateway timeout", "Download failed")
        )
    }

    @Test
    fun usesDefaultForEmptyResponse() {
        assertEquals(
            "Download failed",
            apiErrorMessage(null, "Download failed")
        )
    }
}
