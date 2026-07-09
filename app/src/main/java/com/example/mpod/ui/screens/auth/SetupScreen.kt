package com.example.mpod.ui.screens.auth

import androidx.compose.runtime.*

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
