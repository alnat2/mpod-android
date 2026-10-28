package com.example.mpod.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun UnsubscribeUndoBanner(
    podcastTitle: String,
    secondsRemaining: Int,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
    onDismiss: (() -> Unit)? = null
) {
    MpodStatusBanner(
        message = "The $podcastTitle podcast will be unsubscribed in $secondsRemaining sec",
        type = StatusBannerType.Info,
        actionLabel = "Undo",
        onAction = onUndo,
        onDismiss = onDismiss,
        modifier = modifier
    )
}
