package com.example.mpod.ui.screens.settings

import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.mpod.ui.components.MpodButton
import com.example.mpod.ui.components.MpodOutlinedSurface
import com.example.mpod.ui.components.MpodSwitch
import com.example.mpod.ui.components.PageHeader
import com.example.mpod.ui.theme.MpodTheme
import com.example.mpod.ui.theme.ThemeMode
import com.example.mpod.ui.theme.isDark

@Composable
fun SettingsRoute(
    themeMode: ThemeMode = ThemeMode.System,
    onThemeModeChange: (ThemeMode) -> Unit = {},
    onLogout: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.onResume()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val opmlExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/x-opml")
    ) { uri ->
        viewModel.exportOpml(uri)
    }
    SettingsScreen(
        state = state,
        installedAppBuildInfo = currentInstalledAppBuildInfo(),
        themeMode = themeMode,
        onThemeModeChange = onThemeModeChange,
        onSaveDailyRefreshTime = viewModel::saveDailyRefreshTime,
        onProxyEnabledChange = viewModel::setProxyEnabled,
        onExportOpml = { opmlExportLauncher.launch("mpod-subscriptions.opml") },
        onLogout = onLogout
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState = SettingsUiState(),
    installedAppBuildInfo: InstalledAppBuildInfo = InstalledAppBuildInfo.Unknown,
    themeMode: ThemeMode = ThemeMode.System,
    onThemeModeChange: (ThemeMode) -> Unit = {},
    onSaveDailyRefreshTime: (String) -> Unit = {},
    onProxyEnabledChange: (Boolean) -> Unit = {},
    onExportOpml: () -> Unit = {},
    onLogout: () -> Unit = {}
) {
    var feedRefreshTime by rememberSaveable { mutableStateOf(state.dailyRefreshTime) }
    var showTimePicker by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current

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
                if (state.isRefreshLoading) {
                    SettingsSectionStatus(message = "Loading refresh settings…")
                } else if (state.refreshErrorMessage != null) {
                    SettingsSectionStatus(
                        message = state.refreshErrorMessage,
                        isError = true
                    )
                }
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DailyRefreshTimeField(
                            value = if (state.hasConfirmedSettings) feedRefreshTime else "—",
                            enabled = state.hasConfirmedSettings &&
                                !state.isSavingRefreshTime && !state.isRefreshLoading,
                            onClick = { showTimePicker = true },
                            modifier = Modifier.weight(1f)
                        )
                        SettingsPrimaryButton(
                            text = "Save time",
                            enabled = state.hasConfirmedSettings &&
                                feedRefreshTime != state.dailyRefreshTime &&
                                !state.isSavingRefreshTime && !state.isRefreshLoading,
                            onClick = { onSaveDailyRefreshTime(feedRefreshTime) }
                        )
                    }
                    if (!state.isRefreshLoading && state.refreshErrorMessage == null) {
                        Text(
                            text = state.schedulerStatusText,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }

        SettingCard(
            title = "Use SOCKS5 proxy",
            descriptionContent = {
                when {
                    state.isProxyLoading -> SettingsSectionStatus("Loading proxy status…")
                    state.proxyErrorMessage != null -> SettingsSectionStatus(
                        state.proxyErrorMessage,
                        isError = true
                    )
                    else -> SettingsSectionStatus(state.proxyStatusText)
                }
            },
            action = {
                if (!state.isProxyLoading) {
                    MpodSwitch(
                        checked = state.proxyEnabled,
                        onCheckedChange = onProxyEnabledChange,
                        enabled = state.hasConfirmedSettings && state.proxyConfigured &&
                            !state.isSavingProxy,
                        contentDescription = "Use SOCKS5 proxy"
                    )
                }
            }
        )

        SettingCard(
                title = "Use dark theme",
                description = "Use this option if it feels more comfortable for you.",
                action = {
                    MpodSwitch(
                        checked = themeMode.isDark(isSystemInDarkTheme()),
                        onCheckedChange = { useDarkTheme ->
                            onThemeModeChange(
                                if (useDarkTheme) ThemeMode.Dark else ThemeMode.Light
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
                        enabled = !state.isExportingOpml,
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

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = "Current app build: ${installedAppBuildInfo.versionName} " +
                    "(${installedAppBuildInfo.versionCode}) · ${installedAppBuildInfo.environment}",
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
                text = "Package: ${installedAppBuildInfo.applicationId}",
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
                text = "Server: ${installedAppBuildInfo.backendAddress} · " +
                    "Backend: ${state.appBuild ?: "unknown"}",
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }

    if (showTimePicker) {
        val initialTime = parseDailyRefreshTime(feedRefreshTime)
        val timePickerState = rememberTimePickerState(
            initialHour = initialTime.first,
            initialMinute = initialTime.second,
            is24Hour = DateFormat.is24HourFormat(context)
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        feedRefreshTime = formatDailyRefreshTime(
                            hour = timePickerState.hour,
                            minute = timePickerState.minute
                        )
                        showTimePicker = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text("Cancel")
                }
            },
            text = {
                TimePicker(state = timePickerState)
            }
        )
    }
}

@Composable
private fun DailyRefreshTimeField(
    value: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.background)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = "Daily refresh time"
                role = Role.Button
            }
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = value,
            fontSize = 16.sp,
            lineHeight = 24.sp,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

internal fun parseDailyRefreshTime(value: String): Pair<Int, Int> {
    val parts = value.split(':')
    val hour = parts.getOrNull(0)?.toIntOrNull()
    val minute = parts.getOrNull(1)?.toIntOrNull()
    return if (parts.size == 2 && hour in 0..23 && minute in 0..59) {
        requireNotNull(hour) to requireNotNull(minute)
    } else {
        3 to 0
    }
}

internal fun formatDailyRefreshTime(hour: Int, minute: Int): String {
    return "%02d:%02d".format(hour, minute)
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
private fun SettingsSectionStatus(
    message: String,
    isError: Boolean = false
) {
    Text(
        text = message,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = if (isError) MaterialTheme.colorScheme.error
        else MaterialTheme.colorScheme.onSurfaceVariant
    )
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
            .background(
                if (enabled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.secondaryContainer
            )
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Medium,
            color = if (enabled) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSecondaryContainer,
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
