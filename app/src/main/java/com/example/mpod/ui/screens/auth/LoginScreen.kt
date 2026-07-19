package com.example.mpod.ui.screens.auth

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.tooling.preview.Preview
import com.example.mpod.ui.theme.MpodTheme

@Composable
fun LoginScreen(
    isSubmitting: Boolean = false,
    errorMessage: String? = null,
    onSubmit: (username: String, password: String) -> Unit = { _, _ -> }
) {
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var localError by rememberSaveable { mutableStateOf<String?>(null) }

    fun submit() {
        if (username.isBlank() || password.isBlank()) {
            localError = "Enter username and password."
            return
        }
        localError = null
        onSubmit(username, password)
    }

    AuthScreen(
        hero = "Log in and\nkeep listening",
        cardTitle = "Log in",
        submitLabel = "Log in",
        username = username,
        onUsernameChange = {
            username = it
            localError = null
        },
        password = password,
        onPasswordChange = {
            password = it
            localError = null
        },
        isSubmitting = isSubmitting,
        errorMessage = localError ?: errorMessage,
        onSubmit = ::submit
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
