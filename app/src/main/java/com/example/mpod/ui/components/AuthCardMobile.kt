package com.example.mpod.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mpod.R

@Composable
fun AuthCardMobile(
    title: String,
    submitLabel: String,
    username: String,
    onUsernameChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    usernamePlaceholder: String = "Choose a username",
    passwordPlaceholder: String = "Create a password",
    isSubmitting: Boolean = false,
    errorMessage: String? = null,
    onSubmit: () -> Unit = {}
) {
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    MpodOutlinedSurface(
        modifier = modifier
            .widthIn(max = 320.dp)
            .fillMaxWidth(),
        radius = 10.dp,
        elevation = 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = title,
                fontSize = 24.sp,
                lineHeight = 32.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            LabeledInput(
                label = "Username",
                value = username,
                onValueChange = onUsernameChange,
                placeholder = usernamePlaceholder,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                )
            )
            LabeledInput(
                label = "Password",
                value = password,
                onValueChange = onPasswordChange,
                placeholder = passwordPlaceholder,
                trailingIconRes = if (passwordVisible) R.drawable.ic_view else R.drawable.ic_view_off,
                trailingIconContentDescription = if (passwordVisible) "Hide password" else "Show password",
                onTrailingIconClick = { passwordVisible = !passwordVisible },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                        if (!isSubmitting) onSubmit()
                    }
                ),
                visualTransformation = if (passwordVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                }
            )
            if (!errorMessage.isNullOrBlank()) {
                Text(
                    text = errorMessage,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.error
                )
            }
            MpodButton(
                text = if (isSubmitting) "Please wait..." else submitLabel,
                height = 40.dp,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSubmitting,
                onClick = onSubmit
            )
        }
    }
}
