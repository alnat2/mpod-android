package com.example.mpod

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.lifecycleScope
import com.example.mpod.data.local.preferences.AppSettingsDataStore
import com.example.mpod.playback.SmartListeningManager
import com.example.mpod.ui.navigation.AppNavigation
import com.example.mpod.ui.theme.MpodTheme
import com.example.mpod.ui.theme.ThemeMode
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var smartListeningManager: SmartListeningManager
    @Inject lateinit var appSettingsDataStore: AppSettingsDataStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        smartListeningManager.startObserving()
        enableEdgeToEdge()

        setContent {
            val themeMode by remember {
                appSettingsDataStore.settingsFlow
                    .map { ThemeMode.fromStorage(it.themeMode) }
            }.collectAsState(initialValue = ThemeMode.System)

            MpodTheme(themeMode = themeMode) {
                AppNavigation(
                    themeMode = themeMode,
                    onThemeModeChange = { nextMode ->
                        lifecycleScope.launch {
                            appSettingsDataStore.setThemeMode(nextMode.storageValue)
                        }
                    }
                )
            }
        }
    }
}
