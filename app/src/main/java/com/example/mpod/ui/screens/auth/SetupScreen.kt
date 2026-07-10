package com.example.mpod.ui.screens.auth

import androidx.compose.runtime.*
import androidx.compose.ui.tooling.preview.Preview
import com.example.mpod.ui.theme.MpodTheme

@Composable
fun SetupScreen() {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    AuthScreen(
        hero = "Create the only account for your podcast library",
        cardTitle = "Create your account",
        submitLabel = "Create account",
        username = username,
        onUsernameChange = { username = it },
        password = password,
        onPasswordChange = { password = it }
    )
}

@Preview(
    name = "Setup screen / 360",
    widthDp = 360,
    heightDp = 800,
    showBackground = true
)
@Composable
private fun SetupScreenPreview() {
    MpodTheme {
        SetupScreen()
    }
}
