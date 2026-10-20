package com.example.mpod.ui.screens.settings

import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mpod.R
import com.example.mpod.ui.components.FigmaShadowStyle
import com.example.mpod.ui.components.LabeledInput
import com.example.mpod.ui.components.PageHeader
import com.example.mpod.ui.components.SettingItem
import com.example.mpod.ui.components.SquareIconButton
import com.example.mpod.ui.components.figmaDropShadow
import com.example.mpod.ui.theme.ThemeMode

@Composable
fun SettingsRoute(
    themeMode: ThemeMode = ThemeMode.System,
    onThemeModeChange: (ThemeMode) -> Unit = {},
    onNavigateToAddProxy: () -> Unit = {},
    onNavigateToEditProxy: (String) -> Unit = {},
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
        onNavigateToAddProxy = onNavigateToAddProxy,
        onNavigateToEditProxy = onNavigateToEditProxy,
        onSelectProxyServer = viewModel::selectProxyServer,
        onDismissProxyNotification = viewModel::dismissTopProxyNotification,
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
    onNavigateToAddProxy: () -> Unit = {},
    onNavigateToEditProxy: (String) -> Unit = {},
    onSelectProxyServer: (String) -> Unit = {},
    onDismissProxyNotification: () -> Unit = {},
    onExportOpml: () -> Unit = {}
) {
    var feedRefreshTime by rememberSaveable { mutableStateOf(state.dailyRefreshTime) }
    var showTimePicker by rememberSaveable { mutableStateOf(false) }
    var showChooseProxySheet by rememberSaveable { mutableStateOf(false) }

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
            .padding(top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        AnimatedVisibility(
            visible = state.topProxyNotification != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            state.topProxyNotification?.let { notification ->
                ProxyErrorTopBanner(
                    notification = notification,
                    onEdit = { serverId ->
                        onDismissProxyNotification()
                        onNavigateToEditProxy(serverId)
                    },
                    onDismiss = onDismissProxyNotification
                )
            }
        }

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

        // Card 1: Auto refresh toggle + Feed daily refresh (combined in one card per Figma 1562:13260)
        SettingItem(
            title = "Auto refresh",
            description = "Turn on for a scheduled update.",
            action = {
                Switch(
                    checked = state.isAutoRefreshEnabled,
                    onCheckedChange = onAutoRefreshToggle,
                    colors = settingsSwitchColors()
                )
            },
            content = {
                AnimatedVisibility(
                    visible = state.isAutoRefreshEnabled,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outline
                        )
                        ListItem(
                            headlineContent = {
                                Text(
                                    text = "Feed daily refresh",
                                    fontSize = 16.sp,
                                    lineHeight = 24.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            },
                            supportingContent = {
                                Text(
                                    text = "Feeds are refreshed once per day at a single global time.",
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp,
                                    fontWeight = FontWeight.Normal,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            colors = ListItemDefaults.colors(
                                containerColor = Color.Transparent
                            )
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .padding(bottom = 16.dp),
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
                                width = 116.dp,
                                radius = 8.dp,
                                enabled = feedRefreshTime != state.dailyRefreshTime,
                                onClick = { onSaveDailyRefreshTime(feedRefreshTime) }
                            )
                        }
                    }
                }
            }
        )

        // Card 2: SOCKS5 Proxy toggle & multi-proxy management
        SettingItem(
            title = "Use SOCKS5 proxy",
            description = "Turn on if direct connection update fails.",
            action = {
                Switch(
                    checked = state.isProxyEnabled,
                    onCheckedChange = onProxyToggle,
                    colors = settingsSwitchColors()
                )
            },
            content = {
                AnimatedVisibility(
                    visible = state.isProxyEnabled,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outline
                        )
                        val isNoProxy = state.proxyServers.isEmpty()
                        val activeServerName = state.activeProxyServer?.name
                            ?: state.activeProxyServer?.host
                            ?: "None"

                        ListItem(
                            headlineContent = {
                                Text(
                                    text = "Choose proxy server",
                                    fontSize = 16.sp,
                                    lineHeight = 24.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            },
                            supportingContent = {
                                if (isNoProxy) {
                                    Text(
                                        text = "No proxy set up. Add at least one",
                                        fontSize = 14.sp,
                                        lineHeight = 20.sp,
                                        fontWeight = FontWeight.Normal,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                } else if (state.isProxyChecking) {
                                    Text(
                                        text = "Connecting...",
                                        fontSize = 14.sp,
                                        lineHeight = 20.sp,
                                        fontWeight = FontWeight.Normal,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else if (state.activeProxyError != null) {
                                    Text(
                                        text = state.activeProxyError,
                                        fontSize = 14.sp,
                                        lineHeight = 20.sp,
                                        fontWeight = FontWeight.Normal,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                } else {
                                    Text(
                                        text = "Current - $activeServerName",
                                        fontSize = 14.sp,
                                        lineHeight = 20.sp,
                                        fontWeight = FontWeight.Normal,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            trailingContent = {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_huge_arrow_down_01),
                                    contentDescription = "Choose proxy server",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            colors = ListItemDefaults.colors(
                                containerColor = Color.Transparent
                            ),
                            modifier = Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(bounded = true),
                                role = Role.Button,
                                onClick = {
                                    if (isNoProxy) {
                                        onNavigateToAddProxy()
                                    } else {
                                        showChooseProxySheet = true
                                    }
                                }
                            )
                        )

                        SettingsPrimaryButton(
                            text = "Add new proxy",
                            width = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .padding(bottom = 16.dp),
                            height = 32.dp,
                            radius = 8.dp,
                            onClick = onNavigateToAddProxy
                        )
                    }
                }
            }
        )

        if (showChooseProxySheet) {
            ChooseProxyBottomSheet(
                proxyServers = state.proxyServers,
                activeServerId = state.selectedProxyServerId,
                onSelectServer = onSelectProxyServer,
                onNavigateToEditProxy = onNavigateToEditProxy,
                onDismiss = { showChooseProxySheet = false }
            )
        }

        // Card 3: Use dark theme
        SettingItem(
            title = "Use dark theme",
            description = "Use this option if it feels more comfortable for you.",
            action = {
                Switch(
                    checked = themeMode == ThemeMode.Dark,
                    onCheckedChange = { checked ->
                        onThemeModeChange(if (checked) ThemeMode.Dark else ThemeMode.Light)
                    },
                    colors = settingsSwitchColors()
                )
            }
        )

        // Card 4: Export OPML
        SettingItem(
            title = "Export OPML",
            description = "Download the current subscription list as an OPML file.",
            action = {
                SettingsPrimaryButton(
                    text = if (state.isExportingOpml) "Exporting" else "Export OPML",
                    width = 113.dp,
                    height = 36.dp,
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
    androidx.compose.material3.Card(
        modifier = modifier
            .fillMaxWidth()
            .figmaDropShadow(style = FigmaShadowStyle.Xs, radius = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = if (isError) BorderStroke(1.dp, MaterialTheme.colorScheme.error) else null,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
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
internal fun settingsSwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
    checkedTrackColor = MaterialTheme.colorScheme.primary,
    checkedBorderColor = Color.Transparent,
    uncheckedThumbColor = Color(0xFFF7FBFF),
    uncheckedTrackColor = Color(0xFF353A3F),
    uncheckedBorderColor = Color.Transparent
)

@Composable
private fun SettingsPrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    width: Dp? = 100.dp,
    height: Dp = 36.dp,
    radius: Dp = 10.dp,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(radius)
    Box(
        modifier = modifier
            .then(if (width != null) Modifier.width(width) else Modifier)
            .height(height)
            .figmaDropShadow(style = FigmaShadowStyle.Xs, radius = radius)
            .clip(shape)
            .background(
                if (enabled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.secondaryContainer
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true),
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            )
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

internal fun validateProxyCredentials(username: String, password: String): String? {
    val trimmedUser = username.trim()
    if (trimmedUser.isNotBlank() && password.isBlank()) {
        return "Password is required when username is provided."
    }
    if (password.isNotBlank() && trimmedUser.isBlank()) {
        return "Username is required when password is provided."
    }
    return null
}

@Composable
private fun ProxyErrorTopBanner(
    notification: ProxyErrorNotification,
    onEdit: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    androidx.compose.material3.Card(
        modifier = modifier
            .fillMaxWidth()
            .figmaDropShadow(style = FigmaShadowStyle.Sm, radius = 12.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_huge_alert_circle),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = notification.message,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.error,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            SettingsPrimaryButton(
                text = "Edit",
                width = 54.dp,
                height = 30.dp,
                radius = 6.dp,
                onClick = { onEdit(notification.serverId) }
            )
            SquareIconButton(
                iconRes = R.drawable.ic_huge_multiplication_sign,
                contentDescription = "Dismiss",
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                size = 28.dp,
                iconSize = 14.dp,
                elevation = 0.dp,
                onClick = onDismiss
            )
        }
    }
}

