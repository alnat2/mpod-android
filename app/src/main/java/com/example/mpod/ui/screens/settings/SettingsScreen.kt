package com.example.mpod.ui.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.mpod.ui.components.MpodButton
import com.example.mpod.ui.components.MpodInput
import com.example.mpod.ui.components.MpodOutlinedSurface
import com.example.mpod.ui.components.MpodSwitch
import com.example.mpod.ui.components.PageHeader
import com.example.mpod.ui.theme.MpodTheme
import com.example.mpod.ui.theme.ThemeMode

@Composable
fun SettingsRoute(
    themeMode: ThemeMode = ThemeMode.System,
    onThemeModeChange: (ThemeMode) -> Unit = {},
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
        themeMode = themeMode,
        onThemeModeChange = onThemeModeChange,
        onSaveDailyRefreshTime = viewModel::saveDailyRefreshTime,
        onProxyEnabledChange = viewModel::setProxyEnabled,
        onExportOpml = { opmlExportLauncher.launch("mpod-subscriptions.opml") },
        onLogout = onLogout
    )
}

@Composable
fun SettingsScreen(
    state: SettingsUiState = SettingsUiState(),
    themeMode: ThemeMode = ThemeMode.System,
    onThemeModeChange: (ThemeMode) -> Unit = {},
    onSaveDailyRefreshTime: (String) -> Unit = {},
    onProxyEnabledChange: (Boolean) -> Unit = {},
    onExportOpml: () -> Unit = {},
    onLogout: () -> Unit = {}
) {
    var feedRefreshTime by remember { mutableStateOf(state.dailyRefreshTime) }

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

        if (state.isLoading) {
            SettingsStatusCard(message = "Loading settings")
        } else {
            if (state.errorMessage != null) {
                SettingsStatusCard(
                    message = state.errorMessage,
                    isError = true
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
                        SettingsPrimaryButton(
                            text = "Save time",
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
                        enabled = state.proxyConfigured && !state.isSavingProxy && !state.isLoading,
                        contentDescription = "Use SOCKS5 proxy"
                    )
                }
            )

            SettingCard(
                title = "Use dark theme",
                description = "Use this option if it feels more comfortable for you.",
                action = {
                    MpodSwitch(
                        checked = themeMode == ThemeMode.Dark,
                        onCheckedChange = { useDarkTheme ->
                            onThemeModeChange(
                                if (useDarkTheme) ThemeMode.Dark else ThemeMode.System
                            )
                        },
                        contentDescription = "Use dark theme"
                    )
                }
            )

            SettingCard(
                title = "Export OPML",
                description = "Download the current subscription list as an OPML file.",
                action = {
                    SettingsPrimaryButton(
                        text = if (state.isExportingOpml) "Exporting" else "Export OPML",
                        width = 113.dp,
                        height = 32.dp,
                        radius = 6.dp,
                        enabled = !state.isExportingOpml && !state.isLoading,
                        onClick = onExportOpml
                    )
                }
            )

            SettingCard(
                title = "Session",
                description = "End the current app session",
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
                text = "Backend build: ${state.appBuild ?: "unknown"}",
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun SettingsStatusCard(
    message: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false
) {
    MpodOutlinedSurface(modifier = modifier.fillMaxWidth()) {
        Text(
            text = message,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Medium,
            color = if (isError) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
private fun SettingsPrimaryButton(
    text: String,
    width: Dp = 100.dp,
    height: Dp = 36.dp,
    radius: Dp = 10.dp,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .clip(RoundedCornerShape(radius))
            .background(MaterialTheme.colorScheme.primary)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onPrimary,
            maxLines = 1
        )
    }
}

@Preview(
    name = "Settings loading / 360",
    widthDp = 360,
    heightDp = 800,
    showBackground = true
)
@Composable
private fun SettingsLoadingPreview() {
    MpodTheme {
        SettingsScreen(state = SettingsUiState(isLoading = true))
    }
}

@Preview(
    name = "Settings error / 360",
    widthDp = 360,
    heightDp = 800,
    showBackground = true
)
@Composable
private fun SettingsErrorPreview() {
    MpodTheme {
        SettingsScreen(
            state = SettingsUiState(
                errorMessage = "Could not load settings."
            )
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
