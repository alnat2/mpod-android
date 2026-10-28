package com.example.mpod

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.example.mpod.data.local.preferences.AppSettingsStore
import com.example.mpod.data.repository.SingleTrackRepository
import com.example.mpod.ui.navigation.AppNavigation
import com.example.mpod.ui.navigation.Screen
import com.example.mpod.ui.theme.MpodTheme
import com.example.mpod.ui.theme.ThemeMode
import com.example.mpod.util.SharedIntentParser
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var appSettingsDataStore: AppSettingsStore
    @Inject lateinit var singleTrackRepository: SingleTrackRepository

    private val sharedIntentFlow = MutableSharedFlow<Intent>(extraBufferCapacity = 1)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val isSharedIntent = handleIntent(intent)
        val initialDestination = if (isSharedIntent) Screen.Home.route else Screen.Subscriptions.route

        setContent {
            val themeMode by remember {
                appSettingsDataStore.settingsFlow
                    .map { ThemeMode.fromStorage(it.themeMode) }
            }.collectAsStateWithLifecycle(initialValue = ThemeMode.System)

            MpodTheme(themeMode = themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(
                        themeMode = themeMode,
                        onThemeModeChange = { nextMode ->
                            lifecycleScope.launch {
                                appSettingsDataStore.setThemeMode(nextMode.storageValue)
                            }
                        },
                        initialDestination = initialDestination,
                        sharedIntentFlow = sharedIntentFlow.asSharedFlow()
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (handleIntent(intent)) {
            sharedIntentFlow.tryEmit(intent)
        }
    }

    private fun handleIntent(intent: Intent?): Boolean {
        val audioData = SharedIntentParser.parse(intent) ?: return false
        lifecycleScope.launch {
            runCatching {
                singleTrackRepository.playSingleTrack(audioData.urlOrUri, audioData.title)
            }.onFailure { error ->
                android.util.Log.e("MainActivity", "Failed to resolve or play shared track: ${audioData.urlOrUri}", error)
            }
        }
        return true
    }
}
