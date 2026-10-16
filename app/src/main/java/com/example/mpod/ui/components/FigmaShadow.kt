package com.example.mpod.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class FigmaShadowStyle(
    val elevation: Dp,
    val color: Color
) {
    TwoXs(elevation = 1.dp, color = Color(0x0D000000)),
    Xs(elevation = 2.dp, color = Color(0x1A000000)),
    Sm(elevation = 3.dp, color = Color(0x1A000000)),
    Md(elevation = 6.dp, color = Color(0x1F000000)),
    Lg(elevation = 12.dp, color = Color(0x26000000)),
    Xl(elevation = 20.dp, color = Color(0x2E000000)),
    TwoXl(elevation = 28.dp, color = Color(0x40000000));

    companion object {
        val Default = Sm
    }
}

val FigmaAmbientShadowColor = Color(0x66000000)
val FigmaSpotShadowColor = Color(0xB3000000)

fun Modifier.figmaDropShadow(
    style: FigmaShadowStyle,
    radius: Dp
): Modifier = figmaDropShadow(
    style = style,
    shape = RoundedCornerShape(radius)
)

fun Modifier.figmaDropShadow(
    style: FigmaShadowStyle,
    shape: Shape
): Modifier = shadow(
    elevation = style.elevation,
    shape = shape,
    clip = false,
    ambientColor = FigmaAmbientShadowColor,
    spotColor = FigmaSpotShadowColor
)

fun Modifier.figmaDropShadow(
    radius: Dp,
    offsetY: Dp = 1.dp,
    blur: Dp = 2.dp,
    color: Color = Color(0x1A000000)
): Modifier {
    val elevation = if (blur.value + offsetY.value > 0f) blur + offsetY else 0.dp

    return shadow(
        elevation = elevation,
        shape = RoundedCornerShape(radius),
        clip = false,
        ambientColor = FigmaAmbientShadowColor,
        spotColor = FigmaSpotShadowColor
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
