package com.example.mpod.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mpod.R
import com.example.mpod.data.local.preferences.ProxyServerConfig
import com.example.mpod.ui.components.FigmaShadowStyle
import com.example.mpod.ui.components.MpodButton
import com.example.mpod.ui.components.MpodInput
import com.example.mpod.ui.components.SquareIconButton
import com.example.mpod.ui.components.figmaDropShadow

@Composable
fun EditProxyScreen(
    server: ProxyServerConfig?,
    onBack: () -> Unit,
    onUpdateServer: (id: String, name: String, host: String, port: Int, username: String, password: String) -> Result<Any>,
    onDeleteServer: (id: String) -> Unit,
    onSaveSuccess: () -> Unit,
    onDeleteSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (server == null) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SquareIconButton(
                iconRes = R.drawable.ic_huge_arrow_left_02,
                contentDescription = "Back",
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                size = 44.dp,
                iconSize = 20.dp,
                radius = 10.dp,
                onClick = onBack
            )
            Text(
                text = "Proxy server not found",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        return
    }

    var name by remember(server.id) { mutableStateOf(server.name) }
    var addressPort by remember(server.id) { mutableStateOf(server.addressPortDisplay) }
    var username by remember(server.id) { mutableStateOf(server.username) }
    var password by remember(server.id) { mutableStateOf(server.password) }
    var passwordVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Top App Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SquareIconButton(
                iconRes = R.drawable.ic_huge_arrow_left_02,
                contentDescription = "Back",
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                size = 44.dp,
                iconSize = 20.dp,
                radius = 10.dp,
                onClick = onBack
            )
            Text(
                text = "Edit proxy",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        // Form Card
        OutlinedCard(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = CardDefaults.outlinedCardBorder(),
            modifier = Modifier
                .fillMaxWidth()
                .figmaDropShadow(style = FigmaShadowStyle.Xs, radius = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Server name
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Server name",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    MpodInput(
                        value = name,
                        onValueChange = {
                            name = it
                            errorMessage = null
                        },
                        placeholder = "E.g. my proxy",
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Proxy address:port
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Proxy address:port",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    MpodInput(
                        value = addressPort,
                        onValueChange = {
                            addressPort = it
                            errorMessage = null
                        },
                        placeholder = "E.g. 192.168.1.1:1080",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Username
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Username",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    MpodInput(
                        value = username,
                        onValueChange = { username = it },
                        placeholder = "Enter username",
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Password
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Password",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    MpodInput(
                        value = password,
                        onValueChange = { password = it },
                        placeholder = "Enter password",
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIconRes = if (passwordVisible) R.drawable.ic_huge_view_off else R.drawable.ic_huge_view,
                        trailingIconContentDescription = if (passwordVisible) "Hide password" else "Show password",
                        onTrailingIconClick = { passwordVisible = !passwordVisible },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (errorMessage != null) {
                    Text(
                        text = errorMessage ?: "",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Save changes button
                MpodButton(
                    text = "Save changes",
                    primary = true,
                    height = 44.dp,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        val parsed = parseProxyAddressPort(addressPort)
                        if (name.trim().isBlank()) {
                            errorMessage = "Server name cannot be empty."
                            return@MpodButton
                        }
                        if (parsed == null) {
                            errorMessage = "Use address:port (example: 127.0.0.1:1080)."
                            return@MpodButton
                        }
                        val result = onUpdateServer(
                            server.id,
                            name.trim(),
                            parsed.first,
                            parsed.second,
                            username.trim(),
                            password
                        )
                        if (result.isSuccess) {
                            onSaveSuccess()
                        } else {
                            errorMessage = result.exceptionOrNull()?.message ?: "Failed to update proxy."
                        }
                    }
                )

                // Delete server button
                MpodButton(
                    text = "Delete server",
                    primary = false,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.error,
                    height = 44.dp,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        onDeleteServer(server.id)
                        onDeleteSuccess()
                    }
                )
            }
        }
    }
}
