package com.example.mpod.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.mpod.ui.navigation.Screen
import com.example.mpod.ui.theme.MpodTheme

@Preview(
    name = "mpod mobile components / 360",
    widthDp = 360,
    heightDp = 1800,
    showBackground = true
)
@Composable
private fun MpodMobileComponentsPreview() {
    MpodTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            PreviewLabel("PageHeader Mobile")
            PageHeader(
                title = "Now playing",
                showActions = true
            )

            PreviewLabel("Player Mobile")
            PlayerView(modifier = Modifier.fillMaxWidth())

            PreviewLabel("EpisodeRow Mobile")
            EpisodeRow(
                title = "Why store loyalty cards became a UX minefield",
                podcastName = "Decoder Ring",
                duration = "54m",
                isPlaying = true
            )
            EpisodeRow(
                title = "How public transit maps teach invisible habits",
                podcastName = "Decoder Ring",
                duration = "36m",
                date = "31.03.26"
            )

            PreviewLabel("PodcastCard Mobile")
            PodcastCard(
                title = "Decoder Ring",
                description = "Culture stories behind everyday design",
                selected = false,
                onUnsubscribe = {}
            )
            PodcastCard(
                title = "Decoder Ring",
                description = "Culture stories behind everyday design",
                selected = true,
                onUnsubscribe = {}
            )

            PreviewLabel("SettingItem")
            SettingItem(
                title = "Export OPML",
                description = "Download the current subscription list as an OPML file."
            )

            PreviewLabel("FileDropzone")
            FileDropzone(
                modifier = Modifier.fillMaxWidth()
            )

            PreviewLabel("Auth Inputs")
            LabeledInput(
                label = "Username",
                value = "",
                onValueChange = {},
                placeholder = "Choose a username"
            )
            LabeledInput(
                label = "Password",
                value = "",
                onValueChange = {},
                placeholder = "Create a password",
                trailingIconRes = com.example.mpod.R.drawable.ic_view,
                trailingIconContentDescription = "Show password"
            )

            PreviewLabel("Bottom Nav")
            MpodBottomNav(
                currentRoute = Screen.Home.route,
                onNavigate = {}
            )
        }
    }
}

@Composable
private fun PreviewLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
