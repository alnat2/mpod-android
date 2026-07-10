package com.example.mpod.ui.screens.auth

import androidx.compose.runtime.Composable
import com.example.mpod.ui.components.AuthShellMobile

@Composable
fun AuthScreen(
    hero: String,
    cardTitle: String,
    submitLabel: String,
    username: String,
    onUsernameChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit = {}
) {
    AuthShellMobile(
        hero = hero,
        cardTitle = cardTitle,
        submitLabel = submitLabel,
        username = username,
        onUsernameChange = onUsernameChange,
        password = password,
        onPasswordChange = onPasswordChange,
        onSubmit = onSubmit
    )
}
