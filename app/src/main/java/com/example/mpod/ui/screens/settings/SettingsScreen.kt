package com.example.mpod.ui.screens.settings

import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.mpod.ui.components.LabeledInput
import com.example.mpod.ui.components.MpodOutlinedSurface
import com.example.mpod.ui.components.MpodSwitch
import com.example.mpod.ui.components.PageHeader
import com.example.mpod.ui.theme.ThemeMode

@Composable
fun SettingsRoute(
    themeMode: ThemeMode = ThemeMode.System,
    onThemeModeChange: (ThemeMode) -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val opmlExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/x-opml")
    ) { uri ->
        viewModel.exportOpml(uri)
    }

    SettingsScreen(
        state = state,
        themeMode = themeMode,
        onThemeModeChange = onThemeModeChange,
        onAutoRefreshToggle = viewModel::setAutoRefreshEnabled,
        onSaveDailyRefreshTime = viewModel::saveDailyRefreshTime,
        onProxyToggle = viewModel::setProxyEnabled,
        onSaveProxySettings = viewModel::saveProxySettings,
        onExportOpml = { opmlExportLauncher.launch("mpoddy-subscriptions.opml") }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState = SettingsUiState(),
    themeMode: ThemeMode = ThemeMode.System,
    onThemeModeChange: (ThemeMode) -> Unit = {},
    onAutoRefreshToggle: (Boolean) -> Unit = {},
    onSaveDailyRefreshTime: (String) -> Unit = {},
    onProxyToggle: (Boolean) -> Unit = {},
    onSaveProxySettings: (String, Int, String, String) -> Unit = { _, _, _, _ -> },
    onExportOpml: () -> Unit = {}
) {
    var feedRefreshTime by rememberSaveable { mutableStateOf(state.dailyRefreshTime) }
    var showTimePicker by rememberSaveable { mutableStateOf(false) }

    var proxyAddressPortInput by rememberSaveable {
        mutableStateOf(formatProxyAddressPort(state.proxyHost, state.proxyPort))
    }
    var proxyUserInput by rememberSaveable { mutableStateOf(state.proxyUsername) }
    var proxyPasswordInput by rememberSaveable { mutableStateOf(state.proxyPassword) }
    val context = LocalContext.current

    LaunchedEffect(state.dailyRefreshTime) {
        feedRefreshTime = state.dailyRefreshTime
    }
    LaunchedEffect(state.proxyHost, state.proxyPort) {
        proxyAddressPortInput = formatProxyAddressPort(state.proxyHost, state.proxyPort)
    }
    LaunchedEffect(state.proxyUsername, state.proxyPassword) {
        proxyUserInput = state.proxyUsername
        proxyPasswordInput = state.proxyPassword
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        val headerSubtitle = buildString {
            append(state.lastRefreshHeaderText)
            if (!state.currentIpGeoText.isNullOrBlank()) {
                append("\n")
                append(state.currentIpGeoText)
            }
        }
        PageHeader(
            title = "Settings",
            subtitle = headerSubtitle
        )

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
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Card 1: Auto refresh + Daily refresh time accordion
        SettingCard(
            title = "Auto refresh",
            description = "Turn on for a scheduled update.",
            action = {
                MpodSwitch(
                    checked = state.isAutoRefreshEnabled,
                    onCheckedChange = onAutoRefreshToggle,
                    contentDescription = "Auto refresh"
                )
            },
            content = {
                AnimatedVisibility(
                    visible = state.isAutoRefreshEnabled,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Feed daily refresh",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Feeds are refreshed once per day at a single global time.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            DailyRefreshTimeField(
                                value = feedRefreshTime,
                                enabled = true,
                                onClick = { showTimePicker = true },
                                modifier = Modifier.weight(1f)
                            )
                            SettingsPrimaryButton(
                                text = "Save time",
                                enabled = feedRefreshTime != state.dailyRefreshTime,
                                onClick = { onSaveDailyRefreshTime(feedRefreshTime) }
                            )
                        }
                    }
                }
            }
        )

        // Card 2: SOCKS5 Proxy toggle
        SettingCard(
            title = "Use SOCKS5 proxy",
            description = "Turn on if direct connection update fails.",
            action = {
                MpodSwitch(
                    checked = state.isProxyEnabled,
                    onCheckedChange = onProxyToggle,
                    contentDescription = "Use SOCKS5 proxy"
                )
            }
        )

        // Card 2b: Proxy settings (separate card matching Figma 1264:7739)
        AnimatedVisibility(
            visible = state.isProxyEnabled,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            val parsed = parseProxyAddressPort(proxyAddressPortInput)
            val isSaveEnabled = parsed != null

            SettingCard(
                title = "Proxy settings",
                description = "Fill out to connect to the server",
                content = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        LabeledInput(
                            label = "Proxy address:port",
                            value = proxyAddressPortInput,
                            onValueChange = { proxyAddressPortInput = it },
                            placeholder = "E.g. 192.168.1.1:1080"
                        )

                        LabeledInput(
                            label = "User login",
                            value = proxyUserInput,
                            onValueChange = { proxyUserInput = it },
                            placeholder = "Enter username"
                        )

                        LabeledInput(
                            label = "Password",
                            value = proxyPasswordInput,
                            onValueChange = { proxyPasswordInput = it },
                            placeholder = "Enter password",
                            visualTransformation = PasswordVisualTransformation()
                        )

                        if (state.proxyMessage != null) {
                            Text(
                                text = state.proxyMessage,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        SettingsPrimaryButton(
                            text = "Save settings",
                            width = null,
                            modifier = Modifier.fillMaxWidth(),
                            height = 36.dp,
                            radius = 10.dp,
                            enabled = isSaveEnabled,
                            onClick = {
                                if (parsed != null) {
                                    onSaveProxySettings(
                                        parsed.first,
                                        parsed.second,
                                        proxyUserInput,
                                        proxyPasswordInput
                                    )
                                }
                            }
                        )
                    }
                }
            )
        }

        // Card 3: Theme mode selector
        SettingCard(
            title = "Appearance",
            description = "Choose theme: System follows device setting.",
            content = {
                ThemeModeSelector(
                    currentMode = themeMode,
                    onModeSelected = onThemeModeChange,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        )

        // Card 4: Export OPML
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

        Text(
            text = "Current app build: ${state.appBuild}",
            fontSize = 14.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.fillMaxWidth()
        )
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
private fun SettingsPrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    width: Dp? = 100.dp,
    height: Dp = 36.dp,
    radius: Dp = 10.dp,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .then(if (width != null) Modifier.width(width) else Modifier)
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

@Composable
private fun SettingCard(
    title: String,
    description: String? = null,
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
            verticalArrangement = Arrangement.spacedBy(8.dp)
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
                    if (description != null) {
                        Text(
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

@Composable
private fun ThemeModeSelector(
    currentMode: ThemeMode,
    onModeSelected: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val modes = listOf(
        ThemeMode.System to "System",
        ThemeMode.Light to "Light",
        ThemeMode.Dark to "Dark"
    )
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        for ((mode, label) in modes) {
            val isSelected = currentMode == mode
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.background
                    )
                    .border(
                        1.dp,
                        if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline,
                        RoundedCornerShape(8.dp)
                    )
                    .clickable(
                        role = Role.Button,
                        onClick = { onModeSelected(mode) }
                    )
                    .semantics {
                        contentDescription = "$label theme"
                        role = Role.Button
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    fontSize = 14.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

internal fun parseProxyAddressPort(input: String): Pair<String, Int>? {
    val trimmed = input.trim()
    if (trimmed.isBlank()) return null
    val lastColon = trimmed.lastIndexOf(':')
    return if (lastColon > 0 && lastColon < trimmed.length - 1) {
        val host = trimmed.substring(0, lastColon).trim()
        val port = trimmed.substring(lastColon + 1).trim().toIntOrNull()
        if (host.isNotBlank() && port != null && port in 1..65535) {
            host to port
        } else null
    } else {
        null
    }
}

internal fun formatProxyAddressPort(host: String, port: Int): String {
    return if (host.isNotBlank()) "$host:$port" else ""
}

