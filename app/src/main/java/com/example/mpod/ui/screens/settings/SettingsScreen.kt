package com.example.mpod.ui.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.mpod.ui.components.MpodButton
import com.example.mpod.ui.components.MpodInput
import com.example.mpod.ui.components.MpodOutlinedSurface
import com.example.mpod.ui.components.MpodSwitch
import com.example.mpod.ui.components.PageHeader

@Composable
fun SettingsRoute(
    onLogout: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val opmlExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/xml")
    ) { uri ->
        viewModel.exportOpml(uri)
    }
    SettingsScreen(
        state = state,
        onSaveDailyRefreshTime = viewModel::saveDailyRefreshTime,
        onProxyEnabledChange = viewModel::setProxyEnabled,
        onExportOpml = { opmlExportLauncher.launch("mpod-subscriptions.opml") },
        onLogout = onLogout
    )
}

@Composable
fun SettingsScreen(
    state: SettingsUiState = SettingsUiState(),
    onSaveDailyRefreshTime: (String) -> Unit = {},
    onProxyEnabledChange: (Boolean) -> Unit = {},
    onExportOpml: () -> Unit = {},
    onLogout: () -> Unit = {}
) {
    var backendAddress by remember { mutableStateOf("192.168.0.222:5051") }
    var feedRefreshTime by remember { mutableStateOf(state.dailyRefreshTime) }
    var useDarkTheme by remember { mutableStateOf(false) }

    LaunchedEffect(state.dailyRefreshTime) {
        feedRefreshTime = state.dailyRefreshTime
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        PageHeader(title = "Settings")

        if (state.errorMessage != null) {
            Text(
                text = state.errorMessage,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (state.exportMessage != null) {
            Text(
                text = state.exportMessage,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.fillMaxWidth()
            )
        }

        SettingCard(
            title = "Backend address",
            description = "Enter the backend's IP address and port, separated by a colon."
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MpodInput(
                    value = backendAddress,
                    onValueChange = { backendAddress = it },
                    modifier = Modifier.weight(1f)
                )
                MpodButton(
                    text = "Save conf",
                    height = 36.dp,
                    radius = 10.dp,
                    modifier = Modifier.width(100.dp)
                )
            }
        }

        SettingCard(
            title = "Feed daily refresh",
            description = "Feeds are refreshed once per day at a single global time."
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MpodInput(
                        value = feedRefreshTime,
                        onValueChange = { feedRefreshTime = it },
                        modifier = Modifier.weight(1f)
                    )
                MpodButton(
                    text = "Save time",
                    height = 36.dp,
                    radius = 10.dp,
                    modifier = Modifier.width(100.dp),
                    enabled = !state.isSavingRefreshTime && !state.isLoading,
                    onClick = { onSaveDailyRefreshTime(feedRefreshTime) }
                )
                }
                Text(
                    text = state.schedulerStatusText,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }

        SettingCard(
            title = "Use SOCKS5 proxy",
            description = state.proxyStatusText,
            action = {
                MpodSwitch(
                    checked = state.proxyEnabled,
                    onCheckedChange = onProxyEnabledChange,
                    enabled = state.proxyConfigured && !state.isSavingProxy && !state.isLoading
                )
            }
        )

        SettingCard(
            title = "Use dark theme",
            description = "Use this option if it feels more comfortable for you.",
            action = {
                MpodSwitch(checked = useDarkTheme, onCheckedChange = { useDarkTheme = it })
            }
        )

        SettingCard(
            title = "Export OPML",
            description = "Download the current subscription list as an OPML file.",
            action = {
                MpodButton(
                    text = if (state.isExportingOpml) "Exporting" else "Export OPML",
                    height = 32.dp,
                    radius = 6.dp,
                    modifier = Modifier.width(113.dp),
                    enabled = !state.isExportingOpml && !state.isLoading,
                    onClick = onExportOpml
                )
            }
        )

        SettingCard(
            title = "Session",
            description = "End the current browser session",
            action = {
                MpodButton(
                    text = "Log out",
                    primary = false,
                    height = 32.dp,
                    radius = 6.dp,
                    modifier = Modifier.width(113.dp),
                    onClick = onLogout
                )
            }
        )

        Text(
            text = "Current app build: ${state.appBuild ?: "unknown"}",
            fontSize = 14.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun SettingCard(
    title: String,
    description: String? = null,
    descriptionContent: (@Composable () -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
    content: (@Composable () -> Unit)? = null
) {
    MpodOutlinedSurface(
        modifier = Modifier.fillMaxWidth(),
        radius = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = title,
                        fontSize = 16.sp,
                        lineHeight = 24.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    when {
                        descriptionContent != null -> descriptionContent()
                        description != null -> Text(
                            text = description,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (action != null) {
                    Box(contentAlignment = Alignment.CenterEnd) {
                        action()
                    }
                }
            }
            if (content != null) {
                content()
            }
        }
    }
}
