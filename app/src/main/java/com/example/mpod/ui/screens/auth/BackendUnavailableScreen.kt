package com.example.mpod.ui.screens.auth

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.mpod.ui.components.AuthShellFrameMobile
import com.example.mpod.ui.components.MpodButton
import com.example.mpod.ui.theme.MpodTheme

@Composable
fun BackendUnavailableScreen(
    onRetry: () -> Unit = {}
) {
    AuthShellFrameMobile(hero = "mpod is not reachable") {
        MpodButton(
            text = "Retry",
            height = 40.dp,
            onClick = onRetry
        )
    }
}

@Preview(
    name = "Backend unavailable / 360",
    widthDp = 360,
    heightDp = 800,
    showBackground = true
)
@Composable
private fun BackendUnavailableScreenPreview() {
    MpodTheme {
        BackendUnavailableScreen()
    }
}
