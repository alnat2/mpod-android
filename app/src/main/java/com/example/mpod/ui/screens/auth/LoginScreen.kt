package com.example.mpod.ui.screens.auth

import androidx.compose.runtime.*

@Composable
fun LoginScreen() {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    AuthScreen(
        hero = "Log in and\nkeep listening",
        cardTitle = "Log in",
        submitLabel = "Log in",
        username = username,
        onUsernameChange = { username = it },
        password = password,
        onPasswordChange = { password = it }
    )
}
