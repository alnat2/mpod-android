package com.example.mpod.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mpod.R
import com.example.mpod.ui.navigation.Screen

@Composable
fun PageHeader(
    title: String,
    subtitle: String? = null,
    showActions: Boolean = false,
    onRefreshClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    fontSize = 16.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (showActions) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SquareIconButton(
                    iconRes = R.drawable.ic_refresh_dot,
                    contentDescription = "Refresh",
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    elevation = 0.dp,
                    onClick = onRefreshClick
                )
                SquareIconButton(
                    iconRes = R.drawable.ic_view,
                    contentDescription = "View",
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    elevation = 1.dp
                )
            }
        }
    }
}

@Composable
fun SquareIconButton(
    iconRes: Int,
    contentDescription: String,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.background,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    size: Dp = 40.dp,
    iconSize: Dp = 16.dp,
    radius: Dp = 8.dp,
    border: BorderStroke? = null,
    elevation: Dp = 1.dp,
    onClick: (() -> Unit)? = null
) {
    val clickModifier = if (onClick == null) {
        Modifier
    } else {
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick
        )
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .then(if (elevation > 0.dp) Modifier.figmaDropShadow(radius = radius) else Modifier)
            .clip(RoundedCornerShape(radius))
            .background(containerColor)
            .then(if (border == null) Modifier else Modifier.border(border, RoundedCornerShape(radius)))
            .then(clickModifier)
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = contentDescription,
            modifier = Modifier.size(iconSize),
            tint = contentColor
        )
    }
}

@Composable
fun MpodButton(
    text: String,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    outlined: Boolean = false,
    height: Dp = 32.dp,
    radius: Dp = 8.dp,
    iconRes: Int? = null,
    containerColor: Color? = null,
    contentColor: Color? = null,
    elevation: Dp = 1.dp,
    enabled: Boolean = true,
    onClick: () -> Unit = {}
) {
    val background = containerColor ?: if (primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer
    val foreground = contentColor ?: if (primary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer
    Box(
        modifier = modifier
            .height(height)
            .then(if (elevation > 0.dp) Modifier.figmaDropShadow(radius = radius) else Modifier)
            .clip(RoundedCornerShape(radius))
            .background(background)
            .then(if (outlined) Modifier.border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(radius)) else Modifier)
            .alpha(if (enabled) 1f else 0.65f)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (iconRes != null) {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = foreground
                )
            }
            Text(
                text = text,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Medium,
                color = foreground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun MpodInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    trailingIconRes: Int? = null,
    trailingIconContentDescription: String? = null,
    onTrailingIconClick: (() -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        visualTransformation = visualTransformation,
        textStyle = TextStyle(
            fontFamily = MaterialTheme.typography.bodyLarge.fontFamily,
            fontSize = 16.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        ),
        modifier = modifier
            .height(36.dp)
            .figmaDropShadow(radius = 8.dp, blur = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.background)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 4.dp),
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            fontSize = 16.sp,
                            lineHeight = 24.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    innerTextField()
                }
                if (trailingIconRes != null) {
                    val iconModifier = if (onTrailingIconClick == null) {
                        Modifier.size(16.dp)
                    } else {
                        Modifier
                            .size(16.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onTrailingIconClick
                            )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Icon(
                        painter = painterResource(id = trailingIconRes),
                        contentDescription = trailingIconContentDescription,
                        modifier = iconModifier,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    )
}

@Composable
fun LabeledInput(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    trailingIconRes: Int? = null,
    trailingIconContentDescription: String? = null,
    onTrailingIconClick: (() -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground
        )
        MpodInput(
            value = value,
            onValueChange = onValueChange,
            placeholder = placeholder,
            trailingIconRes = trailingIconRes,
            trailingIconContentDescription = trailingIconContentDescription,
            onTrailingIconClick = onTrailingIconClick,
            visualTransformation = visualTransformation,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun MpodSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val track = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    val thumb = if (checked) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.background
    Box(
        modifier = modifier
            .size(width = 44.dp, height = 24.dp)
            .clip(CircleShape)
            .background(track)
            .alpha(if (enabled) 1f else 0.65f)
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(2.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(thumb)
        )
    }
}

@Composable
fun MpodBottomNav(
    currentRoute: String?,
    onNavigate: (Screen) -> Unit,
    modifier: Modifier = Modifier
) {
    val items = listOf(
        Triple(Screen.Home, "Home", R.drawable.ic_icon),
        Triple(Screen.Subscriptions, "Subscriptions", R.drawable.ic_icon_1),
        Triple(Screen.Settings, "Settings", R.drawable.ic_icon_2),
        Triple(Screen.AddPodcast, "Add podcast", R.drawable.ic_icon_3)
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(65.dp)
            .background(MaterialTheme.colorScheme.background),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { (screen, label, iconRes) ->
                val selected = currentRoute == screen.route
                val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                Column(
                    modifier = Modifier
                        .height(56.dp)
                        .clickable { onNavigate(screen) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .height(24.dp)
                            .width(if (screen == Screen.AddPodcast) 32.dp else 28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = iconRes),
                            contentDescription = label,
                            tint = color,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = label,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = color,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
fun MpodOutlinedSurface(
    modifier: Modifier = Modifier,
    radius: Dp = 8.dp,
    elevation: Dp = 0.dp,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier.then(
            if (elevation > 0.dp) Modifier.figmaDropShadow(radius = radius) else Modifier
        ),
        shape = RoundedCornerShape(radius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        content()
    }
}
