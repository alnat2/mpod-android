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

    private val sharedIntentFlow = MutableSharedFlow<Intent>(replay = 1, extraBufferCapacity = 1)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        tryPersistUriPermission(intent)
        if (SharedIntentParser.parse(intent) != null) {
            sharedIntentFlow.tryEmit(intent)
        }

        val initialDestination = Screen.Home.route

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
        tryPersistUriPermission(intent)
        if (SharedIntentParser.parse(intent) != null) {
            sharedIntentFlow.tryEmit(intent)
        }
    }

    private fun tryPersistUriPermission(intent: Intent?) {
        val uri = intent?.data ?: if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(Intent.EXTRA_STREAM, android.net.Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(Intent.EXTRA_STREAM) as? android.net.Uri
        } ?: return

        if (uri.scheme.equals("content", ignoreCase = true)) {
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: SecurityException) {
                // Sender did not set FLAG_GRANT_PERSISTABLE_URI_PERMISSION;
                // transient read grant to the Activity remains valid for process lifetime.
            }
        }
    }
}
