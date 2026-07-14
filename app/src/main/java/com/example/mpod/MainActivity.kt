package com.example.mpod

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import com.example.mpod.ui.theme.MpodTheme
import com.example.mpod.ui.theme.ThemeMode

import dagger.hilt.android.AndroidEntryPoint
import com.example.mpod.ui.navigation.AppNavigation

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val preferences = remember { getSharedPreferences(THEME_PREFERENCES, MODE_PRIVATE) }
            var themeMode by remember {
                mutableStateOf(ThemeMode.fromStorage(preferences.getString(THEME_MODE_KEY, null)))
            }
            DisposableEffect(preferences) {
                val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                    if (key == THEME_MODE_KEY) {
                        themeMode = ThemeMode.fromStorage(preferences.getString(key, null))
                    }
                }
                preferences.registerOnSharedPreferenceChangeListener(listener)
                onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
            }

            MpodTheme(themeMode = themeMode) {
                AppNavigation(
                    themeMode = themeMode,
                    onThemeModeChange = { nextMode ->
                        themeMode = nextMode
                        preferences.edit { putString(THEME_MODE_KEY, nextMode.storageValue) }
                    }
                )
            }
        }
    }

    companion object {
        private const val THEME_PREFERENCES = "mpod_appearance"
        private const val THEME_MODE_KEY = "theme_mode"
    }
}
