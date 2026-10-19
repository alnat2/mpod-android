package com.example.mpod.ui.components

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val FigmaAmbientShadowColor = Color(0x66000000)
val FigmaSpotShadowColor = Color(0xB3000000)

/**
 * Android implementation of Figma Shadow Tokens.
 * Encapsulates elevation, vertical offset (offsetY), blur radius, and shadow color.
 */
enum class FigmaShadowStyle(
    val elevation: Dp,
    val offsetY: Dp,
    val blur: Dp,
    val color: Color,
    val ambientColor: Color = FigmaAmbientShadowColor,
    val spotColor: Color = FigmaSpotShadowColor
) {
    TwoXs(
        elevation = 1.dp,
        offsetY = 1.dp,
        blur = 2.dp,
        color = Color(0x1A000000)
    ),
    Xs(
        elevation = 2.dp,
        offsetY = 1.dp,
        blur = 2.dp,
        color = Color(0x33000000)
    ),
    Sm(
        elevation = 3.dp,
        offsetY = 1.dp,
        blur = 3.dp,
        color = Color(0x4D000000)
    ),
    Md(
        elevation = 6.dp,
        offsetY = 2.dp,
        blur = 4.dp,
        color = Color(0x99000000), // 60% black
        ambientColor = Color(0x80000000),
        spotColor = Color(0xCC000000)
    ),
    Lg(
        elevation = 12.dp,
        offsetY = 4.dp,
        blur = 8.dp,
        color = Color(0xB3000000), // 70% black
        ambientColor = Color(0x80000000),
        spotColor = Color(0xE6000000)
    ),
    Xl(
        elevation = 20.dp,
        offsetY = 8.dp,
        blur = 12.dp,
        color = Color(0xCC000000), // 80% black
        ambientColor = Color(0x99000000),
        spotColor = Color(0xF2000000)
    ),
    TwoXl(
        elevation = 28.dp,
        offsetY = 12.dp,
        blur = 16.dp,
        color = Color(0xE6000000) // 90% black
    );

    companion object {
        val Default = Sm
    }
}

/**
 * Applies a drop shadow to the modifier using the given Figma shadow token.
 * Renders elevation for platform consistency and canvas blur for rich contrast on dark backgrounds.
 */
fun Modifier.figmaDropShadow(
    style: FigmaShadowStyle,
    shape: Shape = RoundedCornerShape(0.dp)
): Modifier = this
    .shadow(
        elevation = style.elevation,
        shape = shape,
        clip = false,
        ambientColor = style.ambientColor,
        spotColor = style.spotColor
    )
    .drawBehind {
        if (style.blur <= 0.dp && style.offsetY == 0.dp) return@drawBehind
        drawIntoCanvas { canvas ->
            val paint = Paint()
            val frameworkPaint = paint.asFrameworkPaint()
            frameworkPaint.color = style.color.toArgb()
            if (style.blur > 0.dp) {
                frameworkPaint.maskFilter = android.graphics.BlurMaskFilter(
                    style.blur.toPx(),
                    android.graphics.BlurMaskFilter.Blur.NORMAL
                )
            }
            val cornerRadiusPx = when (shape) {
                is RoundedCornerShape -> shape.topStart.toPx(size, this)
                CircleShape -> size.minDimension / 2f
                else -> 0f
            }
            val left = 0f
            val top = style.offsetY.toPx()
            val right = size.width
            val bottom = size.height + style.offsetY.toPx()
            canvas.nativeCanvas.drawRoundRect(
                left, top, right, bottom,
                cornerRadiusPx, cornerRadiusPx,
                frameworkPaint
            )
        }
    }

fun Modifier.figmaDropShadow(
    style: FigmaShadowStyle,
    radius: Dp
): Modifier = figmaDropShadow(
    style = style,
    shape = RoundedCornerShape(radius)
)

fun Modifier.figmaDropShadow(
    radius: Dp
): Modifier = figmaDropShadow(
    style = FigmaShadowStyle.Default,
    radius = radius
)

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
