package com.tkno.miora.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp

@Composable
fun GoogleStyleViewfinder(
    centerX: Float,
    centerY: Float,
    width: Float,
    height: Float,
    rotationDegrees: Float = 0f,
    color: Color = Color(0xFF8AB4F8),
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val side = minOf(width, height)
        if (side <= 0f) return@Canvas

        val strokeWidth = 5.dp.toPx()
        val cornerLength = (side * 0.22f).coerceIn(24.dp.toPx(), 44.dp.toPx())
        val cornerRadius = (cornerLength * 0.5f).coerceIn(12.dp.toPx(), 22.dp.toPx())

        val halfW = width / 2f
        val halfH = height / 2f
        val left = centerX - halfW
        val top = centerY - halfH
        val right = centerX + halfW
        val bottom = centerY + halfH

        rotate(degrees = rotationDegrees, pivot = androidx.compose.ui.geometry.Offset(centerX, centerY)) {
            // Top-Left
            drawPath(
                path = Path().apply {
                    moveTo(left, top + cornerLength)
                    lineTo(left, top + cornerRadius)
                    quadraticTo(left, top, left + cornerRadius, top)
                    lineTo(left + cornerLength, top)
                },
                color = color,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )

            // Top-Right
            drawPath(
                path = Path().apply {
                    moveTo(right - cornerLength, top)
                    lineTo(right - cornerRadius, top)
                    quadraticTo(right, top, right, top + cornerRadius)
                    lineTo(right, top + cornerLength)
                },
                color = color,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )

            // Bottom-Left
            drawPath(
                path = Path().apply {
                    moveTo(left, bottom - cornerLength)
                    lineTo(left, bottom - cornerRadius)
                    quadraticTo(left, bottom, left + cornerRadius, bottom)
                    lineTo(left + cornerLength, bottom)
                },
                color = color,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )

            // Bottom-Right
            drawPath(
                path = Path().apply {
                    moveTo(right - cornerLength, bottom)
                    lineTo(right - cornerRadius, bottom)
                    quadraticTo(right, bottom, right, bottom - cornerRadius)
                    lineTo(right, bottom - cornerLength)
                },
                color = color,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
        }
    }
}
