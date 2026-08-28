package com.nitronbox.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path

/**
 * The NitronBox mark: two sharp blades forming an N with a clear gap between them —
 * the left blade rises, the right one falls.
 */
@Composable
fun NitronLogo(
    modifier: Modifier = Modifier,
    topColor: Color = Color(0xFF8AB6FF),
    bottomColor: Color = Color(0xFF3FC8F5),
) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val leftBlade = Path().apply {
            moveTo(w * 0.06f, h * 0.84f)
            lineTo(w * 0.46f, h * 0.16f)
            lineTo(w * 0.46f, h * 0.84f)
            close()
        }
        val rightBlade = Path().apply {
            moveTo(w * 0.54f, h * 0.16f)
            lineTo(w * 0.94f, h * 0.84f)
            lineTo(w * 0.54f, h * 0.84f)
            close()
        }
        drawPath(
            leftBlade,
            brush = Brush.linearGradient(
                colors = listOf(topColor, bottomColor),
                start = Offset(w * 0.06f, h * 0.84f),
                end = Offset(w * 0.46f, h * 0.16f),
            ),
        )
        drawPath(
            rightBlade,
            brush = Brush.linearGradient(
                colors = listOf(topColor, bottomColor),
                start = Offset(w * 0.54f, h * 0.16f),
                end = Offset(w * 0.94f, h * 0.84f),
            ),
        )
    }
}
