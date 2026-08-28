package com.nitronbox.app.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nitronbox.app.ui.theme.NitronTheme

/**
 * Flat Vercel-style slider: a thin hairline track with an accent fill and a round dot thumb —
 * no Material track caps. Supports tap-to-set and horizontal drag.
 */
@Composable
fun NitronSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
) {
    val trackColor = NitronTheme.colors.border
    val fillColor = NitronTheme.colors.accent
    val thumbFill = NitronTheme.colors.surface
    val thumbStroke = NitronTheme.colors.accent
    BoxWithConstraints(modifier.height(30.dp), contentAlignment = Alignment.CenterStart) {
        val trackWidth = maxWidth
        val density = LocalDensity.current
        val span = (valueRange.endInclusive - valueRange.start).coerceAtLeast(0.0001f)
        val fraction = ((value - valueRange.start) / span).coerceIn(0f, 1f)
        val thumbSize = 14.dp
        val trackHeight = 3.dp

        val rawThumbX: Dp = trackWidth * fraction
        val thumbX: Dp by animateDpAsState(
            targetValue = rawThumbX.coerceIn(0.dp, trackWidth - thumbSize),
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = 0.9f),
            label = "thumbX",
        )

        fun setFromX(x: Dp) {
            val f = (x / trackWidth).coerceIn(0f, 1f)
            onValueChange(valueRange.start + f * span)
        }

        Canvas(
            Modifier
                .fillMaxWidth()
                .height(30.dp)
                .pointerInput(valueRange) {
                    detectTapGestures { offset ->
                        setFromX(with(density) { offset.x.toDp() })
                    }
                }
                .pointerInput(valueRange) {
                    detectHorizontalDragGestures { change, _ ->
                        change.consume()
                        setFromX(with(density) { change.position.x.toDp() })
                    }
                },
        ) {
            val trackHeightPx = trackHeight.toPx()
            val centerY = size.height / 2f
            drawRoundRect(
                color = trackColor,
                topLeft = Offset(0f, centerY - trackHeightPx / 2f),
                size = Size(size.width, trackHeightPx),
                cornerRadius = CornerRadius(trackHeightPx / 2f),
            )
            val filledPx = thumbX.toPx().coerceIn(0f, size.width)
            drawRoundRect(
                color = fillColor,
                topLeft = Offset(0f, centerY - trackHeightPx / 2f),
                size = Size(filledPx, trackHeightPx),
                cornerRadius = CornerRadius(trackHeightPx / 2f),
            )
        }

        Box(
            Modifier
                .offset(x = thumbX)
                .size(thumbSize)
                .shadow(3.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.25f), spotColor = Color.Black.copy(alpha = 0.3f))
                .background(thumbFill, CircleShape)
                .border(2.dp, thumbStroke, CircleShape),
        )
    }
}
