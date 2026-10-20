package com.example.mpod.ui.screens.settings

import androidx.compose.foundation.BorderStroke
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
import com.example.mpod.ui.components.FigmaShadowStyle
import com.example.mpod.ui.components.MpodButton
import com.example.mpod.ui.components.MpodInput
import com.example.mpod.ui.components.SquareIconButton
import com.example.mpod.ui.components.figmaDropShadow

@Composable
fun AddProxyScreen(
    onBack: () -> Unit,
    onSaveServer: (name: String, host: String, port: Int, username: String, password: String) -> Result<Any>,
    onSaveSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    var name by remember { mutableStateOf("") }
    var addressPort by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
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
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.primary,
                size = 44.dp,
                iconSize = 24.dp,
                radius = 10.dp,
                shadowStyle = FigmaShadowStyle.Xs,
                onClick = onBack
            )
            Text(
                text = "Add proxy",
                fontSize = 30.sp,
                lineHeight = 36.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        // Form Card (SettingItem card in Figma)
        OutlinedCard(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier
                .fillMaxWidth()
                .figmaDropShadow(style = FigmaShadowStyle.Xs, radius = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Subheader (settings-top)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Proxy settings",
                        fontSize = 16.sp,
                        lineHeight = 24.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Fill out to connect to the server",
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Inputs Column (gap-y-4 = 16dp)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Server name
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Server name",
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
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
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Proxy address:port",
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
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
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Username",
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
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
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Password",
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
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

                    // Save button (height 36dp, radius 10dp, primary orange)
                    MpodButton(
                        text = "Save",
                        primary = true,
                        height = 36.dp,
                        radius = 10.dp,
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
                            val result = onSaveServer(
                                name.trim(),
                                parsed.first,
                                parsed.second,
                                username.trim(),
                                password
                            )
                            if (result.isSuccess) {
                                onSaveSuccess()
                            } else {
                                errorMessage = result.exceptionOrNull()?.message ?: "Failed to save proxy."
                            }
                        }
                    )
                }
            }
        }
    }
}

