package com.example.mpod.ui.components

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

fun Modifier.figmaDropShadow(
    radius: Dp,
    offsetY: Dp = 1.dp,
    blur: Dp = 2.dp,
    color: Color = Color(0x1A000000)
): Modifier = drawBehind {
    val blurPx = blur.toPx()
    val radiusPx = radius.toPx()
    val offsetYPx = offsetY.toPx()
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color.toArgb()
        maskFilter = BlurMaskFilter(blurPx, BlurMaskFilter.Blur.NORMAL)
    }

    drawIntoCanvas { canvas ->
        canvas.nativeCanvas.drawRoundRect(
            0f,
            offsetYPx,
            size.width,
            size.height + offsetYPx,
            radiusPx,
            radiusPx,
            paint
        )
    }
}

fun Modifier.figmaDashedBorder(
    color: Color,
    radius: Dp,
    strokeWidth: Dp = 1.dp,
    dash: Dp = 6.dp,
    gap: Dp = 6.dp
): Modifier = drawBehind {
    val strokePx = strokeWidth.toPx()
    val inset = strokePx / 2f
    drawRoundRect(
        color = color,
        topLeft = Offset(inset, inset),
        size = size.copy(
            width = size.width - strokePx,
            height = size.height - strokePx
        ),
        cornerRadius = CornerRadius(radius.toPx(), radius.toPx()),
        style = Stroke(
            width = strokePx,
            pathEffect = PathEffect.dashPathEffect(
                intervals = floatArrayOf(dash.toPx(), gap.toPx())
            )
        )
    )
}
