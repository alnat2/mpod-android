package com.example.mpod.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

fun Modifier.figmaDropShadow(
    radius: Dp,
    offsetY: Dp = 1.dp,
    blur: Dp = 2.dp,
    color: Color = Color(0x1A000000)
): Modifier {
    val elevation = if (blur.value + offsetY.value > 0f) blur + offsetY else 0.dp
    val shadowColor = color.copy(alpha = (color.alpha * 2.4f).coerceAtMost(1f))

    return shadow(
        elevation = elevation,
        shape = RoundedCornerShape(radius),
        clip = false,
        ambientColor = shadowColor,
        spotColor = shadowColor
    )
}

fun Modifier.figmaDashedBorder(
    color: Color,
    radius: Dp,
    strokeWidth: Dp = 1.dp,
    dash: Dp = 6.dp,
    gap: Dp = 6.dp
): Modifier = drawWithCache {
    val strokePx = strokeWidth.toPx()
    val inset = strokePx / 2f
    val stroke = Stroke(
        width = strokePx,
        pathEffect = PathEffect.dashPathEffect(
            intervals = floatArrayOf(dash.toPx(), gap.toPx())
        )
    )
    val cornerRadius = CornerRadius(radius.toPx(), radius.toPx())

    onDrawBehind {
        drawRoundRect(
            color = color,
            topLeft = Offset(inset, inset),
            size = size.copy(
                width = size.width - strokePx,
                height = size.height - strokePx
            ),
            cornerRadius = cornerRadius,
            style = stroke
        )
    }
}
