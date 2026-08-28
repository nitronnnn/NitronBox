package com.nitronbox.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path

/**
 * The NitronBox mark: two thin blades forming a lightning-like N with a clear gap.
 * Geometry was drawn in the logo editor and is kept exactly as exported (108-unit space).
 */
@Composable
fun NitronLogo(
    modifier: Modifier = Modifier,
    leftTop: Color = Color.White,
    leftBottom: Color = Color(0xFF666666),
    rightTop: Color = Color.White,
    rightBottom: Color = Color(0xFF545454),
) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val leftBlade = Path().apply {
            moveTo(w * 0.4438f, h * 0.3594f)
            lineTo(w * 0.5000f, h * 0.4578f)
            lineTo(w * 0.2047f, h * 0.6266f)
            close()
        }
        val rightBlade = Path().apply {
            moveTo(w * 0.4578f, h * 0.5000f)
            lineTo(w * 0.7531f, h * 0.3313f)
            lineTo(w * 0.5141f, h * 0.5984f)
            close()
        }
        drawPath(leftBlade, Brush.verticalGradient(listOf(leftTop, leftBottom)))
        drawPath(rightBlade, Brush.verticalGradient(listOf(rightTop, rightBottom)))
    }
}
