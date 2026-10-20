package com.example.mpod.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingItem(
    title: String,
    description: String? = null,
    modifier: Modifier = Modifier,
    descriptionColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    border: BorderStroke? = null,
    showAction: Boolean = true,
    action: (@Composable () -> Unit)? = null,
    content: (@Composable () -> Unit)? = null
) {
    val effectiveAction: (@Composable () -> Unit)? = when {
        !showAction -> null
        action != null -> action
        else -> {
            {
                MpodButton(
                    text = title,
                    radius = 6.dp,
                    height = 32.dp
                )
            }
        }
    }

    androidx.compose.material3.Card(
        modifier = modifier
            .fillMaxWidth()
            .figmaDropShadow(style = FigmaShadowStyle.Xs, radius = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = border,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            ListItem(
                headlineContent = {
                    Text(
                        text = title,
                        fontSize = 16.sp,
                        lineHeight = 24.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                supportingContent = if (description != null) {
                    {
                        Text(
                            text = description,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            fontWeight = FontWeight.Normal,
                            color = descriptionColor
                        )
                    }
                } else null,
                trailingContent = effectiveAction,
                colors = ListItemDefaults.colors(
                    containerColor = Color.Transparent
                )
            )

            if (content != null) {
                content()
            }
        }
    }
}

