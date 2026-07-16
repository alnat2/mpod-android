package com.example.mpod.ui.screens.settings

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsSaveConsistencyTest {
    @Test
    fun confirmedValueSurvivesFailedStatusReload() = runBlocking {
        val confirmed = SettingsUiState(
            dailyRefreshTime = "04:30",
            isSavingRefreshTime = false
        )

        val result = reloadAfterConfirmedSave(
            confirmedState = confirmed,
            reloadErrorMessage = "Refresh time was saved, but status could not be refreshed."
        ) {
            error("Scheduler is unavailable")
        }

        assertEquals("04:30", result.dailyRefreshTime)
        assertEquals(false, result.isSavingRefreshTime)
        assertEquals(
            "Refresh time was saved, but status could not be refreshed.",
            result.errorMessage
        )
    }

    @Test
    fun successfulReloadReplacesConfirmedFallbackState() = runBlocking {
        val loaded = SettingsUiState(
            dailyRefreshTime = "04:30",
            proxyEnabled = true,
            proxyStatusText = "Proxy is on"
        )

        val result = reloadAfterConfirmedSave(
            confirmedState = SettingsUiState(proxyEnabled = true),
            reloadErrorMessage = "Proxy setting was saved, but status could not be refreshed."
        ) {
            loaded
        }

        assertEquals(loaded, result)
    }
}
