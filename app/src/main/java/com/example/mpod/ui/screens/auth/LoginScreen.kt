package com.example.mpod.ui.screens.auth

import androidx.compose.runtime.*
import androidx.compose.ui.tooling.preview.Preview
import com.example.mpod.ui.theme.MpodTheme

@Composable
fun LoginScreen(
    onSubmit: (username: String, password: String) -> Unit = { _, _ -> }
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    AuthScreen(
        hero = "Log in and\nkeep listening",
        cardTitle = "Log in",
        submitLabel = "Log in",
        username = username,
        onUsernameChange = { username = it },
        password = password,
        onPasswordChange = { password = it },
        onSubmit = { onSubmit(username, password) }
    )
}

@Preview(
    name = "Login screen / 360",
    widthDp = 360,
    heightDp = 800,
    showBackground = true
)
@Composable
private fun LoginScreenPreview() {
    MpodTheme {
        LoginScreen()
    }
}
