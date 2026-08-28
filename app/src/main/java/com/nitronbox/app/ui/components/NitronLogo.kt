package com.nitronbox.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path

/**
 * The NitronBox mark: two thin blades forming a lightning-like N with a clear gap.
 * Geometry was drawn in the logo editor and is kept exactly as exported (108-unit space).
 * The canvas letterboxes the mark, so it never stretches on non-square surfaces.
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
        val aspect = 108f / 108f
        val drawW = minOf(size.width, size.height * aspect)
        val drawH = drawW / aspect
        val offX = (size.width - drawW) / 2f
        val offY = (size.height - drawH) / 2f

        fun px(f: Float) = offX + drawW * f
        fun py(f: Float) = offY + drawH * f

        val leftBlade = Path().apply {
            moveTo(px(0.4438f), py(0.3594f))
            lineTo(px(0.5000f), py(0.4578f))
            lineTo(px(0.2047f), py(0.6266f))
            close()
        }
        val rightBlade = Path().apply {
            moveTo(px(0.4578f), py(0.5000f))
            lineTo(px(0.7531f), py(0.3313f))
            lineTo(px(0.5141f), py(0.5984f))
            close()
        }
        drawPath(
            leftBlade,
            Brush.verticalGradient(
                listOf(leftTop, leftBottom),
                startY = py(0.3594f),
                endY = py(0.6266f),
            ),
        )
        drawPath(
            rightBlade,
            Brush.verticalGradient(
                listOf(rightTop, rightBottom),
                startY = py(0.3313f),
                endY = py(0.6430f),
            ),
        )
    }
}
