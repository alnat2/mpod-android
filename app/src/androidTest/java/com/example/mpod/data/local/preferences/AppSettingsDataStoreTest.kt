package com.example.mpod.data.local.preferences

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class AppSettingsDataStoreTest {
    private lateinit var file: File
    private lateinit var scope: CoroutineScope

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        file = File(context.cacheDir, "settings-${System.nanoTime()}.preferences_pb")
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    @After
    fun tearDown() {
        scope.cancel()
        file.delete()
    }

    @Test
    fun valuesSurviveScopeShutdownAndReopen() = runBlocking {
        val first = newStore()
        first.setThemeMode("Dark")
        first.setProxySettings(
            enabled = true,
            host = "127.0.0.1",
            port = 1080,
            type = "SOCKS5",
            username = "user",
            password = "secret"
        )
        first.setPlaybackSpeed(1.5f)
        first.setActiveEpisodeId(42L)

        val firstSettings = first.settingsFlow.first()
        scope.cancel()
        val reopenedScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val reopened = AppSettingsDataStore(
                PreferenceDataStoreFactory.create(scope = reopenedScope) { file }
            )
            val settings = reopened.settingsFlow.first()

            assertEquals("Dark", settings.themeMode)
            assertEquals(true, settings.isProxyEnabled)
            assertEquals("127.0.0.1", settings.proxyHost)
            assertEquals(1080, settings.proxyPort)
            assertEquals(1.5f, settings.playbackSpeed)
            assertEquals(42L, settings.activeEpisodeId)
            assertEquals(firstSettings, settings)

            reopened.setActiveEpisodeId(null)
            assertNull(reopened.getActiveEpisodeId())
        } finally {
            reopenedScope.cancel()
        }
    }

    private fun newStore(): AppSettingsDataStore = AppSettingsDataStore(
        PreferenceDataStoreFactory.create(scope = scope) { file }
    )
}
