package com.example.mpod

import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import com.example.mpod.share.ShareImportManager
import com.example.mpod.util.SharedIntentParser
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var appSettingsDataStore: AppSettingsStore
    @Inject lateinit var shareImportManager: ShareImportManager
    private var lastShareFingerprint: String? = null
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        handleIncomingIntent(intent, fromCreate = true, savedFingerprint = savedInstanceState?.getString(SHARE_FINGERPRINT_KEY))

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
                            lifecycleScope.launch { appSettingsDataStore.setThemeMode(nextMode.storageValue) }
                        },
                        initialDestination = initialDestination,
                        shareImportManager = shareImportManager
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent, fromCreate = false)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        lastShareFingerprint?.let { outState.putString(SHARE_FINGERPRINT_KEY, it) }
        super.onSaveInstanceState(outState)
    }

    private fun handleIncomingIntent(intent: Intent?, fromCreate: Boolean, savedFingerprint: String? = null) {
        if (intent?.action == ACTION_CANCEL_SHARE_IMPORT) {
            intent.getStringExtra(EXTRA_SHARE_IMPORT_TOKEN)?.let { token ->
                lifecycleScope.launch { shareImportManager.cancelImport(token) }
            }
            return
        }
        val isShare = SharedIntentParser.isShareIntent(intent)
        val isViewImport = intent?.action == Intent.ACTION_VIEW
        if (!isShare && !isViewImport) return
        val parsed = SharedIntentParser.parse(intent)
        if (parsed == null) {
            if (!isShare) return
            val message = if (intent?.action == Intent.ACTION_SEND_MULTIPLE) {
                "Share one audio item at a time."
            } else {
                "This shared item is not a supported audio link or file."
            }
            shareImportManager.announceUnsupportedShare(message)
            return
        }
        val fingerprint = shareImportManager.fingerprint(parsed.urlOrUri)
        lastShareFingerprint = fingerprint
        if (fromCreate && savedFingerprint == fingerprint) return
        val uri = runCatching { android.net.Uri.parse(parsed.urlOrUri) }.getOrNull()
        val sourceUri = uri?.takeIf { it.scheme.equals("content", ignoreCase = true) }?.toString()
        if (sourceUri != null) {
            persistShareReadGrant(uri)
            requestNotificationPermissionForCopy()
        }
        shareImportManager.submitFromActivity(
            payload = parsed.urlOrUri,
            title = parsed.title,
            sourceUri = sourceUri,
            fingerprint = fingerprint,
            suppressExistingPending = fromCreate
        )
    }

    private fun persistShareReadGrant(uri: android.net.Uri) {
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
            // Many share providers grant only temporary access; copy starts immediately below.
        }
    }

    private fun requestNotificationPermissionForCopy() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    companion object {
        const val ACTION_CANCEL_SHARE_IMPORT = "com.example.mpod.CANCEL_SHARE_IMPORT"
        const val EXTRA_SHARE_IMPORT_TOKEN = "share_import_token"
        private const val SHARE_FINGERPRINT_KEY = "share_import_fingerprint"
    }
}
