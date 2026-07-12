package com.example.mpod.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mpod.R
import com.example.mpod.ui.theme.MpodTheme
import java.net.URI

enum class AddPodcastMode {
    RssFeedUrl,
    ImportOpmlFile
}

@Composable
fun AddPodcastModal(
    onDismiss: () -> Unit,
    onAddUrl: (String) -> Unit,
    onImportOpml: () -> Unit,
    initialMode: AddPodcastMode = AddPodcastMode.RssFeedUrl,
    isSubmitting: Boolean = false,
    errorMessage: String? = null
) {
    var mode by remember(initialMode) { mutableStateOf(initialMode) }
    var url by remember { mutableStateOf("") }
    var inputError by remember { mutableStateOf<String?>(null) }

    fun submitRssUrl() {
        val trimmedUrl = url.trim()
        inputError = when {
            trimmedUrl.isBlank() -> "Paste RSS feed URL."
            !trimmedUrl.isHttpUrl() -> "Enter a valid http or https RSS feed URL."
            else -> null
        }
        if (inputError == null) {
            onAddUrl(trimmedUrl)
        }
    }

    ModalScreenMobile {
        AddPodcastMobile(
            mode = mode,
            onModeChange = { mode = it },
            url = url,
            onUrlChange = {
                url = it
                inputError = null
            },
            isSubmitting = isSubmitting,
            errorMessage = inputError ?: errorMessage,
            onDismiss = onDismiss,
            onAddUrl = ::submitRssUrl,
            onImportOpml = onImportOpml
        )
    }
}

@Composable
fun AddPodcastMobile(
    mode: AddPodcastMode,
    onModeChange: (AddPodcastMode) -> Unit,
    url: String,
    onUrlChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    isSubmitting: Boolean = false,
    errorMessage: String? = null,
    onDismiss: () -> Unit = {},
    onAddUrl: () -> Unit = {},
    onImportOpml: () -> Unit = {}
) {
    val focusManager = LocalFocusManager.current

    Column(
        modifier = modifier
            .widthIn(max = 320.dp)
            .fillMaxWidth()
            .figmaDropShadow(
                radius = 8.dp,
                offsetY = 8.dp,
                blur = 9.dp
            )
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Add Podcast",
                fontSize = 20.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Icon(
                painter = painterResource(id = R.drawable.ic_multiplication_sign),
                contentDescription = "Close",
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .size(24.dp)
                    .clickable(onClick = onDismiss)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.Start
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ModalTab(
                    text = "RSS Feed URL",
                    selected = mode == AddPodcastMode.RssFeedUrl,
                    onClick = { onModeChange(AddPodcastMode.RssFeedUrl) }
                )
                ModalTab(
                    text = "Import OPML File",
                    selected = mode == AddPodcastMode.ImportOpmlFile,
                    onClick = { onModeChange(AddPodcastMode.ImportOpmlFile) }
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (mode == AddPodcastMode.RssFeedUrl) {
                Text(
                    text = "Paste RSS feed URL",
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                MpodInput(
                    value = url,
                    onValueChange = onUrlChange,
                    placeholder = "https://feeds.example.com/podcast.xml",
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            focusManager.clearFocus()
                            if (!isSubmitting) onAddUrl()
                        }
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                FileDropzone(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(232.dp),
                    onBrowse = onImportOpml
                )
            }
            if (!errorMessage.isNullOrBlank()) {
                Text(
                    text = errorMessage,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MpodButton(
                text = "Cancel",
                primary = false,
                elevation = 0.dp,
                modifier = Modifier.weight(1f),
                onClick = onDismiss
            )
            MpodButton(
                text = when {
                    isSubmitting -> "Please wait..."
                    mode == AddPodcastMode.RssFeedUrl -> "Add Feed"
                    else -> "Import OPML"
                },
                modifier = Modifier.weight(1f),
                enabled = !isSubmitting,
                onClick = if (mode == AddPodcastMode.RssFeedUrl) onAddUrl else onImportOpml
            )
        }
    }
}

@Composable
fun FileDropzone(
    modifier: Modifier = Modifier,
    onBrowse: () -> Unit = {}
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.background)
            .figmaDashedBorder(
                color = MaterialTheme.colorScheme.primary,
                radius = 8.dp
            )
            .clickable(onClick = onBrowse)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Choose OPML file",
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Icon(
                painter = painterResource(id = R.drawable.ic_file_upload),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(80.dp)
            )
            Text(
                text = "Tap to browse files on this device",
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        MpodButton(
            text = "Choose file",
            primary = false,
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.primary,
            elevation = 0.dp,
            height = 36.dp,
            modifier = Modifier.width(140.dp),
            onClick = onBrowse
        )
    }
}

@Preview(
    name = "Add RSS feed modal / 360",
    widthDp = 360,
    heightDp = 800,
    showBackground = true
)
@Composable
private fun AddRssFeedModalPreview() {
    MpodTheme {
        ModalScreenMobile {
            AddPodcastMobile(
                mode = AddPodcastMode.RssFeedUrl,
                onModeChange = {},
                url = "",
                onUrlChange = {}
            )
        }
    }
}

@Preview(
    name = "Import OPML modal / 360",
    widthDp = 360,
    heightDp = 800,
    showBackground = true
)
@Composable
private fun ImportOpmlModalPreview() {
    MpodTheme {
        ModalScreenMobile {
            AddPodcastMobile(
                mode = AddPodcastMode.ImportOpmlFile,
                onModeChange = {},
                url = "",
                onUrlChange = {}
            )
        }
    }
}

@Composable
private fun ModalTab(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .height(28.dp)
            .clip(RoundedCornerShape(8.dp))
            .then(
                if (selected) {
                    Modifier
                        .figmaDropShadow(radius = 8.dp)
                        .background(MaterialTheme.colorScheme.background)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1
        )
    }
}

private fun String.isHttpUrl(): Boolean = runCatching {
    val uri = URI(this)
    uri.scheme in setOf("http", "https") && !uri.host.isNullOrBlank()
}.getOrDefault(false)
