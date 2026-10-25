package com.example.mpod.data.local.preferences

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AppSettingsDataStoreUnitTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var prefsFile: File
    private lateinit var job: CompletableJob
    private lateinit var scope: CoroutineScope

    @Before
    fun setUp() {
        prefsFile = tempFolder.newFile("test_settings.preferences_pb")
        job = SupervisorJob()
        scope = CoroutineScope(job + Dispatchers.IO)
    }

    @After
    fun tearDown() {
        job.cancel()
    }

    private fun createStore(customScope: CoroutineScope = scope): AppSettingsDataStore {
        val dataStore = PreferenceDataStoreFactory.create(scope = customScope) { prefsFile }
        return AppSettingsDataStore(dataStore)
    }

    @Test
    fun addProxyServer_preservesExistingServers_andPersistsAcrossReopen() = runTest {
        val store1 = createStore()

        val server1 = ProxyServerConfig(
            id = "srv-1",
            name = "First Server",
            host = "10.0.0.1",
            port = 1080
        )
        val server2 = ProxyServerConfig(
            id = "srv-2",
            name = "Second Server",
            host = "10.0.0.2",
            port = 8080
        )

        // Add first server
        store1.addProxyServer(server1)
        val afterFirst = store1.settingsFlow.first()
        assertEquals(1, afterFirst.proxyServers.size)
        assertEquals("First Server", afterFirst.proxyServers[0].name)
        assertEquals("srv-1", afterFirst.selectedProxyServerId)
        assertEquals(server1, afterFirst.activeProxyServer)

        // Add second server -> must preserve first server!
        store1.addProxyServer(server2)
        val afterSecond = store1.settingsFlow.first()
        assertEquals("Both servers must be preserved in proxyServers", 2, afterSecond.proxyServers.size)
        assertEquals("First Server", afterSecond.proxyServers[0].name)
        assertEquals("Second Server", afterSecond.proxyServers[1].name)
        // First server remains active
        assertEquals("srv-1", afterSecond.selectedProxyServerId)
        assertEquals(server1, afterSecond.activeProxyServer)

        // Cancel store1 scope and wait for cancellation to fully complete
        job.cancel()
        job.join()

        // Reopen store on the same file with a new scope to verify persistence
        val newJob = SupervisorJob()
        val newScope = CoroutineScope(newJob + Dispatchers.IO)
        try {
            val store2 = createStore(newScope)
            val reloaded = store2.settingsFlow.first()
            assertEquals("Both servers must survive reload", 2, reloaded.proxyServers.size)
            assertEquals("First Server", reloaded.proxyServers[0].name)
            assertEquals("Second Server", reloaded.proxyServers[1].name)
            assertEquals("srv-1", reloaded.selectedProxyServerId)
            assertEquals(server1, reloaded.activeProxyServer)
        } finally {
            newJob.cancel()
            newJob.join()
        }
    }
}
